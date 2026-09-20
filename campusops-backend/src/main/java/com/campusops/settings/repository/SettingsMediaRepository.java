package com.campusops.settings.repository;

import com.campusops.enums.SettingsMediaType;
import com.campusops.settings.entity.SettingsMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Acces aux medias d'identite visuelle (logo, favicon). */
@Repository
public interface SettingsMediaRepository extends JpaRepository<SettingsMedia, Long> {

    Optional<SettingsMedia> findByType(SettingsMediaType type);

}
