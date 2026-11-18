package gesbiblio.metier;

/**
 * Membre du personnel connecté.
 * Rôles : AGENT (prêts, retours, réservations, encaissement),
 *         BIBLIOTHECAIRE (+ catalogue et adhérents),
 *         ADMIN (+ comptes, paramètres, journal, remise de pénalités).
 */
public final class Session {

    public static final String ADMIN = "ADMIN";
    public static final String BIBLIOTHECAIRE = "BIBLIOTHECAIRE";
    public static final String AGENT = "AGENT";

    private static Session courante;

    private final int id;
    private final String login;
    private final String nom;
    private final String role;

    public Session(int id, String login, String nom, String role) {
        this.id = id;
        this.login = login;
        this.nom = nom;
        this.role = role;
    }

    public static Session courante() {
        return courante;
    }

    public static void ouvrir(Session s) {
        courante = s;
    }

    public static void fermer() {
        courante = null;
    }

    /** Login de l'utilisateur courant (ou "systeme" s'il n'y en a pas). */
    public static String loginCourant() {
        return courante == null ? "systeme" : courante.login;
    }

    /** Id de l'utilisateur courant (ou null). */
    public static Integer idCourant() {
        return courante == null ? null : courante.id;
    }

    public static int niveau(String role) {
        if (ADMIN.equals(role)) {
            return 3;
        }
        if (BIBLIOTHECAIRE.equals(role)) {
            return 2;
        }
        return 1;
    }

    /** Vrai si l'utilisateur courant a au moins le rôle demandé. */
    public static boolean aLeRole(String roleMinimum) {
        return courante != null && niveau(courante.role) >= niveau(roleMinimum);
    }

    public static void exiger(String roleMinimum) throws RegleException {
        if (!aLeRole(roleMinimum)) {
            throw new RegleException("Action réservée au rôle " + roleMinimum + " (ou supérieur).");
        }
    }

    public int getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getNom() {
        return nom;
    }

    public String getRole() {
        return role;
    }
}
