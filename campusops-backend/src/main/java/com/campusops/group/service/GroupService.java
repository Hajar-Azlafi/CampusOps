package com.campusops.group.service;

import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.group.dto.GroupRequestDto;
import com.campusops.group.dto.GroupResponseDto;
import com.campusops.group.entity.Group;
import com.campusops.group.mapper.GroupMapper;
import com.campusops.group.repository.GroupRepository;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.validation.ReferentialStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final PromotionRepository promotionRepository;
    private final AcademicYearRepository academicYearRepository;
    private final GroupMapper groupMapper;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;

    public GroupResponseDto createGroup(GroupRequestDto request) {
        Promotion promotion = findPromotionOrThrow(request.getPromotionId());
        accessScope.assertProgramAccessible(promotion.getProgram().getId());

        // §3/§7 : un groupe ne peut naitre que sous une promotion utilisable
        // (promotion active, filiere active, departement actif, annee active).
        ReferentialStatus.requireUsable(promotion);

        if (groupRepository.existsByPromotionIdAndNom(promotion.getId(), request.getNom())) {
            throw new DuplicateResourceException(
                    "Un groupe avec le nom " + request.getNom()
                            + " existe déjà dans cette promotion");
        }

        Group group = groupMapper.toEntity(request);
        group.setPromotion(promotion);
        group.setActif(true);

        Group saved = groupRepository.save(group);
        return groupMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public GroupResponseDto getGroupById(Long id) {
        Group group = findGroupOrThrow(id);
        accessScope.assertProgramAccessible(group.getPromotion().getProgram().getId());
        return groupMapper.toResponseDto(group);
    }

    @Transactional(readOnly = true)
    public List<GroupResponseDto> filterGroups(Boolean actif, Long academicYearId) {
        // Les groupes sont propres a une annee universitaire (§12). Sans annee
        // explicite, on se cale sur l'annee active : la page « gestion des groupes »
        // n'affiche que les groupes de l'annee active ; ceux des annees passees
        // restent en base et consultables via l'annee correspondante.
        Long effectiveYearId = resolveEffectiveYearId(academicYearId);

        List<Group> groups;
        if (accessScope.isAdmin()) {
            if (effectiveYearId != null) {
                groups = (actif != null)
                        ? groupRepository.findByPromotion_AcademicYear_IdAndActif(effectiveYearId, actif)
                        : groupRepository.findByPromotion_AcademicYear_Id(effectiveYearId);
            } else {
                groups = (actif != null)
                        ? groupRepository.findByActif(actif)
                        : groupRepository.findAll();
            }
        } else {
            groups = groupRepository.findByPromotion_Program_IdIn(accessScope.myProgramIds());
            if (effectiveYearId != null) {
                groups = groups.stream()
                        .filter(g -> g.getPromotion() != null
                                && g.getPromotion().getAcademicYear() != null
                                && g.getPromotion().getAcademicYear().getId().equals(effectiveYearId))
                        .toList();
            }
            if (actif != null) {
                groups = groups.stream().filter(g -> g.isActif() == actif).toList();
            }
        }
        return groups.stream().map(groupMapper::toResponseDto).toList();
    }

    /**
     * Resout l'annee de filtrage : celle demandee, sinon l'annee active ; ou
     * {@code null} si aucune annee active (repli non cassant, historique visible).
     */
    private Long resolveEffectiveYearId(Long academicYearId) {
        if (academicYearId != null) {
            return academicYearId;
        }
        return academicYearRepository.findFirstByActifTrue()
                .map(y -> y.getId())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<GroupResponseDto> getGroupsByPromotion(Long promotionId, Boolean actif) {
        Promotion promotion = findPromotionOrThrow(promotionId);
        accessScope.assertProgramAccessible(promotion.getProgram().getId());
        // §13 : pour alimenter un select, actif=true ne renvoie que les groupes
        // UTILISABLES (chaine parente active : promotion, filiere, departement,
        // annee). actif=false = inactifs (historique/admin). null = tout.
        List<Group> groups;
        if (Boolean.TRUE.equals(actif)) {
            groups = groupRepository.findUsableByPromotionId(promotionId);
        } else if (Boolean.FALSE.equals(actif)) {
            groups = groupRepository.findByPromotionId(promotionId).stream()
                    .filter(g -> !g.isActif())
                    .toList();
        } else {
            groups = groupRepository.findByPromotionId(promotionId);
        }
        return groups.stream()
                .map(groupMapper::toResponseDto)
                .toList();
    }

    /**
     * Consultation des groupes d'une année universitaire donnée (courante ou
     * historique), via la promotion. Garantit l'isolation par année : les
     * groupes d'une année passée restent consultables sans se mélanger à ceux
     * de l'année active.
     */
    @Transactional(readOnly = true)
    public List<GroupResponseDto> getGroupsByAcademicYear(Long academicYearId, Boolean actif) {
        if (!academicYearRepository.existsById(academicYearId)) {
            throw new ResourceNotFoundException(
                    "Année universitaire introuvable avec l'id " + academicYearId);
        }
        List<Group> groups = (actif != null)
                ? groupRepository.findByPromotion_AcademicYear_IdAndActif(academicYearId, actif)
                : groupRepository.findByPromotion_AcademicYear_Id(academicYearId);
        groups = restrictToScope(groups);
        return groups.stream().map(groupMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupResponseDto> searchGroups(String keyword) {
        return restrictToScope(groupRepository.searchByKeyword(keyword)).stream()
                .map(groupMapper::toResponseDto)
                .toList();
    }

    public GroupResponseDto updateGroup(Long id, GroupRequestDto request) {
        Group group = findGroupOrThrow(id);
        accessScope.assertProgramAccessible(group.getPromotion().getProgram().getId());
        Promotion promotion = findPromotionOrThrow(request.getPromotionId());
        accessScope.assertProgramAccessible(promotion.getProgram().getId());

        // §16 : un rattachement inchange reste autorise, mais on ne deplace pas
        // un groupe vers une promotion inutilisable (inactive ou dont un parent
        // l'est). L'historique n'est pas bloque retroactivement.
        boolean promotionChanged = group.getPromotion() == null
                || !promotion.getId().equals(group.getPromotion().getId());
        if (promotionChanged) {
            ReferentialStatus.requireUsable(promotion);
        }

        if (groupRepository.existsByPromotionIdAndNomAndIdNot(
                promotion.getId(), request.getNom(), id)) {
            throw new DuplicateResourceException(
                    "Un groupe avec le nom " + request.getNom()
                            + " existe déjà dans cette promotion");
        }

        group.setNom(request.getNom());
        group.setPromotion(promotion);
        group.setAnneeNiveau(request.getAnneeNiveau());
        group.setEffectif(request.getEffectif());

        Group updated = groupRepository.save(group);
        return groupMapper.toResponseDto(updated);
    }

    /**
     * Desactive un groupe (desactivation logique). Un groupe est une feuille de
     * la hierarchie referentielle : sa desactivation ne cascade pas (les
     * emplois du temps/sessions sont des enregistrements historiques,
     * conserves). Le groupe cesse simplement d'etre propose pour de nouvelles
     * operations (§7).
     */
    public void deactivateGroup(Long id) {
        Group group = findGroupOrThrow(id);
        accessScope.assertProgramAccessible(group.getPromotion().getProgram().getId());
        group.setActif(false);
        groupRepository.save(group);
    }

    /**
     * Reactive un groupe. Interdit tant que sa promotion parente n'est pas
     * elle-meme utilisable (§16) : promotion active, filiere active, departement
     * actif, annee universitaire active. On remonte donc toute la chaine.
     */
    public void activateGroup(Long id) {
        Group group = findGroupOrThrow(id);
        accessScope.assertProgramAccessible(group.getPromotion().getProgram().getId());
        if (!ReferentialStatus.usable(group.getPromotion())) {
            throw new BadRequestException(
                    "Impossible de réactiver ce groupe : sa promotion n'est pas "
                            + "utilisable (promotion, filière, département ou année "
                            + "universitaire désactivé). Réactivez d'abord le parent.");
        }
        group.setActif(true);
        groupRepository.save(group);
    }

    /**
     * Compteurs d'impact avant suppression d'un groupe : usages metier qui la
     * bloquent (seances, emplois du temps, occupations/examens, reservations).
     * Un groupe est une feuille : aucune suppression en cascade. Sert a la modale
     * de confirmation. Perimetre : ADMIN, ou RP proprietaire de la filiere.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        Group group = findGroupOrThrow(id);
        accessScope.assertProgramAccessible(group.getPromotion().getProgram().getId());
        return deletionAnalyzer.analyze(group);
    }

    /**
     * Supprime un groupe. Feuille de la hierarchie : aucun enfant en cascade. La
     * suppression est refusee, avec message metier, tant qu'un usage (seances,
     * emplois du temps, occupations/examens, reservations) le reference — il faut
     * d'abord liberer ces usages (« suppression apres modification »). Le
     * parametre {@code cascade} est sans effet (aucun enfant) : il n'existe que
     * pour l'uniformite de l'API de suppression.
     */
    public void deleteGroup(Long id, boolean cascade) {
        Group group = findGroupOrThrow(id);
        accessScope.assertProgramAccessible(group.getPromotion().getProgram().getId());
        DeletionImpact impact = deletionAnalyzer.analyze(group);
        impact.requireConfirmed(cascade);
        groupRepository.delete(group);
    }

    /**
     * Restreint une liste de groupes au perimetre de l'utilisateur courant.
     * ADMIN : liste inchangee. RP : uniquement les groupes de SES filieres.
     */
    private List<Group> restrictToScope(List<Group> groups) {
        if (accessScope.isAdmin()) {
            return groups;
        }
        Set<Long> mine = accessScope.myProgramIdSet();
        return groups.stream()
                .filter(g -> g.getPromotion() != null
                        && g.getPromotion().getProgram() != null
                        && mine.contains(g.getPromotion().getProgram().getId()))
                .toList();
    }

    private Group findGroupOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Groupe introuvable avec l'id " + id));
    }

    private Promotion findPromotionOrThrow(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Promotion introuvable avec l'id " + id));
    }
}
