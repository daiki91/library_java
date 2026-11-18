package adminis;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.JTableHeader;

/** Habillage commun des fenêtres de la première version. */
final class LegacyUi {

	private static final Color FOND = new Color(244, 247, 250);
	private static final Color ENCRE = new Color(31, 45, 61);
	private static final Color ACCENT = new Color(20, 112, 104);
	private static final Color BORDURE = new Color(220, 228, 235);

	private LegacyUi() {
	}

	static void appliquer(JFrame fenetre, JPanel racine, String titre) {
		fenetre.setTitle("GesBiblio | " + titre);
		racine.setBackground(FOND);
		styliser(racine);
	}

	private static void styliser(Container parent) {
		for (Component composant : parent.getComponents()) {
			if (composant instanceof JButton) {
				JButton bouton = (JButton) composant;
				bouton.setFont(bouton.getFont().deriveFont(Font.BOLD));
				bouton.setBackground(bouton.getText().toLowerCase().contains("supprim")
						? new Color(169, 57, 57) : ACCENT);
				bouton.setForeground(Color.WHITE);
				bouton.setOpaque(true);
				bouton.setContentAreaFilled(true);
				bouton.setFocusPainted(false);
				bouton.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
			} else if (composant instanceof JTextField) {
				composant.setFont(composant.getFont().deriveFont(14f));
				((JTextField) composant).setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(BORDURE), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
			} else if (composant instanceof JComboBox) {
				composant.setFont(composant.getFont().deriveFont(14f));
			} else if (composant instanceof JTable) {
				JTable tableau = (JTable) composant;
				tableau.setRowHeight(26);
				tableau.setFillsViewportHeight(true);
				tableau.setSelectionBackground(new Color(226, 242, 239));
				tableau.setSelectionForeground(ENCRE);
				tableau.setGridColor(BORDURE);
				tableau.setShowVerticalLines(false);
				JTableHeader entete = tableau.getTableHeader();
				entete.setFont(entete.getFont().deriveFont(Font.BOLD));
				entete.setBackground(new Color(235, 240, 244));
				entete.setForeground(ENCRE);
			} else if (composant instanceof JLabel) {
				JLabel etiquette = (JLabel) composant;
				etiquette.setForeground(ENCRE);
				if (etiquette.getFont().getSize() >= 16) {
					etiquette.setFont(etiquette.getFont().deriveFont(Font.BOLD, 19f));
				}
			} else if (composant instanceof JPanel) {
				composant.setBackground(FOND);
			}
			if (composant instanceof Container) {
				styliser((Container) composant);
			}
		}
	}
}
