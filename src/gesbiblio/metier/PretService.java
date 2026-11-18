package gesbiblio.metier;

import java.sql.Connection;
import java.sql.SQLException;

import gesbiblio.db.Sql;

/**
 * Cœur du métier : emprunt, retour, renouvellement, perte.
 * Chaque opération est une transaction : en cas d'erreur, rien n'est enregistré.
 */
public final class PretService {

    private PretService() {
    }

    // ------------------------------------------------------------------ EMPRUNT

    /**
     * Enregistre un prêt après avoir vérifié toutes les règles :
     * compte actif, carte valide, limite de prêts de la catégorie, aucun retard,
     * pénalités sous le seuil, exemplaire disponible (ou réservé pour CE lecteur),
     * pas deux exemplaires du même titre.
     */
    public static String emprunter(final String matricule, final String codeBarres) throws SQLException, RegleException {
        Session.exiger(Session.AGENT);
        if (Outils.vide(matricule).isEmpty()) {
            throw new RegleException("Saisissez le matricule de l'adhérent.");
        }
        if (Outils.vide(codeBarres).isEmpty()) {
            throw new RegleException("Saisissez ou scannez le code-barres de l'exemplaire.");
        }
        return Transaction.executer(c -> {
            Lecteur a = Lecteur.charger(c, matricule);
            a.verifierCompte();

            int enCours = a.empruntsEnCours(c);
            if (enCours >= a.maxEmprunts) {
                throw new RegleException(a.nomComplet + " a déjà " + enCours + " emprunt(s) en cours : la limite pour la catégorie « "
                        + a.categorie + " » est de " + a.maxEmprunts + ".");
            }
            int retards = a.empruntsEnRetard(c);
            if (retards > 0) {
                throw new RegleException(a.nomComplet + " a " + retards + " document(s) en retard : aucun nouveau prêt avant leur retour.");
            }
            double impaye = a.penalitesImpayees(c);
            double seuil = Outils.parametreDecimal(c, "seuil_blocage_penalites", 0);
            String devise = Outils.parametre(c, "devise", "");
            if (seuil > 0 && impaye >= seuil) {
                throw new RegleException(a.nomComplet + " doit " + Outils.montant(impaye, devise)
                        + " de pénalités (seuil de blocage : " + Outils.montant(seuil, devise) + ").");
            }

            Object[] ex = Sql.ligne(c, "SELECT e.id, e.statut, e.ouvrage_id, o.titre FROM exemplaire e "
                    + "JOIN ouvrage o ON o.id = e.ouvrage_id WHERE e.code_barres = ? FOR UPDATE", Outils.vide(codeBarres));
            if (ex == null) {
                throw new RegleException("Aucun exemplaire avec le code-barres « " + Outils.vide(codeBarres) + " ».");
            }
            int exId = ((Number) ex[0]).intValue();
            String statut = (String) ex[1];
            int ouvrageId = ((Number) ex[2]).intValue();
            String titre = (String) ex[3];

            if ("EMPRUNTE".equals(statut)) {
                Object ech = Sql.valeur(c, "SELECT date_echeance FROM emprunt WHERE exemplaire_id = ? AND date_retour IS NULL", exId);
                throw new RegleException("« " + titre + " » est déjà emprunté (retour prévu le " + Outils.date(ech) + ").");
            }
            if ("RESERVE".equals(statut)) {
                Object[] r = Sql.ligne(c, "SELECT r.id, r.adherent_id, CONCAT_WS(' ', a.prenom, a.nom), r.date_limite_retrait "
                        + "FROM reservation r JOIN adherent a ON a.id = r.adherent_id "
                        + "WHERE r.exemplaire_id = ? AND r.statut = 'A_RETIRER' FOR UPDATE", exId);
                if (r != null && ((Number) r[1]).intValue() != a.id) {
                    throw new RegleException("Cet exemplaire est mis de côté pour " + r[2] + " (réservation) jusqu'au "
                            + Outils.date(r[3]) + ".");
                }
            } else if (!"DISPONIBLE".equals(statut)) {
                throw new RegleException("Cet exemplaire n'est pas prêtable (statut : " + statut + ").");
            }
            if (a.empruntDejaCetOuvrage(c, ouvrageId)) {
                throw new RegleException(a.nomComplet + " a déjà un exemplaire de « " + titre + " » en cours de prêt.");
            }

            // La réservation éventuelle de ce lecteur pour ce titre est honorée.
            Sql.maj(c, "UPDATE reservation SET statut = 'HONOREE', exemplaire_id = ? "
                    + "WHERE adherent_id = ? AND ouvrage_id = ? AND statut IN ('EN_ATTENTE', 'A_RETIRER')", exId, a.id, ouvrageId);

            int empruntId = Sql.inserer(c, "INSERT INTO emprunt (exemplaire_id, adherent_id, personnel_id, date_emprunt, date_echeance) "
                    + "VALUES (?, ?, ?, CURDATE(), DATE_ADD(CURDATE(), INTERVAL ? DAY))", exId, a.id, Session.idCourant(), a.dureePret);
            Sql.maj(c, "UPDATE exemplaire SET statut = 'EMPRUNTE' WHERE id = ?", exId);
            Object echeance = Sql.valeur(c, "SELECT date_echeance FROM emprunt WHERE id = ?", empruntId);

            Outils.journal(c, "EMPRUNT", a.matricule + " - " + Outils.vide(codeBarres) + " - " + titre);
            return "Prêt enregistré : « " + titre + " » pour " + a.nomComplet + ".\n"
                    + "À rendre au plus tard le " + Outils.date(echeance) + ".\n"
                    + "Emprunts en cours : " + (enCours + 1) + " / " + a.maxEmprunts + ".";
        });
    }

