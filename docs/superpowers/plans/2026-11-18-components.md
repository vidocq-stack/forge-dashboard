# Composants des PR et commits importés — plan

> Exécuté par Mistral Vibe, une tâche par session, piloté par Claude Code. TDD : chaque test est écrit et lancé
> (rouge) avant le code, puis relancé (vert).

**Objectif** : composants des PR lus dans les tags du titre (repli : le dépôt), affichés en pastilles avec un titre
court, filtre « Composant » dans l'onglet PR ; un commit déjà compté sous un autre identifiant n'est pas recompté.

**Spec** : `docs/superpowers/specs/2026-11-18-components-design.md` (fait foi).

## Contraintes globales

- Conventions du projet : `CLAUDE.md` (Java 25, JPMS strict, TDD, pas de dépendance ajoutée, pas de réflexion).
- Code, noms et javadoc en anglais, dans le style des fichiers voisins (javadoc courte qui dit le pourquoi).
- Textes de l'interface dans les 7 fichiers `forge-dashboard-app/src/main/resources/i18n/messages*.properties`
  (`messages.properties` = anglais, `_de`, `_es`, `_fr`, `_it`, `_nl`, `_pl`), mêmes clés partout.
- Classes CSS : constantes de `ui/Theme` (listées dans `CLASSES`), chacune avec sa règle dans
  `forge-dashboard-app/src/main/resources/META-INF/resources/styles/dashboard.css` (`ThemeTest` le vérifie).
- Test d'une classe (depuis la racine du dépôt) :
  `./mvnw -ntp -o -pl forge-dashboard-app clean verify -Dtest=<Classe> -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=NONE -Dfailsafe.failIfNoSpecifiedTests=false`
  (Docker requis pour les `*IntegrationTest` : PostgreSQL des dev services ; il tourne déjà.)
- Interdit : `git commit`, `git push`, toute commande git qui modifie l'état ; lire hors du dépôt (rien sous
  `~/.config`) ; modifier `CLAUDE.md`, le `pom.xml`, les migrations Flyway.

## Revue (cas à ne pas casser)

1. Un commit relu sous son propre identifiant (même dépôt, même SHA) est toujours mis à jour.
2. Deux commits différents à la même seconde du même auteur (messages différents) comptent tous les deux.
3. Titre `null` : pas de tag, composant = dépôt, titre court vide.
4. `fix: handle [FOY] header` n'a pas de tag (tag seulement en tête).
5. Le filtre ne recharge pas la grille quand rien ne change (focus clavier, cf. `PullsPanel.refresh`).

---

### Tâche 1 : un commit importé ne compte qu'une fois

**Fichiers** : `store/CommitStatRepository.java`, `collect/CommitsLoop.java`,
test `collect/CommitsLoopIntegrationTest.java` (paquetage `io.vidocq.tools.dashboard`).

- [ ] Tests (dans `CommitsLoopIntegrationTest`, avec ses fixtures existantes) :
  - `aCommitImportedFromAnotherRepositoryCountsOnce` : un commit `a1` du dépôt A est stocké par une première passe ;
    le dépôt B (même org) a ensuite dans son historique un commit `b1` de même e-mail d'auteur, même date d'auteur,
    même message. Après la passe de B : `commits.findById(CommitStat.idOf(org, "B", "b1"))` est vide,
    `idOf(org, "A", "a1")` est présent, et le curseur `last_commit_at` de B vaut la date de `b1`.
  - `aCommitOfTheSameSecondWithAnotherMessageCounts` : même auteur, même date, message différent → stocké.
