package gesbiblio.ui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import gesbiblio.metier.CatalogueService;
import gesbiblio.metier.ReservationService;
import gesbiblio.metier.Session;

/** Catalogue : recherche des titres, gestion des ouvrages et de leurs exemplaires. */
public class CataloguePanel extends JPanel implements Actualisable {

    private static final long serialVersionUID = 1L;

    private final JTextField recherche = new JTextField(28);
    private final JTable ouvrages = Ui.tableau();
    private final JTable exemplaires = Ui.tableau();
    private final JLabel titreExemplaires = new JLabel("Exemplaires : sélectionnez un titre");

    public CataloguePanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        boolean gestion = Session.aLeRole(Session.BIBLIOTHECAIRE);

        List<JButton> b1 = new ArrayList<JButton>();
        b1.add(Ui.bouton("Rechercher", e -> actualiser()));
        if (gestion) {
            b1.add(Ui.boutonPrincipal("Nouvel ouvrage", e -> nouvelOuvrage()));
            b1.add(Ui.bouton("Modifier", e -> modifierOuvrage()));
            b1.add(Ui.boutonDanger("Supprimer", e -> supprimerOuvrage()));
            b1.add(Ui.bouton("Nouvelle catégorie", e -> nouvelleCategorie()));
        }
        b1.add(Ui.bouton("Réserver ce titre", e -> reserver()));
        JPanel haut = Ui.ligne(new JLabel("Titre, auteur, ISBN ou code-barres :"), recherche);
        for (JButton b : b1) {
            haut.add(b);
        }
        add(haut, BorderLayout.NORTH);