    // ------------------------------------------------------------------ RETOUR

    /**
     * Enregistre le retour d'un exemplaire : calcule le retard et la pénalité,
     * met l'exemplaire en réparation s'il est endommagé, sinon le donne au premier
     * lecteur de la file de réservation ou le remet en rayon.
     */
    public static String retourner(final String codeBarres, final String etatRetour) throws SQLException, RegleException {
        Session.exiger(Session.AGENT);
        final String etat = etatRetour == null || etatRetour.isEmpty() ? "BON" : etatRetour;
        return Transaction.executer(c -> {
            Object[] ex = chargerExemplaire(c, codeBarres);
            int exId = ((Number) ex[0]).intValue();
            String titre = (String) ex[3];
            Object[] em = empruntEnCours(c, exId, (String) ex[1]);
            int empruntId = ((Number) em[0]).intValue();
            int adherentId = ((Number) em[1]).intValue();
            int retard = ((Number) em[2]).intValue();
            String nom = (String) em[3];

            Sql.maj(c, "UPDATE emprunt SET date_retour = CURDATE(), etat_retour = ? WHERE id = ?", etat, empruntId);

            StringBuilder msg = new StringBuilder("Retour enregistré : « " + titre + " » rendu par " + nom + ".");
            if (retard > 0) {
                double ppj = Outils.parametreDecimal(c, "penalite_par_jour", 0);
                String devise = Outils.parametre(c, "devise", "");
                msg.append("\nRetard : ").append(retard).append(" jour(s).");
                if (ppj > 0) {
                    double montant = retard * ppj;
                    Sql.maj(c, "INSERT INTO penalite (adherent_id, emprunt_id, montant, motif, date_creation) VALUES (?, ?, ?, ?, CURDATE())",
                            adherentId, empruntId, montant, "Retard de " + retard + " jour(s) - " + titre);
                    msg.append(" Pénalité : ").append(Outils.montant(montant, devise)).append(".");
                }
            } else {
                msg.append("\nRendu dans les délais.");
            }

            if ("ENDOMMAGE".equals(etat)) {
                Sql.maj(c, "UPDATE exemplaire SET statut = 'EN_REPARATION', etat = 'ENDOMMAGE' WHERE id = ?", exId);
                msg.append("\nExemplaire endommagé : placé EN RÉPARATION (non prêtable).");
            } else {
                Sql.maj(c, "UPDATE exemplaire SET etat = ? WHERE id = ?", etat, exId);
                String resa = ReservationService.liberer(c, exId);
                msg.append("\n").append(resa == null ? "Exemplaire remis en rayon (DISPONIBLE)." : resa);
            }
            Outils.journal(c, "RETOUR", Outils.vide(codeBarres) + " - " + titre + " - retard " + retard + " j - état " + etat);
            return msg.toString();
        });
    }

