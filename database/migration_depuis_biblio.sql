-- =====================================================================
--  Reprise des données de l'ancienne base "biblio" vers "gesbiblio"
--  À exécuter APRÈS database/schema.sql :
--      mysql -u root -p < database/migration_depuis_biblio.sql
--  Le script peut être relancé sans créer de doublons.
--
--  Correspondances :
--    biblio.utilisateur  ->  gesbiblio.adherent   (matricule U0001, U0002, ...)
--    biblio.livre        ->  gesbiblio.ouvrage    (même id)
--                        +   gesbiblio.exemplaire (1 copie par livre, code EX000001, ...)
--    livre.disponible='NON' -> un emprunt en cours (date d'emprunt inconnue :
--                              on prend la date de la migration)
-- =====================================================================
USE gesbiblio;
SET NAMES utf8mb4;

INSERT IGNORE INTO adherent (id, matricule, nom, prenom, categorie_code, statut, date_inscription)
SELECT u.idutilisateur,
       CONCAT('U', LPAD(u.idutilisateur, 4, '0')),
       -- "arphan bodian" -> prénom "arphan", nom "bodian"
       CASE WHEN LOCATE(' ', TRIM(u.nomUtilisateur)) > 0
            THEN TRIM(SUBSTRING(TRIM(u.nomUtilisateur), LOCATE(' ', TRIM(u.nomUtilisateur)) + 1))
            ELSE TRIM(u.nomUtilisateur) END,
       CASE WHEN LOCATE(' ', TRIM(u.nomUtilisateur)) > 0
            THEN SUBSTRING_INDEX(TRIM(u.nomUtilisateur), ' ', 1)
            ELSE NULL END,
       'ETUDIANT', 'ACTIF', CURDATE()
FROM biblio.utilisateur u;

INSERT IGNORE INTO ouvrage (id, titre, auteur)
SELECT l.idlivre, l.titre, l.autheur FROM biblio.livre l;

INSERT IGNORE INTO exemplaire (id, ouvrage_id, code_barres, statut, etat, date_acquisition)
SELECT l.idlivre, l.idlivre, CONCAT('EX', LPAD(l.idlivre, 6, '0')),
       CASE WHEN l.disponible = 'NON' AND l.idutilisateur IS NOT NULL THEN 'EMPRUNTE' ELSE 'DISPONIBLE' END,
       'BON', CURDATE()
FROM biblio.livre l;

INSERT INTO emprunt (exemplaire_id, adherent_id, date_emprunt, date_echeance)
SELECT l.idlivre, l.idutilisateur, CURDATE(),
       DATE_ADD(CURDATE(), INTERVAL (SELECT duree_pret_jours FROM categorie_adherent WHERE code = 'ETUDIANT') DAY)
FROM biblio.livre l
WHERE l.disponible = 'NON' AND l.idutilisateur IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM emprunt e WHERE e.exemplaire_id = l.idlivre AND e.date_retour IS NULL);

INSERT INTO journal_audit (date_heure, login, action, details)
VALUES (NOW(), 'migration', 'MIGRATION', 'Reprise des données de la base biblio');
