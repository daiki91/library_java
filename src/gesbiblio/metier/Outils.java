package gesbiblio.metier;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import gesbiblio.db.Sql;

/** Paramètres du règlement et journal d'audit. */
public final class Outils {

    private Outils() {
    }

    public static String parametre(Connection c, String cle, String defaut) throws SQLException {
        Object v = Sql.valeur(c, "SELECT valeur FROM parametre WHERE cle = ?", cle);
        return v == null ? defaut : v.toString().trim();
    }

    public static int parametreEntier(Connection c, String cle, int defaut) throws SQLException {
        try {
            return Integer.parseInt(parametre(c, cle, String.valueOf(defaut)));
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    public static double parametreDecimal(Connection c, String cle, double defaut) throws SQLException {
        try {
            return Double.parseDouble(parametre(c, cle, String.valueOf(defaut)).replace(',', '.'));
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    /** Enregistre une opération dans le journal (dans la même transaction que l'opération). */
    public static void journal(Connection c, String action, String details) throws SQLException {
        if (details != null && details.length() > 500) {
            details = details.substring(0, 497) + "...";
        }
        Sql.maj(c, "INSERT INTO journal_audit (date_heure, login, action, details) VALUES (NOW(), ?, ?, ?)",
                Session.loginCourant(), action, details);
    }

    /** Valeur booléenne SQL (TINYINT(1) arrive en Boolean, une comparaison en nombre). */
    public static boolean vrai(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        return o instanceof Number && ((Number) o).intValue() != 0;
    }

    public static String vide(String s) {
        return s == null ? "" : s.trim();
    }

    /** Chaîne vide -> null (pour les colonnes facultatives). */
    public static String nul(String s) {
        s = vide(s);
        return s.isEmpty() ? null : s;
    }

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Date SQL -> "jj/mm/aaaa". */
    public static String date(Object d) {
        if (d == null) {
            return "-";
        }
        if (d instanceof java.sql.Date) {
            return ((java.sql.Date) d).toLocalDate().format(FR);
        }
        if (d instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) d).toLocalDateTime().toLocalDate().format(FR);
        }
        return d.toString();
    }

    /** "jj/mm/aaaa" (ou vide) -> date SQL (ou null). */
    public static java.sql.Date lireDate(String s) throws RegleException {
        s = vide(s);
        if (s.isEmpty()) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(s, FR));
        } catch (DateTimeParseException e) {
            throw new RegleException("Date invalide « " + s + " » : utilisez le format jj/mm/aaaa.");
        }
    }

    public static Integer lireEntier(String s, String champ) throws RegleException {
        s = vide(s);
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new RegleException(champ + " doit être un nombre entier.");
        }
    }

    public static String montant(double m, String devise) {
        if (m == Math.rint(m)) {
            return String.format("%,.0f %s", m, devise);
        }
        return String.format("%,.2f %s", m, devise);
    }
}
