package gesbiblio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;

import gesbiblio.db.Database;
import gesbiblio.db.Sql;
import gesbiblio.metier.AdherentService;
import gesbiblio.metier.Outils;
import gesbiblio.metier.PretService;

/**
 * Guichet de prêt.
 *  Emprunt : adhérent et livre choisis par auto-complétion (seuls les titres disponibles sont proposés),
 *            exemplaire choisi automatiquement, date de retour calculée selon la catégorie du lecteur.
 *  Retour  : prêt en cours choisi par auto-complétion ; un aperçu indique retard, pénalité,
 *            possibilité de prolongation et réservation en attente avant de valider.
 */
public class PretPanel extends JPanel implements Actualisable {

    private static final long serialVersionUID = 1L;
    private static final String AIDE_FICHE = "Tapez le nom, le prénom, le matricule ou le téléphone de l'adhérent.";

    // ---- Emprunt
    private final ChampRecherche adherent = new ChampRecherche(this::adherents, "Nom, prénom, matricule ou téléphone...");
    private final ChampRecherche livre = new ChampRecherche(this::livresDisponibles, "Titre, auteur, ISBN ou code-barres...");
    private final JComboBox<String> exemplaire = new JComboBox<String>();
    private final JLabel infoLivre = new JLabel(" ");
    private final JLabel retourPrevu = new JLabel("-");
    private final JLabel detailRetourPrevu = new JLabel(" ");
    private final JTextArea fiche = new JTextArea(5, 40);
    private final JButton boutonEmprunter = Ui.boutonPrincipal("Emprunter", e -> emprunter());

    // ---- Retour / prolongation
    private final ChampRecherche pret = new ChampRecherche(this::pretsEnCours, "Titre, code-barres, nom ou matricule de l'emprunteur...");
    private final JEditorPane apercu = new JEditorPane("text/html", "");
    private final JComboBox<String> etat = new JComboBox<String>(new String[] { "BON", "USE", "ENDOMMAGE" });
    private final JButton boutonRetour = Ui.boutonPrincipal("Enregistrer le retour", e -> retourner());
    private final JButton boutonProlonger = Ui.boutonSucces("Prolonger", e -> prolonger());
    private final JButton boutonPerdu = Ui.boutonDanger("Déclarer perdu", e -> perdu());

    private final JTable table = Ui.tableau();
    private final JLabel titreTable = new JLabel();

    public PretPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ------------------------------------------------ Emprunt
        fiche.setEditable(false);
        fiche.setLineWrap(true);
        fiche.setWrapStyleWord(true);
        fiche.setFont(Theme.police(Font.PLAIN, 13));
        afficherFiche(null, AIDE_FICHE);
        retourPrevu.setFont(Theme.police(Font.BOLD, 16));
        retourPrevu.setForeground(Theme.PRIMAIRE);
        detailRetourPrevu.setForeground(Theme.TEXTE_DOUX);
        infoLivre.setForeground(Theme.TEXTE_DOUX);
        detailRetourPrevu.setText("choisissez d'abord l'adhérent");
        exemplaire.setEnabled(false);

        JPanel emprunt = new JPanel(new GridBagLayout());
        emprunt.setBorder(Theme.cadre("1. Emprunt"));
        int y = 0;
        ligne(emprunt, y++, "Adhérent :", adherent);
        pleineLargeur(emprunt, y++, fiche);
        ligne(emprunt, y++, "Livre :", livre);
        ligne(emprunt, y++, "Exemplaire :", exemplaire);
        pleineLargeur(emprunt, y++, infoLivre);
        JPanel date = new JPanel(new BorderLayout(8, 0));
        date.setOpaque(false);
        date.add(retourPrevu, BorderLayout.WEST);
        date.add(detailRetourPrevu, BorderLayout.CENTER);
        ligne(emprunt, y++, "Retour prévu le :", date);
        pleineLargeur(emprunt, y++, Ui.ligne(boutonEmprunter, Ui.bouton("Effacer", e -> effacerEmprunt())));
        remplir(emprunt, y);