- [ ] Lancer : rouge.
- [ ] `CommitStatRepository` : `List<CommitStat> findByAuthoredAt(Instant authoredAt);`
- [ ] `CommitsLoop.read` : avant `stat(...)` (qui peut appeler la forge), sauter le commit si
  `countedElsewhere(org, repo, c)` : un `CommitStat` de même `authoredAt`, même `authorEmail`, même `message`
  (`Objects.equals`) et d'`id` différent de `CommitStat.idOf(org, repo, c.sha())`. Un commit sauté ne compte pas dans
  `fresh` mais fait avancer `newest`. Javadoc : pourquoi (import d'un dépôt dans un autre, `filter-repo`).
- [ ] Lancer `CommitsLoopIntegrationTest` : vert.

### Tâche 2 : tags, composants et titre court d'une PR

**Fichiers** : `live/PullView.java`, test `live/PullViewTest.java`.

- [ ] Tests :
  - `tagsFollowTheWipMarker` : repo `vidocq`, `[WIP] [NOETHER] fix(persistence): flush (#123)` → `tags()` =
    `[NOETHER]`, `components()` = `[noether]`, `shortTitle()` = `fix(persistence): flush (#123)`, `draft()` vrai.
  - `severalTagsWithOrWithoutSpaces` : `[VAUBAN][CASSINI] refactor(spi): rename` et `[VAUBAN] [CASSINI] refactor(spi): rename`
    → `[VAUBAN, CASSINI]`, composants `[vauban, cassini]`, titre court `refactor(spi): rename`.
  - `withoutTagTheComponentIsTheRepository` : repo `vauban`, `feat: proxies` → `[]`, `[vauban]`, `feat: proxies`.
  - `theWipMarkerLeavesTheShortTitle` : `WIP: build(mcp): x` → `build(mcp): x` ; `WIP : docs` → `docs` ;
    `WIP docs: y` → `docs: y` ; `[WIP] h3` → `h3`.
  - `onlyALeadingTagIsAComponent` : `fix: handle [FOY] header` → pas de tag ; `[foy] x` → pas de tag (majuscules).
  - `nothingLeftKeepsTheWholeTitle` : `[WIP]` → titre court `[WIP]` ; `[RUNTIME]` → tags `[RUNTIME]`, titre court
    `[RUNTIME]`.
  - `aPullWithoutTitle` : titre `null` → `[]`, `[<repo>]`, `""`.
- [ ] Lancer `PullViewTest` : rouge.
- [ ] `PullView` : `List<String> tags()`, `List<String> components()`, `String shortTitle()`. Un tag :
  `\[[A-Z][A-Z0-9_-]*]`, `[WIP]` exclu ; le marqueur WIP en tête est celui que reconnaît déjà `draft()`.
  Composants : tags en minuscules (`Locale.ROOT`), sinon `List.of(repo)`.
- [ ] Lancer `PullViewTest` : vert.

### Tâche 3 : pastilles de tags et titre court

**Fichiers** : `ui/Theme.java`, `styles/dashboard.css`, `ui/Badges.java`, `ui/Parts.java`, `ui/PullsPanel.java`,
`ui/ProjectsPanel.java`, les 7 `messages*.properties` ; tests `ui/ThemeTest.java`, `ui/BadgesTest.java`,
`ui/PullsPanelIntegrationTest.java`, `ui/ProjectsPanelIntegrationTest.java`.

- [ ] Tests :
  - `BadgesTest.aTagIsANeutralPill` : `Badges.tag("NOETHER", messages)` → texte `NOETHER`, classes `Theme.PILL` et
    `Theme.PILL_TAG`, attribut `title` = texte de `pull.component` avec `noether`.
  - `ThemeTest` : la pastille de tag est lisible (même contrôle de contraste que les autres pastilles,
    `pillsAreReadable`).
  - `PullsPanelIntegrationTest.aTaggedPullShowsItsTagsAndItsShortTitle` : PR de titre
    `[WIP] [NOETHER][VAUBAN] fix(persistence): flush` → dans la cellule PR, deux pastilles `PILL_TAG` `NOETHER`,
    `VAUBAN`, puis le bouton titre `#<n> fix(persistence): flush` dont l'attribut `title` est
    `#<n> [WIP] [NOETHER][VAUBAN] fix(persistence): flush`, puis la pastille WIP. Une PR sans tag n'a aucune
    pastille de tag.
  - `ProjectsPanelIntegrationTest.anUnfoldedTaggedPullShowsItsTags` : même chose dans la liste dépliée d'un projet.
- [ ] Lancer : rouge.
- [ ] `Theme.PILL_TAG = "dash-pill-tag"` (dans `CLASSES`) ; CSS : fond `#eef1f5`, texte `#3a4250`, bordure
  `#c3c9d2` pleine.
- [ ] `Badges.tag(String tag, Messages m)` ; clé `pull.component` : en `Component: {0}`, de `Komponente: {0}`,
  es `Componente: {0}`, fr `Composant : {0}`, it `Componente: {0}`, nl `Component: {0}`, pl `Komponent: {0}`.
- [ ] `Parts.title(String text, String tooltip, Runnable onClick)` (l'actuelle délègue avec `tooltip = text`).
- [ ] `PullsPanel.pull` et `ProjectsPanel.pullLine` : pastilles de tags avant le titre court, info-bulle = titre
  entier ; la ligne passe à la ligne si la place manque (`flex-wrap: wrap`), les pastilles gardent leur taille.
- [ ] Lancer les quatre classes de test : vert.

### Tâche 4 : filtre « Composant » de l'onglet PR

**Fichiers** : `ui/model/PullsModel.java`, `ui/PullsPanel.java`, les 7 `messages*.properties` ; tests
`ui/model/PullsModelTest.java`, `ui/PullsPanelIntegrationTest.java`.

- [ ] Tests :
  - `PullsModelTest.componentsOfTheOpenPulls` : PR `vauban#7` sans tag, `vidocq#3` `[VAUBAN][CASSINI] x`,
    `foy#1` sans tag → `PullsModel.components(rows)` = `[cassini, foy, vauban]` (triés, sans doublon).
  - `PullsModelTest.aComponentKeepsItsPulls` : `PullsModel.of(rows, "vauban")` = `vauban#7` et `vidocq#3`, dans
    l'ordre de `rows` ; `of(rows, null)` = `rows`.
  - `PullsPanelIntegrationTest.theComponentFilterKeepsItsPulls` : choisir `vauban` → la grille n'a que ses PR ;
    effacer → toutes.
  - `PullsPanelIntegrationTest.aComponentWithoutOpenPullClearsTheFilter` : filtre sur un composant dont la seule PR
    se ferme → après `refresh()`, filtre vide et toutes les PR affichées.
- [ ] Lancer : rouge.
- [ ] `PullsModel.components(List<PullRow>)`, `PullsModel.of(List<PullRow>, String component)`.
- [ ] `PullsPanel` : `ComboBox<String>` au-dessus de la grille (libellé `pulls.filter.component`, texte d'attente
  `pulls.filter.all`, bouton d'effacement). Ses éléments ne sont remis que si la liste des composants change ; un
  changement de valeur relance `refresh()`. `refresh()` filtre les lignes avant la comparaison avec `shown`.
  Clés : `pulls.filter.component` en `Component`, de `Komponente`, es `Componente`, fr `Composant`, it `Componente`,
  nl `Component`, pl `Komponent` ; `pulls.filter.all` en `All components`, de `Alle Komponenten`, es
  `Todos los componentes`, fr `Tous les composants`, it `Tutti i componenti`, nl `Alle componenten`, pl
  `Wszystkie komponenty`.
- [ ] Lancer `PullsModelTest` et `PullsPanelIntegrationTest` : vert.

### Tâche 5 : README

- [ ] Section de l'onglet PR : tags de composant en pastilles, titre court, filtre « Composant » (sans tag : le
  dépôt). Section Commiters : un commit importé d'un autre dépôt (même auteur, date, message) ne compte qu'une fois.
