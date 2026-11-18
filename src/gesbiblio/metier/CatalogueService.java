package gesbiblio.metier;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gesbiblio.db.Database;
import gesbiblio.db.Sql;

/** Catalogue : ouvrages (titres) et exemplaires (copies physiques). */
public final class CatalogueService {

    /** Statuts qu'un bibliothécaire peut donner à la main (EMPRUNTE et RESERVE sont gérés par les prêts). */
    public static final List<String> STATUTS_MANUELS = Arrays.asList("DISPONIBLE", "EN_REPARATION", "PERDU", "RETIRE");
    public static final List<String> ETATS = Arrays.asList("NEUF", "BON", "USE", "ENDOMMAGE");

    private CatalogueService() {
    }

    public static List<String> categories() throws SQLException {
        List<String> l = new ArrayList<String>();
        try (Connection c = Database.connexion()) {
            for (Object[] r : Sql.lignes(c, "SELECT libelle FROM categorie ORDER BY libelle")) {
                l.add((String) r[0]);
            }
        }
        return l;
    }

    public static void ajouterCategorie(final String libelle) throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        if (Outils.vide(libelle).isEmpty()) {
            throw new RegleException("Le libellé est obligatoire.");
        }
        Transaction.executer(c -> {
            if (Sql.entier(c, "SELECT COUNT(*) FROM categorie WHERE libelle = ?", Outils.vide(libelle)) > 0) {
                throw new RegleException("Cette catégorie existe déjà.");
            }
            Sql.maj(c, "INSERT INTO categorie (libelle) VALUES (?)", Outils.vide(libelle));
            Outils.journal(c, "AJOUT_CATEGORIE", libelle);
            return null;
        });
    }

    private static Integer categorieId(Connection c, String libelle) throws SQLException {
        if (Outils.vide(libelle).isEmpty()) {
            return null;
        }
        Object id = Sql.valeur(c, "SELECT id FROM categorie WHERE libelle = ?", Outils.vide(libelle));
        return id == null ? null : ((Number) id).intValue();
    }

    private static void verifierIsbn(Connection c, String isbn, int idExclu) throws SQLException, RegleException {
        if (isbn != null && Sql.entier(c, "SELECT COUNT(*) FROM ouvrage WHERE isbn = ? AND id <> ?", isbn, idExclu) > 0) {
            throw new RegleException("Un ouvrage avec l'ISBN " + isbn + " existe déjà.");
        }
    }

    /** Ajoute un titre et crée tout de suite ses exemplaires (codes-barres générés automatiquement). */
    public static String ajouterOuvrage(final String isbn, final String titre, final String auteur, final String editeur,
            final String annee, final String categorie, final int nbExemplaires, final String rayon)
            throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        if (Outils.vide(titre).isEmpty()) {
            throw new RegleException("Le titre est obligatoire.");
        }
        final Integer an = Outils.lireEntier(annee, "L'année");
        if (nbExemplaires < 0 || nbExemplaires > 200) {
            throw new RegleException("Nombre d'exemplaires invalide (0 à 200).");
        }
        return Transaction.executer(c -> {
            verifierIsbn(c, Outils.nul(isbn), 0);
            int id = Sql.inserer(c, "INSERT INTO ouvrage (isbn, titre, auteur, editeur, annee, categorie_id) VALUES (?, ?, ?, ?, ?, ?)",
                    Outils.nul(isbn), Outils.vide(titre), Outils.nul(auteur), Outils.nul(editeur), an, categorieId(c, categorie));
            List<String> codes = creerExemplaires(c, id, nbExemplaires, rayon, null);
            Outils.journal(c, "AJOUT_OUVRAGE", "n° " + id + " - " + titre + " - " + nbExemplaires + " exemplaire(s)");
            return "Ouvrage n° " + id + " ajouté avec " + codes.size() + " exemplaire(s)"
                    + (codes.isEmpty() ? "." : " : " + String.join(", ", codes) + ".");
        });
    }

    public static void modifierOuvrage(final int id, final String isbn, final String titre, final String auteur,
            final String editeur, final String annee, final String categorie) throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        if (Outils.vide(titre).isEmpty()) {
            throw new RegleException("Le titre est obligatoire.");
        }
        final Integer an = Outils.lireEntier(annee, "L'année");
        Transaction.executer(c -> {
            verifierIsbn(c, Outils.nul(isbn), id);
            int n = Sql.maj(c, "UPDATE ouvrage SET isbn = ?, titre = ?, auteur = ?, editeur = ?, annee = ?, categorie_id = ? WHERE id = ?",
                    Outils.nul(isbn), Outils.vide(titre), Outils.nul(auteur), Outils.nul(editeur), an, categorieId(c, categorie), id);
            if (n == 0) {
                throw new RegleException("Ouvrage introuvable.");
            }
            Outils.journal(c, "MODIF_OUVRAGE", "n° " + id + " - " + titre);
            return null;
        });
    }

    /** Suppression seulement si le titre n'a aucun historique ; sinon on retire ses exemplaires. */
    public static void supprimerOuvrage(final int id) throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        Transaction.executer(c -> {
            int histo = Sql.entier(c, "SELECT COUNT(*) FROM emprunt em JOIN exemplaire e ON e.id = em.exemplaire_id WHERE e.ouvrage_id = ?", id)
                    + Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE ouvrage_id = ?", id);
            if (histo > 0) {
                throw new RegleException("Ce titre a un historique de prêts ou de réservations : il ne peut pas être supprimé.\n"
                        + "Passez plutôt ses exemplaires au statut RETIRE.");
            }
            Sql.maj(c, "DELETE FROM exemplaire WHERE ouvrage_id = ?", id);
            if (Sql.maj(c, "DELETE FROM ouvrage WHERE id = ?", id) == 0) {
                throw new RegleException("Ouvrage introuvable.");
            }
            Outils.journal(c, "SUPPR_OUVRAGE", "n° " + id);
            return null;
        });
    }

    /** Ajoute des exemplaires ; s'il y a une file de réservation, ils sont aussitôt mis de côté. */
    public static String ajouterExemplaires(final int ouvrageId, final int nombre, final String rayon, final String codeManuel)
            throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        if (nombre < 1 || nombre > 200) {
            throw new RegleException("Nombre d'exemplaires invalide (1 à 200).");
        }
        if (!Outils.vide(codeManuel).isEmpty() && nombre != 1) {
            throw new RegleException("Un code-barres manuel ne peut être donné que pour un seul exemplaire.");
        }
        return Transaction.executer(c -> {
            if (Sql.valeur(c, "SELECT id FROM ouvrage WHERE id = ? FOR UPDATE", ouvrageId) == null) {
                throw new RegleException("Ouvrage introuvable.");
            }
            List<String> messages = new ArrayList<String>();
            List<String> codes = creerExemplaires(c, ouvrageId, nombre, rayon, Outils.nul(codeManuel), messages);
            Outils.journal(c, "AJOUT_EXEMPLAIRES", "ouvrage n° " + ouvrageId + " : " + String.join(", ", codes));
            StringBuilder sb = new StringBuilder(codes.size() + " exemplaire(s) ajouté(s) : " + String.join(", ", codes) + ".");
            for (String m : messages) {
                sb.append("\n").append(m);
            }
            return sb.toString();
        });
    }

    private static List<String> creerExemplaires(Connection c, int ouvrageId, int nombre, String rayon, String codeManuel)
            throws SQLException, RegleException {
        return creerExemplaires(c, ouvrageId, nombre, rayon, codeManuel, new ArrayList<String>());
    }

    private static List<String> creerExemplaires(Connection c, int ouvrageId, int nombre, String rayon, String codeManuel,
            List<String> messages) throws SQLException, RegleException {
        List<String> codes = new ArrayList<String>();
        for (int i = 0; i < nombre; i++) {
            String code = codeManuel;
            if (code != null && Sql.entier(c, "SELECT COUNT(*) FROM exemplaire WHERE code_barres = ?", code) > 0) {
                throw new RegleException("Le code-barres « " + code + " » est déjà utilisé.");
            }
            String provisoire = code != null ? code : "TMP-" + System.nanoTime() + "-" + i;
            int exId = Sql.inserer(c, "INSERT INTO exemplaire (ouvrage_id, code_barres, statut, etat, rayon, date_acquisition) "
                    + "VALUES (?, ?, 'DISPONIBLE', 'NEUF', ?, CURDATE())", ouvrageId, provisoire, Outils.nul(rayon));
            if (code == null) {
                code = String.format("EX%06d", exId);
                // Si le code automatique a déjà été pris à la main, on ajoute un suffixe.
                if (Sql.entier(c, "SELECT COUNT(*) FROM exemplaire WHERE code_barres = ?", code) > 0) {
                    code = code + "-" + exId;
                }
                Sql.maj(c, "UPDATE exemplaire SET code_barres = ? WHERE id = ?", code, exId);
            }
            codes.add(code);
            String m = ReservationService.liberer(c, exId);
            if (m != null) {
                messages.add(m);
            }
        }
        return codes;
    }

    /** Change le statut, l'état ou le rayon d'un exemplaire, en respectant le circuit des prêts. */
    public static String modifierExemplaire(final int exemplaireId, final String statut, final String etat, final String rayon)
            throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        return Transaction.executer(c -> {
            Object[] ex = Sql.ligne(c, "SELECT statut, code_barres FROM exemplaire WHERE id = ? FOR UPDATE", exemplaireId);
            if (ex == null) {
                throw new RegleException("Exemplaire introuvable.");
            }
            String actuel = (String) ex[0];
            Sql.maj(c, "UPDATE exemplaire SET etat = ?, rayon = ? WHERE id = ?", etat, Outils.nul(rayon), exemplaireId);
            String msg = "Exemplaire " + ex[1] + " mis à jour.";
            if (statut != null && !statut.equals(actuel)) {
                if ("EMPRUNTE".equals(actuel)) {
                    throw new RegleException("Exemplaire en prêt : utilisez « Retour » ou « Déclarer perdu » dans l'onglet Prêts.");
                }
                if ("RESERVE".equals(actuel)) {
                    throw new RegleException("Exemplaire mis de côté pour une réservation : annulez d'abord la réservation.");
                }
                if (!STATUTS_MANUELS.contains(statut)) {
                    throw new RegleException("Statut non autorisé : " + statut);
                }
                if ("DISPONIBLE".equals(statut)) {
                    if ("ENDOMMAGE".equals(etat)) {
                        throw new RegleException("Un exemplaire ENDOMMAGÉ ne peut pas être remis en rayon : changez d'abord son état.");
                    }
                    String m = ReservationService.liberer(c, exemplaireId);
                    msg += m == null ? " Remis en rayon (DISPONIBLE)." : "\n" + m;
                } else {
                    Sql.maj(c, "UPDATE exemplaire SET statut = ? WHERE id = ?", statut, exemplaireId);
                    msg += " Nouveau statut : " + statut + ".";
                }
            }
            Outils.journal(c, "MODIF_EXEMPLAIRE", ex[1] + " : " + actuel + " -> " + statut + ", état " + etat);
            return msg;
        });
    }

    public static void supprimerExemplaire(final int exemplaireId) throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        Transaction.executer(c -> {
            int histo = Sql.entier(c, "SELECT COUNT(*) FROM emprunt WHERE exemplaire_id = ?", exemplaireId)
                    + Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE exemplaire_id = ?", exemplaireId);
            if (histo > 0) {
                throw new RegleException("Cet exemplaire a un historique de prêts : passez-le au statut RETIRE au lieu de le supprimer.");
            }
            Object code = Sql.valeur(c, "SELECT code_barres FROM exemplaire WHERE id = ?", exemplaireId);
            if (Sql.maj(c, "DELETE FROM exemplaire WHERE id = ?", exemplaireId) == 0) {
                throw new RegleException("Exemplaire introuvable.");
            }
            Outils.journal(c, "SUPPR_EXEMPLAIRE", String.valueOf(code));
            return null;
        });
    }
}
