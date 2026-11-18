package gesbiblio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.Connection;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

import gesbiblio.db.Database;
import gesbiblio.db.Sql;
import gesbiblio.metier.Outils;

/** Tableau de bord : indicateurs clés et rapports. */
public class StatsPanel extends JPanel implements Actualisable {

    private static final long serialVersionUID = 1L;

    private final JPanel indicateurs = new JPanel(new GridLayout(2, 4, 10, 10));
    private final JTable populaires = Ui.tableau();
    private final JTable parMois = Ui.tableau();
    private final JTable lecteurs = Ui.tableau();
    private final JTable parCategorie = Ui.tableau();
    private final JTable inventaire = Ui.tableau();

    public StatsPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel haut = new JPanel(new BorderLayout());
        haut.add(Ui.ligne(Ui.titre("Tableau de bord"), Ui.bouton("Actualiser", e -> actualiser())), BorderLayout.NORTH);
        haut.add(indicateurs, BorderLayout.CENTER);
        add(haut, BorderLayout.NORTH);

        JTabbedPane t = new JTabbedPane();
        Theme.ongletsPlats(t);
        t.addTab("Titres populaires", Ui.defilement(populaires));
        t.addTab("Prêts par mois", Ui.defilement(parMois));
        t.addTab("Lecteurs actifs", Ui.defilement(lecteurs));
        t.addTab("Par catégorie", Ui.defilement(parCategorie));
        t.addTab("Inventaire", Ui.defilement(inventaire));
        add(t, BorderLayout.CENTER);
        actualiser();
    }

    /** Carte d'indicateur : bande de couleur à gauche, grand chiffre, libellé. */
    private static JPanel carte(String libelle, String valeur, Color couleur) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.SURFACE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDURE),
                        BorderFactory.createMatteBorder(0, 5, 0, 0, couleur)),
                BorderFactory.createEmptyBorder(8, 14, 8, 10)));
        JLabel v = new JLabel(valeur);
        v.setFont(Theme.police(Font.BOLD, 26));
        v.setForeground(couleur);
        JLabel l = new JLabel(libelle);
        l.setFont(Theme.police(Font.PLAIN, 13));
        l.setForeground(Theme.TEXTE_DOUX);
        p.add(v, BorderLayout.CENTER);
        p.add(l, BorderLayout.SOUTH);
        return p;
    }

    @Override
    public void actualiser() {
        indicateurs.removeAll();
        try (Connection c = Database.connexion()) {
            int retards = Sql.entier(c, "SELECT COUNT(*) FROM emprunt WHERE date_retour IS NULL AND date_echeance < CURDATE()");
            double impaye = Sql.decimal(c, "SELECT IFNULL(SUM(montant), 0) FROM penalite WHERE statut = 'IMPAYEE'");
            indicateurs.add(carte("Titres au catalogue", String.valueOf(Sql.entier(c, "SELECT COUNT(*) FROM ouvrage")), Theme.PRIMAIRE));
            indicateurs.add(carte("Exemplaires en service",
                    String.valueOf(Sql.entier(c, "SELECT COUNT(*) FROM exemplaire WHERE statut NOT IN ('PERDU','RETIRE')")), Theme.PRIMAIRE));
            indicateurs.add(carte("Adhérents actifs", String.valueOf(Sql.entier(c, "SELECT COUNT(*) FROM adherent WHERE statut = 'ACTIF'")), Theme.ACCENT));
            indicateurs.add(carte("Prêts en cours", String.valueOf(Sql.entier(c, "SELECT COUNT(*) FROM emprunt WHERE date_retour IS NULL")), Theme.BLEU));
            indicateurs.add(carte("Prêts en retard", String.valueOf(retards), retards > 0 ? Theme.ROUGE : Theme.VERT));
            indicateurs.add(carte("Réservations en attente",
                    String.valueOf(Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE statut IN ('EN_ATTENTE','A_RETIRER')")), Theme.ORANGE));
            indicateurs.add(carte("Prêts ce mois-ci", String.valueOf(Sql.entier(c,
                    "SELECT COUNT(*) FROM emprunt WHERE date_emprunt >= DATE_FORMAT(CURDATE(), '%Y-%m-01')")), Theme.ACCENT));
            indicateurs.add(carte("Pénalités impayées", Outils.montant(impaye, Outils.parametre(c, "devise", "")), impaye > 0 ? Theme.ROUGE : Theme.VERT));
        } catch (Exception e) {
            Ui.erreur(this, e);
        }
        indicateurs.revalidate();
        indicateurs.repaint();

        Ui.charger(this, populaires, "SELECT o.titre AS Titre, o.auteur AS Auteur, COUNT(em.id) AS `Nombre de prêts` "
                + "FROM emprunt em JOIN exemplaire e ON e.id = em.exemplaire_id JOIN ouvrage o ON o.id = e.ouvrage_id "
                + "GROUP BY o.id, o.titre, o.auteur ORDER BY COUNT(em.id) DESC LIMIT 20");
        Ui.charger(this, parMois, "SELECT DATE_FORMAT(date_emprunt, '%Y-%m') AS Mois, COUNT(*) AS `Prêts`, "
                + "SUM(date_retour IS NOT NULL AND date_retour > date_echeance) AS `Rendus en retard` "
                + "FROM emprunt WHERE date_emprunt >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH) "
                + "GROUP BY DATE_FORMAT(date_emprunt, '%Y-%m') ORDER BY Mois DESC");
        Ui.charger(this, lecteurs, "SELECT a.matricule AS Matricule, CONCAT_WS(' ', a.prenom, a.nom) AS `Adhérent`, "
                + "a.categorie_code AS `Catégorie`, COUNT(em.id) AS `Nombre de prêts` FROM emprunt em "
                + "JOIN adherent a ON a.id = em.adherent_id GROUP BY a.id, a.matricule, a.prenom, a.nom, a.categorie_code "
                + "ORDER BY COUNT(em.id) DESC LIMIT 20");
        Ui.charger(this, parCategorie, "SELECT c.libelle AS `Catégorie`, "
                + "(SELECT COUNT(*) FROM adherent a WHERE a.categorie_code = c.code) AS `Adhérents`, "
                + "(SELECT COUNT(*) FROM emprunt em JOIN adherent a ON a.id = em.adherent_id WHERE a.categorie_code = c.code) AS `Prêts (total)`, "
                + "(SELECT COUNT(*) FROM emprunt em JOIN adherent a ON a.id = em.adherent_id WHERE a.categorie_code = c.code "
                + " AND em.date_retour IS NULL) AS `Prêts en cours` FROM categorie_adherent c ORDER BY c.libelle");
        Ui.charger(this, inventaire, "SELECT statut AS Statut, COUNT(*) AS `Exemplaires` FROM exemplaire GROUP BY statut "
                + "ORDER BY COUNT(*) DESC");
    }
}
