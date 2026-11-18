package gesbiblio.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * Charte graphique de GesBiblio : une seule palette pour toute l'application.
 *  - bleu nuit   : identité (bandeau, titres, boutons principaux)
 *  - vert        : disponible / à jour / payé
 *  - orange      : à surveiller (réservé, en attente, usé)
 *  - rouge       : bloquant (retard, perdu, suspendu, impayé, suppression)
 *  - gris        : clos / retiré / neutre
 */
public final class Theme {

    // ---- Palette
    public static final Color PRIMAIRE = new Color(0x1F3A5F);       // bleu nuit
    public static final Color PRIMAIRE_SURVOL = new Color(0x2B4F7E);
    public static final Color PRIMAIRE_CLAIR = new Color(0xE3ECF7);
    public static final Color ACCENT = new Color(0x2A9D8F);          // sarcelle
    public static final Color FOND = new Color(0xF3F5F8);
    public static final Color SURFACE = Color.WHITE;
    public static final Color BORDURE = new Color(0xD5DCE5);
    public static final Color TEXTE = new Color(0x1E2A38);
    public static final Color TEXTE_DOUX = new Color(0x5E6B7A);
    public static final Color LIGNE_ALTERNEE = new Color(0xF7F9FC);
    public static final Color SELECTION = new Color(0xCFE0F5);
    public static final Color ENTETE_TABLE = new Color(0xE8EDF3);

    public static final Color VERT = new Color(0x2E7D32);
    public static final Color VERT_CLAIR = new Color(0xE6F4EA);
    public static final Color ORANGE = new Color(0xB86E00);
    public static final Color ORANGE_CLAIR = new Color(0xFFF3E0);
    public static final Color ROUGE = new Color(0xC62828);
    public static final Color ROUGE_CLAIR = new Color(0xFDECEC);
    public static final Color BLEU = new Color(0x1565C0);
    public static final Color BLEU_CLAIR = new Color(0xE3F0FD);
    public static final Color GRIS = new Color(0x6B7785);
    public static final Color GRIS_CLAIR = new Color(0xEEF1F4);
    public static final Color VIOLET = new Color(0x6A3FA0);
    public static final Color VIOLET_CLAIR = new Color(0xF1EAFA);

    public static final String POLICE = choisirPolice();

    /** Couleur (texte, fond) de chaque statut affiché dans les tableaux. */
    private static final Map<String, Color[]> STATUTS = new HashMap<String, Color[]>();

    static {
        Color[] vert = { VERT, VERT_CLAIR };
        Color[] bleu = { BLEU, BLEU_CLAIR };
        Color[] orange = { ORANGE, ORANGE_CLAIR };
        Color[] rouge = { ROUGE, ROUGE_CLAIR };
        Color[] gris = { GRIS, GRIS_CLAIR };
        Color[] violet = { VIOLET, VIOLET_CLAIR };
        for (String s : new String[] { "DISPONIBLE", "ACTIF", "HONOREE", "PAYEE", "NEUF", "BON" }) {
            STATUTS.put(s, vert);
        }
        for (String s : new String[] { "EMPRUNTE", "EN_ATTENTE", "AGENT" }) {
            STATUTS.put(s, bleu);
        }
        for (String s : new String[] { "RESERVE", "A_RETIRER", "EN_REPARATION", "USE" }) {
            STATUTS.put(s, orange);
        }
        for (String s : new String[] { "PERDU", "SUSPENDU", "IMPAYEE", "ENDOMMAGE" }) {
            STATUTS.put(s, rouge);
        }
        for (String s : new String[] { "RETIRE", "ANNULEE", "EXPIREE", "REMISE" }) {
            STATUTS.put(s, gris);
        }
        STATUTS.put("ADMIN", violet);
        STATUTS.put("BIBLIOTHECAIRE", new Color[] { ACCENT, new Color(0xE2F4F1) });
    }

    /** Colonnes dont les valeurs sont des statuts à colorer. */
    private static final List<String> COLONNES_STATUT = Arrays.asList("Statut", "État", "État retour", "Rôle");

    private Theme() {
    }

