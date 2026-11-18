package gesbiblio.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import gesbiblio.metier.AdherentService;
import gesbiblio.metier.Session;

/** Inscription et suivi des adhérents. */
public class AdherentPanel extends JPanel implements Actualisable {

    private static final long serialVersionUID = 1L;

    private final JTextField recherche = new JTextField(22);
    private final JComboBox<String> filtre = new JComboBox<String>(new String[] { "Tous", "ACTIF", "SUSPENDU" });
    private final JTable table = Ui.tableau();

    public AdherentPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel haut = Ui.ligne(new JLabel("Matricule, nom ou téléphone :"), recherche, new JLabel("Statut :"), filtre,
                Ui.bouton("Rechercher", e -> actualiser()));
        if (Session.aLeRole(Session.BIBLIOTHECAIRE)) {
            haut.add(Ui.boutonPrincipal("Nouvel adhérent", e -> nouveau()));
            haut.add(Ui.bouton("Modifier", e -> modifier()));
            haut.add(Ui.bouton("Suspendre / réactiver", e -> basculer()));
            haut.add(Ui.boutonDanger("Supprimer", e -> supprimer()));
        }
        haut.add(Ui.bouton("Historique", e -> historique()));
        add(haut, BorderLayout.NORTH);
        add(Ui.defilement(table), BorderLayout.CENTER);
        recherche.addActionListener(e -> actualiser());
        filtre.addActionListener(e -> actualiser());
        actualiser();
    }

    @Override
    public void actualiser() {
        String q = "%" + recherche.getText().trim() + "%";
        String statut = (String) filtre.getSelectedItem();
        Ui.charger(this, table, "SELECT a.id AS `N°`, a.matricule AS Matricule, a.nom AS Nom, a.prenom AS `Prénom`, "
                + "a.categorie_code AS `Catégorie`, a.statut AS Statut, a.telephone AS `Téléphone`, a.email AS `E-mail`, "
                + "a.date_inscription AS `Inscrit le`, a.date_expiration AS `Carte jusqu'au`, "
                + "(SELECT COUNT(*) FROM emprunt em WHERE em.adherent_id = a.id AND em.date_retour IS NULL) AS `Prêts en cours`, "
                + "(SELECT IFNULL(SUM(p.montant), 0) FROM penalite p WHERE p.adherent_id = a.id AND p.statut = 'IMPAYEE') AS `Impayé` "
                + "FROM adherent a WHERE (a.matricule LIKE ? OR a.nom LIKE ? OR a.prenom LIKE ? OR a.telephone LIKE ?) "
                + "AND (? = 'Tous' OR a.statut = ?) ORDER BY a.nom, a.prenom", q, q, q, q, statut, statut);
    }

    private List<String> categories() {
        try {
            return AdherentService.categories();
        } catch (Exception e) {
            Ui.erreur(this, e);
            return new ArrayList<String>();
        }
    }

    private Formulaire formulaire(Object mat, Object nom, Object prenom, Object cat, Object tel, Object mail, Object exp) {
        return new Formulaire()
                .texte("matricule", "Matricule / n° de carte * :", mat)
                .texte("nom", "Nom * :", nom)
                .texte("prenom", "Prénom :", prenom)
                .liste("categorie", "Catégorie :", categories(), cat, false)
                .texte("telephone", "Téléphone :", tel)
                .texte("email", "E-mail :", mail)
                .texte("expiration", "Carte valable jusqu'au :", exp)
                .note("Date au format jj/mm/aaaa ; laissez vide pour une carte sans limite.");
    }

    private void nouveau() {
        final Formulaire f = formulaire("", "", "", "ETUDIANT", "", "", "");
        if (f.executer(this, "Nouvel adhérent", () -> AdherentService.ajouter(f.get("matricule"), f.get("nom"), f.get("prenom"),
                f.get("email"), f.get("telephone"), f.get("categorie"), f.get("expiration")))) {
            recherche.setText(f.get("matricule"));
            actualiser();
        }
    }

    private void modifier() {
        final Integer id = Ui.idSelectionne(this, table, "un adhérent");
        if (id == null) {
            return;
        }
        final Formulaire f = formulaire(Ui.selection(table, 1), Ui.selection(table, 2), Ui.selection(table, 3), Ui.selection(table, 4),
                Ui.selection(table, 6), Ui.selection(table, 7), Ui.date(Ui.selection(table, 9)));
        if (f.executer(this, "Modifier l'adhérent", () -> {
            AdherentService.modifier(id, f.get("matricule"), f.get("nom"), f.get("prenom"), f.get("email"), f.get("telephone"),
                    f.get("categorie"), f.get("expiration"));
            return null;
        })) {
            actualiser();
        }
    }

    private void basculer() {
        final Integer id = Ui.idSelectionne(this, table, "un adhérent");
        if (id != null) {
            Ui.lancer(this, () -> AdherentService.basculerStatut(id), this::actualiser);
        }
    }

    private void supprimer() {
        final Integer id = Ui.idSelectionne(this, table, "un adhérent");
        if (id != null && Ui.confirmer(this, "Supprimer définitivement l'adhérent " + Ui.selection(table, 1) + " ?")) {
            Ui.lancer(this, () -> {
                AdherentService.supprimer(id);
                return "Adhérent supprimé.";
            }, this::actualiser);
        }
    }

    /** Fiche complète : situation, historique des prêts, pénalités, réservations. */
    private void historique() {
        final Integer id = Ui.idSelectionne(this, table, "un adhérent");
        if (id == null) {
            return;
        }
        String situation;
        try {
            situation = AdherentService.situation(Ui.s(Ui.selection(table, 1)));
        } catch (Exception e) {
            Ui.erreur(this, e);
            return;
        }
        JTable prets = Ui.tableau();
        Ui.charger(this, prets, "SELECT o.titre AS Titre, ex.code_barres AS `Code-barres`, em.date_emprunt AS `Emprunté le`, "
                + "em.date_echeance AS `Échéance`, em.date_retour AS `Rendu le`, em.etat_retour AS `État retour`, "
                + "em.nb_renouvellements AS `Renouv.` FROM emprunt em JOIN exemplaire ex ON ex.id = em.exemplaire_id "
                + "JOIN ouvrage o ON o.id = ex.ouvrage_id WHERE em.adherent_id = ? ORDER BY em.date_emprunt DESC, em.id DESC", id);
        JTable penalites = Ui.tableau();
        Ui.charger(this, penalites, "SELECT date_creation AS `Date`, motif AS Motif, montant AS Montant, statut AS Statut, "
                + "date_reglement AS `Réglée le` FROM penalite WHERE adherent_id = ? ORDER BY id DESC", id);
        JTable resas = Ui.tableau();
        Ui.charger(this, resas, "SELECT r.date_reservation AS `Date`, o.titre AS Titre, r.statut AS Statut, "
                + "r.date_limite_retrait AS `À retirer avant` FROM reservation r JOIN ouvrage o ON o.id = r.ouvrage_id "
                + "WHERE r.adherent_id = ? ORDER BY r.id DESC", id);

        JTabbedPane onglets = new JTabbedPane();
        Theme.ongletsPlats(onglets);
        onglets.addTab("Prêts (" + prets.getRowCount() + ")", Ui.defilement(prets));
        onglets.addTab("Pénalités (" + penalites.getRowCount() + ")", Ui.defilement(penalites));
        onglets.addTab("Réservations (" + resas.getRowCount() + ")", Ui.defilement(resas));
        JPanel p = new JPanel(new BorderLayout(6, 6));
        javax.swing.JTextArea t = new javax.swing.JTextArea(situation);
        t.setEditable(false);
        t.setFont(Theme.police(java.awt.Font.PLAIN, 13));
        t.setBackground(Theme.PRIMAIRE_CLAIR);
        t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.PRIMAIRE),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        p.add(t, BorderLayout.NORTH);
        p.add(onglets, BorderLayout.CENTER);
        p.setPreferredSize(new Dimension(820, 460));
        JOptionPane.showMessageDialog(this, p, "Historique de " + Ui.selection(table, 1), JOptionPane.PLAIN_MESSAGE);
    }
}
