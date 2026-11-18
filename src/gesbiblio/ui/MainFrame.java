package gesbiblio.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import gesbiblio.db.Database;
import gesbiblio.metier.ReservationService;
import gesbiblio.metier.Session;

/** Fenêtre principale : navigation claire, avec accès filtré selon le rôle. */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final Color NAVIGATION = Theme.PRIMAIRE;
    private static final Color NAVIGATION_TEXTE = new Color(0xD6E2F0);

    private final CardLayout disposition = new CardLayout();
    private final JPanel contenu = new JPanel(disposition);
    private final JLabel titrePage = new JLabel();
    private final JLabel descriptionPage = new JLabel();
    private final List<Page> pages = new ArrayList<Page>();

    public MainFrame() {
        super("GesBiblio - Gestion de bibliothèque");
        Session session = Session.courante();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 680));
        setSize(1280, 800);
        setLocationRelativeTo(null);

        ajouterPage("Prêts / retours", "Enregistrer les prêts, retours et renouvellements.", new PretPanel());
        ajouterPage("Catalogue", "Rechercher les ouvrages et suivre leurs exemplaires.", new CataloguePanel());
        ajouterPage("Adhérents", "Consulter les dossiers et gérer les inscriptions.", new AdherentPanel());
        ajouterPage("Réservations", "Suivre les demandes et les exemplaires à retirer.", new ReservationPanel());
        ajouterPage("Retards & pénalités", "Repérer les retards et suivre les paiements.", new RetardPanel());
        if (Session.aLeRole(Session.BIBLIOTHECAIRE)) {
            ajouterPage("Statistiques", "Consulter l’activité et l’état du fonds documentaire.", new StatsPanel());
        }
        if (Session.aLeRole(Session.ADMIN)) {
            ajouterPage("Administration", "Gérer les accès, les règles de prêt et le journal.", new AdminPanel());
        }

        JPanel racine = new JPanel(new BorderLayout());
        racine.setBackground(Ui.FOND);
        setContentPane(racine);

        JPanel navigation = creerNavigation(session);
        racine.add(navigation, BorderLayout.WEST);

        JPanel principal = new JPanel(new BorderLayout());
        principal.setBackground(Ui.FOND);
        principal.add(creerEntete(), BorderLayout.NORTH);
        contenu.setBackground(Ui.FOND);
        contenu.setBorder(BorderFactory.createEmptyBorder(14, 18, 18, 18));
        principal.add(contenu, BorderLayout.CENTER);
        racine.add(principal, BorderLayout.CENTER);

        if (!pages.isEmpty()) {
            afficher(pages.get(0));
        }
        setJMenuBar(creerMenu());
    }

    private JPanel creerNavigation(Session session) {
        JPanel panneau = new JPanel();
        panneau.setLayout(new BoxLayout(panneau, BoxLayout.Y_AXIS));
        panneau.setBackground(NAVIGATION);
        panneau.setBorder(BorderFactory.createEmptyBorder(20, 14, 16, 14));
        panneau.setPreferredSize(new Dimension(230, 0));

        JLabel marque = new JLabel("GesBiblio");
        marque.setForeground(Color.WHITE);
        marque.setFont(Theme.police(Font.BOLD, 24));
        marque.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));
        marque.setAlignmentX(LEFT_ALIGNMENT);
        panneau.add(marque);

        JLabel sousMarque = new JLabel("GESTION DE BIBLIOTHÈQUE");
        sousMarque.setForeground(new Color(0x9FB6D0));
        sousMarque.setFont(Theme.police(Font.BOLD, 10));
        sousMarque.setBorder(BorderFactory.createEmptyBorder(0, 9, 24, 8));
        sousMarque.setAlignmentX(LEFT_ALIGNMENT);
        panneau.add(sousMarque);

        for (Page page : pages) {
            ajouterBoutonNavigation(panneau, page);
        }

        panneau.add(Box.createVerticalGlue());
        JLabel utilisateur = new JLabel("<html><b>" + echapper(session.getNom()) + "</b><br>"
                + echapper(session.getRole()) + "</html>");
        utilisateur.setForeground(Color.WHITE);
        utilisateur.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x3A5677)),
                BorderFactory.createEmptyBorder(14, 8, 8, 8)));
        utilisateur.setAlignmentX(LEFT_ALIGNMENT);
        panneau.add(utilisateur);
        return panneau;
    }

    private void ajouterBoutonNavigation(JPanel navigation, Page page) {
        JButton bouton = new BoutonNavigation(page.titre);
        bouton.setHorizontalAlignment(SwingConstants.LEFT);
        bouton.setAlignmentX(LEFT_ALIGNMENT);
        bouton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        bouton.setPreferredSize(new Dimension(200, 42));
        bouton.setForeground(NAVIGATION_TEXTE);
        bouton.setBackground(NAVIGATION);
        bouton.setFocusPainted(false);
        bouton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        bouton.addActionListener(e -> afficher(page));
        navigation.add(bouton);
        navigation.add(Box.createVerticalStrut(4));
        page.bouton = bouton;
    }

    private JPanel creerEntete() {
        JPanel entete = new JPanel(new BorderLayout(12, 0));
        entete.setBackground(Ui.SURFACE);
        entete.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, Theme.PRIMAIRE_CLAIR),
                BorderFactory.createEmptyBorder(18, 22, 16, 22)));

        JPanel textes = new JPanel();
        textes.setLayout(new BoxLayout(textes, BoxLayout.Y_AXIS));
        textes.setBackground(Ui.SURFACE);
        titrePage.setFont(Theme.police(Font.BOLD, 22));
        titrePage.setForeground(Theme.PRIMAIRE);
        descriptionPage.setForeground(Ui.SECONDAIRE);
        descriptionPage.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        textes.add(titrePage);
        textes.add(descriptionPage);
        entete.add(textes, BorderLayout.CENTER);

        JLabel base = new JLabel("<html><b>Base de données</b><br>" + echapper(Database.description()) + "</html>",
                SwingConstants.RIGHT);
        base.setForeground(Ui.SECONDAIRE);
        base.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.BORDURE),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        entete.add(base, BorderLayout.EAST);
        return entete;
    }

    private void ajouterPage(String titre, String description, javax.swing.JPanel panneau) {
        Page page = new Page(titre, description, panneau);
        pages.add(page);
        contenu.add(panneau, titre);
    }

    private void afficher(Page page) {
        disposition.show(contenu, page.titre);
        titrePage.setText(page.titre);
        descriptionPage.setText(page.description);
        for (Page p : pages) {
            if (p.bouton != null) {
                boolean actif = p == page;
                p.bouton.setBackground(actif ? Ui.ACCENT : NAVIGATION);
                p.bouton.setForeground(actif ? Color.WHITE : NAVIGATION_TEXTE);
            }
        }
        if (page.panneau instanceof Actualisable) {
            ((Actualisable) page.panneau).actualiser();
        }
    }

    private JMenuBar creerMenu() {
        JMenuBar barre = new JMenuBar();
        JMenu fichier = new JMenu("Fichier");
        JMenuItem mdp = new JMenuItem("Changer mon mot de passe");
        mdp.addActionListener(e -> App.changerMotDePasse(this, false));
        JMenuItem deco = new JMenuItem("Se déconnecter");
        deco.addActionListener(e -> {
            dispose();
            Session.fermer();
            App.demarrer();
        });
        JMenuItem quitter = new JMenuItem("Quitter");
        quitter.addActionListener(e -> System.exit(0));
        fichier.add(mdp);
        fichier.add(deco);
        fichier.addSeparator();
        fichier.add(quitter);
        barre.add(fichier);
        return barre;
    }

    private static String echapper(String texte) {
        return texte == null ? "" : texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Au démarrage : clôt les réservations dont le délai de retrait est dépassé. */
    public void traitementsDuJour() {
        try {
            String m = ReservationService.expirerDelaisDepasses();
            if (!m.startsWith("Aucune")) {
                Ui.info(this, m);
            }
        } catch (Exception e) {
            Ui.erreur(this, e);
        }
    }

    /** Bouton de la barre latérale : fond plein arrondi, texte aligné à gauche, survol éclairci. */
    private static final class BoutonNavigation extends JButton {
        private static final long serialVersionUID = 1L;

        BoutonNavigation(String texte) {
            super(texte);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(Theme.police(Font.BOLD, 14));
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color fond = getBackground();
            boolean actif = !NAVIGATION.equals(fond);
            if (!actif && getModel().isRollover()) {
                fond = Theme.melanger(NAVIGATION, Color.WHITE, 0.10f);
            }
            g2.setColor(fond);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            if (actif) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 8, 4, getHeight() - 16, 4, 4);
            }
            g2.setFont(getFont());
            g2.setColor(getForeground());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), 16, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static final class Page {
        private final String titre;
        private final String description;
        private final javax.swing.JPanel panneau;
        private JButton bouton;

        private Page(String titre, String description, javax.swing.JPanel panneau) {
            this.titre = titre;
            this.description = description;
            this.panneau = panneau;
        }
    }

}
