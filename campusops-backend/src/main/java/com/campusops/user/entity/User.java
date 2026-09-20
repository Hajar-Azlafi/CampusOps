package com.campusops.user.entity;

import com.campusops.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "password")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column
    private String department;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ----- Securite de la connexion (Module 11, §7) -----

    /**
     * Nombre d'echecs de connexion consecutifs. Remis a zero a chaque connexion
     * reussie et a chaque changement de mot de passe.
     *
     * <p><b>Migration non destructive</b> : colonne <b>nullable</b> ajoutee via
     * {@code ddl-auto=update} ; les comptes existants valent NULL, traite comme
     * zero par le service de securite de connexion.</p>
     */
    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    /**
     * Date/heure jusqu'a laquelle le compte est verrouille apres trop d'echecs
     * (parametre « durée de verrouillage »). {@code null} = compte non verrouille.
     * Colonne nullable (migration non destructive).
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * Date du dernier mot de passe <b>choisi par l'utilisateur</b> : reference du
     * calcul d'expiration (parametre « durée de validité du mot de passe »).
     * {@code null} pour les comptes anterieurs a cette colonne : la date de
     * creation sert alors de reference. Colonne nullable (migration non
     * destructive).
     */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    // ----- Implementation UserDetails (Spring Security) -----

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Verrouillage temporaire du compte apres trop d'echecs de connexion (§7).
     * Spring Security s'appuie sur cette methode : un compte verrouille ne peut
     * pas s'authentifier, meme avec le bon mot de passe, jusqu'a l'echeance.
     */
    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || !lockedUntil.isAfter(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }


    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = true;
}