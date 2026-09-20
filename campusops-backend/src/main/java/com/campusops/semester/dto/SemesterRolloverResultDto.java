package com.campusops.semester.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Résultat récapitulatif d'une bascule de semestre (§20). Non destructif :
 * aucun semestre ni emploi du temps n'est supprimé — les niveaux avancent d'un
 * semestre et les emplois du temps de l'année active du semestre quitté sont
 * archivés (leurs salles sont alors automatiquement libérées, cf. F1).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemesterRolloverResultDto {

    /** Niveaux ayant effectivement avancé au semestre suivant. */
    private List<SemesterAdvanceDto> avancements;

    /**
     * Niveaux inchangés car déjà au dernier semestre de leur cycle (aucun
     * semestre actif d'ordre supérieur). Libellés « Niveau (Semestre) ».
     */
    private List<String> semestresAuDernier;

    /** Nombre d'emplois du temps archivés (salles libérées) durant la bascule. */
    private int emploisDuTempsArchives;

    /** Message de synthèse prêt à afficher (français). */
    private String message;
}
