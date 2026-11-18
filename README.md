# library_java — GesBiblio 2

Application de bureau de **gestion de bibliothèque** en Java (Swing) avec une base **MySQL**.

La version 2 remplace la logique d'origine, trop limitée pour une vraie bibliothèque
(un livre = une seule ligne `OUI`/`NON`, pas de dates, pas d'historique, aucune règle de prêt),
par un fonctionnement proche des logiciels de bibliothèque : **ouvrages et exemplaires**,
**historique des prêts avec échéances**, **règles de prêt par catégorie de lecteur**,
**réservations avec file d'attente**, **pénalités**, **comptes du personnel par rôle** et **journal d'audit**.

L'ancienne application (`adminis.Emprunt`, base `biblio`) est conservée telle quelle à côté.

## Démarrage rapide (Windows)

1. Démarrer MySQL (XAMPP, WAMP ou service MySQL).
2. Créer la nouvelle base, puis reprendre les anciennes données :

   ```bat
   mysql -u root -p < database\schema.sql
   mysql -u root -p < database\migration_depuis_biblio.sql
   ```

   La migration est facultative (elle suppose que l'ancienne base `biblio` existe) et peut être relancée sans doublons.

3. Double-cliquer sur **`lancer.bat`** (compile puis lance), ou :

   ```bat
   javac -encoding UTF-8 -cp mysql-connector-java-5.1.41-bin.jar -d bin src\gesbiblio\db\*.java src\gesbiblio\metier\*.java src\gesbiblio\ui\*.java
   java -cp "bin;mysql-connector-java-5.1.41-bin.jar" gesbiblio.ui.App
   ```

4. Se connecter avec **admin / admin** : un nouveau mot de passe est demandé à la première connexion.

Les identifiants MySQL ne sont plus dans le code : ils sont lus dans `config/db.properties`,
créé automatiquement au premier lancement (par défaut `root` / `passer`). Modifiez ce fichier si besoin.

## Logique métier

### Les données

| Notion | Table | Rôle |
|---|---|---|
| Ouvrage | `ouvrage` | Le titre : ISBN, titre, auteur, éditeur, année, catégorie |
| Exemplaire | `exemplaire` | Une copie physique : code-barres, statut, état, rayon. Un ouvrage a N exemplaires |
| Adhérent | `adherent` | Le lecteur : matricule (n° de carte), catégorie, statut ACTIF/SUSPENDU, date d'expiration de la carte |
| Catégorie d'adhérent | `categorie_adherent` | Droits de prêt : nombre max de prêts, durée, renouvellements |
| Emprunt | `emprunt` | Un prêt daté : emprunt, échéance, retour, état au retour. **Gardé à vie** = historique |
| Réservation | `reservation` | File d'attente par titre |
| Pénalité | `penalite` | Retard ou perte : IMPAYEE / PAYEE / REMISE |
| Personnel | `personnel` | Comptes de connexion (mot de passe haché SHA-256 + sel) |
| Paramètres | `parametre` | Règlement : pénalité/jour, devise, forfait perte, seuil de blocage, délais |
| Journal | `journal_audit` | Qui a fait quoi, quand |

Statuts d'un exemplaire : `DISPONIBLE` → `EMPRUNTE` → (retour) → `DISPONIBLE`, ou `RESERVE` (mis de côté pour un lecteur),
`EN_REPARATION` (rendu endommagé), `PERDU`, `RETIRE` (désherbé).

### Emprunt — un prêt est refusé si :

- le compte est suspendu ou la carte a expiré ;
- le lecteur a atteint le nombre de prêts de sa catégorie (Étudiant 3, Enseignant 6, Personnel 4, Externe 2 par défaut) ;
- il a un document en retard ;
- ses pénalités impayées atteignent le seuil de blocage ;
- l'exemplaire n'est pas disponible, ou est mis de côté pour un autre lecteur ;
- il a déjà un exemplaire du même titre.

Sinon le prêt est créé avec l'échéance de sa catégorie (14 / 30 / 21 / 14 jours) **et** l'exemplaire passe à `EMPRUNTE`,
dans une seule transaction SQL.

### Retour

Date de retour enregistrée, retard calculé, pénalité créée (`retard × penalite_par_jour`).
Exemplaire endommagé → `EN_REPARATION`. Sinon, si des lecteurs attendent ce titre, l'exemplaire est **mis de côté**
pour le premier de la file (avec un délai de retrait) ; sinon il redevient `DISPONIBLE`.

### Renouvellement

Autorisé si le compte est en règle, si le prêt n'est pas en retard, si le quota de renouvellements
de la catégorie n'est pas atteint et si personne n'attend ce titre. La nouvelle échéance = échéance actuelle
+ durée de prêt de la catégorie. Le guichet affiche cette règle avant de valider (bouton « Prolonger » grisé
avec la raison en info-bulle quand c'est impossible).

### Réservation

Possible seulement si aucun exemplaire n'est en rayon. File « premier arrivé, premier servi ».
Une réservation non retirée dans le délai expire (au démarrage de l'application ou via le bouton dédié)
et l'exemplaire passe au lecteur suivant.

### Suppression

Un ouvrage, un exemplaire ou un adhérent qui a un historique n'est jamais supprimé : on passe l'exemplaire en `RETIRE`
ou on suspend l'adhérent, pour garder les statistiques et la traçabilité.

## Rôles

| Rôle | Accès |
|---|---|
| AGENT | Prêts, retours, renouvellements, pertes, réservations, encaissement des pénalités, consultation |
| BIBLIOTHECAIRE | + catalogue (ouvrages, exemplaires), adhérents, statistiques |
| ADMIN | + comptes du personnel, règlement de prêt, journal d'audit, remise de pénalités |

## Écrans

- **Prêts / Retours** : guichet avec auto-complétion.
  - *Emprunt* : l'adhérent se cherche par nom, prénom, matricule ou téléphone (sa situation s'affiche en vert ou rouge) ;
    le livre se cherche par titre, auteur, ISBN ou code-barres parmi les **seuls titres disponibles**
    (ou mis de côté pour ce lecteur), avec le nombre d'exemplaires ; l'exemplaire est choisi automatiquement
    et la **date de retour est calculée** selon la catégorie du lecteur.
  - *Retour / prolongation* : le prêt se cherche par titre, code-barres ou emprunteur ; un aperçu indique
    le retard, la pénalité, si la prolongation est possible (et la nouvelle date) et si une réservation attend le livre.
  - Flèches pour parcourir les suggestions, Entrée pour choisir ; une douchette code-barres fonctionne aussi.
- **Catalogue** : recherche par titre, auteur, ISBN ou code-barres ; exemplaires de chaque titre et où ils se trouvent.
- **Adhérents** : inscription, suspension, historique complet (prêts, pénalités, réservations).
- **Réservations** : files d'attente, exemplaires à remettre.
- **Retards & pénalités** : liste de relance, encaissement, remise.
- **Statistiques** : indicateurs, titres et lecteurs les plus actifs, prêts par mois, inventaire.
- **Administration** : comptes, paramètres du règlement, droits par catégorie, journal.

## Structure

```
library_java-main/
├── database/
│   ├── schema.sql                    # base gesbiblio (tables, règlement par défaut, compte admin)
│   └── migration_depuis_biblio.sql   # reprise des données de l'ancienne base biblio
├── config/db.properties              # identifiants MySQL (créé au 1er lancement, non versionné)
├── src/
│   ├── gesbiblio/db/                 # connexion et utilitaires JDBC
│   ├── gesbiblio/metier/             # règles de gestion (PretService, ReservationService, ...)
│   ├── gesbiblio/ui/                 # interface Swing (App = point d'entrée)
│   ├── adminis/, conn/               # ancienne application (inchangée)
├── lancer.bat
└── mysql-connector-java-5.1.41-bin.jar
```

## Maven

```bash
mvn package
java -jar target/gesbiblio-1.0.0.jar
```

## Remarques

- Avec **MySQL 8**, le pilote 5.1.41 peut échouer à l'authentification : utilisez un pilote
  `mysql-connector-j` 8.x et ajoutez `&allowPublicKeyRetrieval=true` à `db.url` si nécessaire.
- Toutes les requêtes utilisent des `PreparedStatement` (protection contre l'injection SQL).
- L'ancienne application reste lançable : `java -cp "bin;mysql-connector-java-5.1.41-bin.jar" adminis.Emprunt`.
