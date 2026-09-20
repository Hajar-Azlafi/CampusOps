package com.campusops.auth.entity;

import com.campusops.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Jeton de reinitialisation de mot de passe. Le jeton en clair n'est jamais
 * stocke : seule son empreinte SHA-256 ({@code tokenHash}) est persistee, ce qui
 * empeche de reconstituer le lien meme en cas de fuite de la base. Un jeton est
 * a usage unique ({@code used}) et expire apres une duree limitee
 * ({@code expiresAt}).
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empreinte SHA-256 du jeton (jamais le jeton en clair). */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Le jeton est exploitable s'il n'a pas ete utilise et n'a pas expire. */
    public boolean isUsable() {
        return !used && expiresAt.isAfter(LocalDateTime.now());
    }
}