    private static String choisirPolice() {
        List<String> dispo = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String p : new String[] { "Segoe UI", "Helvetica Neue", "Noto Sans", "DejaVu Sans" }) {
            if (dispo.contains(p)) {
                return p;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font police(int style, float taille) {
        return new Font(POLICE, style, Math.round(taille));
    }

    /** À appeler avant de créer la moindre fenêtre. */
    public static void appliquer() {
        try {
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(i.getName())) {
                    UIManager.setLookAndFeel(i.getClassName());
                    break;
                }
            }
        } catch (Exception ignore) {
            // apparence par défaut
        }
        UIManager.put("control", FOND);
        UIManager.put("nimbusBase", PRIMAIRE);
        UIManager.put("nimbusBlueGrey", new Color(0xB8C4D2));
        UIManager.put("nimbusFocus", new Color(0x6FA3D8));
        UIManager.put("nimbusLightBackground", SURFACE);
        UIManager.put("nimbusSelectionBackground", PRIMAIRE);
        UIManager.put("nimbusSelectedText", Color.WHITE);
        UIManager.put("nimbusSelection", PRIMAIRE);
        UIManager.put("text", TEXTE);
        UIManager.put("info", new Color(0xFFFDE7));
        UIManager.put("nimbusOrange", new Color(0xE08A00));
        UIManager.put("nimbusRed", ROUGE);
        UIManager.put("nimbusGreen", VERT);
        UIManager.put("nimbusInfoBlue", BLEU);
        UIManager.put("nimbusAlertYellow", new Color(0xF2B400));
        UIManager.put("Table.alternateRowColor", LIGNE_ALTERNEE);
        UIManager.put("Table.gridColor", new Color(0xE3E8EE));
        UIManager.put("Table.showGrid", Boolean.TRUE);
        UIManager.put("Table[Enabled+Selected].textBackground", SELECTION);
        UIManager.put("Table[Enabled+Selected].textForeground", TEXTE);
        UIManager.put("TabbedPane.font", new FontUIResource(police(Font.BOLD, 13)));
        UIManager.put("ToolTip.background", new Color(0xFFFDE7));
        // Boutons des boîtes de dialogue toujours en français, quelle que soit la langue du système.
        UIManager.put("OptionPane.okButtonText", "OK");
        UIManager.put("OptionPane.cancelButtonText", "Annuler");
        UIManager.put("OptionPane.yesButtonText", "Oui");
        UIManager.put("OptionPane.noButtonText", "Non");

        FontUIResource normale = new FontUIResource(police(Font.PLAIN, 13));
        for (Object cle : UIManager.getLookAndFeelDefaults().keySet().toArray()) {
            if (cle != null && cle.toString().endsWith(".font") && !"TabbedPane.font".equals(cle)) {
                UIManager.put(cle, normale);
            }
        }
        UIManager.put("defaultFont", normale);
        UIManager.getLookAndFeelDefaults().put("defaultFont", normale);
    }

    // ------------------------------------------------------------------ Couleurs des cellules

    /** Couleurs {texte, fond} d'une valeur de statut, ou null si ce n'est pas un statut connu. */
    public static Color[] couleursStatut(String colonne, Object valeur) {
        if (valeur == null) {
            return null;
        }
        String v = valeur.toString();
        if (COLONNES_STATUT.contains(colonne)) {
            return STATUTS.get(v);
        }
        if ("Actif".equals(colonne)) {
            return "Oui".equals(v) ? STATUTS.get("ACTIF") : STATUTS.get("SUSPENDU");
        }
        if ("Mot de passe provisoire".equals(colonne) && "Oui".equals(v)) {
            return STATUTS.get("RESERVE");
        }
        return null;
    }

    /** Couleur de texte d'une valeur chiffrée ou d'une date (retards, impayés...), ou null. */
    public static Color couleurValeur(String colonne, Object valeur) {
        if (valeur instanceof Number) {
            double n = ((Number) valeur).doubleValue();
            if (colonne.contains("etard") || "Impayé".equals(colonne) || "Pénalité à ce jour".equals(colonne)) {
                return n > 0 ? ROUGE : null;
            }
            if ("Disponibles".equals(colonne)) {
                return n > 0 ? VERT : ROUGE;
            }
            if ("En attente".equals(colonne) || "Position".equals(colonne)) {
                return n > 0 ? ORANGE : null;
            }
        }
        if (valeur instanceof java.sql.Date
                && ("Échéance".equals(colonne) || "Carte jusqu'au".equals(colonne) || "À retirer avant".equals(colonne))) {
            return ((java.sql.Date) valeur).toLocalDate().isBefore(java.time.LocalDate.now()) ? ROUGE : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ Composants

    /** Cadre de section avec titre coloré. */
    public static Border cadre(String titre) {
        TitledBorder t = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDURE), "  " + titre + "  ");
        t.setTitleFont(police(Font.BOLD, 14));
        t.setTitleColor(PRIMAIRE);
        return BorderFactory.createCompoundBorder(t, BorderFactory.createEmptyBorder(4, 6, 6, 6));
    }