        // ------------------------------------------------ Retour / prolongation
        apercu.setEditable(false);
        apercu.setFocusable(false);
        apercu.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        // Nimbus ignore setBackground sur un JEditorPane : on lui donne un peintre qui utilise la couleur demandée.
        javax.swing.UIDefaults fondPerso = new javax.swing.UIDefaults();
        javax.swing.Painter<JComponent> peintre = (g, c, w, h) -> {
            g.setColor(c.getBackground());
            g.fillRect(0, 0, w, h);
        };
        fondPerso.put("EditorPane[Enabled].backgroundPainter", peintre);
        fondPerso.put("EditorPane[Disabled].backgroundPainter", peintre);
        fondPerso.put("EditorPane[Selected].backgroundPainter", peintre);
        apercu.putClientProperty("Nimbus.Overrides", fondPerso);
        apercu.putClientProperty("Nimbus.Overrides.InheritDefaults", Boolean.TRUE);
        afficherApercu(null);
        JPanel retour = new JPanel(new GridBagLayout());
        retour.setBorder(Theme.cadre("2. Retour / prolongation"));
        y = 0;
        ligne(retour, y++, "Prêt en cours :", pret);
        pleineLargeur(retour, y++, apercu);
        ligne(retour, y++, "État au retour :", etat);
        pleineLargeur(retour, y++, Ui.ligne(boutonRetour, boutonProlonger, boutonPerdu));
        pleineLargeur(retour, y++, Ui.ligne(new JLabel("<html><i>Astuce : double-cliquez sur un prêt du tableau pour le sélectionner.</i></html>")));
        remplir(retour, y);

        JPanel haut = new JPanel(new GridLayout(1, 2, 10, 10));
        haut.setOpaque(false);
        haut.add(emprunt);
        haut.add(retour);
        add(haut, BorderLayout.NORTH);

        JPanel bas = new JPanel(new BorderLayout());
        bas.setOpaque(false);
        titreTable.setFont(Theme.police(Font.BOLD, 14));
        titreTable.setForeground(Theme.PRIMAIRE);
        bas.add(Ui.ligne(titreTable, Ui.bouton("Tous les prêts", e -> effacerEmprunt()), Ui.bouton("Actualiser", e -> actualiser())),
                BorderLayout.NORTH);
        bas.add(Ui.defilement(table), BorderLayout.CENTER);
        add(bas, BorderLayout.CENTER);

