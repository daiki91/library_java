package gesbiblio.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import gesbiblio.db.Sql;
import gesbiblio.metier.RegleException;

/** Fonctions communes de l'interface : messages, tableaux, boutons. */
public final class Ui {

    // Noms de couleurs utilisés par la fenêtre principale : ils pointent tous vers la palette unique de Theme.
    public static final Color FOND = Theme.FOND;
    public static final Color SURFACE = Theme.SURFACE;
    public static final Color ENCRE = Theme.TEXTE;
    public static final Color SECONDAIRE = Theme.TEXTE_DOUX;
    public static final Color ACCENT = Theme.ACCENT;
    public static final Color ACCENT_CLAIR = new Color(0xE2F4F1);
    public static final Color BORDURE = Theme.BORDURE;

    /** Applique la charte graphique (voir Theme). */
    public static void appliquerTheme() {
        Theme.appliquer();
    }

    public interface Action {
        /** @return le message à afficher (ou null) */
        String executer() throws Exception;
    }

    private Ui() {
    }

    public static void erreur(Component parent, Exception e) {
        if (e instanceof RegleException) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Opération refusée", JOptionPane.WARNING_MESSAGE);
        } else if (e instanceof SQLException) {
            JOptionPane.showMessageDialog(parent, "Erreur de base de données :\n" + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(parent, "Erreur inattendue :\n" + e, "Erreur", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "GesBiblio", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirmer(Component parent, String question) {
        return JOptionPane.showConfirmDialog(parent, question, "Confirmation", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Exécute une action, affiche son message ou l'erreur, puis lance « apres » (rafraîchissement). */
    public static boolean lancer(Component parent, Action action, Runnable apres) {
        boolean ok = false;
        try {
            String m = action.executer();
            if (m != null && !m.isEmpty()) {
                info(parent, m);
            }
            ok = true;
        } catch (Exception e) {
            erreur(parent, e);
        }
        if (apres != null) {
            apres.run();
        }
        return ok;
    }

    /** Bouton d'action secondaire (blanc bordé). */
    public static JButton bouton(String texte, ActionListener a) {
        return bouton(texte, Theme.Style.SECONDAIRE, a);
    }

    /** Bouton de l'action principale de l'écran (bleu nuit). */
    public static JButton boutonPrincipal(String texte, ActionListener a) {
        return bouton(texte, Theme.Style.PRINCIPAL, a);
    }

    /** Bouton d'une action destructrice ou pénalisante (rouge). */
    public static JButton boutonDanger(String texte, ActionListener a) {
        return bouton(texte, Theme.Style.DANGER, a);
    }

    /** Bouton d'une action qui règle une situation (vert). */
    public static JButton boutonSucces(String texte, ActionListener a) {
        return bouton(texte, Theme.Style.SUCCES, a);
    }

    private static JButton bouton(String texte, Theme.Style style, ActionListener a) {
        JButton b = new Theme.Bouton(texte, style);
        b.addActionListener(a);
        return b;
    }

    public static JPanel ligne(Component... composants) {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 6));
        for (Component c : composants) {
            p.add(c);
        }
        return p;
    }

    public static JLabel titre(String texte) {
        JLabel l = new JLabel(texte);
        l.setFont(Theme.police(java.awt.Font.BOLD, 18));
        l.setForeground(Theme.PRIMAIRE);
        l.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 10));
        return l;
    }

    /** Tableau triable, sélection d'une ligne, dates affichées en jj/mm/aaaa. */
    public static JTable tableau() {
        JTable t = new JTable() {
            private static final long serialVersionUID = 1L;

            /** Lignes zébrées ; statuts, retards et échéances dépassées en couleur. */
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                boolean choisie = isRowSelected(row);
                Object v = getValueAt(row, col);
                String colonne = getColumnName(col);
                Color[] statut = Theme.couleursStatut(colonne, v);
                Color valeur = Theme.couleurValeur(colonne, v);
                Color fond = choisie ? Theme.SELECTION : (row % 2 == 0 ? Theme.SURFACE : Theme.LIGNE_ALTERNEE);
                Color texte = Theme.TEXTE;
                int style = Font.PLAIN;
                if (statut != null) {
                    texte = statut[0];
                    style = Font.BOLD;
                    if (!choisie) {
                        fond = statut[1];
                    }
                } else if (valeur != null) {
                    texte = valeur;
                    style = Font.BOLD;
                }
                c.setBackground(fond);
                c.setForeground(texte);
                c.setFont(Theme.police(style, 13));
                if (c instanceof JComponent) {
                    ((JComponent) c).setOpaque(true);
                    ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                }
                return c;
            }
        };
        t.setAutoCreateRowSorter(true);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(0xE3E8EE));
        t.setIntercellSpacing(new java.awt.Dimension(0, 1));
        t.setSelectionBackground(Theme.SELECTION);
        t.setSelectionForeground(Theme.TEXTE);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, col);
                l.setOpaque(true);
                l.setBackground(Theme.ENTETE_TABLE);
                l.setForeground(Theme.PRIMAIRE);
                l.setFont(Theme.police(Font.BOLD, 12));
                l.setHorizontalAlignment(SwingConstants.LEFT);
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 2, 1, Theme.BORDURE),
                        BorderFactory.createEmptyBorder(6, 6, 6, 6)));
                return l;
            }
        });
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setRowHeight(26);
        DefaultTableCellRenderer dates = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            private final SimpleDateFormat jour = new SimpleDateFormat("dd/MM/yyyy");
            private final SimpleDateFormat heure = new SimpleDateFormat("dd/MM/yyyy HH:mm");

            @Override
            protected void setValue(Object v) {
                if (v instanceof java.sql.Timestamp) {
                    setText(heure.format(v));
                } else if (v instanceof java.util.Date) {
                    setText(jour.format(v));
                } else {
                    setText(v == null ? "" : v.toString());
                }
            }
        };
        dates.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(java.sql.Date.class, dates);
        t.setDefaultRenderer(java.sql.Timestamp.class, dates);
        return t;
    }

    /** Recharge un tableau à partir d'une requête (les alias SQL servent d'en-têtes). */
    public static void charger(Component parent, JTable t, String sql, Object... params) {
        try {
            t.setModel(Sql.table(sql, params));
            ajusterColonnes(t);
        } catch (SQLException e) {
            erreur(parent, e);
        }
    }

    /** Largeur de chaque colonne proportionnelle à son contenu. */
    private static void ajusterColonnes(JTable t) {
        for (int c = 0; c < t.getColumnCount(); c++) {
            TableColumn col = t.getColumnModel().getColumn(c);
            Component h = t.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(t, col.getHeaderValue(), false, false, 0, c);
            int w = h.getPreferredSize().width;
            for (int r = 0; r < Math.min(t.getRowCount(), 200); r++) {
                w = Math.max(w, t.prepareRenderer(t.getCellRenderer(r, c), r, c).getPreferredSize().width);
            }
            col.setPreferredWidth(Math.min(w + 16, 420));
        }
    }

    /** Valeur d'une colonne de la ligne sélectionnée (tient compte du tri), ou null. */
    public static Object selection(JTable t, int colonne) {
        int r = t.getSelectedRow();
        if (r < 0) {
            return null;
        }
        return t.getModel().getValueAt(t.convertRowIndexToModel(r), colonne);
    }

    /** Id (1re colonne) de la ligne sélectionnée ; affiche un message sinon. */
    public static Integer idSelectionne(Component parent, JTable t, String quoi) {
        Object v = selection(t, 0);
        if (v == null) {
            JOptionPane.showMessageDialog(parent, "Sélectionnez d'abord " + quoi + " dans la liste.", "GesBiblio",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return ((Number) v).intValue();
    }

    /** Zone défilante blanche bordée, pour les tableaux. */
    public static javax.swing.JScrollPane defilement(Component c) {
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(c);
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDURE));
        return sp;
    }

    public static String s(Object o) {
        return o == null ? "" : o.toString();
    }

    public static String date(Object o) {
        return o == null ? "" : new SimpleDateFormat("dd/MM/yyyy").format(o);
    }
}