    // ------------------------------------------------------------------ RENOUVELLEMENT

    /**
     * Prolonge un prêt si : compte en règle, pas de retard, quota non atteint, personne n'attend ce titre.
     * La nouvelle échéance part de l'échéance actuelle (+ durée de prêt de la catégorie).
     */
    public static String renouveler(final String codeBarres) throws SQLException, RegleException {
        Session.exiger(Session.AGENT);
        return Transaction.executer(c -> {
            Object[] ex = chargerExemplaire(c, codeBarres);
            int exId = ((Number) ex[0]).intValue();
            int ouvrageId = ((Number) ex[2]).intValue();
            String titre = (String) ex[3];
            Object[] em = empruntEnCours(c, exId, (String) ex[1]);
            int empruntId = ((Number) em[0]).intValue();
            int retard = ((Number) em[2]).intValue();
            String matricule = (String) em[4];
            int nbRenouv = ((Number) em[5]).intValue();

            Lecteur a = Lecteur.charger(c, matricule);
            String refus = motifRefusRenouvellement(c, a, retard, nbRenouv, ouvrageId, titre);
            if (refus != null) {
                throw new RegleException("Renouvellement refusé : " + refus);
            }
            Sql.maj(c, "UPDATE emprunt SET nb_renouvellements = nb_renouvellements + 1, "
                    + "date_echeance = DATE_ADD(date_echeance, INTERVAL ? DAY) WHERE id = ?", a.dureePret, empruntId);
            Object ech = Sql.valeur(c, "SELECT date_echeance FROM emprunt WHERE id = ?", empruntId);
            Outils.journal(c, "RENOUVELLEMENT", matricule + " - " + Outils.vide(codeBarres) + " - " + titre);
            return "Prêt renouvelé : « " + titre + " » à rendre le " + Outils.date(ech) + " ("
                    + (nbRenouv + 1) + "/" + a.maxRenouvellements + " renouvellement(s)).";
        });
    }

    /** La règle de prolongation, utilisée à la fois par l'aperçu et par le renouvellement. null = autorisé. */
    private static String motifRefusRenouvellement(Connection c, Lecteur a, int retard, int nbRenouv, int ouvrageId, String titre)
            throws SQLException {
        if (!"ACTIF".equals(a.statut)) {
            return "le compte de " + a.nomComplet + " est suspendu.";
        }
        if (a.carteExpiree) {
            return "la carte de " + a.nomComplet + " a expiré le " + Outils.date(a.dateExpiration) + ".";
        }
        if (retard > 0) {
            return "le prêt a " + retard + " jour(s) de retard : il doit être rendu, pas prolongé.";
        }
        if (nbRenouv >= a.maxRenouvellements) {
            return nbRenouv + " renouvellement(s) déjà fait(s), maximum " + a.maxRenouvellements
                    + " pour la catégorie « " + a.categorie + " ».";
        }
        int attente = Sql.entier(c, "SELECT COUNT(*) FROM reservation WHERE ouvrage_id = ? AND statut = 'EN_ATTENTE'", ouvrageId);
        if (attente > 0) {
            return attente + " lecteur(s) attendent « " + titre + " ».";
        }
        return null;
    }

    // ------------------------------------------------------------------ APERÇUS (avant de valider)

