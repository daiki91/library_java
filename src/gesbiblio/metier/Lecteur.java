package gesbiblio.metier;

import java.sql.Connection;
import java.sql.SQLException;

import gesbiblio.db.Sql;

/** Un adhérent avec les droits de prêt de sa catégorie (chargé pour une opération de prêt). */
final class Lecteur {

    int id;
    String matricule;
    String nomComplet;
    String statut;
    Object dateExpiration;
    boolean carteExpiree;
    String categorie;
    int maxEmprunts;
    int dureePret;
    int maxRenouvellements;

    private Lecteur() {
    }

    /** Charge et verrouille l'adhérent (FOR UPDATE) pour éviter deux prêts simultanés au-delà de la limite. */
    static Lecteur charger(Connection c, String matricule) throws SQLException, RegleException {
        Object[] l = Sql.ligne(c,
                "SELECT a.id, a.matricule, CONCAT_WS(' ', a.prenom, a.nom), a.statut, a.date_expiration, "
                        + "IFNULL(a.date_expiration < CURDATE(), 0), c.libelle, c.max_emprunts, c.duree_pret_jours, c.max_renouvellements "
                        + "FROM adherent a JOIN categorie_adherent c ON c.code = a.categorie_code "
                        + "WHERE a.matricule = ? FOR UPDATE",
                Outils.vide(matricule));
        if (l == null) {
            throw new RegleException("Aucun adhérent avec le matricule « " + Outils.vide(matricule) + " ».");
        }
        Lecteur x = new Lecteur();
        x.id = ((Number) l[0]).intValue();
        x.matricule = (String) l[1];
        x.nomComplet = (String) l[2];
        x.statut = (String) l[3];
        x.dateExpiration = l[4];
        x.carteExpiree = Outils.vrai(l[5]);
        x.categorie = (String) l[6];
        x.maxEmprunts = ((Number) l[7]).intValue();
        x.dureePret = ((Number) l[8]).intValue();
        x.maxRenouvellements = ((Number) l[9]).intValue();
        return x;
    }

    /** Règle : le compte doit être actif et la carte valide. */
    void verifierCompte() throws RegleException {
        if (!"ACTIF".equals(statut)) {
            throw new RegleException("Le compte de " + nomComplet + " est suspendu.");
        }
        if (carteExpiree) {
            throw new RegleException("La carte de " + nomComplet + " a expiré le " + Outils.date(dateExpiration)
                    + ". Renouvelez l'adhésion avant tout prêt.");
        }
    }

    int empruntsEnCours(Connection c) throws SQLException {
        return Sql.entier(c, "SELECT COUNT(*) FROM emprunt WHERE adherent_id = ? AND date_retour IS NULL", id);
    }

    int empruntsEnRetard(Connection c) throws SQLException {
        return Sql.entier(c, "SELECT COUNT(*) FROM emprunt WHERE adherent_id = ? AND date_retour IS NULL "
                + "AND date_echeance < CURDATE()", id);
    }

    double penalitesImpayees(Connection c) throws SQLException {
        return Sql.decimal(c, "SELECT IFNULL(SUM(montant), 0) FROM penalite WHERE adherent_id = ? AND statut = 'IMPAYEE'", id);
    }

    boolean empruntDejaCetOuvrage(Connection c, int ouvrageId) throws SQLException {
        return Sql.entier(c, "SELECT COUNT(*) FROM emprunt em JOIN exemplaire e ON e.id = em.exemplaire_id "
                + "WHERE em.adherent_id = ? AND em.date_retour IS NULL AND e.ouvrage_id = ?", id, ouvrageId) > 0;
    }
}
