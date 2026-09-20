package com.campusops.academicyear.service;

import com.campusops.academicyear.dto.AcademicYearRequestDto;
import com.campusops.academicyear.dto.AcademicYearResponseDto;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passage à l'année universitaire suivante — méthode <b>sûre et contrôlée par
 * l'administrateur</b> :
 * <ul>
 *   <li>ne supprime jamais de donnée (les années/promotions/groupes/emplois du
 *       temps précédents restent consultables) ;</li>
 *   <li>crée l'année suivante si elle n'existe pas, ou réutilise celle déjà
 *       créée (get-or-create par libellé) ;</li>
 *   <li>active la nouvelle année en désactivant l'ancienne (invariant « une
 *       seule année active » garanti par {@link AcademicYearService}) ;</li>
 *   <li>amorce la structure (promotions + groupes) de la nouvelle année de
 *       façon idempotente.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicYearRolloverService {

    private static final Pattern LIBELLE_PATTERN = Pattern.compile("(\\d{4})\\D+(\\d{4})");

    private final AcademicYearRepository academicYearRepository;
    private final AcademicYearService academicYearService;
    private final AcademicStructureService academicStructureService;

    /**
     * Bascule vers l'année suivant l'année active courante.
     *
     * @return la nouvelle année active
     */
    @Transactional
    public AcademicYearResponseDto passerAAnneeSuivante() {
        AcademicYear current = academicYearRepository.findFirstByActifTrue()
                .orElseThrow(() -> new BadRequestException(
                        "Aucune année universitaire active : impossible de déterminer l'année suivante."));

        String nextLibelle = computeNextLibelle(current.getLibelle());

        Long newYearId;
        Optional<AcademicYear> existing = academicYearRepository.findByLibelle(nextLibelle);
        if (existing.isPresent()) {
            newYearId = existing.get().getId();
            academicYearService.activateAcademicYear(newYearId);
            log.info("Passage à l'année suivante : année « {} » déjà existante, activée.", nextLibelle);
        } else {
            AcademicYearRequestDto request = AcademicYearRequestDto.builder()
                    .libelle(nextLibelle)
                    .dateDebut(nextDateDebut(current, nextLibelle))
                    .dateFin(nextDateFin(current, nextLibelle))
                    .definirCommeActive(true)
                    .build();
            AcademicYearResponseDto created = academicYearService.createAcademicYear(request);
            newYearId = created.getId();
            log.info("Passage à l'année suivante : nouvelle année « {} » créée et activée.", nextLibelle);
        }

        AcademicYear newYear = academicYearRepository.findById(newYearId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Année universitaire introuvable après bascule (id " + newYearId + ")"));

        AcademicStructureService.SeedCounts counts =
                academicStructureService.seedPromotionsAndGroupsForYear(newYear);
        log.info("Structure de « {} » amorcée : {} promotion(s), {} groupe(s).",
                nextLibelle, counts.promotions(), counts.groups());

        return academicYearService.getAcademicYearById(newYearId);
    }

    /**
     * Déduit le libellé de l'année suivante à partir d'un libellé « AAAA-AAAA ».
     * Exemple : « 2026-2027 » → « 2027-2028 ». Lève une erreur explicite si le
     * format ne permet pas d'inférer l'année suivante (l'admin crée alors
     * l'année manuellement).
     */
    String computeNextLibelle(String libelle) {
        if (libelle != null) {
            Matcher m = LIBELLE_PATTERN.matcher(libelle);
            if (m.find()) {
                int start = Integer.parseInt(m.group(1));
                int end = Integer.parseInt(m.group(2));
                return (start + 1) + "-" + (end + 1);
            }
        }
        throw new BadRequestException(
                "Impossible de déterminer automatiquement l'année suivante à partir du libellé « "
                        + libelle + " ». Créez la nouvelle année manuellement.");
    }

    private LocalDate nextDateDebut(AcademicYear current, String nextLibelle) {
        if (current.getDateDebut() != null) {
            return current.getDateDebut().plusYears(1);
        }
        int startYear = firstYearOf(nextLibelle);
        return LocalDate.of(startYear, 9, 1);
    }

    private LocalDate nextDateFin(AcademicYear current, String nextLibelle) {
        if (current.getDateFin() != null) {
            return current.getDateFin().plusYears(1);
        }
        int endYear = secondYearOf(nextLibelle);
        return LocalDate.of(endYear, 6, 30);
    }

    private int firstYearOf(String libelle) {
        Matcher m = LIBELLE_PATTERN.matcher(libelle);
        if (m.find()) return Integer.parseInt(m.group(1));
        return LocalDate.now().getYear();
    }

    private int secondYearOf(String libelle) {
        Matcher m = LIBELLE_PATTERN.matcher(libelle);
        if (m.find()) return Integer.parseInt(m.group(2));
        return LocalDate.now().getYear() + 1;
    }
}
