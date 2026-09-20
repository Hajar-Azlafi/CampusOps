package com.campusops.settings.repository;

import com.campusops.settings.entity.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acces a la configuration globale unique. La cle primaire etant fixe
 * ({@link AppSettings#SINGLETON_ID}), aucune methode de recherche
 * supplementaire n'est necessaire.
 */
@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
}
