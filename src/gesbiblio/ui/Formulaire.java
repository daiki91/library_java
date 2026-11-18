package gesbiblio.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import gesbiblio.metier.RegleException;

/**
 * Petit formulaire de saisie dans une boîte de dialogue.
 * Si l'enregistrement est refusé (règle non respectée), le formulaire se rouvre
 * avec les valeurs déjà saisies, pour que l'agent n'ait pas à tout retaper.
 */
public final class Formulaire {

    private final JPanel panneau = new JPanel(new GridBagLayout());
    private final Map<String, JComponent> champs = new LinkedHashMap<String, JComponent>();
    private int ligne;

    private Formulaire ajouter(String cle, String libelle, JComponent champ) {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.gridy = ligne++;
        g.gridx = 0;
        g.anchor = GridBagConstraints.LINE_END;
        JLabel l = new JLabel(libelle);
        l.setForeground(Theme.TEXTE);
        l.setFont(Theme.police(java.awt.Font.BOLD, 13));
        panneau.add(l, g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        champ.setPreferredSize(new Dimension(280, 30));
        panneau.add(champ, g);
        champs.put(cle, champ);
        return this;
    }

    /** Titre coloré en haut du formulaire. */
    public Formulaire entete(String titre, String sousTitre) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = ligne++;
        g.gridx = 0;
        g.gridwidth = 2;
        g.insets = new Insets(0, 4, 10, 4);
        g.anchor = GridBagConstraints.LINE_START;
        JLabel l = new JLabel("<html><span style='font-size:18pt;color:#1F3A5F'><b>" + titre
                + "</b></span><br><span style='color:#5E6B7A'>" + sousTitre + "</span></html>");
        panneau.add(l, g);
        return this;
    }

    public Formulaire texte(String cle, String libelle, Object valeur) {
        return ajouter(cle, libelle, new JTextField(valeur == null ? "" : valeur.toString(), 26));
    }

    public Formulaire motDePasse(String cle, String libelle) {
        return ajouter(cle, libelle, new JPasswordField(26));
    }

    public Formulaire liste(String cle, String libelle, List<String> choix, Object valeur, boolean avecVide) {
        JComboBox<String> cb = new JComboBox<String>();
        if (avecVide) {
            cb.addItem("");
        }
        for (String c : choix) {
            cb.addItem(c);
        }
        if (valeur != null) {
            cb.setSelectedItem(valeur.toString());
        }
        return ajouter(cle, libelle, cb);
    }

    public Formulaire nombre(String cle, String libelle, int valeur, int min, int max) {
        return ajouter(cle, libelle, new JSpinner(new SpinnerNumberModel(valeur, min, max, 1)));
    }

    public Formulaire caseACocher(String cle, String libelle, boolean coche) {
        return ajouter(cle, libelle, new JCheckBox("", coche));
    }

    public Formulaire note(String texte) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = ligne++;
        g.gridx = 0;
        g.gridwidth = 2;
        g.insets = new Insets(8, 6, 4, 6);
        g.anchor = GridBagConstraints.LINE_START;
        JLabel l = new JLabel("<html><i>" + texte + "</i></html>");
        l.setForeground(Theme.TEXTE_DOUX);
        panneau.add(l, g);
        return this;
    }

    public String get(String cle) {
        JComponent c = champs.get(cle);
        if (c instanceof JPasswordField) {
            return new String(((JPasswordField) c).getPassword());
        }
        if (c instanceof JTextField) {
            return ((JTextField) c).getText().trim();
        }
        if (c instanceof JComboBox) {
            Object v = ((JComboBox<?>) c).getSelectedItem();
            return v == null ? "" : v.toString();
        }
        if (c instanceof JSpinner) {
            return ((JSpinner) c).getValue().toString();
        }
        if (c instanceof JCheckBox) {
            return String.valueOf(((JCheckBox) c).isSelected());
        }
        return "";
    }

    public int entier(String cle) {
        return Integer.parseInt(get(cle));
    }

    public boolean coche(String cle) {
        return Boolean.parseBoolean(get(cle));
    }

    /**
     * Affiche le formulaire et exécute l'action à la validation.
     * @return true si l'action a réussi, false si l'utilisateur a annulé
     */
    public boolean executer(Component parent, String titre, Ui.Action action) {
        while (JOptionPane.showConfirmDialog(parent, panneau, titre, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                String m = action.executer();
                if (m != null && !m.isEmpty()) {
                    Ui.info(parent, m);
                }
                return true;
            } catch (RegleException e) {
                Ui.erreur(parent, e);
            } catch (Exception e) {
                Ui.erreur(parent, e);
                return false;
            }
        }
        return false;
    }
}
