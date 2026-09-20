package com.campusops.semester.service;

import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.semester.dto.SemesterRequestDto;
import com.campusops.semester.dto.SemesterResponseDto;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.mapper.SemesterMapper;
import com.campusops.semester.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final LevelRepository levelRepository;
    private final SemesterMapper semesterMapper;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;

    public SemesterResponseDto createSemester(SemesterRequestDto request) {
        accessScope.requireAdmin();
        Level level = resolveLevel(request.getLevelId());
        ensureNomUniqueDansNiveau(request.getNom(), level, null);
        validatePeriode(request.getDateDebut(), request.getDateFin());

        Semester semester = semesterMapper.toEntity(request);
        semester.setLevel(level);
        semester.setActif(true);

        Semester saved = semesterRepository.save(semester);
        return semesterMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public SemesterResponseDto getSemesterById(Long id) {
        return semesterMapper.toResponseDto(findSemesterOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<SemesterResponseDto> filterSemesters(Boolean actif, Long levelId) {
        List<Semester> semesters;
        if (levelId != null) {
            semesters = (actif != null)
                    ? semesterRepository.findByLevelIdAndActifOrderByOrdreAsc(levelId, actif)
                    : semesterRepository.findByLevelIdOrderByOrdreAsc(levelId);
        } else if (actif != null) {
            semesters = semesterRepository.findByActif(actif);
        } else {
            semesters = semesterRepository.findAllByOrderByOrdreAsc();
        }
        return semesters.stream().map(semesterMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SemesterResponseDto> searchSemesters(String keyword) {
        return semesterRepository.searchByKeyword(keyword).stream()
                .map(semesterMapper::toResponseDto)
                .toList();
    }

    public SemesterResponseDto updateSemester(Long id, SemesterRequestDto request) {
        accessScope.requireAdmin();
        Semester semester = findSemesterOrThrow(id);
        Level level = resolveLevel(request.getLevelId());
        ensureNomUniqueDansNiveau(request.getNom(), level, id);
        validatePeriode(request.getDateDebut(), request.getDateFin());

        semester.setNom(request.getNom());
        semester.setOrdre(request.getOrdre());
        semester.setLevel(level);
        semester.setDateDebut(request.getDateDebut());
        semester.setDateFin(request.getDateFin());

        Semester updated = semesterRepository.save(semester);
        return semesterMapper.toResponseDto(updated);
    }

    public void deactivateSemester(Long id) {
        accessScope.requireAdmin();
        Semester semester = findSemesterOrThrow(id);
        semester.setActif(false);
        semesterRepository.save(semester);
    }

    public void activateSemester(Long id) {
        accessScope.requireAdmin();
        Semester semester = findSemesterOrThrow(id);
        semester.setActif(true);
        semesterRepository.save(semester);
    }

    /**
     * Compteurs d'impact avant suppression d'un semestre : usages metier qui la
     * bloquent (modules ancres sur ce semestre, seances, emplois du temps,
     * examens). Sert a la modale de confirmation.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findSemesterOrThrow(id));
    }

    /**
     * Supprime un semestre. Referentiel : aucun enfant en cascade. La suppression
     * est refusee, avec message metier, tant qu'un module y est ancre ou qu'une
     * seance / emploi du temps / examen le reference : il faut d'abord liberer
     * ces usages (« suppression apres modification »). Le parametre {@code
     * cascade} est sans effet (aucun enfant) : il n'existe que pour l'uniformite
     * de l'API de suppression.
     */
    public void deleteSemester(Long id, boolean cascade) {
        accessScope.requireAdmin();
        Semester semester = findSemesterOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(semester);
        impact.requireConfirmed(cascade);
        semesterRepository.delete(semester);
    }

    /**
     * Semestres actuellement courants — support de la bascule (§20). Un niveau
     * peut en compter plusieurs (années coexistantes) ; ils sont tous renvoyés.
     */
    @Transactional(readOnly = true)
    public List<SemesterResponseDto> getCurrentSemesters() {
        return semesterRepository.findByCourantTrue().stream()
                .map(semesterMapper::toResponseDto)
                .toList();
    }

    /**
     * Ajoute le semestre à l'ensemble des semestres courants de son niveau. Un
     * niveau peut avoir <b>plusieurs</b> semestres courants simultanés lorsqu'il
     * couvre plusieurs années (ex. S1 + S3 pour un tronc commun, S1 + S3 + S5
     * pour un cycle ingénieur), car des cohortes d'années différentes coexistent.
     * Les autres courants du niveau ne sont donc PAS dégagés. Opération
     * idempotente ; un semestre inactif est refusé.
     */
    public SemesterResponseDto setCourant(Long id) {
        accessScope.requireAdmin();
        Semester cible = findSemesterOrThrow(id);
        if (!cible.isActif()) {
            throw new BadRequestException(
                    "Un semestre inactif ne peut pas être défini comme courant.");
        }
        cible.setCourant(true);
        return semesterMapper.toResponseDto(semesterRepository.save(cible));
    }

    /**
     * Retire le semestre de l'ensemble des semestres courants de son niveau, sans
     * rien supprimer. Opération inverse de {@link #setCourant(Long)}, idempotente.
     */
    public SemesterResponseDto unsetCourant(Long id) {
        accessScope.requireAdmin();
        Semester cible = findSemesterOrThrow(id);
        cible.setCourant(false);
        return semesterMapper.toResponseDto(semesterRepository.save(cible));
    }

    private Semester findSemesterOrThrow(Long id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Semestre introuvable avec l'id " + id));
    }

    /** Résout le niveau associé (optionnel). */
    private Level resolveLevel(Long levelId) {
        if (levelId == null) {
            return null;
        }
        return levelRepository.findById(levelId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Niveau introuvable avec l'id " + levelId));
    }

    /**
     * Vérifie la cohérence de la période du semestre : si les deux bornes sont
     * fournies, la fin doit être postérieure ou égale au début. Les bornes nulles
     * sont tolérées (période non renseignée ou ouverte).
     */
    private void validatePeriode(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut != null && dateFin != null && dateFin.isBefore(dateDebut)) {
            throw new BadRequestException(
                    "La date de fin du semestre doit être postérieure ou égale à la date de début.");
        }
    }

    /**
     * Vérifie l'unicité du nom AU SEIN du niveau. Un même libellé (« S1 ») est
     * autorisé dans deux niveaux différents mais interdit deux fois dans le
     * même niveau. Les semestres sans niveau sont contrôlés par nom seul.
     */
    private void ensureNomUniqueDansNiveau(String nom, Level level, Long idExclu) {
        boolean doublon;
        if (level == null) {
            doublon = (idExclu == null)
                    ? semesterRepository.existsByNomAndLevelIsNull(nom)
                    : semesterRepository.existsByNomAndLevelIsNullAndIdNot(nom, idExclu);
        } else {
            doublon = (idExclu == null)
                    ? semesterRepository.existsByNomAndLevelId(nom, level.getId())
                    : semesterRepository.existsByNomAndLevelIdAndIdNot(nom, level.getId(), idExclu);
        }
        if (doublon) {
            String contexte = (level == null) ? "" : " pour le niveau « " + level.getNom() + " »";
            throw new DuplicateResourceException(
                    "Un semestre nommé « " + nom + " » existe déjà" + contexte);
        }
    }
}
