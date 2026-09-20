package com.campusops.dashboard.report;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Modele interne, neutre vis-a-vis du format de sortie, decrivant un rapport
 * tabulaire. Il est produit par {@code DashboardReportService} puis serialise
 * en PDF ou en Excel par les writers dedies. Ce decouplage respecte le principe
 * de responsabilite unique : la construction des donnees est independante de leur
 * mise en forme.
 */
@Getter
@Builder
public class ReportData {

    /** Titre principal du rapport (affichable, avec accents). */
    private final String titre;

    /** Sous-titre optionnel (periode, portee, etc.). */
    private final String sousTitre;

    /** Date de generation du rapport. */
    @Builder.Default
    private final LocalDateTime genereLe = LocalDateTime.now();

    /** Intitules des colonnes. */
    @Builder.Default
    private final List<String> entetes = new ArrayList<>();

    /** Lignes de donnees ; chaque ligne a le meme nombre de cellules que d'entetes. */
    @Builder.Default
    private final List<List<String>> lignes = new ArrayList<>();
}
