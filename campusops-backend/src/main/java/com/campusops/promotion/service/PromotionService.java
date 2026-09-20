package com.campusops.promotion.service;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionExecutor;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.promotion.dto.PromotionRequestDto;
import com.campusops.promotion.dto.PromotionResponseDto;
import com.campusops.promotion.entity.Promotion;
import com.campusops.promotion.mapper.PromotionMapper;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.validation.ReferentialCascadeService;
import com.campusops.validation.ReferentialStatus;
import com.campusops.validation.dto.DeactivationImpact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final ProgramRepository programRepository;
    private final LevelRepository levelRepository;
    private final AcademicYearRepository academicYearRepository;
    private final PromotionMapper promotionMapper;
    private final AccessScopeService accessScope;
    private final ReferentialCascadeService cascade;
    private final DeletionAnalyzer deletionAnalyzer;
    private final DeletionExecutor deletionExecutor;

    public PromotionResponseDto createPromotion(PromotionRequestDto request) {
        accessScope.requireAdmin();
        Program program = findProgramOrThrow(request.getProgramId());
        Level level = findLevelOrThrow(request.getLevelId());
        AcademicYear academicYear = findAcademicYearOrThrow(request.getAcademicYearId());

        // §3/§6 : on ne cree pas de promotion sous une filiere (ou un
        // departement) inactif, ni pour une annee universitaire desactivee.
        ReferentialStatus.requireUsable(program);
        ReferentialStatus.requireUsable(academicYear);

        if (promotionRepository.existsByProgramIdAndLevelIdAndAcademicYearId(
                program.getId(), level.getId(), academicYear.getId())) {
            throw new DuplicateResourceException(
                    "Une promotion existe déjà pour cette filière, ce niveau et cette année universitaire");
        }

        Promotion promotion = promotionMapper.toEntity(request);
        promotion.setProgram(program);
        promotion.setLevel(level);
        promotion.setAcademicYear(academicYear);
        promotion.setActif(true);

        Promotion saved = promotionRepository.save(promotion);
        return promotionMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public PromotionResponseDto getPromotionById(Long id) {
        Promotion promotion = findPromotionOrThrow(id);
        accessScope.assertProgramAccessible(promotion.getProgram().getId());
        return promotionMapper.toResponseDto(promotion);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponseDto> filterPromotions(Boolean actif) {
        List<Promotion> promotions;
        if (accessScope.isAdmin()) {
            promotions = (actif != null)
                    ? promotionRepository.findByActif(actif)
                    : promotionRepository.findAll();
        } else {
            promotions = promotionRepository.findByProgramIdIn(accessScope.myProgramIds());
            if (actif != null) {
                promotions = promotions.stream().filter(p -> p.isActif() == actif).toList();
            }
        }
        return promotions.stream().map(promotionMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PromotionResponseDto> getPromotionsByProgram(Long programId, Boolean actif) {
        if (!programRepository.existsById(programId)) {
            throw new ResourceNotFoundException("Filière introuvable avec l'id " + programId);
        }
        accessScope.assertProgramAccessible(programId);
        // §13 : pour alimenter un select, actif=true ne renvoie que les
        // promotions UTILISABLES (chaine parente active). actif=false renvoie les
        // inactives (vue historique/admin). null = tout.
        List<Promotion> promotions;
        if (Boolean.TRUE.equals(actif)) {
            promotions = promotionRepository.findUsableByProgramId(programId);
        } else if (Boolean.FALSE.equals(actif)) {
            promotions = promotionRepository.findByProgramId(programId).stream()
                    .filter(p -> !p.isActif())
                    .toList();
        } else {
            promotions = promotionRepository.findByProgramId(programId);
        }
        return promotions.stream()
                .map(promotionMapper::toResponseDto)
                .toList();
    }

    /**
     * Consultation des promotions d'une année universitaire donnée (courante ou
     * historique). Permet l'isolation par année : chaque année conserve ses
     * propres promotions/groupes sans mélange.
     */
    @Transactional(readOnly = true)
    public List<PromotionResponseDto> getPromotionsByAcademicYear(Long academicYearId, Boolean actif) {
        if (!academicYearRepository.existsById(academicYearId)) {
            throw new ResourceNotFoundException(
                    "Année universitaire introuvable avec l'id " + academicYearId);
        }
        List<Promotion> promotions = (actif != null)
                ? promotionRepository.findByAcademicYearIdAndActif(academicYearId, actif)
                : promotionRepository.findByAcademicYearId(academicYearId);
        promotions = restrictToScope(promotions);
        return promotions.stream().map(promotionMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PromotionResponseDto> searchPromotions(String keyword) {
        return restrictToScope(promotionRepository.searchByKeyword(keyword)).stream()
                .map(promotionMapper::toResponseDto)
                .toList();
    }

    public PromotionResponseDto updatePromotion(Long id, PromotionRequestDto request) {
        accessScope.requireAdmin();
        Promotion promotion = findPromotionOrThrow(id);
        Program program = findProgramOrThrow(request.getProgramId());
        Level level = findLevelOrThrow(request.getLevelId());
        AcademicYear academicYear = findAcademicYearOrThrow(request.getAcademicYearId());

        // §16 : on ne rattache pas une promotion a une filiere ou une annee
        // desactivee. Un rattachement inchange reste autorise (pas de blocage
        // retroactif sur une promotion deja historique).
        boolean programChanged = promotion.getProgram() == null
                || !program.getId().equals(promotion.getProgram().getId());
        if (programChanged) {
            ReferentialStatus.requireUsable(program);
        }
        boolean yearChanged = promotion.getAcademicYear() == null
                || !academicYear.getId().equals(promotion.getAcademicYear().getId());
        if (yearChanged) {
            ReferentialStatus.requireUsable(academicYear);
        }

        if (promotionRepository.existsByProgramIdAndLevelIdAndAcademicYearIdAndIdNot(
                program.getId(), level.getId(), academicYear.getId(), id)) {
            throw new DuplicateResourceException(
                    "Une promotion existe déjà pour cette filière, ce niveau et cette année universitaire");
        }

        promotion.setNom(request.getNom());
        promotion.setProgram(program);
        promotion.setLevel(level);
        promotion.setAcademicYear(academicYear);

        Promotion updated = promotionRepository.save(promotion);
        return promotionMapper.toResponseDto(updated);
    }

    /**
     * Desactive une promotion (desactivation logique) et propage vers le bas :
     * ses groupes deviennent inutilisables pour les nouvelles operations (§3,
     * §7, §15). Aucune donnee historique n'est supprimee.
     */
    public void deactivatePromotion(Long id) {
        accessScope.requireAdmin();
        Promotion promotion = findPromotionOrThrow(id);
        promotion.setActif(false);
        promotionRepository.save(promotion);
        cascade.onPromotionDeactivated(id);
    }

    /**
     * Reactive une promotion. Interdit tant que sa filiere ou son annee
     * universitaire parente est inactive (§16), et ne propage pas aux groupes
     * (ils gardent leur etat individuel).
     */
    public void activatePromotion(Long id) {
        accessScope.requireAdmin();
        Promotion promotion = findPromotionOrThrow(id);
        if (!ReferentialStatus.usable(promotion.getProgram())) {
            throw new BadRequestException(
                    "Impossible de réactiver cette promotion : sa filière « "
                            + safeName(promotion.getProgram() == null
                                    ? null : promotion.getProgram().getNom())
                            + " » est désactivée. Réactivez d'abord la filière.");
        }
        if (!ReferentialStatus.usable(promotion.getAcademicYear())) {
            throw new BadRequestException(
                    "Impossible de réactiver cette promotion : son année "
                            + "universitaire est désactivée. Réactivez-la d'abord.");
        }
        promotion.setActif(true);
        promotionRepository.save(promotion);
        cascade.onPromotionReactivated(id);
    }

    /** Compteurs d'impact d'une desactivation de promotion (§17). */
    @Transactional(readOnly = true)
    public DeactivationImpact getDeactivationImpact(Long id) {
        accessScope.requireAdmin();
        Promotion promotion = findPromotionOrThrow(id);
        accessScope.assertProgramAccessible(promotion.getProgram().getId());
        return cascade.promotionImpact(id);
    }

    /**
     * Compteurs d'impact avant suppression : groupes supprimes en cascade et
     * usages metier qui la bloquent (seances, emplois du temps,
     * occupations/examens). Sert a la modale de confirmation.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findPromotionOrThrow(id));
    }

    /**
     * Supprime une promotion. Suppression <b>hierarchique</b> : ses groupes sont
     * supprimes en cascade apres confirmation ({@code cascade == true}). Refusee,
     * avec message metier, si un usage subsiste (seances, emplois du temps,
     * occupations/examens) : il faut d'abord liberer ces usages.
     */
    public void deletePromotion(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Promotion promotion = findPromotionOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(promotion);
        impact.requireConfirmed(cascade);
        deletionExecutor.deletePromotion(promotion);
    }

    /**
     * Restreint une liste de promotions au perimetre de l'utilisateur courant.
     * ADMIN : liste inchangee. RP : uniquement les promotions de SES filieres.
     */
    private List<Promotion> restrictToScope(List<Promotion> promotions) {
        if (accessScope.isAdmin()) {
            return promotions;
        }
        Set<Long> mine = accessScope.myProgramIdSet();
        return promotions.stream()
                .filter(p -> p.getProgram() != null && mine.contains(p.getProgram().getId()))
                .toList();
    }

    private Promotion findPromotionOrThrow(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Promotion introuvable avec l'id " + id));
    }

    private static String safeName(String nom) {
        return (nom == null || nom.isBlank()) ? "sans nom" : nom;
    }

    private Program findProgramOrThrow(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Filière introuvable avec l'id " + id));
    }

    private Level findLevelOrThrow(Long id) {
        return levelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Niveau introuvable avec l'id " + id));
    }

    private AcademicYear findAcademicYearOrThrow(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Année universitaire introuvable avec l'id " + id));
    }
}
