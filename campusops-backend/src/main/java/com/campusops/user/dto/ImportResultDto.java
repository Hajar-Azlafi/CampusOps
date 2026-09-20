package com.campusops.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResultDto {
    private int totalRows;
    private int successCount;

    /**
     * Comptes deja existants qui ont ete <b>mis a jour</b> au lieu d'etre signales
     * en doublon. Toujours 0 tant que le parametre « ecrasement des donnees
     * existantes » (§9) reste desactive, ce qui est la valeur par defaut.
     */
    private int updatedCount;

    private int errorCount;
    private List<CreatedAccountDto> createdAccounts;
    private List<ImportRowErrorDto> errors;

    /**
     * Message de synthese, renseigne lorsque le resultat merite une explication :
     * fichier refuse en bloc par la validation automatique, ou lignes ignorees en
     * mode tolerant. {@code null} dans le cas courant.
     */
    private String message;
}