package gesbiblio.metier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import gesbiblio.db.Sql;

/** Comptes du personnel, mots de passe, paramètres du règlement, pénalités. */
public final class AdministrationService {

    public static final List<String> ROLES = Arrays.asList(Session.AGENT, Session.BIBLIOTHECAIRE, Session.ADMIN);

    private AdministrationService() {
    }

    // ------------------------------------------------------------------ MOTS DE PASSE

    static String hacher(String sel, String motDePasse) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((sel + motDePasse).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nouveauSel() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    private static void verifierRobustesse(String mdp) throws RegleException {
        if (mdp == null || mdp.length() < 6) {
            throw new RegleException("Le mot de passe doit contenir au moins 6 caractères.");
        }
    }

    /**
     * Connexion du personnel.
     * @return la session ouverte ; getRole() etc. Le booléen « doit changer » est lu par l'interface.
     */
    public static Object[] connecter(final String login, final String motDePasse) throws SQLException, RegleException {
        return Transaction.executer(c -> {
            Object[] p = Sql.ligne(c, "SELECT id, login, nom, role, sel, mot_de_passe_hash, actif, doit_changer_mdp "
                    + "FROM personnel WHERE login = ?", Outils.vide(login));
            if (p == null || !hacher((String) p[4], motDePasse == null ? "" : motDePasse).equals(p[5])) {
                throw new RegleException("Identifiant ou mot de passe incorrect.");
            }
            if (!Outils.vrai(p[6])) {
                throw new RegleException("Ce compte est désactivé. Contactez l'administrateur.");
            }
            Session s = new Session(((Number) p[0]).intValue(), (String) p[1], (String) p[2], (String) p[3]);
            Session.ouvrir(s);
            Outils.journal(c, "CONNEXION", s.getLogin());
            return new Object[] { s, Outils.vrai(p[7]) };
        });
    }

    public static void changerMotDePasse(final String ancien, final String nouveau) throws SQLException, RegleException {
        if (Session.courante() == null) {
            throw new RegleException("Aucune session ouverte.");
        }
        verifierRobustesse(nouveau);
        Transaction.executer(c -> {
            Object[] p = Sql.ligne(c, "SELECT sel, mot_de_passe_hash FROM personnel WHERE id = ?", Session.idCourant());
            if (p == null || !hacher((String) p[0], ancien == null ? "" : ancien).equals(p[1])) {
                throw new RegleException("Ancien mot de passe incorrect.");
            }
            if (nouveau.equals(ancien)) {
                throw new RegleException("Le nouveau mot de passe doit être différent de l'ancien.");
            }
            String sel = nouveauSel();
            Sql.maj(c, "UPDATE personnel SET sel = ?, mot_de_passe_hash = ?, doit_changer_mdp = 0 WHERE id = ?",
                    sel, hacher(sel, nouveau), Session.idCourant());
            Outils.journal(c, "CHANGEMENT_MDP", Session.loginCourant());
            return null;
        });
    }

    // ------------------------------------------------------------------ COMPTES DU PERSONNEL

    public static void creerCompte(final String login, final String nom, final String role, final String motDePasseProvisoire)
            throws SQLException, RegleException {
        Session.exiger(Session.ADMIN);
        if (Outils.vide(login).isEmpty() || Outils.vide(nom).isEmpty()) {
            throw new RegleException("Identifiant et nom sont obligatoires.");
        }
        if (!ROLES.contains(role)) {
            throw new RegleException("Rôle inconnu.");
        }
        verifierRobustesse(motDePasseProvisoire);
        Transaction.executer(c -> {
            if (Sql.entier(c, "SELECT COUNT(*) FROM personnel WHERE login = ?", Outils.vide(login)) > 0) {
                throw new RegleException("L'identifiant « " + Outils.vide(login) + " » existe déjà.");
            }
            String sel = nouveauSel();
            Sql.maj(c, "INSERT INTO personnel (login, nom, role, sel, mot_de_passe_hash, actif, doit_changer_mdp) VALUES (?, ?, ?, ?, ?, 1, 1)",
                    Outils.vide(login), Outils.vide(nom), role, sel, hacher(sel, motDePasseProvisoire));
            Outils.journal(c, "CREATION_COMPTE", Outils.vide(login) + " (" + role + ")");
            return null;
        });
    }

    public static void reinitialiserMotDePasse(final int id, final String motDePasseProvisoire) throws SQLException, RegleException {
        Session.exiger(Session.ADMIN);
        verifierRobustesse(motDePasseProvisoire);
        Transaction.executer(c -> {
            String sel = nouveauSel();
            if (Sql.maj(c, "UPDATE personnel SET sel = ?, mot_de_passe_hash = ?, doit_changer_mdp = 1 WHERE id = ?",
                    sel, hacher(sel, motDePasseProvisoire), id) == 0) {
                throw new RegleException("Compte introuvable.");
            }
            Outils.journal(c, "REINIT_MDP", "compte n° " + id);
            return null;
        });
    }

    public static void modifierCompte(final int id, final String nom, final String role, final boolean actif)
            throws SQLException, RegleException {
        Session.exiger(Session.ADMIN);
        if (!ROLES.contains(role)) {
            throw new RegleException("Rôle inconnu.");
        }
        Transaction.executer(c -> {
            if (Session.idCourant() != null && id == Session.idCourant() && (!actif || !Session.ADMIN.equals(role))) {
                throw new RegleException("Vous ne pouvez pas désactiver votre propre compte ni retirer votre rôle ADMIN.");
            }
            if ((!actif || !Session.ADMIN.equals(role))
                    && Sql.entier(c, "SELECT COUNT(*) FROM personnel WHERE role = 'ADMIN' AND actif = 1 AND id <> ?", id) == 0) {
                throw new RegleException("Il doit rester au moins un administrateur actif.");
            }
            Sql.maj(c, "UPDATE personnel SET nom = ?, role = ?, actif = ? WHERE id = ?", Outils.vide(nom), role, actif ? 1 : 0, id);
            Outils.journal(c, "MODIF_COMPTE", "compte n° " + id + " " + role + (actif ? "" : " désactivé"));
            return null;
        });
    }

    // ------------------------------------------------------------------ RÈGLEMENT

    public static void modifierParametre(final String cle, final String valeur) throws SQLException, RegleException {
        Session.exiger(Session.ADMIN);
        if (Outils.vide(valeur).isEmpty()) {
            throw new RegleException("La valeur ne peut pas être vide.");
        }
        if (!"devise".equals(cle)) {
            try {
                if (Double.parseDouble(Outils.vide(valeur).replace(',', '.')) < 0) {
                    throw new RegleException("La valeur doit être positive.");
                }
            } catch (NumberFormatException e) {
                throw new RegleException("La valeur doit être un nombre.");
            }
        }
        Transaction.executer(c -> {
            Sql.maj(c, "UPDATE parametre SET valeur = ? WHERE cle = ?", Outils.vide(valeur), cle);
            Outils.journal(c, "PARAMETRE", cle + " = " + valeur);
            return null;
        });
    }

    public static void modifierCategorieAdherent(final String code, final String libelle, final int maxEmprunts,
            final int duree, final int maxRenouv) throws SQLException, RegleException {
        Session.exiger(Session.ADMIN);
        if (maxEmprunts < 0 || duree < 1 || maxRenouv < 0) {
            throw new RegleException("Valeurs invalides (durée ≥ 1 jour, autres valeurs ≥ 0).");
        }
        Transaction.executer(c -> {
            int n = Sql.maj(c, "UPDATE categorie_adherent SET libelle = ?, max_emprunts = ?, duree_pret_jours = ?, max_renouvellements = ? "
                    + "WHERE code = ?", Outils.vide(libelle), maxEmprunts, duree, maxRenouv, code);
            if (n == 0) {
                Sql.maj(c, "INSERT INTO categorie_adherent VALUES (?, ?, ?, ?, ?)",
                        Outils.vide(code).toUpperCase(), Outils.vide(libelle), maxEmprunts, duree, maxRenouv);
            }
            Outils.journal(c, "CATEGORIE_ADHERENT", code + " : " + maxEmprunts + " prêts, " + duree + " j, " + maxRenouv + " renouv.");
            return null;
        });
    }

    // ------------------------------------------------------------------ PÉNALITÉS

    public static String payerPenalite(final int id) throws SQLException, RegleException {
        Session.exiger(Session.AGENT);
        return reglerPenalite(id, "PAYEE", null);
    }

    /** Annulation d'une pénalité (geste commercial, erreur) : réservée à l'administrateur. */
    public static String remettrePenalite(final int id, final String motif) throws SQLException, RegleException {
        Session.exiger(Session.ADMIN);
        if (Outils.vide(motif).isEmpty()) {
            throw new RegleException("Indiquez le motif de la remise.");
        }
        return reglerPenalite(id, "REMISE", motif);
    }

    private static String reglerPenalite(final int id, final String statut, final String motif) throws SQLException, RegleException {
        return Transaction.executer((Connection c) -> {
            Object[] p = Sql.ligne(c, "SELECT statut, montant FROM penalite WHERE id = ? FOR UPDATE", id);
            if (p == null) {
                throw new RegleException("Pénalité introuvable.");
            }
            if (!"IMPAYEE".equals(p[0])) {
                throw new RegleException("Cette pénalité est déjà réglée (" + p[0] + ").");
            }
            Sql.maj(c, "UPDATE penalite SET statut = ?, date_reglement = CURDATE() WHERE id = ?", statut, id);
            Outils.journal(c, "PENALITE_" + statut, "n° " + id + (motif == null ? "" : " - " + motif));
            return "Pénalité n° " + id + " : " + statut + ".";
        });
    }
}
