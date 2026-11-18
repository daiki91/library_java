package gesbiblio.ui;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import gesbiblio.metier.AdministrationService;
import gesbiblio.metier.Outils;

/** Administration : comptes du personnel, règlement de prêt, journal d'audit. */
public class AdminPanel extends JPanel implements Actualisable {

    private static final long serialVersionUID = 1L;

    private final JTable comptes = Ui.tableau();
    private final JTable parametres = Ui.tableau();
    private final JTable categories = Ui.tableau();
    private final JTable journal = Ui.tableau();
    private final JTextField filtreJournal = new JTextField(20);

    public AdminPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JTabbedPane t = new JTabbedPane();
        Theme.ongletsPlats(t);

        JPanel pComptes = new JPanel(new BorderLayout());
        pComptes.add(Ui.ligne(Ui.boutonPrincipal("Nouveau compte", e -> nouveauCompte()), Ui.bouton("Modifier", e -> modifierCompte()),
                Ui.bouton("Réinitialiser le mot de passe", e -> reinitialiser())), BorderLayout.NORTH);
        pComptes.add(Ui.defilement(comptes), BorderLayout.CENTER);
        t.addTab("Comptes du personnel", pComptes);

        JPanel pParam = new JPanel(new BorderLayout());
        pParam.add(Ui.ligne(Ui.titre("Paramètres"), Ui.bouton("Modifier la valeur", e -> modifierParametre())), BorderLayout.NORTH);
        pParam.add(Ui.defilement(parametres), BorderLayout.CENTER);
        JPanel pCat = new JPanel(new BorderLayout());
        pCat.add(Ui.ligne(Ui.titre("Droits de prêt par catégorie d'adhérent"), Ui.bouton("Modifier", e -> modifierCategorie(false)),
                Ui.bouton("Nouvelle catégorie", e -> modifierCategorie(true))), BorderLayout.NORTH);
        pCat.add(Ui.defilement(categories), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pParam, pCat);
        split.setResizeWeight(0.5);
        t.addTab("Règlement de prêt", split);

        JPanel pJournal = new JPanel(new BorderLayout());
        pJournal.add(Ui.ligne(new JLabel("Filtrer (action, login, détail) :"), filtreJournal,
                Ui.bouton("Filtrer", e -> actualiser())), BorderLayout.NORTH);
        pJournal.add(Ui.defilement(journal), BorderLayout.CENTER);
        t.addTab("Journal d'audit", pJournal);
        filtreJournal.addActionListener(e -> actualiser());

