package gesbiblio.ui;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;

import gesbiblio.metier.AdministrationService;
import gesbiblio.metier.Session;

/** Relances des retards et encaissement des pénalités. */
public class RetardPanel extends JPanel implements Actualisable {

    private static final long serialVersionUID = 1L;

    private final JTable retards = Ui.tableau();
    private final JTable penalites = Ui.tableau();
    private final JCheckBox toutes = new JCheckBox("Afficher aussi les pénalités réglées");

    public RetardPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel haut = new JPanel(new BorderLayout());
        haut.add(Ui.ligne(Ui.titre("Documents en retard (à relancer)"), Ui.bouton("Actualiser", e -> actualiser())), BorderLayout.NORTH);
        haut.add(Ui.defilement(retards), BorderLayout.CENTER);

        JPanel bas = new JPanel(new BorderLayout());
        JPanel barre = Ui.ligne(Ui.titre("Pénalités"), Ui.boutonSucces("Encaisser le paiement", e -> payer()));
        if (Session.aLeRole(Session.ADMIN)) {
            barre.add(Ui.bouton("Accorder une remise", e -> remettre()));
        }
        barre.add(toutes);
        bas.add(barre, BorderLayout.NORTH);
        bas.add(Ui.defilement(penalites), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, haut, bas);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);
        toutes.addActionListener(e -> actualiser());
        actualiser();
    }

    @Override
    public void actualiser() {
        Ui.charger(this, retards, "SELECT a.matricule AS Matricule, CONCAT_WS(' ', a.prenom, a.nom) AS `Adhérent`, "
                + "a.telephone AS `Téléphone`, a.email AS `E-mail`, o.titre AS Titre, ex.code_barres AS `Code-barres`, "
                + "em.date_echeance AS `Échéance`, DATEDIFF(CURDATE(), em.date_echeance) AS `Jours de retard`, "
                + "DATEDIFF(CURDATE(), em.date_echeance) * (SELECT valeur FROM parametre WHERE cle = 'penalite_par_jour') AS `Pénalité à ce jour` "
                + "FROM emprunt em JOIN adherent a ON a.id = em.adherent_id JOIN exemplaire ex ON ex.id = em.exemplaire_id "
                + "JOIN ouvrage o ON o.id = ex.ouvrage_id WHERE em.date_retour IS NULL AND em.date_echeance < CURDATE() "
                + "ORDER BY em.date_echeance");
        String filtre = toutes.isSelected() ? "" : "WHERE p.statut = 'IMPAYEE' ";
        Ui.charger(this, penalites, "SELECT p.id AS `N°`, p.date_creation AS `Date`, a.matricule AS Matricule, "
                + "CONCAT_WS(' ', a.prenom, a.nom) AS `Adhérent`, p.motif AS Motif, p.montant AS Montant, p.statut AS Statut, "
                + "p.date_reglement AS `Réglée le` FROM penalite p JOIN adherent a ON a.id = p.adherent_id " + filtre
                + "ORDER BY p.id DESC");
    }

    private void payer() {
        final Integer id = Ui.idSelectionne(this, penalites, "une pénalité");
        if (id != null && Ui.confirmer(this, "Confirmer le paiement de " + Ui.selection(penalites, 5) + " par "
                + Ui.selection(penalites, 3) + " ?")) {
            Ui.lancer(this, () -> AdministrationService.payerPenalite(id), this::actualiser);
        }
    }

    private void remettre() {
        final Integer id = Ui.idSelectionne(this, penalites, "une pénalité");
        if (id == null) {
            return;
        }
        final Formulaire f = new Formulaire().texte("motif", "Motif de la remise :", "");
        if (f.executer(this, "Remise de la pénalité n° " + id, () -> AdministrationService.remettrePenalite(id, f.get("motif")))) {
            actualiser();
        }
    }
}
