package gesbiblio.metier;

import java.sql.Connection;
import java.sql.SQLException;

import gesbiblio.db.Database;

/**
 * Exécute un traitement dans une transaction SQL : tout est validé, ou rien.
 * Exemple : l'emprunt est créé ET l'exemplaire passe à EMPRUNTE, jamais l'un sans l'autre.
 */
public final class Transaction {

    public interface Travail<T> {
        T executer(Connection c) throws SQLException, RegleException;
    }

    private Transaction() {
    }

    public static <T> T executer(Travail<T> travail) throws SQLException, RegleException {
        try (Connection c = Database.connexion()) {
            c.setAutoCommit(false);
            try {
                T resultat = travail.executer(c);
                c.commit();
                return resultat;
            } catch (SQLException | RegleException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