    /** Ce qu'il se passera si l'on rend ou prolonge ce prêt maintenant. */
    public static final class Apercu {
        public String titre;
        public String codeBarres;
        public String adherent;
        public String matricule;
        public Object dateEmprunt;
        public Object echeance;
        public int retard;
        public double penalite;
        public String devise;
        public int nbRenouvellements;
        public int maxRenouvellements;
        /** null si la prolongation est autorisée, sinon la raison du refus. */
        public String refusProlongation;
        public Object nouvelleEcheance;
        /** Lecteur à qui l'exemplaire sera mis de côté au retour (ou null). */
        public String prochainReservataire;
    }

    public static Apercu apercu(final String codeBarres) throws SQLException, RegleException {
        try (Connection c = gesbiblio.db.Database.connexion()) {
            Object[] ex = Sql.ligne(c, "SELECT e.id, e.statut, e.ouvrage_id, o.titre FROM exemplaire e "
                    + "JOIN ouvrage o ON o.id = e.ouvrage_id WHERE e.code_barres = ?", Outils.vide(codeBarres));
            if (ex == null) {
                throw new RegleException("Aucun exemplaire avec le code-barres « " + Outils.vide(codeBarres) + " ».");
            }
            int ouvrageId = ((Number) ex[2]).intValue();
            Object[] em = Sql.ligne(c, "SELECT em.id, a.matricule, CONCAT_WS(' ', a.prenom, a.nom), em.date_emprunt, em.date_echeance, "
                    + "GREATEST(DATEDIFF(CURDATE(), em.date_echeance), 0), em.nb_renouvellements "
                    + "FROM emprunt em JOIN adherent a ON a.id = em.adherent_id "
                    + "WHERE em.exemplaire_id = ? AND em.date_retour IS NULL", ex[0]);
            if (em == null) {
                throw new RegleException("Cet exemplaire n'est pas en prêt (statut actuel : " + ex[1] + ").");
            }
            Apercu x = new Apercu();
            x.titre = (String) ex[3];
            x.codeBarres = Outils.vide(codeBarres);
            x.matricule = (String) em[1];
            x.adherent = (String) em[2];
            x.dateEmprunt = em[3];
            x.echeance = em[4];
            x.retard = ((Number) em[5]).intValue();
            x.nbRenouvellements = ((Number) em[6]).intValue();
            x.devise = Outils.parametre(c, "devise", "");
            x.penalite = x.retard * Outils.parametreDecimal(c, "penalite_par_jour", 0);
            Lecteur a = Lecteur.charger(c, x.matricule);
            x.maxRenouvellements = a.maxRenouvellements;
            x.refusProlongation = motifRefusRenouvellement(c, a, x.retard, x.nbRenouvellements, ouvrageId, x.titre);
            if (x.refusProlongation == null) {
                x.nouvelleEcheance = Sql.valeur(c, "SELECT CAST(DATE_ADD(?, INTERVAL ? DAY) AS DATE)", x.echeance, a.dureePret);
            }
            Object suivant = Sql.valeur(c, "SELECT CONCAT(CONCAT_WS(' ', a.prenom, a.nom), ' (', a.matricule, ')') "
                    + "FROM reservation r JOIN adherent a ON a.id = r.adherent_id "
                    + "WHERE r.ouvrage_id = ? AND r.statut = 'EN_ATTENTE' ORDER BY r.date_reservation, r.id LIMIT 1", ouvrageId);
            x.prochainReservataire = suivant == null ? null : suivant.toString();
            return x;
        }
    }

    /** Date de retour qui sera fixée si cet adhérent emprunte aujourd'hui : {date, nombre de jours, catégorie}. */
    public static Object[] retourPrevu(final String matricule) throws SQLException, RegleException {
        try (Connection c = gesbiblio.db.Database.connexion()) {
            Object[] l = Sql.ligne(c, "SELECT DATE_ADD(CURDATE(), INTERVAL k.duree_pret_jours DAY), k.duree_pret_jours, k.libelle "
                    + "FROM adherent a JOIN categorie_adherent k ON k.code = a.categorie_code WHERE a.matricule = ?", Outils.vide(matricule));
            if (l == null) {
                throw new RegleException("Aucun adhérent avec le matricule « " + Outils.vide(matricule) + " ».");
            }
            return l;
        }
    }