    public enum Style {
        PRINCIPAL, SECONDAIRE, DANGER, SUCCES
    }

    /** Bouton plat aux coins arrondis, coloré selon la nature de l'action. */
    public static final class Bouton extends JButton {

        private static final long serialVersionUID = 1L;
        private final Style style;
        private boolean survol;
        private boolean appui;

        public Bouton(String texte, Style style) {
            super(texte);
            this.style = style;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(police(Font.BOLD, 13));
            setForeground(style == Style.SECONDAIRE ? PRIMAIRE : Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(6, 14, 6, 14));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    survol = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    survol = false;
                    appui = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    appui = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    appui = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(Math.max(fm.stringWidth(getText()) + 30, 76), fm.getHeight() + 14);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        private Color fond() {
            Color base;
            switch (style) {
            case DANGER:
                base = ROUGE;
                break;
            case SUCCES:
                base = VERT;
                break;
            case SECONDAIRE:
                base = SURFACE;
                break;
            default:
                base = PRIMAIRE;
            }
            if (!isEnabled()) {
                return GRIS_CLAIR;
            }
            if (style == Style.SECONDAIRE) {
                return appui ? new Color(0xD6E2F0) : survol ? PRIMAIRE_CLAIR : SURFACE;
            }
            if (appui) {
                return base.darker();
            }
            return survol ? melanger(base, Color.WHITE, 0.15f) : base;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fond());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (style == Style.SECONDAIRE) {
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(survol ? PRIMAIRE : BORDURE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            // Texte dessiné ici (et non par Nimbus) pour qu'il ne soit jamais tronqué.
            g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(isEnabled() ? getForeground() : GRIS);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    /** Onglets plats : onglet actif blanc souligné de bleu nuit, les autres discrets. */
    public static void ongletsPlats(JTabbedPane t) {
        t.setFont(police(Font.BOLD, 13));
        t.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        t.setBackground(FOND);
        t.setOpaque(true);
        t.setUI(new BasicTabbedPaneUI() {
            @Override
            protected void installDefaults() {
                super.installDefaults();
                tabInsets = new Insets(8, 14, 8, 14);
                selectedTabPadInsets = new Insets(0, 0, 0, 0);
                tabAreaInsets = new Insets(2, 2, 0, 2);
                contentBorderInsets = new Insets(1, 0, 0, 0);
            }

            @Override
            protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean choisi) {
                g.setColor(choisi ? SURFACE : (getRolloverTab() == index ? PRIMAIRE_CLAIR : FOND));
                g.fillRect(x, y, w, h);
                if (choisi) {
                    g.setColor(PRIMAIRE);
                    g.fillRect(x, y + h - 3, w, 3);
                }
            }

            @Override
            protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h, boolean choisi) {
                if (choisi) {
                    g.setColor(BORDURE);
                    g.drawLine(x, y, x + w - 1, y);
                    g.drawLine(x, y, x, y + h - 1);
                    g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
                }
            }

            @Override
            protected void paintFocusIndicator(Graphics g, int placement, java.awt.Rectangle[] r, int index,
                    java.awt.Rectangle icone, java.awt.Rectangle texte, boolean choisi) {
                // pas de pointillés
            }

            @Override
            protected void paintContentBorder(Graphics g, int placement, int choisi) {
                Insets in = tabPane.getInsets();
                int y = in.top + calculateTabAreaHeight(placement, runCount, maxTabHeight) - 1;
                g.setColor(BORDURE);
                g.fillRect(in.left, y, tabPane.getWidth() - in.left - in.right, 1);
            }

            @Override
            protected void paintText(Graphics g, int placement, Font f, java.awt.FontMetrics fm, int index, String titre,
                    java.awt.Rectangle r, boolean choisi) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(f);
                g2.setColor(choisi ? PRIMAIRE : TEXTE_DOUX);
                g2.drawString(titre, r.x, r.y + fm.getAscent());
            }
        });
    }

    public static Color melanger(Color a, Color b, float part) {
        return new Color(Math.round(a.getRed() * (1 - part) + b.getRed() * part),
                Math.round(a.getGreen() * (1 - part) + b.getGreen() * part),
                Math.round(a.getBlue() * (1 - part) + b.getBlue() * part));
    }
}
