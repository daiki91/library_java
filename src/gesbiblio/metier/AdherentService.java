package gesbiblio.metier;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import gesbiblio.db.Database;
import gesbiblio.db.Sql;

/** Inscription et gestion des adhérents. */
public final class AdherentService {

    private AdherentService() {
    }

    /** Codes des catégories d'adhérents (ETUDIANT, ENSEIGNANT...). */
    public static List<String> categories() throws SQLException {
        List<String> l = new ArrayList<String>();
        try (Connection c = Database.connexion()) {
            for (Object[] r : Sql.lignes(c, "SELECT code FROM categorie_adherent ORDER BY code")) {
                l.add((String) r[0]);
            }
        }
        return l;
    }

    private static void valider(String matricule, String nom, String email) throws RegleException {
        if (Outils.vide(matricule).isEmpty()) {
            throw new RegleException("Le matricule (n° de carte) est obligatoire.");
        }
        if (Outils.vide(nom).isEmpty()) {
            throw new RegleException("Le nom est obligatoire.");
        }
        String e = Outils.vide(email);
        if (!e.isEmpty() && (!e.contains("@") || e.startsWith("@") || e.endsWith("@"))) {
            throw new RegleException("Adresse e-mail invalide.");
        }
    }

    public static String ajouter(final String matricule, final String nom, final String prenom, final String email,
            final String telephone, final String categorie, final String dateExpiration) throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        valider(matricule, nom, email);
        final java.sql.Date exp = Outils.lireDate(dateExpiration);
        return Transaction.executer(c -> {
            if (Sql.entier(c, "SELECT COUNT(*) FROM adherent WHERE matricule = ?", Outils.vide(matricule)) > 0) {
                throw new RegleException("Le matricule « " + Outils.vide(matricule) + " » est déjà attribué.");
            }
            Sql.inserer(c, "INSERT INTO adherent (matricule, nom, prenom, email, telephone, categorie_code, statut, date_inscription, date_expiration) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'ACTIF', CURDATE(), ?)",
                    Outils.vide(matricule), Outils.vide(nom), Outils.nul(prenom), Outils.nul(email), Outils.nul(telephone), categorie, exp);
            Outils.journal(c, "AJOUT_ADHERENT", Outils.vide(matricule) + " - " + nom);
            return "Adhérent " + Outils.vide(matricule) + " inscrit.";
        });
    }

    public static void modifier(final int id, final String matricule, final String nom, final String prenom, final String email,
            final String telephone, final String categorie, final String dateExpiration) throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        valider(matricule, nom, email);
        final java.sql.Date exp = Outils.lireDate(dateExpiration);
        Transaction.executer(c -> {
            if (Sql.entier(c, "SELECT COUNT(*) FROM adherent WHERE matricule = ? AND id <> ?", Outils.vide(matricule), id) > 0) {
                throw new RegleException("Le matricule « " + Outils.vide(matricule) + " » est déjà attribué.");
            }
            Sql.maj(c, "UPDATE adherent SET matricule = ?, nom = ?, prenom = ?, email = ?, telephone = ?, categorie_code = ?, "
                    + "date_expiration = ? WHERE id = ?",
                    Outils.vide(matricule), Outils.vide(nom), Outils.nul(prenom), Outils.nul(email), Outils.nul(telephone), categorie, exp, id);
            Outils.journal(c, "MODIF_ADHERENT", Outils.vide(matricule));
            return null;
        });
    }

    /** Suspend ou réactive un compte (un compte suspendu ne peut ni emprunter ni réserver). */
    public static String basculerStatut(final int id) throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        return Transaction.executer(c -> {
            Object[] a = Sql.ligne(c, "SELECT statut, matricule FROM adherent WHERE id = ? FOR UPDATE", id);
            if (a == null) {
                throw new RegleException("Adhérent introuvable.");
            }
            String nouveau = "ACTIF".equals(a[0]) ? "SUSPENDU" : "ACTIF";
            Sql.maj(c, "UPDATE adherent SET statut = ? WHERE id = ?", nouveau, id);
            Outils.journal(c, "STATUT_ADHERENT", a[1] + " -> " + nouveau);
            return "Compte " + a[1] + " : " + nouveau + ".";
        });
    }

    /** Suppression seulement si l'adhérent n'a aucun historique ; sinon on le suspend. */
    public static void supprimer(final int id) throws SQLException, RegleException {
        Session.exiger(Session.BIBLIOTHECAIRE);
        Transaction.executer(c -> {
            int histo = Sql.entier(c, "SELECT COUNT(*) FROM emprunt WHERE adherent_id = ?", id)
                    + Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE adherent_id = ?", id)
                    + Sql.entier(c, "SELECT COUNT(*) FROM penalite WHERE adherent_id = ?", id);
            if (histo > 0) {
                throw new RegleException("Cet adhérent a un historique (prêts, réservations ou pénalités) : il ne peut pas être supprimé.\n"
                        + "Suspendez plutôt son compte.");
            }
            Object mat = Sql.valeur(c, "SELECT matricule FROM adherent WHERE id = ?", id);
            if (Sql.maj(c, "DELETE FROM adherent WHERE id = ?", id) == 0) {
                throw new RegleException("Adhérent introuvable.");
            }
            Outils.journal(c, "SUPPR_ADHERENT", String.valueOf(mat));
            return null;
        });
    }

    /** Fiche de situation affichée au guichet avant un prêt. */
    public static String situation(final String matricule) throws SQLException, RegleException {
        try (Connection c = Database.connexion()) {
            Object[] a = Sql.ligne(c, "SELECT a.id, CONCAT_WS(' ', a.prenom, a.nom), c.libelle, a.statut, a.date_expiration, "
                    + "c.max_emprunts, IFNULL(a.date_expiration < CURDATE(), 0), a.telephone, a.email "
                    + "FROM adherent a JOIN categorie_adherent c ON c.code = a.categorie_code WHERE a.matricule = ?", Outils.vide(matricule));
            if (a == null) {
                throw new RegleException("Aucun adhérent avec le matricule « " + Outils.vide(matricule) + " ».");
            }
            int id = ((Number) a[0]).intValue();
            int enCours = Sql.entier(c, "SELECT COUNT(*) FROM emprunt WHERE adherent_id = ? AND date_retour IS NULL", id);
            int retards = Sql.entier(c, "SELECT COUNT(*) FROM emprunt WHERE adherent_id = ? AND date_retour IS NULL AND date_echeance < CURDATE()", id);
            double impaye = Sql.decimal(c, "SELECT IFNULL(SUM(montant), 0) FROM penalite WHERE adherent_id = ? AND statut = 'IMPAYEE'", id);
            int resa = Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE adherent_id = ? AND statut IN ('EN_ATTENTE', 'A_RETIRER')", id);
            int aRetirer = Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE adherent_id = ? AND statut = 'A_RETIRER'", id);
            String devise = Outils.parametre(c, "devise", "");
            double seuil = Outils.parametreDecimal(c, "seuil_blocage_penalites", 0);
            boolean expiree = Outils.vrai(a[6]);
            StringBuilder sb = new StringBuilder();
            sb.append(a[1]).append(" - ").append(a[2]).append("\n");
            sb.append("Compte : ").append(a[3]);
            sb.append("   |   Carte valable jusqu'au : ").append(a[4] == null ? "illimitée" : Outils.date(a[4]))
                    .append(expiree ? " (EXPIRÉE)" : "").append("\n");
            sb.append("Emprunts en cours : ").append(enCours).append(" / ").append(a[5]);
            sb.append("   |   En retard : ").append(retards).append("\n");
            sb.append("Pénalités impayées : ").append(Outils.montant(impaye, devise));
            sb.append("   |   Réservations actives : ").append(resa);
            if (aRetirer > 0) {
                sb.append(" (dont ").append(aRetirer).append(" à retirer maintenant)");
            }
            if (seuil > 0 && impaye >= seuil) {
                sb.append("\nPénalités au-delà du seuil de blocage (").append(Outils.montant(seuil, devise)).append(") : réglez-les avant tout prêt.");
            }
            return sb.toString();
        }
    }
}
