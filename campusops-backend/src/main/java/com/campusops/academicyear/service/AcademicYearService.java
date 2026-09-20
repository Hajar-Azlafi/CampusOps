package com.campusops.academicyear.service;

import com.campusops.academicyear.dto.AcademicYearRequestDto;
import com.campusops.academicyear.dto.AcademicYearResponseDto;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.mapper.AcademicYearMapper;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final AcademicYearMapper academicYearMapper;
    private final DeletionAnalyzer deletionAnalyzer;

    public AcademicYearResponseDto createAcademicYear(AcademicYearRequestDto request) {
        if (academicYearRepository.existsByLibelle(request.getLibelle())) {
            throw new DuplicateResourceException(
                    "Une année universitaire avec le libellé " + request.getLibelle() + " existe déjà");
        }
        if (request.getDateFin().isBefore(request.getDateDebut())) {
            throw new BadRequestException(
                    "La date de fin doit être postérieure à la date de début");
        }

        AcademicYear academicYear = academicYearMapper.toEntity(request);

        // Règle métier : une seule année active à la fois.
        // La toute première année créée devient active d'office (sinon le système
        // n'aurait aucune année courante). Ensuite, une nouvelle année n'est active
        // que si l'administrateur le demande explicitement (definirCommeActive),
        // auquel cas l'ancienne année active est automatiquement désactivée.
        boolean aucuneAnneeActive = academicYearRepository.countByActif(true) == 0;
        boolean doitEtreActive = aucuneAnneeActive || Boolean.TRUE.equals(request.getDefinirCommeActive());

        academicYear.setActif(false);
        AcademicYear saved = academicYearRepository.save(academicYear);

        if (doitEtreActive) {
            desactiverToutesLesAutresAnnees(saved.getId());
            saved.setActif(true);
            saved = academicYearRepository.save(saved);
        }

        return academicYearMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public AcademicYearResponseDto getCurrentAcademicYear() {
        AcademicYear current = academicYearRepository.findFirstByActifTrue()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune année universitaire active n'est définie"));
        return academicYearMapper.toResponseDto(current);
    }


    @Transactional(readOnly = true)
    public AcademicYearResponseDto getAcademicYearById(Long id) {
        return academicYearMapper.toResponseDto(findAcademicYearOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponseDto> filterAcademicYears(Boolean actif) {
        List<AcademicYear> academicYears = (actif != null)
                ? academicYearRepository.findByActif(actif)
                : academicYearRepository.findAll();
        return academicYears.stream().map(academicYearMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponseDto> searchAcademicYears(String keyword) {
        return academicYearRepository.searchByKeyword(keyword).stream()
                .map(academicYearMapper::toResponseDto)
                .toList();
    }

    public AcademicYearResponseDto updateAcademicYear(Long id, AcademicYearRequestDto request) {
        AcademicYear academicYear = findAcademicYearOrThrow(id);

        if (academicYearRepository.existsByLibelleAndIdNot(request.getLibelle(), id)) {
            throw new DuplicateResourceException(
                    "Une année universitaire avec le libellé " + request.getLibelle() + " existe déjà");
        }
        if (request.getDateFin().isBefore(request.getDateDebut())) {
            throw new BadRequestException(
                    "La date de fin doit etre posterieure a la date de debut");
        }

        academicYear.setLibelle(request.getLibelle());
        academicYear.setDateDebut(request.getDateDebut());
        academicYear.setDateFin(request.getDateFin());

        AcademicYear updated = academicYearRepository.save(academicYear);
        return academicYearMapper.toResponseDto(updated);
    }

    public void deactivateAcademicYear(Long id) {
        AcademicYear academicYear = findAcademicYearOrThrow(id);
        // Garantir qu'il existe toujours au moins une année active :
        // on refuse de désactiver la dernière (ou l'unique) année active.
        if (academicYear.isActif() && academicYearRepository.countByActif(true) <= 1) {
            throw new BadRequestException(
                    "Impossible de désactiver l'unique année universitaire active. "
                            + "Activez d'abord une autre année.");
        }
        academicYear.setActif(false);
        academicYearRepository.save(academicYear);
    }

    /**
     * Active une année universitaire en garantissant l'invariant métier
     * « une seule année active à la fois ». L'opération est transactionnelle :
     * l'année active courante est d'abord désactivée (et flushée pour respecter
     * l'index unique partiel PostgreSQL), puis l'année cible est activée.
     */
    public void activateAcademicYear(Long id) {
        AcademicYear cible = findAcademicYearOrThrow(id);
        desactiverToutesLesAutresAnnees(id);
        if (!cible.isActif()) {
            cible.setActif(true);
            academicYearRepository.saveAndFlush(cible);
        }
    }

    /**
     * Compteurs d'impact avant suppression d'une année universitaire : usages
     * métier qui la bloquent (promotions, séances, emplois du temps,
     * occupations/examens, réservations rattachés à cette année). Une année
     * porteuse d'historique est ainsi bloquée : elle ne peut être supprimée par
     * accident. Sert à la modale de confirmation. Contrôle d'accès (ADMIN) assuré
     * par le contrôleur, comme pour les autres écritures de ce service.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        return deletionAnalyzer.analyze(findAcademicYearOrThrow(id));
    }

    /**
     * Supprime une année universitaire. Aucun enfant supprimé en cascade : la
     * suppression est refusée, avec message métier, dès qu'une donnée y est
     * rattachée (promotion, séance, emploi du temps, occupation/examen,
     * réservation), afin de <b>ne jamais détruire d'historique académique par
     * accident</b>. Seule une année vide (ex. créée par erreur, jamais amorcée)
     * peut être supprimée. Préférer la <b>désactivation</b> pour archiver une
     * année passée. Le paramètre {@code cascade} est sans effet (aucun enfant) :
     * il n'existe que pour l'uniformité de l'API. Contrôle d'accès (ADMIN) assuré
     * par le contrôleur.
     */
    public void deleteAcademicYear(Long id, boolean cascade) {
        AcademicYear year = findAcademicYearOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(year);
        impact.requireConfirmed(cascade);
        academicYearRepository.delete(year);
    }

    /**
     * Désactive toutes les années actives sauf celle dont l'id est exclu.
     * Le flush intermédiaire évite une violation transitoire de l'index unique
     * partiel (une seule ligne actif = true) avant d'activer la cible.
     */
    private void desactiverToutesLesAutresAnnees(Long exceptId) {
        List<AcademicYear> actives = academicYearRepository.findByActif(true);
        boolean modifie = false;
        for (AcademicYear annee : actives) {
            if (!annee.getId().equals(exceptId)) {
                annee.setActif(false);
                academicYearRepository.save(annee);
                modifie = true;
            }
        }
        if (modifie) {
            academicYearRepository.flush();
        }
    }

    private AcademicYear findAcademicYearOrThrow(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Année universitaire introuvable avec l'id " + id));
    }
}
