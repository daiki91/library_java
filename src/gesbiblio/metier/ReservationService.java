package gesbiblio.metier;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import gesbiblio.db.Sql;

/**
 * Réservations : file d'attente par titre (premier arrivé, premier servi).
 * Quand un exemplaire revient, il est mis de côté pour le premier de la file,
 * qui a un délai (paramètre delai_retrait_reservation) pour venir le chercher.
 */
public final class ReservationService {

    private ReservationService() {
    }

    /** Réserve un titre dont aucun exemplaire n'est disponible en rayon. */
    public static String reserver(final String matricule, final int ouvrageId) throws SQLException, RegleException {
        Session.exiger(Session.AGENT);
        return Transaction.executer(c -> {
            Lecteur a = Lecteur.charger(c, matricule);
            a.verifierCompte();
            Object titre = Sql.valeur(c, "SELECT titre FROM ouvrage WHERE id = ? FOR UPDATE", ouvrageId);
            if (titre == null) {
                throw new RegleException("Ouvrage introuvable (n° " + ouvrageId + ").");
            }
            int enService = Sql.entier(c, "SELECT COUNT(*) FROM exemplaire WHERE ouvrage_id = ? AND statut NOT IN ('PERDU', 'RETIRE')", ouvrageId);
            if (enService == 0) {
                throw new RegleException("Aucun exemplaire de « " + titre + " » n'est en service : réservation impossible.");
            }
            int dispo = Sql.entier(c, "SELECT COUNT(*) FROM exemplaire WHERE ouvrage_id = ? AND statut = 'DISPONIBLE'", ouvrageId);
            if (dispo > 0) {
                throw new RegleException(dispo + " exemplaire(s) de « " + titre + " » disponible(s) en rayon : empruntez-le directement.");
            }
            if (a.empruntDejaCetOuvrage(c, ouvrageId)) {
                throw new RegleException(a.nomComplet + " a déjà ce titre en prêt.");
            }
            if (Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE adherent_id = ? AND ouvrage_id = ? "
                    + "AND statut IN ('EN_ATTENTE', 'A_RETIRER')", a.id, ouvrageId) > 0) {
                throw new RegleException(a.nomComplet + " a déjà une réservation active pour ce titre.");
            }
            int max = Outils.parametreEntier(c, "max_reservations", 3);
            int actives = Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE adherent_id = ? AND statut IN ('EN_ATTENTE', 'A_RETIRER')", a.id);
            if (actives >= max) {
                throw new RegleException(a.nomComplet + " a déjà " + actives + " réservation(s) active(s) (maximum " + max + ").");
            }
            int id = Sql.inserer(c, "INSERT INTO reservation (ouvrage_id, adherent_id, date_reservation, statut) VALUES (?, ?, NOW(), 'EN_ATTENTE')",
                    ouvrageId, a.id);
            int position = Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE ouvrage_id = ? AND statut = 'EN_ATTENTE' AND id <= ?",
                    ouvrageId, id);
            Outils.journal(c, "RESERVATION", a.matricule + " - " + titre);
            return "Réservation n° " + id + " enregistrée pour " + a.nomComplet + " : « " + titre + " ».\n"
                    + "Position dans la file d'attente : " + position + ".";
        });
    }

    /** Annule une réservation ; si un exemplaire était mis de côté, il passe au suivant de la file. */
    public static String annuler(final int reservationId) throws SQLException, RegleException {
        Session.exiger(Session.AGENT);
        return Transaction.executer(c -> {
            Object[] r = Sql.ligne(c, "SELECT statut, exemplaire_id FROM reservation WHERE id = ? FOR UPDATE", reservationId);
            if (r == null) {
                throw new RegleException("Réservation introuvable.");
            }
            String statut = (String) r[0];
            if (!"EN_ATTENTE".equals(statut) && !"A_RETIRER".equals(statut)) {
                throw new RegleException("Cette réservation est déjà close (" + statut + ").");
            }
            Sql.maj(c, "UPDATE reservation SET statut = 'ANNULEE' WHERE id = ?", reservationId);
            String suite = "";
            if ("A_RETIRER".equals(statut) && r[1] != null) {
                String m = liberer(c, ((Number) r[1]).intValue());
                suite = "\n" + (m == null ? "L'exemplaire mis de côté est remis en rayon." : m);
            }
            Outils.journal(c, "ANNULATION_RESERVATION", "n° " + reservationId);
            return "Réservation n° " + reservationId + " annulée." + suite;
        });
    }

    /** Clôt les réservations non retirées dans le délai et passe l'exemplaire au lecteur suivant. */
    public static String expirerDelaisDepasses() throws SQLException, RegleException {
        return Transaction.executer(c -> {
            List<Object[]> l = Sql.lignes(c, "SELECT id, exemplaire_id FROM reservation "
                    + "WHERE statut = 'A_RETIRER' AND date_limite_retrait < CURDATE() FOR UPDATE");
            StringBuilder sb = new StringBuilder();
            for (Object[] r : l) {
                Sql.maj(c, "UPDATE reservation SET statut = 'EXPIREE' WHERE id = ?", r[0]);
                if (r[1] != null) {
                    String m = liberer(c, ((Number) r[1]).intValue());
                    if (m != null) {
                        sb.append("\n").append(m);
                    }
                }
                Outils.journal(c, "EXPIRATION_RESERVATION", "n° " + r[0]);
            }
            if (l.isEmpty()) {
                return "Aucune réservation expirée.";
            }
            return l.size() + " réservation(s) expirée(s) : délai de retrait dépassé." + sb;
        });
    }

    /**
     * Un exemplaire redevient prêtable : s'il y a une file d'attente pour ce titre,
     * il est mis de côté (RESERVE) pour le premier lecteur ; sinon il passe DISPONIBLE.
     * @return le message à afficher si l'exemplaire a été mis de côté, sinon null
     */
    static String liberer(Connection c, int exemplaireId) throws SQLException {
        Object[] ex = Sql.ligne(c, "SELECT e.ouvrage_id, e.code_barres, o.titre FROM exemplaire e "
                + "JOIN ouvrage o ON o.id = e.ouvrage_id WHERE e.id = ?", exemplaireId);
        Object[] r = Sql.ligne(c, "SELECT r.id, CONCAT_WS(' ', a.prenom, a.nom), a.matricule, a.telephone, a.email "
                + "FROM reservation r JOIN adherent a ON a.id = r.adherent_id "
                + "WHERE r.ouvrage_id = ? AND r.statut = 'EN_ATTENTE' "
                + "ORDER BY r.date_reservation, r.id LIMIT 1 FOR UPDATE", ex[0]);
        if (r == null) {
            Sql.maj(c, "UPDATE exemplaire SET statut = 'DISPONIBLE' WHERE id = ?", exemplaireId);
            return null;
        }
        int delai = Outils.parametreEntier(c, "delai_retrait_reservation", 3);
        Sql.maj(c, "UPDATE reservation SET statut = 'A_RETIRER', exemplaire_id = ?, "
                + "date_limite_retrait = DATE_ADD(CURDATE(), INTERVAL ? DAY) WHERE id = ?", exemplaireId, delai, r[0]);
        Sql.maj(c, "UPDATE exemplaire SET statut = 'RESERVE' WHERE id = ?", exemplaireId);
        Object limite = Sql.valeur(c, "SELECT date_limite_retrait FROM reservation WHERE id = ?", r[0]);
        Outils.journal(c, "MISE_DE_COTE", ex[1] + " pour " + r[2]);
        String contact = Outils.vide((String) r[3]);
        if (r[4] != null && !Outils.vide((String) r[4]).isEmpty()) {
            contact = contact.isEmpty() ? (String) r[4] : contact + " / " + r[4];
        }
        return "RÉSERVÉ : mettez l'exemplaire " + ex[1] + " de côté pour " + r[1] + " (" + r[2] + ")"
                + (contact.isEmpty() ? "" : ", à prévenir : " + contact)
                + ".\nÀ retirer avant le " + Outils.date(limite) + ".";
    }
}
