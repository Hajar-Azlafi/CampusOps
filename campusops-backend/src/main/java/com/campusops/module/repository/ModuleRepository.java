package com.campusops.module.repository;

import com.campusops.module.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModuleRepository extends JpaRepository<Module, Long> {

    /** Liste globale (ADMIN), triée par filière puis semestre puis nom. */
    List<Module> findAllByOrderByProgram_NomAscSemester_OrdreAscNomAsc();

    /**
     * Modules d'un contexte pédagogique précis (filière + semestre), tous
     * statuts confondus — pour l'administration. Trié par nom (§3).
     */
    List<Module> findByProgramIdAndSemesterIdOrderByNomAsc(Long programId, Long semesterId);

    /**
     * Modules <b>actifs</b> d'un contexte (filière + semestre) — utilisé pour
     * alimenter la liste déroulante de l'ajout/modification de séance (§4, §10).
     */
    List<Module> findByProgramIdAndSemesterIdAndActifOrderByNomAsc(
            Long programId, Long semesterId, boolean actif);

    /** Tous les modules d'une filière, tous semestres — trié semestre puis nom. */
    List<Module> findByProgramIdOrderBySemester_OrdreAscNomAsc(Long programId);

    /**
     * Modules des filières dont l'identifiant figure dans l'ensemble fourni —
     * périmètre d'un responsable pédagogique (§12-§13). Trié filière/semestre/nom.
     */
    List<Module> findByProgramIdInOrderByProgram_NomAscSemester_OrdreAscNomAsc(
            Collection<Long> programIds);

    /** Détection de doublon dans un contexte (insensible à la casse). */
    Optional<Module> findByProgramIdAndSemesterIdAndNomIgnoreCase(
            Long programId, Long semesterId, String nom);

    /** Un module référence-t-il ce semestre ? (garde-fou de nettoyage). */
    boolean existsBySemesterId(Long semesterId);

    // --- Comptages pour l'analyse de suppression (refonte suppressions) -------

    /** Modules d'un semestre (bloque la suppression du semestre). */
    long countBySemesterId(Long semesterId);

    /** Modules d'une filiere (enfants supprimes en cascade avec la filiere). */
    long countByProgramId(Long programId);

    /** Modules d'un departement (enfants supprimes en cascade avec le departement). */
    long countByProgram_Department_Id(Long departmentId);

    /* ==================================================================
     *  PROPAGATION ACTIF / INACTIF (cascade descendante)
     *  Un module appartient a une filiere : desactiver la filiere (ou son
     *  departement) rend ses modules inutilisables pour de nouveaux EDT (§6, §20).
     * ================================================================== */

    /** Modules d'une filiere (cascade a la desactivation d'une filiere). */
    List<Module> findByProgramId(Long programId);

    /** Modules d'un departement (cascade a la desactivation d'un departement). */
    List<Module> findByProgram_Department_Id(Long departmentId);
}
