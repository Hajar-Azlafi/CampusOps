package com.campusops.settings.entity;

import com.campusops.enums.WeekDay;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persistance des jours ouvrables de l'universite dans une <b>colonne unique</b>
 * (« LUNDI,MARDI,... ») plutot que dans une table de collection.
 *
 * <p>Motivation (§12 : eviter une multiplication inutile des tables) : la
 * configuration globale est un enregistrement unique lu a chaque calcul de
 * disponibilite ; une table auxiliaire ajouterait une jointure permanente pour
 * au plus sept valeurs. Le converter garantit une serialisation deterministe
 * (ordre naturel du lundi au dimanche) et tolere les valeurs inconnues d'une
 * ancienne version sans faire echouer le demarrage.</p>
 */
@Converter
public class WeekDaySetConverter implements AttributeConverter<Set<WeekDay>, String> {

    @Override
    public String convertToDatabaseColumn(Set<WeekDay> jours) {
        if (jours == null || jours.isEmpty()) {
            return "";
        }
        return jours.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<WeekDay> convertToEntityAttribute(String colonne) {
        if (colonne == null || colonne.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(colonne.split(","))
                .map(String::trim)
                .filter(valeur -> !valeur.isEmpty())
                .map(WeekDaySetConverter::parseOrNull)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static WeekDay parseOrNull(String valeur) {
        try {
            return WeekDay.valueOf(valeur.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
