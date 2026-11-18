package gesbiblio.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.swing.table.DefaultTableModel;

/** Petites fonctions utilitaires JDBC : toutes les requêtes passent par des PreparedStatement. */
public final class Sql {

    private Sql() {
    }

    private static PreparedStatement preparer(Connection c, String sql, Object[] params, boolean cles) throws SQLException {
        PreparedStatement ps = cles ? c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) : c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    /** Exécute un SELECT et renvoie les lignes. */
    public static List<Object[]> lignes(Connection c, String sql, Object... params) throws SQLException {
        List<Object[]> res = new ArrayList<Object[]>();
        try (PreparedStatement ps = preparer(c, sql, params, false); ResultSet rs = ps.executeQuery()) {
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Object[] l = new Object[n];
                for (int i = 0; i < n; i++) {
                    l[i] = rs.getObject(i + 1);
                }
                res.add(l);
            }
        }
        return res;
    }

    /** Première ligne d'un SELECT, ou null. */
    public static Object[] ligne(Connection c, String sql, Object... params) throws SQLException {
        List<Object[]> l = lignes(c, sql, params);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Première colonne de la première ligne, ou null. */
    public static Object valeur(Connection c, String sql, Object... params) throws SQLException {
        Object[] l = ligne(c, sql, params);
        return l == null ? null : l[0];
    }

    public static int entier(Connection c, String sql, Object... params) throws SQLException {
        Object v = valeur(c, sql, params);
        return v == null ? 0 : ((Number) v).intValue();
    }

    public static double decimal(Connection c, String sql, Object... params) throws SQLException {
        Object v = valeur(c, sql, params);
        return v == null ? 0 : ((Number) v).doubleValue();
    }

    /** INSERT / UPDATE / DELETE : renvoie le nombre de lignes modifiées. */
    public static int maj(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = preparer(c, sql, params, false)) {
            return ps.executeUpdate();
        }
    }

    /** INSERT : renvoie la clé générée. */
    public static int inserer(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = preparer(c, sql, params, true)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /** Exécute un SELECT et le transforme en modèle de JTable non éditable (les alias SQL servent d'en-têtes). */
    public static DefaultTableModel table(String sql, Object... params) throws SQLException {
        try (Connection c = Database.connexion();
             PreparedStatement ps = preparer(c, sql, params, false);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            String[] cols = new String[n];
            for (int i = 0; i < n; i++) {
                cols[i] = md.getColumnLabel(i + 1);
            }
            DefaultTableModel m = new DefaultTableModel(cols, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int r, int col) {
                    return false;
                }

                /** Type réel de la colonne : permet un tri correct des dates et des nombres. */
                @Override
                public Class<?> getColumnClass(int col) {
                    for (int r = 0; r < getRowCount(); r++) {
                        Object v = getValueAt(r, col);
                        if (v != null) {
                            return v.getClass();
                        }
                    }
                    return Object.class;
                }
            };
            while (rs.next()) {
                Object[] l = new Object[n];
                for (int i = 0; i < n; i++) {
                    l[i] = rs.getObject(i + 1);
                }
                m.addRow(l);
            }
            return m;
        }
    }
}
