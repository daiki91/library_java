package gesbiblio.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Champ de saisie avec auto-complétion : la liste des suggestions se filtre au fil de la frappe
 * (sans tenir compte des majuscules ni des accents ; plusieurs mots = tous doivent être présents).
 * Flèches haut/bas pour naviguer, Entrée pour choisir, Échap pour fermer.
 */
public class ChampRecherche extends JTextField {

    private static final long serialVersionUID = 1L;
    private static final int MAX_SUGGESTIONS = 30;

    /** Une proposition : libellé affiché (HTML possible), texte de recherche, texte remis dans le champ, valeur. */
    public static final class Suggestion {
        public final String libelle;
        public final String texte;
        public final Object valeur;
        final String cle;

        public Suggestion(String libelle, String texte, String recherche, Object valeur) {
            this.libelle = libelle;
            this.texte = texte;
            this.valeur = valeur;
            this.cle = normaliser(recherche);
        }

        @Override
        public String toString() {
            return texte;
        }
    }

    /** Fournit les suggestions (rechargées à chaque fois que le champ reçoit le focus). */
    public interface Source {
        List<Suggestion> charger() throws Exception;
    }

    /** Appelé quand une suggestion est choisie (ou null quand la saisie est modifiée/effacée). */
    public interface Ecouteur {
        void choix(Suggestion s);
    }

    private final Source source;
    private final String indice;
    private final DefaultListModel<Suggestion> modele = new DefaultListModel<Suggestion>();
    private final JList<Suggestion> liste = new JList<Suggestion>(modele);
    private final JPopupMenu popup = new JPopupMenu();
    private List<Suggestion> toutes = new ArrayList<Suggestion>();
    private Ecouteur ecouteur;
    private Suggestion choisie;
    private String saisie = "";
    private boolean majInterne;

