-- =====================================================================
--  GesBiblio 2 - Schéma de la base de données "gesbiblio"
--  Compatible MySQL 5.6+ / 8.x et MariaDB 10.x
--
--  Import :  mysql -u root -p < database/schema.sql
--  Puis (facultatif) reprise des anciennes données de la base "biblio" :
--            mysql -u root -p < database/migration_depuis_biblio.sql
--
--  Compte créé par défaut :  admin / admin  (changement exigé à la 1re connexion)
-- =====================================================================

SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS gesbiblio CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE gesbiblio;

-- ---------------------------------------------------------------------
--  Paramètres du règlement (modifiables dans l'onglet Administration)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS parametre (
  cle         VARCHAR(50)  NOT NULL PRIMARY KEY,
  valeur      VARCHAR(100) NOT NULL,
  description VARCHAR(255)
) ENGINE=InnoDB;

INSERT IGNORE INTO parametre (cle, valeur, description) VALUES
 ('penalite_par_jour',          '50',   'Montant de la pénalité par jour de retard'),
 ('devise',                     'FCFA', 'Devise des pénalités'),
 ('forfait_perte',              '5000', 'Montant facturé pour un document perdu'),
 ('seuil_blocage_penalites',    '1000', 'Au-delà de ce montant impayé, l''adhérent ne peut plus emprunter'),
 ('delai_retrait_reservation',  '3',    'Nombre de jours pour retirer un document réservé'),
 ('max_reservations',           '3',    'Nombre maximal de réservations actives par adhérent');

-- ---------------------------------------------------------------------
--  Catégories d'adhérents : chaque catégorie a ses propres droits de prêt
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categorie_adherent (
  code                VARCHAR(20)  NOT NULL PRIMARY KEY,
  libelle             VARCHAR(60)  NOT NULL,
  max_emprunts        INT          NOT NULL,
  duree_pret_jours    INT          NOT NULL,
  max_renouvellements INT          NOT NULL
) ENGINE=InnoDB;

INSERT IGNORE INTO categorie_adherent VALUES
 ('ETUDIANT',   'Étudiant / élève',     3, 14, 1),
 ('ENSEIGNANT', 'Enseignant',           6, 30, 2),
 ('PERSONNEL',  'Personnel',            4, 21, 1),
 ('EXTERNE',    'Lecteur externe',      2, 14, 0);

