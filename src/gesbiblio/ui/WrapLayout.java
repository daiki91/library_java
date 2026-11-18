package gesbiblio.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** FlowLayout qui passe à la ligne quand la fenêtre est trop étroite (barres de boutons). */
public class WrapLayout extends FlowLayout {

    private static final long serialVersionUID = 1L;

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return taille(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = taille(target, false);
        d.width -= getHgap() + 1;
        return d;
    }

    private Dimension taille(Container target, boolean preferee) {
        synchronized (target.getTreeLock()) {
            Container parent = target;
            int largeurCible = target.getSize().width;
            while (largeurCible == 0 && parent.getParent() != null) {
                parent = parent.getParent();
                largeurCible = parent.getSize().width;
            }
            if (largeurCible == 0) {
                largeurCible = Integer.MAX_VALUE;
            }
            int hgap = getHgap();
            int vgap = getVgap();
            Insets in = target.getInsets();
            int maxLargeur = largeurCible - (in.left + in.right + hgap * 2);
            Dimension dim = new Dimension(0, 0);
            int largeurLigne = 0;
            int hauteurLigne = 0;
            for (int i = 0; i < target.getComponentCount(); i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) {
                    continue;
                }
                Dimension d = preferee ? m.getPreferredSize() : m.getMinimumSize();
                if (largeurLigne + d.width > maxLargeur) {
                    ajouterLigne(dim, largeurLigne, hauteurLigne);
                    largeurLigne = 0;
                    hauteurLigne = 0;
                }
                if (largeurLigne != 0) {
                    largeurLigne += hgap;
                }
                largeurLigne += d.width;
                hauteurLigne = Math.max(hauteurLigne, d.height);
            }
            ajouterLigne(dim, largeurLigne, hauteurLigne);
            dim.width += in.left + in.right + hgap * 2;
            dim.height += in.top + in.bottom + vgap * 2;
            Container defilement = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (defilement != null && target.isValid()) {
                dim.width -= hgap + 1;
            }
            return dim;
        }
    }

    private void ajouterLigne(Dimension dim, int largeur, int hauteur) {
        dim.width = Math.max(dim.width, largeur);
        if (dim.height > 0) {
            dim.height += getVgap();
        }
        dim.height += hauteur;
    }
}