        JPanel bas = new JPanel(new BorderLayout());
        JPanel barre = Ui.ligne(titreExemplaires);
        if (gestion) {
            barre.add(Ui.boutonPrincipal("Ajouter exemplaire(s)", e -> ajouterExemplaires()));
            barre.add(Ui.bouton("Modifier l'exemplaire", e -> modifierExemplaire()));
            barre.add(Ui.boutonDanger("Supprimer l'exemplaire", e -> supprimerExemplaire()));
        }
        bas.add(barre, BorderLayout.NORTH);
        bas.add(Ui.defilement(exemplaires), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Ui.defilement(ouvrages), bas);
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        recherche.addActionListener(e -> actualiser());
        ouvrages.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                chargerExemplaires();
            }
        });
        actualiser();
    }

    @Override
    public void actualiser() {
        String q = "%" + recherche.getText().trim() + "%";
        Ui.charger(this, ouvrages, "SELECT o.id AS `N°`, o.isbn AS ISBN, o.titre AS Titre, o.auteur AS Auteur, "
                + "o.editeur AS `Éditeur`, o.annee AS `Année`, c.libelle AS `Catégorie`, "
                + "(SELECT COUNT(*) FROM exemplaire e WHERE e.ouvrage_id = o.id AND e.statut NOT IN ('PERDU','RETIRE')) AS Exemplaires, "
                + "(SELECT COUNT(*) FROM exemplaire e WHERE e.ouvrage_id = o.id AND e.statut = 'DISPONIBLE') AS Disponibles, "
                + "(SELECT COUNT(*) FROM reservation r WHERE r.ouvrage_id = o.id AND r.statut = 'EN_ATTENTE') AS `En attente` "
                + "FROM ouvrage o LEFT JOIN categorie c ON c.id = o.categorie_id "
                + "WHERE o.titre LIKE ? OR o.auteur LIKE ? OR o.isbn LIKE ? "
                + "OR o.id IN (SELECT ouvrage_id FROM exemplaire WHERE code_barres LIKE ?) ORDER BY o.titre", q, q, q, q);
        chargerExemplaires();
    }

    private void chargerExemplaires() {
        Object id = Ui.selection(ouvrages, 0);
        if (id == null) {
            titreExemplaires.setText("Exemplaires : sélectionnez un titre");
            Ui.charger(this, exemplaires, "SELECT NULL AS `N°` FROM DUAL WHERE 1 = 0");
            return;
        }
        titreExemplaires.setText("Exemplaires de « " + Ui.s(Ui.selection(ouvrages, 2)) + " »");
        Ui.charger(this, exemplaires, "SELECT e.id AS `N°`, e.code_barres AS `Code-barres`, e.statut AS Statut, e.etat AS `État`, "
                + "e.rayon AS Rayon, e.date_acquisition AS `Acquis le`, "
                + "(SELECT CONCAT(a.matricule, ' - retour le ', DATE_FORMAT(em.date_echeance, '%d/%m/%Y')) FROM emprunt em "
                + " JOIN adherent a ON a.id = em.adherent_id WHERE em.exemplaire_id = e.id AND em.date_retour IS NULL) AS `En prêt chez`, "
                + "(SELECT CONCAT(a.matricule, ' jusqu''au ', DATE_FORMAT(r.date_limite_retrait, '%d/%m/%Y')) FROM reservation r "
                + " JOIN adherent a ON a.id = r.adherent_id WHERE r.exemplaire_id = e.id AND r.statut = 'A_RETIRER') AS `Mis de côté pour`, "
                + "(SELECT COUNT(*) FROM emprunt em WHERE em.exemplaire_id = e.id) AS `Nb prêts` "
                + "FROM exemplaire e WHERE e.ouvrage_id = ? ORDER BY e.code_barres", id);
    }

    private List<String> categories() {
        try {
            return CatalogueService.categories();
        } catch (Exception e) {
            Ui.erreur(this, e);
            return new ArrayList<String>();
        }
    }

    private void nouvelOuvrage() {
        final Formulaire f = new Formulaire()
                .texte("titre", "Titre * :", "")
                .texte("auteur", "Auteur :", "")
                .texte("isbn", "ISBN :", "")
                .texte("editeur", "Éditeur :", "")
                .texte("annee", "Année :", "")
                .liste("categorie", "Catégorie :", categories(), null, true)
                .nombre("nb", "Nombre d'exemplaires :", 1, 0, 200)
                .texte("rayon", "Rayon / cote :", "")
                .note("Les codes-barres des exemplaires sont générés automatiquement (EX000123...).");
        if (f.executer(this, "Nouvel ouvrage", () -> CatalogueService.ajouterOuvrage(f.get("isbn"), f.get("titre"), f.get("auteur"),
                f.get("editeur"), f.get("annee"), f.get("categorie"), f.entier("nb"), f.get("rayon")))) {
            recherche.setText(f.get("titre"));
            actualiser();
        }
    }

    private void modifierOuvrage() {
        final Integer id = Ui.idSelectionne(this, ouvrages, "un ouvrage");
        if (id == null) {
            return;
        }
        final Formulaire f = new Formulaire()
                .texte("titre", "Titre * :", Ui.selection(ouvrages, 2))
                .texte("auteur", "Auteur :", Ui.selection(ouvrages, 3))
                .texte("isbn", "ISBN :", Ui.selection(ouvrages, 1))
                .texte("editeur", "Éditeur :", Ui.selection(ouvrages, 4))
                .texte("annee", "Année :", Ui.selection(ouvrages, 5))
                .liste("categorie", "Catégorie :", categories(), Ui.selection(ouvrages, 6), true);
        if (f.executer(this, "Modifier l'ouvrage n° " + id, () -> {
            CatalogueService.modifierOuvrage(id, f.get("isbn"), f.get("titre"), f.get("auteur"), f.get("editeur"),
                    f.get("annee"), f.get("categorie"));
            return null;
        })) {
            actualiser();
        }
    }

    private void supprimerOuvrage() {
        final Integer id = Ui.idSelectionne(this, ouvrages, "un ouvrage");
        if (id != null && Ui.confirmer(this, "Supprimer « " + Ui.selection(ouvrages, 2) + " » et ses exemplaires ?")) {
            Ui.lancer(this, () -> {
                CatalogueService.supprimerOuvrage(id);
                return "Ouvrage supprimé.";
            }, this::actualiser);
        }
    }

    private void nouvelleCategorie() {
        final Formulaire f = new Formulaire().texte("libelle", "Libellé :", "");
        f.executer(this, "Nouvelle catégorie", () -> {
            CatalogueService.ajouterCategorie(f.get("libelle"));
            return "Catégorie ajoutée.";
        });
    }

    private void ajouterExemplaires() {
        final Integer id = Ui.idSelectionne(this, ouvrages, "un ouvrage");
        if (id == null) {
            return;
        }
        final Formulaire f = new Formulaire()
                .nombre("nb", "Nombre d'exemplaires :", 1, 1, 200)
                .texte("rayon", "Rayon / cote :", "")
                .texte("code", "Code-barres (facultatif) :", "")
                .note("Laissez le code-barres vide pour le générer. Un code manuel n'est possible que pour 1 exemplaire.");
        if (f.executer(this, "Ajouter des exemplaires à « " + Ui.selection(ouvrages, 2) + " »",
                () -> CatalogueService.ajouterExemplaires(id, f.entier("nb"), f.get("rayon"), f.get("code")))) {
            int ligne = ouvrages.getSelectedRow();
            actualiser();
            if (ligne >= 0 && ligne < ouvrages.getRowCount()) {
                ouvrages.setRowSelectionInterval(ligne, ligne);
            }
        }
    }

    private void modifierExemplaire() {
        final Integer id = Ui.idSelectionne(this, exemplaires, "un exemplaire");
        if (id == null) {
            return;
        }
        String statut = Ui.s(Ui.selection(exemplaires, 2));
        List<String> statuts = new ArrayList<String>(CatalogueService.STATUTS_MANUELS);
        if (!statuts.contains(statut)) {
            statuts.add(0, statut);
        }
        final Formulaire f = new Formulaire()
                .liste("statut", "Statut :", statuts, statut, false)
                .liste("etat", "État :", CatalogueService.ETATS, Ui.selection(exemplaires, 3), false)
                .texte("rayon", "Rayon / cote :", Ui.selection(exemplaires, 4))
                .note("EMPRUNTE et RESERVE sont gérés automatiquement par les prêts et les réservations.");
        if (f.executer(this, "Exemplaire " + Ui.selection(exemplaires, 1),
                () -> CatalogueService.modifierExemplaire(id, f.get("statut"), f.get("etat"), f.get("rayon")))) {
            int ligne = ouvrages.getSelectedRow();
            actualiser();
            if (ligne >= 0 && ligne < ouvrages.getRowCount()) {
                ouvrages.setRowSelectionInterval(ligne, ligne);
            }
        }
    }

    private void supprimerExemplaire() {
        final Integer id = Ui.idSelectionne(this, exemplaires, "un exemplaire");
        if (id != null && Ui.confirmer(this, "Supprimer l'exemplaire " + Ui.selection(exemplaires, 1) + " ?")) {
            int ligne = ouvrages.getSelectedRow();
            Ui.lancer(this, () -> {
                CatalogueService.supprimerExemplaire(id);
                return null;
            }, this::actualiser);
            if (ligne >= 0 && ligne < ouvrages.getRowCount()) {
                ouvrages.setRowSelectionInterval(ligne, ligne);
            }
        }
    }

    private void reserver() {
        final Integer id = Ui.idSelectionne(this, ouvrages, "un ouvrage");
        if (id == null) {
            return;
        }
        final Formulaire f = new Formulaire().texte("matricule", "Matricule de l'adhérent :", "");
        if (f.executer(this, "Réserver « " + Ui.selection(ouvrages, 2) + " »",
                () -> ReservationService.reserver(f.get("matricule"), id))) {
            actualiser();
        }
    }
}
