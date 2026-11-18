package gesbiblio.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Accès à la base MySQL.
 * Les identifiants ne sont plus écrits dans le code : ils sont lus dans
 * config/db.properties (créé automatiquement au premier lancement).
 * Un autre fichier peut être indiqué avec -Dgesbiblio.config=chemin.
 */
public final class Database {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/gesbiblio?useSSL=false&useUnicode=true&characterEncoding=UTF-8";

    private static String url;
    private static String user;
    private static String password;

    private Database() {
    }

    /** Remplace la configuration (utilisé pour les tests). */
    public static synchronized void configurer(String u, String usr, String pwd) {
        url = u;
        user = usr;
        password = pwd;
    }

    public static File fichierConfig() {
        String chemin = System.getProperty("gesbiblio.config", "config" + File.separator + "db.properties");
        return new File(chemin);
    }

    private static synchronized void charger() {
        if (url != null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty("db.url", DEFAULT_URL);
        p.setProperty("db.user", "root");
        p.setProperty("db.password", "passer");
        File f = fichierConfig();
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Lecture impossible de " + f.getAbsolutePath(), e);
            }
        } else {
            try {
                if (f.getParentFile() != null) {
                    f.getParentFile().mkdirs();
                }
                try (OutputStream out = new FileOutputStream(f)) {
                    p.store(out, "Connexion GesBiblio - modifiez db.user / db.password selon votre MySQL");
                }
            } catch (IOException ignore) {
                // pas bloquant : on garde les valeurs par défaut
            }
        }
        url = p.getProperty("db.url").trim();
        user = p.getProperty("db.user").trim();
        password = p.getProperty("db.password");
    }

    /** Ouvre une nouvelle connexion (à fermer avec try-with-resources). */
    public static Connection connexion() throws SQLException {
        charger();
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Pilote MySQL introuvable : ajoutez mysql-connector-java au classpath.", e);
        }
        return DriverManager.getConnection(url, user, password);
    }

    public static String description() {
        charger();
        int q = url.indexOf('?');
        return (q > 0 ? url.substring(0, q) : url).replace("jdbc:mysql://", "") + " (utilisateur " + user + ")";
    }
}