        // ------------------------------------------------ Comportements
        adherent.setEcouteur(s -> adherentChoisi(s));
        livre.setEcouteur(s -> livreChoisi(s));
        pret.setEcouteur(s -> pretChoisi(s));
        exemplaire.addActionListener(e -> majBoutonEmprunter());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    final Object code = Ui.selection(table, 4);
                    if (code != null) {
                        pret.choisirSi(v -> code.equals(v));
                    }
                }
            }
        });
        majBoutonEmprunter();
        actualiser();
    }

    // ================================================================== Sources des auto-complétions

    private List<ChampRecherche.Suggestion> adherents() throws Exception {
        List<ChampRecherche.Suggestion> l = new ArrayList<ChampRecherche.Suggestion>();
        try (Connection c = Database.connexion()) {
            for (Object[] r : Sql.lignes(c, "SELECT a.matricule, CONCAT_WS(' ', a.prenom, a.nom), k.libelle, a.statut, "
                    + "IFNULL(a.telephone, ''), (SELECT COUNT(*) FROM emprunt em WHERE em.adherent_id = a.id AND em.date_retour IS NULL), "
                    + "k.max_emprunts FROM adherent a JOIN categorie_adherent k ON k.code = a.categorie_code ORDER BY a.nom, a.prenom")) {
                String nom = Ui.s(r[1]);
                boolean suspendu = !"ACTIF".equals(r[3]);
                String libelle = "<b>" + html(nom) + "</b> &nbsp;<span style='color:#5E6B7A'>" + r[0] + " · " + html(Ui.s(r[2]))
                        + " · " + r[5] + "/" + r[6] + " prêt(s)</span>"
                        + (suspendu ? " &nbsp;<b style='color:#C62828'>SUSPENDU</b>" : "");
                l.add(new ChampRecherche.Suggestion(libelle, nom + " (" + r[0] + ")", r[0] + " " + nom + " " + r[4], r[0]));
            }
        }
        return l;
    }

    /** Titres ayant au moins un exemplaire disponible, ou mis de côté pour l'adhérent choisi. */
    private List<ChampRecherche.Suggestion> livresDisponibles() throws Exception {
        List<ChampRecherche.Suggestion> l = new ArrayList<ChampRecherche.Suggestion>();
        try (Connection c = Database.connexion()) {
            for (Object[] r : Sql.lignes(c, "SELECT o.id, o.titre, IFNULL(o.auteur, ''), IFNULL(o.isbn, ''), "
                    + "SUM(e.statut NOT IN ('PERDU', 'RETIRE')), SUM(e.statut = 'DISPONIBLE'), "
                    + "SUM(e.statut = 'RESERVE' AND r.id IS NOT NULL), GROUP_CONCAT(e.code_barres SEPARATOR ' ') "
                    + "FROM ouvrage o JOIN exemplaire e ON e.ouvrage_id = o.id "
                    + "LEFT JOIN reservation r ON r.exemplaire_id = e.id AND r.statut = 'A_RETIRER' "
                    + " AND r.adherent_id = (SELECT id FROM adherent WHERE matricule = ?) "
                    + "GROUP BY o.id, o.titre, o.auteur, o.isbn "
                    + "HAVING SUM(e.statut = 'DISPONIBLE') > 0 OR SUM(e.statut = 'RESERVE' AND r.id IS NOT NULL) > 0 "
                    + "ORDER BY o.titre", matriculeChoisi())) {
                int total = ((Number) r[4]).intValue();
                int dispo = ((Number) r[5]).intValue();
                int pourLui = ((Number) r[6]).intValue();
                String libelle = "<b>" + html(Ui.s(r[1])) + "</b>"
                        + (Ui.s(r[2]).isEmpty() ? "" : " &nbsp;<span style='color:#5E6B7A'>" + html(Ui.s(r[2])) + "</span>")
                        + " &nbsp;<span style='color:#2E7D32'><b>" + dispo + "</b> disponible(s) / " + total + "</span>"
                        + (pourLui > 0 ? " &nbsp;<b style='color:#B86E00'>MIS DE CÔTÉ POUR CET ADHÉRENT</b>" : "");
                l.add(new ChampRecherche.Suggestion(libelle, Ui.s(r[1]), r[1] + " " + r[2] + " " + r[3] + " " + r[7],
                        new Object[] { r[0], total, dispo, pourLui }));
            }
        }
        return l;
    }

    private List<ChampRecherche.Suggestion> pretsEnCours() throws Exception {
        List<ChampRecherche.Suggestion> l = new ArrayList<ChampRecherche.Suggestion>();
        try (Connection c = Database.connexion()) {
            for (Object[] r : Sql.lignes(c, "SELECT ex.code_barres, o.titre, CONCAT_WS(' ', a.prenom, a.nom), a.matricule, "
                    + "em.date_echeance, GREATEST(DATEDIFF(CURDATE(), em.date_echeance), 0) "
                    + "FROM emprunt em JOIN exemplaire ex ON ex.id = em.exemplaire_id JOIN ouvrage o ON o.id = ex.ouvrage_id "
                    + "JOIN adherent a ON a.id = em.adherent_id WHERE em.date_retour IS NULL ORDER BY o.titre")) {
                int retard = ((Number) r[5]).intValue();
                String libelle = "<b>" + html(Ui.s(r[1])) + "</b> &nbsp;<span style='color:#5E6B7A'>" + r[0] + " · "
                        + html(Ui.s(r[2])) + " (" + r[3] + ") · retour le " + Outils.date(r[4]) + "</span>"
                        + (retard > 0 ? " &nbsp;<b style='color:#C62828'>" + retard + " j de retard</b>" : "");
                l.add(new ChampRecherche.Suggestion(libelle, r[1] + " - " + r[0] + " - " + r[2],
                        r[0] + " " + r[1] + " " + r[2] + " " + r[3], r[0]));
            }
        }
        return l;
    }

    // ================================================================== Emprunt

    private String matriculeChoisi() {
        ChampRecherche.Suggestion s = adherent.getChoisie();
        return s == null ? "" : (String) s.valeur;
    }

    private void adherentChoisi(ChampRecherche.Suggestion s) {
        livre.effacer();
        if (s == null) {
            afficherFiche(null, AIDE_FICHE);
            retourPrevu.setText("-");
            detailRetourPrevu.setText("choisissez d'abord l'adhérent");
        } else {
            try {
                afficherFiche(Boolean.TRUE, AdherentService.situation(matriculeChoisi()));
                Object[] r = PretService.retourPrevu(matriculeChoisi());
                retourPrevu.setText(Outils.date(r[0]));
                detailRetourPrevu.setText("(" + r[1] + " jours - " + r[2] + ")");
            } catch (Exception e) {
                afficherFiche(Boolean.FALSE, e.getMessage());
            }
            livre.requestFocusInWindow();
        }
        actualiser();
    }

    private void livreChoisi(ChampRecherche.Suggestion s) {
        exemplaire.removeAllItems();
        exemplaire.setEnabled(false);
        infoLivre.setText(" ");
        if (s != null) {
            Object[] v = (Object[]) s.valeur;
            try (Connection c = Database.connexion()) {
                String saisie = livre.getSaisie().trim();
                String aSelectionner = null;
                for (Object[] r : Sql.lignes(c, "SELECT e.code_barres, IFNULL(e.rayon, '-'), e.etat, r.id IS NOT NULL "
                        + "FROM exemplaire e LEFT JOIN reservation r ON r.exemplaire_id = e.id AND r.statut = 'A_RETIRER' "
                        + " AND r.adherent_id = (SELECT id FROM adherent WHERE matricule = ?) "
                        + "WHERE e.ouvrage_id = ? AND (e.statut = 'DISPONIBLE' OR (e.statut = 'RESERVE' AND r.id IS NOT NULL)) "
                        + "ORDER BY r.id IS NULL, e.code_barres", matriculeChoisi(), v[0])) {
                    String item = r[0] + "   -   rayon " + r[1] + "   -   " + r[2] + (Outils.vrai(r[3]) ? "   -   MIS DE CÔTÉ" : "");
                    exemplaire.addItem(item);
                    if (r[0].toString().equalsIgnoreCase(saisie)) {
                        aSelectionner = item;
                    }
                }
                if (aSelectionner != null) {
                    exemplaire.setSelectedItem(aSelectionner);
                }
                int attente = Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE ouvrage_id = ? AND statut = 'EN_ATTENTE'", v[0]);
                infoLivre.setText("<html>Exemplaires : <b>" + v[1] + "</b> au total, <b style='color:#2E7D32'>" + v[2]
                        + "</b> disponible(s)" + (attente > 0 ? ", <b style='color:#B86E00'>" + attente + " réservation(s) en attente</b>" : "")
                        + "</html>");
            } catch (Exception e) {
                Ui.erreur(this, e);
            }
            exemplaire.setEnabled(exemplaire.getItemCount() > 0);
            boutonEmprunter.requestFocusInWindow();
        }
        majBoutonEmprunter();
    }

    private String codeExemplaire() {
        Object item = exemplaire.getSelectedItem();
        return item == null ? "" : item.toString().split("\\s")[0];
    }

    private void majBoutonEmprunter() {
        boolean ok = adherent.getChoisie() != null && livre.getChoisie() != null && !codeExemplaire().isEmpty();
        boutonEmprunter.setEnabled(ok);
        boutonEmprunter.setToolTipText(ok ? null : "Choisissez l'adhérent puis le livre.");
    }

    private void emprunter() {
        if (!boutonEmprunter.isEnabled()) {
            return;
        }
        final String code = codeExemplaire();
        if (Ui.lancer(this, () -> PretService.emprunter(matriculeChoisi(), code), null)) {
            livre.effacer();
        }
        adherentChoisi(adherent.getChoisie());
    }

    private void effacerEmprunt() {
        adherent.effacer();
        livre.effacer();
        adherentChoisi(null);
        adherent.requestFocusInWindow();
    }

    /** Fiche verte si l'adhérent peut emprunter, rouge si quelque chose bloque ; ok == null : message d'aide. */
    private void afficherFiche(Boolean ok, String texte) {
        Color bord = Theme.BORDURE;
        Color fond = Theme.SURFACE;
        Color couleur = Theme.TEXTE_DOUX;
        if (ok != null) {
            boolean bloque = !ok || texte.contains("SUSPENDU") || texte.contains("EXPIRÉE")
                    || !texte.contains("En retard : 0") || texte.contains("seuil de blocage") || limiteAtteinte(texte);
            bord = bloque ? Theme.ROUGE : Theme.VERT;
            fond = bloque ? Theme.ROUGE_CLAIR : Theme.VERT_CLAIR;
            couleur = Theme.TEXTE;
            if (ok) {
                texte = (bloque ? "PRÊT BLOQUÉ - voir le détail ci-dessous\n" : "PEUT EMPRUNTER\n") + texte;
            }
        }
        fiche.setText(texte);
        fiche.setBackground(fond);
        fiche.setForeground(couleur);
        fiche.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 4, 1, 1, bord),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    private static boolean limiteAtteinte(String texte) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Emprunts en cours : (\\d+) / (\\d+)").matcher(texte);
        return m.find() && Integer.parseInt(m.group(1)) >= Integer.parseInt(m.group(2));
    }

    // ================================================================== Retour / prolongation

    private String codePret() {
        ChampRecherche.Suggestion s = pret.getChoisie();
        return s == null ? null : (String) s.valeur;
    }

    private void pretChoisi(ChampRecherche.Suggestion s) {
        if (s == null) {
            afficherApercu(null);
            return;
        }
        try {
            afficherApercu(PretService.apercu((String) s.valeur));
        } catch (Exception e) {
            afficherApercu(null);
            Ui.erreur(this, e);
        }
    }

    /** Aperçu de ce qui se passera au retour ou à la prolongation ; active seulement les boutons permis. */
    private void afficherApercu(PretService.Apercu a) {
        boutonRetour.setEnabled(a != null);
        boutonPerdu.setEnabled(a != null);
        boutonProlonger.setEnabled(a != null && a.refusProlongation == null);
        boutonProlonger.setToolTipText(a == null ? null : a.refusProlongation);
        Color bord = Theme.BORDURE;
        Color fond = Theme.SURFACE;
        String html;
        if (a == null) {
            html = "<span style='color:#5E6B7A'>Choisissez un prêt en cours : l'aperçu indiquera le retard, la pénalité "
                    + "et si la prolongation est possible.</span>";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("<b>").append(html(a.titre)).append("</b> (").append(a.codeBarres).append(")<br>");
            sb.append("Emprunté par <b>").append(html(a.adherent)).append("</b> (").append(a.matricule).append(") le ")
                    .append(Outils.date(a.dateEmprunt)).append("<br>");
            sb.append("Retour prévu le <b>").append(Outils.date(a.echeance)).append("</b> : ");
            if (a.retard > 0) {
                sb.append("<b style='color:#C62828'>EN RETARD de ").append(a.retard).append(" jour(s)");
                if (a.penalite > 0) {
                    sb.append(" - pénalité de ").append(Outils.montant(a.penalite, a.devise)).append(" au retour");
                }
                sb.append("</b><br>");
            } else {
                long reste = ChronoUnit.DAYS.between(LocalDate.now(), ((java.sql.Date) a.echeance).toLocalDate());
                sb.append("<b style='color:#2E7D32'>dans les délais</b> (").append(reste == 0 ? "à rendre aujourd'hui" : reste + " jour(s) restant(s)")
                        .append(")<br>");
            }
            sb.append("Prolongation (").append(a.nbRenouvellements).append("/").append(a.maxRenouvellements).append(" utilisée(s)) : ");
            if (a.refusProlongation == null) {
                sb.append("<b style='color:#2E7D32'>possible</b> - nouveau retour le <b>").append(Outils.date(a.nouvelleEcheance)).append("</b><br>");
            } else {
                sb.append("<b style='color:#C62828'>impossible</b> - ").append(html(a.refusProlongation)).append("<br>");
            }
            sb.append("Après le retour : ");
            if (a.prochainReservataire != null) {
                sb.append("<b style='color:#B86E00'>à mettre de côté pour ").append(html(a.prochainReservataire)).append("</b> (réservation)");
            } else {
                sb.append("remis en rayon (disponible)");
            }
            html = sb.toString();
            bord = a.retard > 0 ? Theme.ROUGE : Theme.VERT;
            fond = a.retard > 0 ? Theme.ROUGE_CLAIR : Theme.VERT_CLAIR;
        }
        apercu.setText("<html><body>" + html + "</body></html>");
        apercu.setOpaque(true);
        apercu.setBackground(fond);
        apercu.setFont(Theme.police(Font.PLAIN, 13));
        apercu.setForeground(Theme.TEXTE);
        apercu.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 4, 1, 1, bord),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    private void apresRetour(boolean ok) {
        if (ok) {
            pret.effacer();
            etat.setSelectedIndex(0);
        } else {
            pretChoisi(pret.getChoisie());
        }
        if (adherent.getChoisie() != null) {
            adherentChoisi(adherent.getChoisie());
        } else {
            actualiser();
        }
        pret.requestFocusInWindow();
    }

    private void retourner() {
        final String code = codePret();
        if (code != null) {
            apresRetour(Ui.lancer(this, () -> PretService.retourner(code, (String) etat.getSelectedItem()), null));
        }
    }

    private void prolonger() {
        final String code = codePret();
        if (code != null) {
            boolean ok = Ui.lancer(this, () -> PretService.renouveler(code), null);
            if (ok) {
                pretChoisi(pret.getChoisie());
                actualiser();
            } else {
                apresRetour(false);
            }
        }
    }

    private void perdu() {
        final String code = codePret();
        if (code != null && Ui.confirmer(this, "Déclarer l'exemplaire " + code
                + " comme perdu ?\nLe forfait de perte sera facturé à l'emprunteur.")) {
            apresRetour(Ui.lancer(this, () -> PretService.declarerPerdu(code), null));
        }
    }

    // ================================================================== Mise en page et tableau

    private static void ligne(JPanel p, int y, String libelle, JComponent champ) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = y;
        g.gridx = 0;
        g.insets = new Insets(5, 4, 5, 8);
        g.anchor = GridBagConstraints.LINE_END;
        JLabel l = new JLabel(libelle);
        l.setFont(Theme.police(Font.BOLD, 13));
        p.add(l, g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.LINE_START;
        p.add(champ, g);
    }

    private static void pleineLargeur(JPanel p, int y, JComponent c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = y;
        g.gridx = 0;
        g.gridwidth = 2;
        g.weightx = 1;
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        p.add(c, g);
    }

    private static void remplir(JPanel p, int y) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = y;
        g.weighty = 1;
        p.add(new JLabel(), g);
    }

    private static String html(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void actualiser() {
        String m = matriculeChoisi();
        String sql = "SELECT em.id AS `N°`, a.matricule AS Matricule, CONCAT_WS(' ', a.prenom, a.nom) AS `Adhérent`, "
                + "o.titre AS Titre, ex.code_barres AS `Code-barres`, em.date_emprunt AS `Emprunté le`, "
                + "em.date_echeance AS `Échéance`, GREATEST(DATEDIFF(CURDATE(), em.date_echeance), 0) AS `Retard (j)`, "
                + "em.nb_renouvellements AS `Renouv.` "
                + "FROM emprunt em JOIN adherent a ON a.id = em.adherent_id "
                + "JOIN exemplaire ex ON ex.id = em.exemplaire_id JOIN ouvrage o ON o.id = ex.ouvrage_id "
                + "WHERE em.date_retour IS NULL ";
        if (m.isEmpty()) {
            titreTable.setText("Tous les prêts en cours");
            Ui.charger(this, table, sql + "ORDER BY em.date_echeance");
        } else {
            titreTable.setText("Prêts en cours de " + adherent.getChoisie().texte);
            Ui.charger(this, table, sql + "AND a.matricule = ? ORDER BY em.date_echeance", m);
        }
        adherent.recharger();
        livre.recharger();
        pret.recharger();
    }
}