    public ChampRecherche(Source source, String indice) {
        super(30);
        this.source = source;
        this.indice = indice;

        liste.setFocusable(false);
        liste.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, false);
                c.setText("<html>" + ((Suggestion) v).libelle + "</html>");
                c.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                c.setFont(Theme.police(java.awt.Font.PLAIN, 13));
                c.setBackground(sel ? Theme.SELECTION : (i % 2 == 0 ? Theme.SURFACE : Theme.LIGNE_ALTERNEE));
                c.setForeground(Theme.TEXTE);
                return c;
            }
        });
        liste.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (liste.getSelectedValue() != null) {
                    choisir(liste.getSelectedValue());
                }
            }
        });
        liste.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = liste.locationToIndex(e.getPoint());
                if (i >= 0) {
                    liste.setSelectedIndex(i);
                }
            }
        });
        JScrollPane sp = new JScrollPane(liste, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        popup.setFocusable(false);
        popup.setBorder(BorderFactory.createLineBorder(Theme.PRIMAIRE));
        popup.add(sp);

        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                modifie();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                modifie();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                modifie();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                recharger();
            }

            @Override
            public void focusLost(FocusEvent e) {
                popup.setVisible(false);
            }
        });

        lier(KeyEvent.VK_DOWN, "suivant", () -> deplacer(1));
        lier(KeyEvent.VK_UP, "precedent", () -> deplacer(-1));
        lier(KeyEvent.VK_ESCAPE, "fermer", () -> popup.setVisible(false));
        lier(KeyEvent.VK_ENTER, "valider", this::valider);
    }

    private void lier(int touche, String nom, Runnable r) {
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(touche, 0), nom);
        getActionMap().put(nom, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    public void setEcouteur(Ecouteur e) {
        this.ecouteur = e;
    }

    /** Suggestion actuellement retenue (null si la saisie ne correspond à aucun choix). */
    public Suggestion getChoisie() {
        return choisie;
    }

    /** Texte tapé juste avant le choix (utile pour retrouver un code-barres scanné). */
    public String getSaisie() {
        return saisie;
    }

    /** Recharge la liste depuis la base. */
    public void recharger() {
        try {
            toutes = source.charger();
        } catch (Exception e) {
            toutes = new ArrayList<Suggestion>();
            Ui.erreur(this, e);
        }
    }

    /** Vide le champ sans déclencher de choix. */
    public void effacer() {
        majInterne = true;
        setText("");
        majInterne = false;
        popup.setVisible(false);
        if (choisie != null) {
            choisie = null;
            if (ecouteur != null) {
                ecouteur.choix(null);
            }
        }
    }

    /** Choisit, par programme, la première suggestion dont la valeur vérifie le test. */
    public boolean choisirSi(java.util.function.Predicate<Object> test) {
        recharger();
        for (Suggestion s : toutes) {
            if (test.test(s.valeur)) {
                saisie = "";
                choisir(s);
                return true;
            }
        }
        return false;
    }

    private void modifie() {
        if (majInterne) {
            return;
        }
        if (choisie != null) {
            choisie = null;
            if (ecouteur != null) {
                ecouteur.choix(null);
            }
        }
        SwingUtilities.invokeLater(() -> filtrer(false));
    }

    private void filtrer(boolean meme_vide) {
        String q = normaliser(getText());
        if (q.isEmpty() && !meme_vide) {
            popup.setVisible(false);
            return;
        }
        String[] mots = q.isEmpty() ? new String[0] : q.split("\\s+");
        modele.clear();
        for (Suggestion s : toutes) {
            boolean ok = true;
            for (String m : mots) {
                if (!s.cle.contains(m)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                modele.addElement(s);
                if (modele.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            }
        }
        if (modele.isEmpty() || !isShowing()) {
            popup.setVisible(false);
            return;
        }
        liste.setSelectedIndex(0);
        liste.setVisibleRowCount(Math.min(modele.size(), 8));
        popup.setPopupSize(Math.max(getWidth(), 520), Math.min(modele.size(), 8) * 30 + 4);
        if (!popup.isVisible()) {
            popup.show(this, 0, getHeight());
        } else {
            popup.pack();
            popup.setPopupSize(Math.max(getWidth(), 520), Math.min(modele.size(), 8) * 30 + 4);
            popup.revalidate();
            popup.repaint();
        }
        liste.ensureIndexIsVisible(0);
    }

    private void deplacer(int sens) {
        if (!popup.isVisible()) {
            filtrer(true);
            return;
        }
        int i = Math.max(0, Math.min(modele.size() - 1, liste.getSelectedIndex() + sens));
        liste.setSelectedIndex(i);
        liste.ensureIndexIsVisible(i);
    }

    private void valider() {
        if (popup.isVisible() && liste.getSelectedValue() != null) {
            choisir(liste.getSelectedValue());
            return;
        }
        // Saisie directe (ex. douchette) : on cherche une correspondance unique.
        String q = normaliser(getText());
        if (!q.isEmpty() && choisie == null) {
            Suggestion trouvee = null;
            int n = 0;
            for (Suggestion s : toutes) {
                if (s.cle.contains(q)) {
                    trouvee = s;
                    n++;
                }
            }
            if (n == 1) {
                choisir(trouvee);
                return;
            }
        }
        postActionEvent();
    }

    private void choisir(Suggestion s) {
        saisie = getText();
        majInterne = true;
        setText(s.texte);
        majInterne = false;
        popup.setVisible(false);
        choisie = s;
        if (ecouteur != null) {
            ecouteur.choix(s);
        }
    }

    /** Texte indicatif gris quand le champ est vide. */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty() && indice != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(0x9AA5B1));
            g2.setFont(getFont().deriveFont(java.awt.Font.ITALIC));
            java.awt.Insets in = getInsets();
            g2.drawString(indice, in.left + 2, (getHeight() - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent());
            g2.dispose();
        }
    }

    static String normaliser(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
