package com.campusops.settings.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Persistance d'une liste courte de libelles techniques (extensions de fichiers
 * autorisees a l'import, §9) dans une colonne unique « xlsx,xls ».
 *
 * <p>Meme motivation que {@link WeekDaySetConverter} : eviter une table de
 * collection pour quelques valeurs lues a chaque import. Les valeurs sont
 * normalisees en minuscules, dedoublonnees et debarrassees d'un eventuel point
 * initial (« .xlsx » -> « xlsx »).</p>
 */
@Converter
public class LowerCaseListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> valeurs) {
        if (valeurs == null || valeurs.isEmpty()) {
            return "";
        }
        return valeurs.stream()
                .filter(Objects::nonNull)
                .map(LowerCaseListConverter::normalise)
                .filter(valeur -> !valeur.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    @Override
    public List<String> convertToEntityAttribute(String colonne) {
        if (colonne == null || colonne.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(colonne.split(","))
                .map(LowerCaseListConverter::normalise)
                .filter(valeur -> !valeur.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** Minuscules, sans espaces ni point initial. */
    public static String normalise(String valeur) {
        if (valeur == null) {
            return "";
        }
        String propre = valeur.trim().toLowerCase();
        return propre.startsWith(".") ? propre.substring(1) : propre;
    }
}
