package com.campusops.timetable.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test cible du correctif d'import des heures Excel (bug
 * « 0.354166666666667 n'est pas une heure valide »).
 *
 * <p>Excel enregistre une heure saisie « 08:30 » comme une valeur NUMERIQUE
 * egale a une fraction de journee (08:30 = 0.354166...). L'import doit la
 * normaliser en « 08:30 » AVANT la validation, tout en continuant d'accepter
 * les cellules deja saisies en texte.</p>
 */
class TimetableImportServiceTimeTest {

    /**
     * Instancie le service sans contexte Spring : les methodes testees
     * (readTimeCell / fractionToTime) n'utilisent aucune dependance injectee,
     * on passe donc des valeurs nulles au constructeur genere par Lombok.
     */
    private TimetableImportService newService() throws Exception {
        Constructor<?> ctor = TimetableImportService.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return (TimetableImportService) ctor.newInstance(new Object[ctor.getParameterCount()]);
    }

    @Test
    void fractionToTime_convertitLesFractionsDeJournee() throws Exception {
        TimetableImportService service = newService();

        // Valeurs exactes du ticket.
        assertEquals("08:30", service.fractionToTime(0.354166666666667));
        assertEquals("10:25", service.fractionToTime(0.434027777777778));
        assertEquals("10:35", service.fractionToTime(0.440972222222223));
        assertEquals("12:30", service.fractionToTime(0.520833333333334));
        assertEquals("14:00", service.fractionToTime(0.583333333333333));
        assertEquals("15:55", service.fractionToTime(0.663194444444444));

        // Un « serial date » complet (partie entiere = nombre de jours) : on ne
        // conserve que l'heure.
        assertEquals("08:30", service.fractionToTime(45000.354166666667));

        // Minuit et cas limite d'arrondi 24:00 -> 00:00.
        assertEquals("00:00", service.fractionToTime(0.0));
        assertEquals("00:00", service.fractionToTime(0.999999999999));
    }

    @Test
    void readTimeCell_gereNumeriqueFormateNumeriqueBrutEtTexte() throws Exception {
        TimetableImportService service = newService();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Séances");
            Row row = sheet.createRow(1);

            CreationHelper helper = wb.getCreationHelper();
            CellStyle timeStyle = wb.createCellStyle();
            timeStyle.setDataFormat(helper.createDataFormat().getFormat("h:mm"));

            // (0) Cellule numerique formatee heure : ce que produit Excel pour « 08:30 ».
            Cell c0 = row.createCell(0);
            c0.setCellValue(0.354166666666667);
            c0.setCellStyle(timeStyle);

            // (1) Cellule numerique SANS format date (format « Standard »).
            Cell c1 = row.createCell(1);
            c1.setCellValue(0.520833333333334);

            // (2,3) Cellules texte : acceptees telles quelles (avec trim).
            row.createCell(2).setCellValue("14:00");
            row.createCell(3).setCellValue("  09:45 ");

            // (4) Cellule vide -> chaine vide.
            row.createCell(4).setBlank();

            assertEquals("08:30", service.readTimeCell(row, 0));
            assertEquals("12:30", service.readTimeCell(row, 1));
            assertEquals("14:00", service.readTimeCell(row, 2));
            assertEquals("09:45", service.readTimeCell(row, 3));
            assertEquals("", service.readTimeCell(row, 4));

            // Colonne absente -> chaine vide, sans exception.
            assertEquals("", service.readTimeCell(row, 9));
        }
    }
}
