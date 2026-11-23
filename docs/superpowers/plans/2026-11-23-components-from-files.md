# Composants d'après les fichiers — plan

**Spec :** `docs/superpowers/specs/2026-11-23-components-from-files-design.md`. Exécution inline par Claude, TDD.

1. **`domain/ComponentMap`** — `static ComponentMap parse(String json)` (JSON illisible → `EMPTY`),
   `boolean isEmpty()`, `List<String> tagsOf(Collection<String> files)` (ordre de la carte, dossier le plus long).
   Tests : `ComponentMapTest` (fichier dans un dossier, hors dossier, dossier imbriqué, préfixe sans `/`, ordre,
   JSON illisible, fixture réelle `fixtures/forgejo/components.json`).
2. **Port forge** — `List<String> listPullFiles(owner, repo, number)` (toutes les pages) et
   `Optional<String> readFile(owner, repo, ref, path)` (404 → vide) ; `ForgejoClient`, `ForgejoJson.toFileNames`
   (fixture réelle `pull-files.json`), `FakeForgeApi` (`pullFiles`, `files`).
3. **`PullView`** — composant `fileTags` (+ constructeur à 10 arguments, `fileTags` vide), `componentTags()` →
   `List<ComponentTag(tag, inTitle, inFiles)>`, `components()` = union, `withSonarGate(gate)` ; `SonarLoop` et
   `PullsLoop.SonarCarry` l'utilisent. Tests : `PullViewTest`.
4. **`PullsLoop`** — carte par dépôt (relue quand le dernier commit change), fichiers par PR (relus quand la tête
   change, échec → titre seul, cache purgé des PR fermées). Tests : `PullsLoopIntegrationTest`.
5. **UI** — `Badges.tag(ComponentTag, Messages)`, clés `pull.component.title|files|both` dans les 7 langues,
   `PullsPanel` et `ProjectsPanel`. Tests : `BadgesTest`, filtre de `PullsModelTest`.
6. Suite complète, revue indépendante, README si besoin.
