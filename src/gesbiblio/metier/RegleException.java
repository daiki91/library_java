package gesbiblio.metier;

/** Une règle du règlement de la bibliothèque n'est pas respectée : le message est destiné à l'agent. */
public class RegleException extends Exception {

    private static final long serialVersionUID = 1L;

    public RegleException(String message) {
        super(message);
    }
}