        add(t, BorderLayout.CENTER);
        actualiser();
    }

    @Override
    public void actualiser() {
        Ui.charger(this, comptes, "SELECT id AS `N°`, login AS Identifiant, nom AS Nom, role AS `Rôle`, "
                + "IF(actif = 1, 'Oui', 'Non') AS Actif, IF(doit_changer_mdp = 1, 'Oui', 'Non') AS `Mot de passe provisoire` "
                + "FROM personnel ORDER BY login");
        Ui.charger(this, parametres, "SELECT cle AS `Clé`, valeur AS Valeur, description AS Description FROM parametre ORDER BY cle");
        Ui.charger(this, categories, "SELECT code AS Code, libelle AS `Libellé`, max_emprunts AS `Prêts simultanés max`, "
                + "duree_pret_jours AS `Durée du prêt (jours)`, max_renouvellements AS `Renouvellements max` "
                + "FROM categorie_adherent ORDER BY code");
        String q = "%" + filtreJournal.getText().trim() + "%";
        Ui.charger(this, journal, "SELECT date_heure AS `Date et heure`, login AS Utilisateur, action AS Action, details AS `Détails` "
                + "FROM journal_audit WHERE action LIKE ? OR login LIKE ? OR details LIKE ? ORDER BY id DESC LIMIT 1000", q, q, q);
    }

    private void nouveauCompte() {
        final Formulaire f = new Formulaire()
                .texte("login", "Identifiant :", "")
                .texte("nom", "Nom complet :", "")
                .liste("role", "Rôle :", AdministrationService.ROLES, "AGENT", false)
                .motDePasse("mdp", "Mot de passe provisoire :")
                .note("AGENT : prêts, retours, réservations, encaissements. BIBLIOTHECAIRE : + catalogue et adhérents.<br>"
                        + "ADMIN : + comptes, règlement, journal et remises. Le mot de passe devra être changé à la 1re connexion.");
        if (f.executer(this, "Nouveau compte", () -> {
            AdministrationService.creerCompte(f.get("login"), f.get("nom"), f.get("role"), f.get("mdp"));
            return "Compte « " + f.get("login") + " » créé.";
        })) {
            actualiser();
        }
    }

    private void modifierCompte() {
        final Integer id = Ui.idSelectionne(this, comptes, "un compte");
        if (id == null) {
            return;
        }
        final Formulaire f = new Formulaire()
                .texte("nom", "Nom complet :", Ui.selection(comptes, 2))
                .liste("role", "Rôle :", AdministrationService.ROLES, Ui.selection(comptes, 3), false)
                .caseACocher("actif", "Compte actif :", "Oui".equals(Ui.selection(comptes, 4)));
        if (f.executer(this, "Compte " + Ui.selection(comptes, 1), () -> {
            AdministrationService.modifierCompte(id, f.get("nom"), f.get("role"), f.coche("actif"));
            return null;
        })) {
            actualiser();
        }
    }

    private void reinitialiser() {
        final Integer id = Ui.idSelectionne(this, comptes, "un compte");
        if (id == null) {
            return;
        }
        final Formulaire f = new Formulaire().motDePasse("mdp", "Nouveau mot de passe provisoire :");
        if (f.executer(this, "Réinitialiser " + Ui.selection(comptes, 1), () -> {
            AdministrationService.reinitialiserMotDePasse(id, f.get("mdp"));
            return "Mot de passe réinitialisé : il devra être changé à la prochaine connexion.";
        })) {
            actualiser();
        }
    }

    private void modifierParametre() {
        final Object cle = Ui.selection(parametres, 0);
        if (cle == null) {
            Ui.info(this, "Sélectionnez d'abord un paramètre.");
            return;
        }
        final Formulaire f = new Formulaire().texte("valeur", Ui.s(Ui.selection(parametres, 2)) + " :", Ui.selection(parametres, 1));
        if (f.executer(this, "Paramètre " + cle, () -> {
            AdministrationService.modifierParametre(cle.toString(), f.get("valeur"));
            return null;
        })) {
            actualiser();
        }
    }

    private void modifierCategorie(boolean nouvelle) {
        Object code = nouvelle ? "" : Ui.selection(categories, 0);
        if (code == null) {
            Ui.info(this, "Sélectionnez d'abord une catégorie.");
            return;
        }
        final Formulaire f = new Formulaire();
        if (nouvelle) {
            f.texte("code", "Code (ex. DOCTORANT) :", "");
        }
        f.texte("libelle", "Libellé :", nouvelle ? "" : Ui.selection(categories, 1))
                .nombre("max", "Prêts simultanés max :", nouvelle ? 3 : ((Number) Ui.selection(categories, 2)).intValue(), 0, 100)
                .nombre("duree", "Durée du prêt (jours) :", nouvelle ? 14 : ((Number) Ui.selection(categories, 3)).intValue(), 1, 365)
                .nombre("renouv", "Renouvellements max :", nouvelle ? 1 : ((Number) Ui.selection(categories, 4)).intValue(), 0, 20);
        final String codeFixe = Ui.s(code);
        if (f.executer(this, nouvelle ? "Nouvelle catégorie d'adhérent" : "Catégorie " + codeFixe, () -> {
            String c = nouvelle ? f.get("code") : codeFixe;
            if (Outils.vide(c).isEmpty()) {
                throw new gesbiblio.metier.RegleException("Le code est obligatoire.");
            }
            AdministrationService.modifierCategorieAdherent(c, f.get("libelle"), f.entier("max"), f.entier("duree"), f.entier("renouv"));
            return null;
        })) {
            actualiser();
        }
    }
}
