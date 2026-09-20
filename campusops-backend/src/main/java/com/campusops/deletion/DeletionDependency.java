package com.campusops.deletion;

/**
 * Une dependance nommee et comptee, affichee a l'utilisateur avant une
 * suppression. Selon le contexte, il s'agit soit d'un <b>enfant structurel</b>
 * qui sera supprime en cascade (ex. « 5 filieres »), soit d'un <b>usage
 * metier / historique</b> qui bloque la suppression (ex. « 12 seances »).
 *
 * <p>Le libelle {@link #getLabel()} est pret a afficher (« 12 seances ») et gere
 * l'accord singulier / pluriel. Les composantes {@code count}, {@code singular}
 * et {@code plural} restent exposees pour un eventuel formatage cote client.</p>
 */
public record DeletionDependency(long count, String singular, String plural) {

    /** Fabrique : ne cree une dependance que si le compte est strictement positif. */
    public static DeletionDependency of(long count, String singular, String plural) {
        return new DeletionDependency(count, singular, plural);
    }

    /** Libelle pret a afficher, accord singulier / pluriel gere (« 1 filiere », « 5 filieres »). */
    public String getLabel() {
        return count + " " + (count <= 1 ? singular : plural);
    }
}
