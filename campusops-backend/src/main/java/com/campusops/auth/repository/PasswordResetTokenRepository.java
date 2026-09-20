package com.campusops.auth.repository;

import com.campusops.auth.entity.PasswordResetToken;
import com.campusops.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalide tous les jetons actifs d'un utilisateur. Appele avant d'emettre un
     * nouveau jeton pour qu'une seule demande soit valable a la fois.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true "
            + "WHERE t.user = :user AND t.used = false")
    void invalidateActiveTokens(@Param("user") User user);

    /**
     * Supprime les jetons de reinitialisation d'un utilisateur. La colonne
     * {@code user_id} est NOT NULL : a la suppression d'un compte, ses jetons
     * (ephemeres) sont supprimes.
     */
    long deleteByUserId(Long userId);
}
