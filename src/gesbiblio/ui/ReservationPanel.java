package gesbiblio.ui;

import java.awt.BorderLayout;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTable;

import gesbiblio.db.Database;
import gesbiblio.db.Sql;
import gesbiblio.metier.ReservationService;

/** Suivi des files d'attente de réservation. */
public class ReservationPanel extends JPanel implements Actualisable {

    private static final long serialVersionUID = 1L;

    private final JTable table = Ui.tableau();
    private final JCheckBox closes = new JCheckBox("Afficher aussi les réservations closes");

    public ReservationPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(Ui.ligne(Ui.boutonPrincipal("Nouvelle réservation", e -> nouvelle()),
                Ui.boutonDanger("Annuler la réservation", e -> annuler()),
                Ui.bouton("Clore les délais de retrait dépassés", e -> Ui.lancer(this, ReservationService::expirerDelaisDepasses, this::actualiser)),
                Ui.bouton("Actualiser", e -> actualiser()), closes), BorderLayout.NORTH);
        add(Ui.defilement(table), BorderLayout.CENTER);
        closes.addActionListener(e -> actualiser());
        actualiser();
    }

    @Override
    public void actualiser() {
        String filtre = closes.isSelected() ? "" : "WHERE r.statut IN ('EN_ATTENTE', 'A_RETIRER') ";
        Ui.charger(this, table, "SELECT r.id AS `N°`, r.date_reservation AS `Réservé le`, o.titre AS Titre, "
                + "a.matricule AS Matricule, CONCAT_WS(' ', a.prenom, a.nom) AS `Adhérent`, a.telephone AS `Téléphone`, "
                + "r.statut AS Statut, "
                + "CASE WHEN r.statut = 'EN_ATTENTE' THEN (SELECT COUNT(*) FROM reservation r2 WHERE r2.ouvrage_id = r.ouvrage_id "
                + " AND r2.statut = 'EN_ATTENTE' AND r2.id <= r.id) END AS `Position`, "
                + "ex.code_barres AS `Exemplaire mis de côté`, r.date_limite_retrait AS `À retirer avant` "
                + "FROM reservation r JOIN ouvrage o ON o.id = r.ouvrage_id JOIN adherent a ON a.id = r.adherent_id "
                + "LEFT JOIN exemplaire ex ON ex.id = r.exemplaire_id " + filtre
                + "ORDER BY FIELD(r.statut, 'EN_ATTENTE', 'A_RETIRER') DESC, r.date_reservation DESC");
    }

    private void nouvelle() {
        // Seuls les titres sans exemplaire disponible peuvent être réservés.
        final List<String> titres = new ArrayList<String>();
        try (Connection c = Database.connexion()) {
            for (Object[] l : Sql.lignes(c, "SELECT o.id, o.titre FROM ouvrage o WHERE EXISTS (SELECT 1 FROM exemplaire e "
                    + "WHERE e.ouvrage_id = o.id AND e.statut NOT IN ('PERDU','RETIRE')) AND NOT EXISTS (SELECT 1 FROM exemplaire e "
                    + "WHERE e.ouvrage_id = o.id AND e.statut = 'DISPONIBLE') ORDER BY o.titre")) {
                titres.add(l[0] + " - " + l[1]);
            }
        } catch (Exception e) {
            Ui.erreur(this, e);
            return;
        }
        if (titres.isEmpty()) {
            Ui.info(this, "Tous les titres ont au moins un exemplaire disponible en rayon : aucune réservation n'est nécessaire.");
            return;
        }
        final Formulaire f = new Formulaire()
                .texte("matricule", "Matricule de l'adhérent :", "")
                .liste("titre", "Titre (indisponible) :", titres, null, false);
        if (f.executer(this, "Nouvelle réservation", () -> {
            int id = Integer.parseInt(f.get("titre").substring(0, f.get("titre").indexOf(' ')));
            return ReservationService.reserver(f.get("matricule"), id);
        })) {
            actualiser();
        }
    }

    private void annuler() {
        final Integer id = Ui.idSelectionne(this, table, "une réservation");
        if (id != null && Ui.confirmer(this, "Annuler la réservation n° " + id + " ?")) {
            Ui.lancer(this, () -> ReservationService.annuler(id), this::actualiser);
        }
    }
}