    // ------------------------------------------------------------------ PERTE

    /** Clôt le prêt d'un document perdu, facture le forfait (+ le retard éventuel) et retire l'exemplaire. */
    public static String declarerPerdu(final String codeBarres) throws SQLException, RegleException {
        Session.exiger(Session.AGENT);
        return Transaction.executer(c -> {
            Object[] ex = chargerExemplaire(c, codeBarres);
            int exId = ((Number) ex[0]).intValue();
            String titre = (String) ex[3];
            Object[] em = empruntEnCours(c, exId, (String) ex[1]);
            int empruntId = ((Number) em[0]).intValue();
            int adherentId = ((Number) em[1]).intValue();
            int retard = ((Number) em[2]).intValue();
            String devise = Outils.parametre(c, "devise", "");
            double forfait = Outils.parametreDecimal(c, "forfait_perte", 0);
            double ppj = Outils.parametreDecimal(c, "penalite_par_jour", 0);

            Sql.maj(c, "UPDATE emprunt SET date_retour = CURDATE(), etat_retour = 'PERDU' WHERE id = ?", empruntId);
            Sql.maj(c, "UPDATE exemplaire SET statut = 'PERDU' WHERE id = ?", exId);
            double total = 0;
            if (forfait > 0) {
                Sql.maj(c, "INSERT INTO penalite (adherent_id, emprunt_id, montant, motif, date_creation) VALUES (?, ?, ?, ?, CURDATE())",
                        adherentId, empruntId, forfait, "Perte - " + titre);
                total += forfait;
            }
            if (retard > 0 && ppj > 0) {
                Sql.maj(c, "INSERT INTO penalite (adherent_id, emprunt_id, montant, motif, date_creation) VALUES (?, ?, ?, ?, CURDATE())",
                        adherentId, empruntId, retard * ppj, "Retard de " + retard + " jour(s) - " + titre);
                total += retard * ppj;
            }
            Outils.journal(c, "PERTE", Outils.vide(codeBarres) + " - " + titre);
            return "« " + titre + " » déclaré perdu par " + em[3] + ".\nMontant facturé : " + Outils.montant(total, devise) + ".";
        });
    }

    // ------------------------------------------------------------------ OUTILS

    /** id, statut, ouvrage_id, titre de l'exemplaire (verrouillé). */
    static Object[] chargerExemplaire(Connection c, String codeBarres) throws SQLException, RegleException {
        if (Outils.vide(codeBarres).isEmpty()) {
            throw new RegleException("Saisissez ou scannez le code-barres de l'exemplaire.");
        }
        Object[] ex = Sql.ligne(c, "SELECT e.id, e.statut, e.ouvrage_id, o.titre FROM exemplaire e "
                + "JOIN ouvrage o ON o.id = e.ouvrage_id WHERE e.code_barres = ? FOR UPDATE", Outils.vide(codeBarres));
        if (ex == null) {
            throw new RegleException("Aucun exemplaire avec le code-barres « " + Outils.vide(codeBarres) + " ».");
        }
        return ex;
    }

    /** id, adherent_id, jours de retard, nom, matricule, nb_renouvellements de l'emprunt en cours (verrouillé). */
    private static Object[] empruntEnCours(Connection c, int exId, String statut) throws SQLException, RegleException {
        Object[] em = Sql.ligne(c, "SELECT em.id, em.adherent_id, GREATEST(DATEDIFF(CURDATE(), em.date_echeance), 0), "
                + "CONCAT_WS(' ', a.prenom, a.nom), a.matricule, em.nb_renouvellements "
                + "FROM emprunt em JOIN adherent a ON a.id = em.adherent_id "
                + "WHERE em.exemplaire_id = ? AND em.date_retour IS NULL FOR UPDATE", exId);
        if (em == null) {
            throw new RegleException("Cet exemplaire n'est pas en prêt (statut actuel : " + statut + ").");
        }
        return em;
    }
}
