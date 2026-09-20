package com.campusops;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CampusopsBackendApplication {

	/**
	 * Fuseau de reference du PROCESSUS, fixe AVANT le demarrage de Spring pour que
	 * {@code LocalDate.now()} / {@code LocalDateTime.now()} et les taches planifiees
	 * (rappels, bascules d'annee et de semestre) soient coherents quel que soit le
	 * fuseau du serveur hote. Aligne par defaut sur le fuseau par defaut de la page
	 * Parametres > Universite ({@code Africa/Casablanca}). Surchargable sans
	 * recompiler via {@code -Dcampusops.app.timezone=...} ou la variable
	 * d'environnement {@code CAMPUSOPS_APP_TIMEZONE}.
	 *
	 * <p>NB : le fuseau editable dans Parametres pilote, a chaud, l'affichage des
	 * dates et le jour cible des rappels ({@code ReminderService}). Ce reglage-ci,
	 * de niveau processus, s'applique au demarrage : le maintenir coherent avec la
	 * propriete {@code campusops.app.timezone} evite toute divergence.</p>
	 */
	static final String DEFAULT_TIMEZONE = "Africa/Casablanca";

	public static void main(String[] args) {
		String zone = System.getProperty("campusops.app.timezone");
		if (zone == null || zone.isBlank()) {
			zone = System.getenv().getOrDefault("CAMPUSOPS_APP_TIMEZONE", DEFAULT_TIMEZONE);
		}
		TimeZone.setDefault(TimeZone.getTimeZone(zone));
		SpringApplication.run(CampusopsBackendApplication.class, args);
	}

}
