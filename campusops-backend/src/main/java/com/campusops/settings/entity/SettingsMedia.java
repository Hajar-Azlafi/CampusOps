package com.campusops.settings.entity;

import com.campusops.enums.SettingsMediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Media d'identite visuelle de l'universite : logo et favicon (§11).
 *
 * <p>Les octets sont isoles dans leur propre table pour que la lecture tres
 * frequente de {@link AppSettings} (chemins chauds : disponibilite, import,
 * authentification) ne charge jamais de binaire. Une seule ligne existe par
 * {@link SettingsMediaType} grace a la contrainte d'unicite sur {@code type} :
 * remplacer le logo met a jour la ligne existante.</p>
 *
 * <p>{@code contentType}, {@code fileName} et {@code tailleOctets} sont
 * conserves pour servir le fichier avec les bons en-etetes HTTP et afficher les
 * caracteristiques du media dans l'interface d'administration.</p>
 */
@Entity
@Table(name = "settings_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "data")
public class SettingsMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, unique = true)
    private SettingsMediaType type;

    /** Type MIME reellement detecte a l'upload (jamais deduit de l'extension seule). */
    @Column(name = "content_type", nullable = false)
    private String contentType;

    /** Nom du fichier d'origine, assaini. */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "taille_octets", nullable = false)
    private Long tailleOctets;

    @Lob
    @Column(name = "data", nullable = false, columnDefinition = "BYTEA")
    private byte[] data;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