-- ---------------------------------------------------------------------
--  Adhérents (lecteurs)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS adherent (
  id               INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  matricule        VARCHAR(30)  NOT NULL UNIQUE,
  nom              VARCHAR(80)  NOT NULL,
  prenom           VARCHAR(80),
  email            VARCHAR(120),
  telephone        VARCHAR(30),
  categorie_code   VARCHAR(20)  NOT NULL DEFAULT 'ETUDIANT',
  statut           VARCHAR(15)  NOT NULL DEFAULT 'ACTIF',      -- ACTIF / SUSPENDU
  date_inscription DATE         NOT NULL,
  date_expiration  DATE         NULL,                          -- fin de validité de la carte
  CONSTRAINT fk_adherent_categorie FOREIGN KEY (categorie_code) REFERENCES categorie_adherent(code)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  Catalogue : un OUVRAGE (titre) possède plusieurs EXEMPLAIRES (copies)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categorie (
  id      INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  libelle VARCHAR(80) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT IGNORE INTO categorie (libelle) VALUES
 ('Roman'), ('Théâtre'), ('Poésie'), ('Essai'), ('Sciences'), ('Informatique'),
 ('Histoire'), ('Droit'), ('Économie'), ('Jeunesse'), ('Mémoire / Thèse'), ('Revue');

CREATE TABLE IF NOT EXISTS ouvrage (
  id           INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  isbn         VARCHAR(20)  NULL UNIQUE,
  titre        VARCHAR(255) NOT NULL,
  auteur       VARCHAR(150),
  editeur      VARCHAR(150),
  annee        INT,
  categorie_id INT          NULL,
  CONSTRAINT fk_ouvrage_categorie FOREIGN KEY (categorie_id) REFERENCES categorie(id),
  INDEX idx_ouvrage_titre (titre(100)),
  INDEX idx_ouvrage_auteur (auteur(100))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS exemplaire (
  id               INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  ouvrage_id       INT          NOT NULL,
  code_barres      VARCHAR(50)  NOT NULL UNIQUE,
  -- DISPONIBLE / EMPRUNTE / RESERVE (mis de côté) / EN_REPARATION / PERDU / RETIRE
  statut           VARCHAR(15)  NOT NULL DEFAULT 'DISPONIBLE',
  etat             VARCHAR(15)  NOT NULL DEFAULT 'BON',         -- NEUF / BON / USE / ENDOMMAGE
  rayon            VARCHAR(50),
  date_acquisition DATE,
  CONSTRAINT fk_exemplaire_ouvrage FOREIGN KEY (ouvrage_id) REFERENCES ouvrage(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  Personnel (comptes de connexion) et rôles
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS personnel (
  id                INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  login             VARCHAR(50) NOT NULL UNIQUE,
  nom               VARCHAR(100) NOT NULL,
  role              VARCHAR(20) NOT NULL,                       -- ADMIN / BIBLIOTHECAIRE / AGENT
  sel               VARCHAR(32) NOT NULL,
  mot_de_passe_hash CHAR(64)    NOT NULL,                       -- SHA-256(sel + mot de passe)
  actif             TINYINT(1)  NOT NULL DEFAULT 1,
  doit_changer_mdp  TINYINT(1)  NOT NULL DEFAULT 1
) ENGINE=InnoDB;

INSERT IGNORE INTO personnel (login, nom, role, sel, mot_de_passe_hash, actif, doit_changer_mdp)
VALUES ('admin', 'Administrateur', 'ADMIN', 'gesbiblio-init', SHA2(CONCAT('gesbiblio-init', 'admin'), 256), 1, 1);

-- ---------------------------------------------------------------------
--  Transactions : emprunts, réservations, pénalités
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS emprunt (
  id                 INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  exemplaire_id      INT         NOT NULL,
  adherent_id        INT         NOT NULL,
  personnel_id       INT         NULL,                          -- agent qui a enregistré le prêt
  date_emprunt       DATE        NOT NULL,
  date_echeance      DATE        NOT NULL,
  date_retour        DATE        NULL,                          -- NULL = emprunt en cours
  nb_renouvellements INT         NOT NULL DEFAULT 0,
  etat_retour        VARCHAR(15) NULL,                          -- BON / USE / ENDOMMAGE / PERDU
  CONSTRAINT fk_emprunt_exemplaire FOREIGN KEY (exemplaire_id) REFERENCES exemplaire(id),
  CONSTRAINT fk_emprunt_adherent   FOREIGN KEY (adherent_id)   REFERENCES adherent(id),
  CONSTRAINT fk_emprunt_personnel  FOREIGN KEY (personnel_id)  REFERENCES personnel(id),
  INDEX idx_emprunt_adherent (adherent_id, date_retour),
  INDEX idx_emprunt_exemplaire (exemplaire_id, date_retour)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reservation (
  id                  INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
  ouvrage_id          INT         NOT NULL,
  adherent_id         INT         NOT NULL,
  date_reservation    DATETIME    NOT NULL,
  -- EN_ATTENTE (file d'attente) / A_RETIRER (exemplaire mis de côté) / HONOREE / ANNULEE / EXPIREE
  statut              VARCHAR(15) NOT NULL DEFAULT 'EN_ATTENTE',
  exemplaire_id       INT         NULL,
  date_limite_retrait DATE        NULL,
  CONSTRAINT fk_reservation_ouvrage    FOREIGN KEY (ouvrage_id)    REFERENCES ouvrage(id),
  CONSTRAINT fk_reservation_adherent   FOREIGN KEY (adherent_id)   REFERENCES adherent(id),
  CONSTRAINT fk_reservation_exemplaire FOREIGN KEY (exemplaire_id) REFERENCES exemplaire(id),
  INDEX idx_reservation_file (ouvrage_id, statut, date_reservation)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS penalite (
  id            INT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
  adherent_id   INT           NOT NULL,
  emprunt_id    INT           NULL,
  montant       DECIMAL(10,2) NOT NULL,
  motif         VARCHAR(255)  NOT NULL,
  date_creation DATE          NOT NULL,
  statut        VARCHAR(15)   NOT NULL DEFAULT 'IMPAYEE',       -- IMPAYEE / PAYEE / REMISE
  date_reglement DATE         NULL,
  CONSTRAINT fk_penalite_adherent FOREIGN KEY (adherent_id) REFERENCES adherent(id),
  CONSTRAINT fk_penalite_emprunt  FOREIGN KEY (emprunt_id)  REFERENCES emprunt(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  Journal d'audit : qui a fait quoi et quand
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS journal_audit (
  id         INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  date_heure DATETIME     NOT NULL,
  login      VARCHAR(50),
  action     VARCHAR(50)  NOT NULL,
  details    VARCHAR(500)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
--  Vues utiles pour les rapports
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_emprunts_en_cours AS
SELECT em.id AS emprunt_id, a.matricule, CONCAT(a.nom, ' ', IFNULL(a.prenom, '')) AS adherent,
       o.titre, ex.code_barres, em.date_emprunt, em.date_echeance,
       GREATEST(DATEDIFF(CURDATE(), em.date_echeance), 0) AS jours_retard
FROM emprunt em
JOIN adherent a    ON a.id = em.adherent_id
JOIN exemplaire ex ON ex.id = em.exemplaire_id
JOIN ouvrage o     ON o.id = ex.ouvrage_id
WHERE em.date_retour IS NULL;
