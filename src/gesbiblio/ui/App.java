package gesbiblio.ui;

import java.awt.Component;
import java.sql.Connection;
import java.sql.SQLException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import gesbiblio.db.Database;
import gesbiblio.db.Sql;
import gesbiblio.metier.AdministrationService;
import gesbiblio.metier.Session;

/**
 * Point d'entrée de GesBiblio 2.
 *   java -cp "bin;mysql-connector-java-5.1.41-bin.jar" gesbiblio.ui.App
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Theme.appliquer();
        SwingUtilities.invokeLater(App::demarrer);
    }

    /** Vérifie la base, demande l'identification puis ouvre la fenêtre principale. */
    static void demarrer() {
        if (!verifierBase()) {
            System.exit(1);
        }
        while (true) {
            Formulaire f = new Formulaire()
                    .entete("GesBiblio", "Connexion du personnel")
                    .texte("login", "Identifiant :", "")
                    .motDePasse("mdp", "Mot de passe :")
                    .note("Premier lancement : admin / admin");
            final Object[][] res = new Object[1][];
            boolean ok = f.executer(null, "GesBiblio - Connexion", () -> {
                res[0] = AdministrationService.connecter(f.get("login"), f.get("mdp"));
                return null;
            });
            if (!ok) {
                System.exit(0);
            }
            boolean doitChanger = (Boolean) res[0][1];
            if (doitChanger && !changerMotDePasse(null, true)) {
                Session.fermer();
                continue;
            }
            MainFrame fenetre = new MainFrame();
            fenetre.setVisible(true);
            fenetre.traitementsDuJour();
            return;
        }
    }

    /** @return true si le mot de passe a été changé */
    static boolean changerMotDePasse(Component parent, boolean obligatoire) {
        final Formulaire f = new Formulaire()
                .motDePasse("ancien", "Mot de passe actuel :")
                .motDePasse("nouveau", "Nouveau mot de passe :")
                .motDePasse("confirmation", "Confirmation :");
        if (obligatoire) {
            f.note("Vous utilisez un mot de passe provisoire : choisissez-en un nouveau (6 caractères minimum).");
        }
        return f.executer(parent, "Changer le mot de passe", () -> {
            if (!f.get("nouveau").equals(f.get("confirmation"))) {
                throw new gesbiblio.metier.RegleException("La confirmation ne correspond pas au nouveau mot de passe.");
            }
            AdministrationService.changerMotDePasse(f.get("ancien"), f.get("nouveau"));
            return "Mot de passe modifié.";
        });
    }

    /** Teste la connexion et la présence des tables ; explique quoi faire en cas de problème. */
    private static boolean verifierBase() {
        try (Connection c = Database.connexion()) {
            Sql.valeur(c, "SELECT COUNT(*) FROM personnel");
            return true;
        } catch (SQLException e) {
            String aide;
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("Unknown database") || msg.contains("doesn't exist")) {
                aide = "La base « gesbiblio » n'est pas encore créée. Dans une invite de commandes :\n"
                        + "   mysql -u root -p < database\\schema.sql\n"
                        + "puis, pour reprendre les anciennes données :\n"
                        + "   mysql -u root -p < database\\migration_depuis_biblio.sql";
            } else if (msg.contains("Access denied")) {
                aide = "Identifiant ou mot de passe MySQL refusé.\nCorrigez db.user / db.password dans :\n   "
                        + Database.fichierConfig().getAbsolutePath();
            } else {
                aide = "Vérifiez que MySQL est démarré (XAMPP, WAMP ou service MySQL)\net les paramètres de :\n   "
                        + Database.fichierConfig().getAbsolutePath();
            }
            JOptionPane.showMessageDialog(null, "Connexion à la base impossible.\n\n" + aide + "\n\nDétail : " + msg,
                    "GesBiblio", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
}
