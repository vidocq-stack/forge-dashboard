# forge-dashboard v2 — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** refaire forge-dashboard en Vaadin Flow sur Vidocq 0.4.0-SNAPSHOT. Il suit les orgs CodeFloe (projets,
`main`, Sonar, PR), les runners Actions (activité, occupation, lien runner↔PR) et les stats par commiter, avec
une connexion OIDC CodeFloe.

**Architecture :** un module JPMS unique `io.vidocq.tools.dashboard`, découpé en paquets
(`domain`, `forge`, `sonar`, `store`, `collect`, `live`, `auth`, `ui`).
- Des boucles de collecte planifiées interrogent CodeFloe et Sonar à travers des ports Java. Elles
  persistent dans PostgreSQL (Mansart Data + JDBC pour les agrégats, Flyway) et mettent à jour un état
  courant en mémoire.
- Un bus de changements pousse les mises à jour vers les UI Vaadin ouvertes (`@Push`).

**Tech Stack :** Java 25 (JPMS), Maven 3.10.0, Vidocq runtime 0.4.0-SNAPSHOT (Vauban CDI Lite, Foy Servlet,
Cyrano Rest Client, Ravel Config, Cervantes JWT, Mansart Data/Pool/Transactions, Flyway), Vaadin 25.3.2,
PostgreSQL, JUnit 6.0.3.

**Spec :** `docs/superpowers/specs/2026-11-14-forge-dashboard-v2-design.md`. Lire la spec avant chaque tâche :
elle porte les raisons. Ce plan porte les noms, signatures et tests.

## Global Constraints

- `.sdkmanrc` : `java=25-tem`, `maven=3.10.0`. Wrapper `./mvnw` en 3.10.0. `modelVersion 4.0.0`.
- `vidocq.version=0.4.0-SNAPSHOT` ; BOM `io.vidocq.runtime:vidocq-runtime-bom:${vidocq.version}` (import) ;
  `org.junit:junit-bom:6.0.3` (import) ; `com.vaadin:vaadin-bom:25.3.2` (import).
- Dépôts (dans `<repositories>` ET `<pluginRepositories>`) : `vidocq-snapshots` =
  `https://maven.vidocq.dev/snapshots/`, `vidocq-snapshots-bak` = `https://maven.bak.vidocq.dev/snapshots/`
  (releases off, snapshots on). Pas de Sonatype.
- `vidocq-runtime-maven-plugin` `${vidocq.version}` avec `<extensions>true</extensions>`, **dans le module
  applicatif uniquement**. Ne pas fixer `vidocq.app.layer`, `all` est le défaut.
- Mode production Vaadin : `-Dvaadin.productionMode=true` dans `vidocq.jvmArgs`, `vidocq.run.jvmArgs` et
  `vidocq.dev.jvmArgs`. `vaadin-prod-bundle` (sans Node). `build-frontend` lié à `process-classes`.
- Versions figées : `org.slf4j:slf4j-jdk14:2.0.17`, `org.postgresql:postgresql:42.7.10` (scope runtime).
- Push : `@Push(transport = Transport.WEBSOCKET_XHR)`. Ne jamais forcer `LONG_POLLING`.
- Aucun bean `@RequestScoped`/`@SessionScoped` atteignable depuis le push (`ui.access`, écouteurs du bus) :
  services `@ApplicationScoped`, vues `@Dependent`, identité dans `VaadinSession`.
- Aucun jeton (service, id_token, client_secret) dans le navigateur, les logs, le dépôt ou les fixtures.
- Lecture CodeFloe : 4 appels simultanés max ; `429` et `Retry-After` respectés.
- Tests : patchés dans le module applicatif, donc **pas de `java.net.http` dans les tests**. Les ports ont des
  faux en mémoire ; la persistance est testée sur PostgreSQL via les dev services (Docker/OrbStack requis).
- **Bug d'un SNAPSHOT Vidocq bloquant** :
  1. avertir la session `VIDOCQ` (SendMessage) avec une reproduction minimale ;
  2. si le correctif est rapide, attendre en avançant ailleurs ;
  3. sinon, consigner dans le `BUG.md` du repo concerné, contourner avec `// WORKAROUND <id>` et ajouter
     une ligne dans le README, section « Contournements ».
- Constats push/WebSocket : à envoyer aux sessions `FOY` et `VAADIN`.
- Commits : messages en anglais (conventional commits), terminés par :
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01DQoEaJqYmPngSspMgfXBAc
  ```
- Branche `feat/v2-vaadin`. Pas de push sans demande de l'utilisateur.

## Review Focus

1. **CodeFloe ou Sonar indisponible, lent ou en 429.** L'UI garde les dernières données et l'indicateur de
   fraîcheur passe en « périmé » : jamais d'écran vide. Test : Task 6
   (`liveStateKeepsSnapshotWhenNoUpdate`) et Task 10 (`failingTickKeepsStateAndMarksStale`).
2. **Log d'un job pas encore disponible** (404 ou vide juste après le démarrage). Le runner reste inconnu
   (`null`) et un nouvel essai a lieu au tick suivant, sans exception. Test : Task 7
   (`missingLogHeadIsRetriedNextTick`).
3. **Redémarrage du dashboard pendant que des jobs tournent.** Les `job_execution` ouverts mais absents des
   jobs actifs sont fermés avec leur statut final au premier tick : pas d'occupation infinie. Test : Task 7
   (`openExecutionsAreReconciledAfterRestart`).
4. **Callback OIDC avec un `state` périmé** (session expirée, redéploiement) **ou utilisateur hors groupes.**
   Une seule relance de connexion ou une page 403 claire, jamais de boucle de redirection. Test : Task 11
   (`missingLoginStateRestartsOnce`, `userOutsideGroupsIsDenied`).
5. **Repo vide, sans branche par défaut ou supprimé entre deux ticks** (404/409). Il est ignoré et les autres
   repos sont mis à jour. Test : Task 8 (`notFoundRepoDoesNotStopOthers`).

---

### Task 1 : Socle du projet (pom, Vaadin + Push, /healthz)

Remplace l'ancienne appli (JAX-RS/HTMX) par un squelette Vaadin qui démarre sur Vidocq 0.4.0.

**Files :**
- Delete : `forge-dashboard-app/src/main/java/io/vidocq/tools/dashboard/**` (ancien code),
  `forge-dashboard-app/src/main/resources/static/`, `forge-dashboard-app/src/main/resources/logging.properties`
- Modify : `pom.xml` (agrégateur : `modelVersion 4.0.0`, `packaging pom`, module `forge-dashboard-app`,
  dépôts, `dependencyManagement` des trois BOM, pas de plugin Vidocq), `forge-dashboard-app/pom.xml`,
  `.sdkmanrc` (`maven=3.10.0`)
- Create : `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` (copier de
  `/Users/yblazart/projects/perso/vidocq-workspace/vidocq/main`, Maven 3.10.0)
- Create : `forge-dashboard-app/src/main/java/module-info.java` (module `io.vidocq.tools.dashboard`)
- Create : `.../dashboard/ui/DashboardAppShell.java`, `.../dashboard/ui/MainView.java` (provisoire : un `H1`
  « forge-dashboard »), `.../dashboard/web/HealthServlet.java`
- Create : `forge-dashboard-app/src/main/resources/vidocq.properties`
- Test : `forge-dashboard-app/src/test/java/io/vidocq/tools/dashboard/web/HealthTest.java`

**Interfaces :**
- Produces : module `io.vidocq.tools.dashboard`, `vidocq.mainModule=io.vidocq.tools.dashboard` ; `GET /healthz`
  → `200 text/plain "OK"` ; `DashboardAppShell implements AppShellConfigurator` annoté
  `@Push(transport = Transport.WEBSOCKET_XHR)`.

Dépendances de `forge-dashboard-app/pom.xml` (versions par les BOM, sauf mention) :
- runtime Vidocq : `io.vidocq.runtime:vidocq-runtime-core`,
  `io.vidocq.runtime.extensions.jakartaee.web:vidocq-runtime-foy-servlet-extension`,
  `…jakartaee.web:vidocq-runtime-vaadin-extension`,
  `io.vidocq.runtime.extensions.microprofile:vidocq-runtime-ravel-config-extension` ;
- Vaadin et logs : `com.vaadin:vaadin-core`, `com.vaadin:vaadin-prod-bundle:${vaadin.version}`,
  `org.slf4j:slf4j-jdk14:2.0.17` ;
- test : `io.vidocq.runtime:vidocq-runtime-junit`, `org.junit.jupiter:junit-jupiter`.

Plugins : `vaadin-maven-plugin` `${vaadin.version}` (`prepare-frontend` ; `build-frontend` en
`<phase>process-classes</phase>`) et `vidocq-runtime-maven-plugin` `${vidocq.version}`
(`<extensions>true</extensions>`). Modèle : le `pom.xml` de `VidocqTools/vaadin-on-vidocq` et
`/Users/yblazart/projects/perso/vidocq-tools/vidocq-tutorial/main/library/pom.xml`.

`module-info.java` :
- requires : `io.vidocq.runtime.extensions.jakartaee.web.foy`, `io.vidocq.foy.api`, `io.vidocq.vauban.api`,
  `jakarta.servlet`, `io.vidocq.runtime.extensions.jakartaee.web.vaadin`,
  `io.vidocq.runtime.extensions.microprofile.ravel`, `flow.server`, `flow.html.components`,
  `vaadin.ordered.layout.flow` ;
- `opens io.vidocq.tools.dashboard.ui` (Vaadin instancie les routes par réflexion).
- Chaque composant Vaadin ajouté plus tard ajoute son `requires vaadin.<composant>.flow`. Ajouter aussi
  `vaadin.flow.components.base` et `vaadin.renderer.flow` si l'IDE les réclame.

`vidocq.properties` :
- `vidocq.chappe.listener.default.host=0.0.0.0`
- `vidocq.chappe.listener.default.port=${DASHBOARD_PORT:8080}`
- `vidocq.servlet.context-path=/`

- [ ] **Step 1 : Écrire le test qui échoue**

```java
@VidocqTest(properties = "vaadin.productionMode=true")
class HealthTest {
    @Test
    void healthzAnswersOk(VidocqApplication app) {
        var r = app.get("/healthz");
        assertEquals(200, r.status());
        assertEquals("OK", r.body().strip());
    }

    @Test
    void rootServesVaadinPage(VidocqApplication app) {
        var r = app.get("/");
        assertEquals(200, r.status());
        assertTrue(r.body().contains("<html"), r.body());
    }
}
```

- [ ] **Step 2 : Vérifier qu'il échoue**
Run : `./mvnw -ntp -U -pl forge-dashboard-app verify -Dtest=HealthTest`
Expected : FAIL (pas de servlet `/healthz`, ou pas de compilation avec l'ancien code).

- [ ] **Step 3 : Implémenter**
- `HealthServlet` : `@WebServlet("/healthz")`, `doGet` écrit `OK` en `text/plain`.
- `MainView` : `@Route("")`.
- `DashboardAppShell` : `@Push(transport = Transport.WEBSOCKET_XHR)`, `@PageTitle("forge-dashboard")`.

Supprimer l'ancien code, le `Dockerfile` excepté (il est refait en Task 17).

- [ ] **Step 4 : Vérifier qu'il passe, puis lancer l'appli**
Run : `./mvnw -ntp -pl forge-dashboard-app verify -Dtest=HealthTest`. Expected : PASS.
Si `@VidocqTest` + Vaadin casse (jamais essayé) : appliquer la règle « bug Vidocq » des Global Constraints.
Run : `./mvnw -ntp -pl forge-dashboard-app vidocq:run`, puis `curl -s localhost:8080/healthz`.
Expected : `OK`. Ouvrir `http://localhost:8080/` : la page affiche « forge-dashboard ».

- [ ] **Step 5 : Commit**
`git add -A && git commit` → `build: rebuild forge-dashboard on Vidocq 0.4.0-SNAPSHOT with a Vaadin shell and /healthz`

---

### Task 2 : Règles du domaine (pures)

**Files :**
- Create : `.../dashboard/domain/CiState.java`, `LogHead.java`, `CommitClassifier.java`,
  `CommitClassification.java`, `RawCommit.java`, `Formats.java`
- Test : `.../dashboard/domain/CiStateTest.java`, `LogHeadTest.java`, `CommitClassifierTest.java`,
  `FormatsTest.java`

**Interfaces :**
- Produces :
  - `enum CiState { SUCCESS, FAILURE, PENDING, NONE; static CiState fromForgejo(String state); }`.
    Correspondances : `success`→SUCCESS, `failure|error|warning`→FAILURE, `pending`→PENDING,
    null ou autre→NONE.
  - `record LogHead(String runnerUuid, long taskId, Instant at) { static Optional<LogHead> parse(String text); }`.
    Ne lit que la première ligne qui matche
    `^(\S+) Runner (\S+) \(version:[^)]*\) received task (\d+) `.
  - `record RawCommit(String sha, String org, String repo, Instant authoredAt, String authorName,
    String authorEmail, String authorLogin /*nullable*/, String message, int additions, int deletions)`.
  - `enum CommitKind { HUMAN, BOT, AGENT_VIA_BOT }` (dans `CommitClassification.java`) et
    `record CommitClassification(CommitKind kind, String humanKey /*nullable*/, List<String> agentFamilies,
    String claudeSession /*nullable*/, List<String> rawTrailers)`.
  - `final class CommitClassifier`, construit par `CommitClassifier(Set<String> botNames, Map<String,String> aliases)`,
    avec :
    - `static Optional<String> agentFamily(String coAuthorName)` ;
    - `CommitClassification classify(RawCommit c)`.
  - `final class Formats` :
    - `static String compact(long n)` : `< 10_000` → tel quel ; `k` et `M` à une décimale ;
    - `static String relative(Instant t, Instant now)` : `il y a 8 s`, `il y a 3 min`, `il y a 2 h`,
      `il y a 4 j` ;
    - `static String duration(Duration d)` : `42 s`, `3 min 05 s`, `1 h 12 min`.

Familles d'agents, avec normalisation (on retire la version et toute parenthèse) :
- `Claude Opus|Sonnet|Fable|Haiku` → elles-mêmes ;
- `Codex`, `OpenAI Codex` → `Codex` ;
- `Copilot`, `Copilot App` → `Copilot` ;
- `Mistral Vibe`, `Gemini`, `Cursor` → elles-mêmes.

`botNames` par défaut : `vidocq-bot`, `Vidocq CI Bot`, `Vidocq Bot`, `BOT`, `bot`, `Copilot Agent`, `Copilot`.
La comparaison se fait sur le nom exact de l'auteur.

- [ ] **Step 1 : Écrire les tests qui échouent**

```java
// LogHeadTest
@Test void parsesRunnerUuidTaskAndInstant() {
    var text = "2026-11-14T10:14:47.3061509Z Runner 87c30ab4-ebf0-4d81-bd76-1126048f2d0e (version:v13.0.0) "
             + "received task 175790 of job downstream, triggered by event: pull_request\n"
             + "2026-11-14T10:14:47.3129766Z workflow prepared\n";
    var h = LogHead.parse(text).orElseThrow();
    assertEquals("87c30ab4-ebf0-4d81-bd76-1126048f2d0e", h.runnerUuid());
    assertEquals(175790L, h.taskId());
    assertEquals(Instant.parse("2026-11-14T10:14:47.306150900Z"), h.at());
}
@Test void emptyOrForeignTextGivesEmpty() {
    assertTrue(LogHead.parse("").isEmpty());
    assertTrue(LogHead.parse("2026-11-14T10:14:47Z workflow prepared").isEmpty());
}

// CommitClassifierTest (classifier = new CommitClassifier(DEFAULT_BOTS, Map.of("Yann Blazart", "yblazart")))
@Test void agentFamiliesAreNormalised() {
    assertEquals(Optional.of("Claude Opus"), CommitClassifier.agentFamily("Claude Opus 5.5"));
    assertEquals(Optional.of("Claude Opus"), CommitClassifier.agentFamily("Claude Opus 5 (1M context)"));
    assertEquals(Optional.of("Claude Fable"), CommitClassifier.agentFamily("Claude Fable 5.2"));
    assertEquals(Optional.of("Codex"), CommitClassifier.agentFamily("OpenAI Codex"));
    assertEquals(Optional.of("Copilot"), CommitClassifier.agentFamily("Copilot App"));
    assertEquals(Optional.empty(), CommitClassifier.agentFamily("Antoine Sabot-Durand"));
}
@Test void humanWithAgentTrailerAndSession() {
    var c = commit("Yann Blazart", "yblazart", """
        fix: x

        Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
        Claude-Session: https://claude.ai/code/session_X
        """);
    var k = classifier.classify(c);
    assertEquals(CommitKind.HUMAN, k.kind());
    assertEquals("yblazart", k.humanKey());
    assertEquals(List.of("Claude Opus"), k.agentFamilies());
    assertEquals("https://claude.ai/code/session_X", k.claudeSession());
}
@Test void lowercaseTrailerIsRead() { /* "Co-authored-by: Copilot <x>" → agentFamilies == [Copilot] */ }
@Test void sabotDurandIsNotABot() { /* author "Antoine Sabot-Durand", no trailer → HUMAN */ }
@Test void plainBotIsBot() { /* author "vidocq-bot", no trailer → BOT, humanKey null */ }
@Test void botWithAgentTrailerIsAgentViaBot() { /* author "Vidocq Bot" + Claude trailer → AGENT_VIA_BOT, humanKey null */ }
@Test void aliasResolvesHumanWithoutLogin() { /* author "Yann Blazart", login null → humanKey "yblazart" */ }

// CiStateTest
@Test void mapsForgejoStates() {
    assertEquals(CiState.SUCCESS, CiState.fromForgejo("success"));
    assertEquals(CiState.FAILURE, CiState.fromForgejo("error"));
    assertEquals(CiState.FAILURE, CiState.fromForgejo("warning"));
    assertEquals(CiState.PENDING, CiState.fromForgejo("pending"));
    assertEquals(CiState.NONE, CiState.fromForgejo(null));
}

// FormatsTest
@Test void compactAndRelative() {
    assertEquals("2077", Formats.compact(2077));
    assertEquals("41.8k", Formats.compact(41_814));
    assertEquals("1.3M", Formats.compact(1_250_000));
    var now = Instant.parse("2026-11-14T12:00:00Z");
    assertEquals("il y a 8 s", Formats.relative(now.minusSeconds(8), now));
    assertEquals("il y a 2 h", Formats.relative(now.minus(Duration.ofHours(2)), now));
    assertEquals("3 min 05 s", Formats.duration(Duration.ofSeconds(185)));
}
```

- [ ] **Step 2 : Vérifier qu'ils échouent**
Run : `./mvnw -ntp -pl forge-dashboard-app test -Dtest='CiStateTest,LogHeadTest,CommitClassifierTest,FormatsTest'`.
Expected : FAIL (classes absentes).

- [ ] **Step 3 : Implémenter les signatures ci-dessus.** Trailers lus par
`(?im)^(Co-Authored-By|Claude-Session):\s*(.+)$`. Le nom du co-auteur est la partie avant `<`, sans espaces
de bord.

- [ ] **Step 4 : Vérifier qu'ils passent** (même commande). Expected : PASS.

- [ ] **Step 5 : Commit** → `feat(domain): CI state, runner log head, committer classification and formats`

---

### Task 3 : Port CodeFloe (`ForgeApi`) et adaptateur Cyrano

**Files :**
- Create : `.../dashboard/forge/ForgeApi.java` (port), les DTO (`ForgeRepo.java`, `ForgeRunner.java`,
  `ForgeJob.java`, `ForgeRun.java`, `ForgePull.java`, `CombinedStatus.java`, `StatusContext.java`), les
  exceptions `RateLimitedException.java` et `ForgeNotFoundException.java`, `NextPage.java`
- Create : `.../dashboard/forge/cyrano/ForgejoClient.java` (`@RegisterRestClient(configKey = "forgejo")`),
  `ForgejoAuthFilter.java` (`ClientRequestFilter`, `@ApplicationScoped`), `ForgejoJson.java` (records JSON-B
  bruts), `CyranoForgeApi.java` (`@ApplicationScoped implements ForgeApi`)
- Create : `forge-dashboard-app/src/test/resources/fixtures/forgejo/*.json` et `job-log-head.txt`
- Test : `.../dashboard/forge/ForgejoJsonTest.java`, `NextPageTest.java`, `FakeForgeApi.java` (faux en
  mémoire, réutilisé par les Tasks 7 à 9 et 16)
- Modify : `forge-dashboard-app/pom.xml` (+ `vidocq-runtime-cyrano-rest-client-extension`), `module-info.java`
  (+ `requires io.vidocq.runtime.extensions.microprofile.cyrano;`, `opens` du paquet des records JSON si
  JSON-B l'exige), `vidocq.properties` (+ `forgejo/mp-rest/url=${FORGEJO_URL:https://codefloe.com}/api/v1`,
  `forgejo/mp-rest/readTimeout=20000`)

**Interfaces :**
- Produces :
```java
public interface ForgeApi {
    List<ForgeRepo> listRepos(String org);
    List<ForgeRunner> listRunners(String org);
    List<ForgeJob> listActiveJobs(String org);                       // waiting + running ; [] si l'API répond null
    ForgeRun getRun(String owner, String repo, long runId);
    ForgeJob getJob(String owner, String repo, long jobId);
    Optional<String> getJobLogHead(String owner, String repo, long jobId); // 4 premiers Ko ; empty si 404/vide
    List<ForgePull> listOpenPulls(String owner, String repo);
    List<ForgePull> listPullsUpdatedSince(String owner, String repo, Instant since); // state=all, sort=recentupdate
    CombinedStatus getCombinedStatus(String owner, String repo, String ref);
    List<RawCommit> listCommits(String owner, String repo, String branch, Instant since, int page); // limit 50, stat=true
    Optional<String> pullAuthorForCommit(String owner, String repo, String sha);
    boolean canReadRunners(String org);                              // GET /orgs/{org}/actions/runners → 200
}
record ForgeRepo(long id, String owner, String name, String defaultBranch, boolean archived, boolean empty) {}
record ForgeRunner(long id, String uuid, String name, String status, List<String> labels, String version) {}
record ForgeJob(long id, long runId, long repoId, long taskId, String name, String status,
                List<String> runsOn, String htmlUrl, Instant startedAt /*nullable*/, Instant stoppedAt /*nullable*/) {}
record ForgeRun(long id, String event, String prettyRef, String commitSha, String title, String status,
                Instant started, Instant stopped, String htmlUrl) {}
record ForgePull(int number, String title, String authorLogin, String headRef, String headSha,
                 String htmlUrl, Instant createdAt, Instant updatedAt, Instant mergedAt, boolean draft) {}
record StatusContext(String context, CiState state, String targetUrl) {}
record CombinedStatus(CiState state, List<StatusContext> contexts) {}
final class RateLimitedException extends RuntimeException { Duration retryAfter(); }
final class ForgeNotFoundException extends RuntimeException {}  // 404 et 409 (repo vide)
final class NextPage { static boolean hasNext(String linkHeader); }
```
- `ForgeJob.startedAt`/`stoppedAt` = min `steps[].started` / max `steps[].stopped` (null si absents).
- `pullAuthorForCommit` : `GET /repos/{o}/{r}/commits/{sha}/pull` → `user.login`.
- Le jeton est envoyé par `ForgejoAuthFilter` en `Authorization: token ${FORGEJO_TOKEN}`, et `429` lève
  `RateLimitedException` avec la valeur de `Retry-After` (60 s par défaut).
- Au plus 4 appels simultanés : un `Semaphore(4)` équitable dans `CyranoForgeApi`.

- [ ] **Step 1 : Capturer les fixtures.**
- Lancer un script jetable, depuis le scratchpad (pas dans le repo), qui lit `CODEFLOE_BOT_TOKEN` dans
  `~/.config/vidocq/tokens.env`.
- Il écrit les réponses réelles de chaque endpoint de `ForgeApi` pour `Vidocq/vauban` et l'org `Vidocq`
  dans `src/test/resources/fixtures/forgejo/`, avec un fichier par endpoint : `repos.json`, `runners.json`,
  `runner-jobs.json`, `run.json`, `run-jobs.json`, `job.json`, `pulls-open.json`, `status.json`,
  `commits.json`, `commit-pull.json`, et `job-log-head.txt` (2 premières lignes).
- Remplacer toutes les adresses mail par `<login>@example.invalid`.
- Vérifier : `grep -rE 'token|secret|@(gmail|durand|anthropic)' src/test/resources/fixtures` ne trouve que
  `noreply@anthropic.com` dans les trailers.

- [ ] **Step 2 : Écrire les tests qui échouent**

```java
// ForgejoJsonTest — désérialise chaque fixture avec JsonbBuilder.create() vers les records de ForgejoJson,
// puis vérifie la conversion vers les DTO du port.
@Test void runnerJobsMapToForgeJobs() {
    var jobs = ForgejoJson.toJobs(read("runner-jobs.json"));
    assertFalse(jobs.isEmpty());
    var j = jobs.getFirst();
    assertTrue(j.id() > 0 && j.runId() > 0 && j.repoId() > 0);
    assertTrue(Set.of("waiting", "running").contains(j.status()));
}
@Test void runnerMapsUuidStatusAndLabels() { /* runners.json : uuid non vide, status ∈ {idle, active, offline}, labels non vide */ }
@Test void runMapsPrettyRefAndEvent() { /* run.json : prettyRef commence par "#" quand event == pull_request */ }
@Test void jobStepsGiveStartAndStop() { /* job.json : startedAt <= stoppedAt quand le job est terminé */ }
@Test void commitsMapStatsAndLogin() { /* commits.json : additions >= 0, authorLogin non null sur au moins un commit */ }
@Test void combinedStatusMapsContexts() { /* status.json : state ∈ CiState, contexts non vide */ }
@Test void nullJobListMapsToEmpty() { assertEquals(List.of(), ForgejoJson.toJobs("null")); }

// NextPageTest
@Test void linkHeaderWithNextHasNext() {
    assertTrue(NextPage.hasNext("<https://codefloe.com/api/v1/orgs/Vidocq/repos?page=2&limit=50>; rel=\"next\","
                              + "<https://codefloe.com/api/v1/orgs/Vidocq/repos?page=3&limit=50>; rel=\"last\""));
    assertFalse(NextPage.hasNext(null));
    assertFalse(NextPage.hasNext("<…?page=1>; rel=\"prev\""));
}
```

- [ ] **Step 3 : Vérifier qu'ils échouent**
Run : `./mvnw -ntp -pl forge-dashboard-app test -Dtest='ForgejoJsonTest,NextPageTest'`. Expected : FAIL.

- [ ] **Step 4 : Implémenter.**
- `ForgejoClient` : méthodes JAX-RS qui renvoient `jakarta.ws.rs.core.Response` pour les listes paginées
  (lecture de `Link`), et `String` en `text/plain` pour les logs.
- `ForgejoJson` : records bruts annotés `@JsonbProperty("snake_name")`, et des fonctions statiques
  `toJobs(String json)`, etc.
- `CyranoForgeApi` : pagination (`limit=50`, on avance tant que `NextPage.hasNext`) ; `404/409` →
  `ForgeNotFoundException` ; `429` → `RateLimitedException`.
- `FakeForgeApi` (dans les tests) : `Map` modifiables par les tests, plus des méthodes `failNext(String
  method, RuntimeException e)`.

- [ ] **Step 5 : Vérifier qu'ils passent** (même commande). Expected : PASS.
Smoke manuel :
1. Un `@Startup` provisoire (ou un test manuel non commité), lancé par `./mvnw vidocq:run` avec
   `FORGEJO_TOKEN` défini, logue `listRunners("Vidocq").size()`. Expected : ≥ 6.
2. Retirer ce code provisoire.

- [ ] **Step 6 : Commit** → `feat(forge): CodeFloe port and Cyrano adapter (repos, runners, jobs, runs, PRs, statuses, commits)`

---

### Task 4 : Port Sonar (`SonarApi`) et adaptateur

**Files :**
- Create : `.../dashboard/sonar/SonarApi.java`, `SonarMeasures.java`, `GateStatus.java`, `SonarKeys.java`,
  `cyrano/SonarClient.java` (`@RegisterRestClient(configKey = "sonar")`), `cyrano/SonarAuthFilter.java`,
  `cyrano/CyranoSonarApi.java`
- Create : `src/test/resources/fixtures/sonar/measures.json`, `gate.json`, `gate-pr.json`
- Test : `.../dashboard/sonar/SonarKeysTest.java`, `SonarJsonTest.java`, `FakeSonarApi.java`
- Modify : `vidocq.properties` (`sonar/mp-rest/url=${SONAR_URL:https://sonar.vidocq.dev}`)

**Interfaces :**
- Produces :
```java
public interface SonarApi {
    boolean enabled();                                  // false si SONAR_TOKEN absent
    Optional<SonarMeasures> measures(String key);       // empty si projet inconnu (404)
    Optional<GateStatus> gate(String key);
    Optional<GateStatus> pullRequestGate(String key, int pr);
}
record SonarMeasures(Long ncloc, Long tests, Double coverage, Long testFailures, Long testErrors, Long bugs,
                     Long vulnerabilities, Long codeSmells, Double duplicatedLinesDensity, GateStatus gate) {}
enum GateStatus { OK, ERROR, WARN, NONE; static GateStatus of(String s); }
final class SonarKeys { static Optional<String> keyFor(String org, String repo, Optional<String> override); }
```
- Règles de `keyFor` :
  - une surcharge `"none"` donne `empty` ; toute autre surcharge non vide gagne ;
  - l'org `Vidocq` (insensible à la casse) donne `"vidocq-" + repo` ;
  - toute autre org donne `empty`.
- Auth : `Authorization: Basic base64(SONAR_TOKEN + ":")`.
- Métriques demandées : `ncloc,tests,coverage,test_failures,test_errors,bugs,vulnerabilities,code_smells,duplicated_lines_density,alert_status`.

- [ ] **Step 1 : Capturer les fixtures** (script jetable, `SONAR_USER_TOKEN`) pour `vidocq-vauban` et une PR
  de vauban.
- [ ] **Step 2 : Écrire les tests qui échouent**

```java
@Test void vidocqOrgMapsToPrefixedKey() {
    assertEquals(Optional.of("vidocq-vauban"), SonarKeys.keyFor("Vidocq", "vauban", Optional.empty()));
    assertEquals(Optional.of("vidocq-vidocq"), SonarKeys.keyFor("vidocq", "vidocq", Optional.empty()));
    assertEquals(Optional.empty(), SonarKeys.keyFor("VidocqTools", "arago", Optional.empty()));
    assertEquals(Optional.of("io.vidocq.runtime:vidocq-runtime-build"),
                 SonarKeys.keyFor("Vidocq", "vidocq", Optional.of("io.vidocq.runtime:vidocq-runtime-build")));
    assertEquals(Optional.empty(), SonarKeys.keyFor("Vidocq", "ci", Optional.of("none")));
}
@Test void measuresFixtureMaps() { /* measures.json → ncloc > 0, tests > 0, coverage entre 0 et 100, gate OK|ERROR */ }
@Test void prGateFixtureMaps() { /* gate-pr.json → GateStatus != NONE */ }
```
- [ ] **Step 3 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest='SonarKeysTest,SonarJsonTest'` → FAIL.
- [ ] **Step 4 : Implémenter** les signatures ci-dessus. `enabled()` vaut false quand `SONAR_TOKEN` est vide :
  aucun appel, toutes les méthodes renvoient `empty`.
- [ ] **Step 5 : Vérifier qu'ils passent.** Même commande → PASS.
- [ ] **Step 6 : Commit** → `feat(sonar): Sonar port, key mapping and Cyrano adapter`

---

### Task 5 : Persistance (Flyway, entités, repositories, requêtes d'agrégat)

**Files :**
- Modify : `forge-dashboard-app/pom.xml`. Ajouter :
  - `…jakartaee.web:vidocq-runtime-mansart-data-extension`, `…-mansart-pool-extension`,
    `…-mansart-transactions-extension` ;
  - `io.vidocq.runtime.extensions.essentials:vidocq-runtime-flyway-migration-extension` ;
  - `org.postgresql:postgresql:42.7.10` (runtime) ;
  - en test : `io.vidocq.runtime:vidocq-runtime-devservices-junit` et `vidocq-runtime-devservice-postgres` ;
  - `vidocq-runtime-devservice-postgres` `${vidocq.version}` aussi dans les `<dependencies>` du
    `vidocq-runtime-maven-plugin`.
- Modify : `module-info.java`. Ajouter :
  - les requires `io.vidocq.runtime.extensions.jakartaee.web.mansart.pool`, `….mansart.data`,
    `….mansart.transactions`, `io.vidocq.runtime.extensions.essentials.migration.flyway` et
    `io.vidocq.runtime.core` (pour le test en process) ;
  - `opens db.migration;`.
- Create : `forge-dashboard-app/src/main/resources/db/migration/V1__init.sql`
- Create : `.../dashboard/store/` :
  - entités : `TrackedOrg.java`, `RepoRow.java`, `RunnerRow.java`, `RunnerStatusChange.java`,
    `JobExecution.java`, `QueueSample.java`, `PullRequestState.java`, `SonarSnapshot.java`,
    `CommitStat.java`, `PrStat.java`, `UserPref.java`, `CollectorState.java` ;
  - un repository par entité (`…Repository.java`) ;
  - `StatsQueries.java` (JDBC).
- Test : `.../dashboard/store/StoreIntegrationTest.java` (en process, PostgreSQL via les dev services)

**Interfaces :**
- Toutes les entités sont des classes `jakarta.persistence` avec `@Table(name = "<nom singulier>")`
  explicite. Les noms de tables et colonnes sont ceux de la spec §5.4.
- Clés :
  - clés naturelles en `String` : `PullRequestState.id = "org/repo#n"`, `SonarSnapshot.id = sonarKey` ou
    `sonarKey#n`, `CommitStat.id = "org/repo@sha"`, `PrStat.id = "org/repo#n"`, `UserPref.id = login`,
    `CollectorState.id = loopName` ;
  - les ids CodeFloe pour `RepoRow`, `RunnerRow` et `JobExecution` (cette dernière par `task_id`) ;
  - `@GeneratedValue` (identity) pour `TrackedOrg`, `RunnerStatusChange` et `QueueSample`.
- Colonnes particulières :
  - `CommitStat` : `kind`, `human_key`, `agent_families` (CSV), `claude_session`, `raw_trailers` (texte),
    plus les champs bruts de `RawCommit` ;
  - `RepoRow` : `sonar_key_override` (nullable), `last_commit_at` (nullable, curseur de la boucle commits) ;
  - `RunnerRow` : `capacity_override` (nullable), `last_status`.
- Produces (repositories, `BasicRepository<E, K>`), méthodes en plus du CRUD :
```java
TrackedOrgRepository : List<TrackedOrg> findByActiveTrueOrderByPositionAsc(); Optional<TrackedOrg> findByName(String name);
RepoRowRepository    : List<RepoRow> findByOrgOrderByNameAsc(String org);
RunnerRowRepository  : List<RunnerRow> findByOrg(String org); Optional<RunnerRow> findByUuid(String uuid);
JobExecutionRepository : List<JobExecution> findByOrgAndStoppedAtNull(String org);
                         List<JobExecution> findByRunnerIdAndStartedAtGreaterThanOrderByStartedAtDesc(long runnerId, Instant since);
                         List<JobExecution> findByOrgAndRepoAndPrNumberOrderByStartedAtDesc(String org, String repo, Integer pr);
UserPrefRepository   : (CRUD suffit)
```
- Produces (JDBC, `@ApplicationScoped StatsQueries`, `@Inject DataSource`). `Interval` et `StatusSpan` sont
  créés dans le paquet `domain` (fichiers `domain/Interval.java`, `domain/StatusSpan.java`), car la Task 6 en a
  besoin pour un calcul pur :
```java
record Interval(long runnerId, Instant start, Instant stop /*nullable = en cours*/) {}
record StatusSpan(long runnerId, String status, Instant at) {}
record CommitterAggregate(String humanKey, CommitKind kind, String agentFamilies, long commits, long additions, long deletions) {}
record PrCounts(String login, long opened, long merged) {}
List<Interval> jobIntervals(String org, Instant from, Instant to);
List<StatusSpan> statusChanges(String org, Instant from, Instant to);   // + le dernier changement avant `from`
List<CommitterAggregate> committerAggregates(Optional<String> org, Instant from);  // GROUP BY human_key, kind, agent_families
List<PrCounts> prCounts(Optional<String> org, Instant from);
int purge(Instant jobsBefore, Instant queueBefore);
```

- [ ] **Step 1 : Écrire le test qui échoue.** `StoreIntegrationTest` démarre l'appli en process, sur le modèle de
  `TransactionalRollbackTest` de `vidocq-runtime-mansart-h2-example` :
  - `vidocq.launch.mode=test` et un port libre ;
  - `VidocqBootstrap.create().configure().start()` ;
  - beans obtenus par `CDI.current().select(…)`.

```java
@Test void orgsAreSavedAndListedInOrder() { /* save Vidocq(pos 0), VidocqTools(pos 1) → findByActiveTrueOrderByPositionAsc == [Vidocq, VidocqTools] */ }
@Test void jobExecutionUpsertByTaskId() { /* save task 175790 sans stoppedAt, puis avec stoppedAt → 1 ligne, stoppedAt renseigné */ }
@Test void openExecutionsAreListed() { /* 2 ouvertes + 1 fermée pour Vidocq → findByOrgAndStoppedAtNull("Vidocq").size() == 2 */ }
@Test void committerAggregatesGroupByHumanAndAgents() {
    // 3 commits yblazart (2 "Claude Opus", 1 sans agent), 1 commit vidocq-bot (BOT) depuis il y a 2 jours
    var rows = stats.committerAggregates(Optional.empty(), now.minus(Duration.ofDays(30)));
    assertEquals(3, rows.size());
    assertEquals(2, rows.stream().filter(r -> "yblazart".equals(r.humanKey()) && "Claude Opus".equals(r.agentFamilies()))
                        .findFirst().orElseThrow().commits());
}
@Test void jobIntervalsAreClippedToWindowByCaller() { /* insère un job [from-1h, from+10min] → jobIntervals(from, to) le renvoie (chevauchement) */ }
@Test void purgeRemovesOldRows() { /* job stoppé il y a 200 j, sample de 40 j → purge(now-180j, now-30j) == 2 */ }
```
- [ ] **Step 2 : Vérifier qu'il échoue.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest=StoreIntegrationTest`. Expected : FAIL (tables absentes).
- [ ] **Step 3 : Implémenter**
  - `V1__init.sql` : tables de la spec §5.4, plus les index `job_execution(org, stopped_at)`,
    `job_execution(runner_id, started_at)`, `commit_stat(authored_at)`, `commit_stat(org, authored_at)`,
    `queue_sample(org, minute)` (unique). Horodatages en `timestamptz`.
  - `StatsQueries` : SQL natif via `DataSource`, en lecture seule (le `purge` fait exception).
  - Pas de `GROUP BY` dans les repositories : Mansart ne le supporte pas.
  - `vidocq.properties` :
    - `vidocq.pool.url=${DB_URL:}`, `vidocq.pool.username=${DB_USER:}`, `vidocq.pool.password=${DB_PASSWORD:}`
      (en dev et en test, les dev services les remplissent) ;
    - `vidocq.pool.maxSize=10`.
- [ ] **Step 4 : Vérifier qu'il passe.** Même commande → PASS. Puis `./mvnw -ntp -pl forge-dashboard-app vidocq:dev` :
  le conteneur PostgreSQL démarre, Flyway applique V1 (dans les logs), `/healthz` répond `OK`.
- [ ] **Step 5 : Commit** → `feat(store): PostgreSQL schema, Mansart Data repositories and aggregate queries`

---

### Task 6 : État courant, bus de changements, calcul d'occupation (purs)

**Files :**
- Create : `.../dashboard/live/OrgSnapshot.java` (et les vues `RepoView`, `PullView`, `RunnerView`, `JobView`
  dans le même paquet), `LiveState.java`, `ChangeEvent.java`, `ChangeBus.java`, `LoopHealth.java`,
  `ClockProducer.java`
- Create : `.../dashboard/domain/OccupancyCalculator.java`, `Occupancy.java`
- Test : `.../dashboard/live/LiveStateTest.java`, `ChangeBusTest.java`, `.../domain/OccupancyCalculatorTest.java`

**Interfaces :**
- Produces :
```java
record JobView(long taskId, long jobId, String repo, Integer prNumber, String ref, String jobName, String status,
               Instant startedAt, String htmlUrl, Long runnerId, String runnerName) {}
record RunnerView(long id, String name, String status, List<String> labels, int capacity, List<JobView> running) {}
record PullView(String repo, int number, String title, String author, String headSha, CiState state,
                List<StatusContext> contexts, GateStatus sonarGate, String htmlUrl, Instant updatedAt) {}
record RepoView(String name, CiState mainState, String mainSha, String lastCommitTitle, Instant lastCommitAt,
                SonarMeasures sonar /*nullable*/, List<PullView> pulls, String htmlUrl) {}
record OrgSnapshot(String org, List<RepoView> repos, List<RunnerView> runners, List<JobView> waiting, Instant updatedAt) {
    static OrgSnapshot empty(String org);
    OrgSnapshot withRepos(List<RepoView> repos, Instant at);
    OrgSnapshot withRunners(List<RunnerView> runners, List<JobView> waiting, Instant at);
}
@ApplicationScoped class ClockProducer { @Produces @ApplicationScoped Clock clock(); }   // Clock.systemUTC()
sealed interface ChangeEvent permits ChangeEvent.RunnersChanged, ChangeEvent.PullsChanged, ChangeEvent.SonarChanged,
        ChangeEvent.CommitsChanged, ChangeEvent.HealthChanged, ChangeEvent.OrgsChanged {
    record RunnersChanged(String org) implements ChangeEvent {}
    record PullsChanged(String org) implements ChangeEvent {}
    record SonarChanged(String org) implements ChangeEvent {}
    record CommitsChanged() implements ChangeEvent {}
    record HealthChanged() implements ChangeEvent {}
    record OrgsChanged() implements ChangeEvent {}
}
@ApplicationScoped class LiveState {
    OrgSnapshot snapshot(String org);                                    // OrgSnapshot.empty(org) si inconnu
    void update(String org, UnaryOperator<OrgSnapshot> change, ChangeEvent event); // remplacement atomique + publish
}
@ApplicationScoped class ChangeBus {
    Registration subscribe(Consumer<ChangeEvent> listener);              // Registration::remove
    void publish(ChangeEvent e);   // chaque écouteur appelé sur un thread virtuel ; une exception est loguée, pas propagée
}
record LoopHealth(String loop, Instant lastSuccess, Instant lastFailure, String lastError, Duration cadence) {
    boolean stale(Instant now);    // lastSuccess null ou plus vieux que 3 × cadence
}
record Occupancy(double slotRatio, double anyBusyRatio) {}
final class OccupancyCalculator {
    static Occupancy compute(List<Interval> jobs, List<StatusSpan> statuses, int capacity, Instant from, Instant to);
    static int observedCapacity(List<Interval> jobs);   // chevauchement max, au moins 1
}
```
- `Interval` et `StatusSpan` sont ceux de la Task 5. Le temps passé en `offline` est retiré du dénominateur.
  Un intervalle encore ouvert (`stop` null) court jusqu'à `to`.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
// OccupancyCalculatorTest (t(s) = Instant.EPOCH.plusSeconds(s))
@Test void slotAndAnyBusyRatios() {
    var jobs = List.of(new Interval(1, t(10), t(60)), new Interval(1, t(20), t(40)));
    var o = OccupancyCalculator.compute(jobs, List.of(), 2, t(0), t(100));
    assertEquals(0.35, o.slotRatio(), 1e-9);
    assertEquals(0.50, o.anyBusyRatio(), 1e-9);
}
@Test void offlineTimeIsExcluded() {
    var o = OccupancyCalculator.compute(List.of(new Interval(1, t(0), t(40))),
            List.of(new StatusSpan(1, "offline", t(80)), new StatusSpan(1, "idle", t(100))), 1, t(0), t(100));
    assertEquals(0.5, o.slotRatio(), 1e-9);   // 40 / 80
}
@Test void openIntervalRunsToWindowEnd() { /* Interval(1, t(50), null), window [0,100), cap 1 → 0.5 */ }
@Test void observedCapacityIsMaxOverlap() { /* les deux jobs ci-dessus → 2 ; liste vide → 1 */ }

// LiveStateTest
@Test void updatePublishesEventAndReplacesSnapshot() { /* update("Vidocq", s -> withRunners, RunnersChanged) → snapshot changé, écouteur reçoit RunnersChanged("Vidocq") */ }
@Test void liveStateKeepsSnapshotWhenNoUpdate() { /* après un update, aucun appel ultérieur → snapshot inchangé, jamais remis à vide */ }

// ChangeBusTest
@Test void failingListenerDoesNotBlockOthers() { /* écouteur 1 lève, écouteur 2 reçoit l'événement (attente bornée 1 s) */ }
@Test void removedRegistrationStopsDelivery() { /* … */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest='OccupancyCalculatorTest,LiveStateTest,ChangeBusTest'` → FAIL.
- [ ] **Step 3 : Implémenter.** `LiveState` stocke une `ConcurrentHashMap<String, AtomicReference<OrgSnapshot>>`.
  `ChangeBus` utilise une `CopyOnWriteArrayList`, avec `Thread.ofVirtual().start` pour chaque écouteur.
- [ ] **Step 4 : Vérifier qu'ils passent.** Même commande → PASS.
- [ ] **Step 5 : Commit** → `feat(live): live snapshots, change bus and runner occupancy calculation`

---

### Task 7 : Boucle runners (jobs, runner↔job, intervalles, file d'attente)

**Files :**
- Create : `.../dashboard/collect/RunnerDiff.java` (pur), `JobResolver.java` (pur), `RunnersLoop.java`
  (`@ApplicationScoped`, orchestrateur)
- Test : `.../dashboard/collect/RunnerDiffTest.java`, `JobResolverTest.java`, `RunnersLoopIntegrationTest.java` (en
  process, PostgreSQL, `FakeForgeApi` passé au constructeur)

**Interfaces :**
- Consumes : `ForgeApi`, `LogHead` (Task 2), les repositories `JobExecution`, `RunnerRow`, `RunnerStatusChange`
  et `QueueSample` (Task 5), `LiveState` et `OccupancyCalculator.observedCapacity` (Task 6).
- Produces :
```java
record RunnerDiffResult(List<ForgeJob> newRunning, Set<Long> finishedTaskIds, List<ForgeJob> waiting) {}
final class RunnerDiff { static RunnerDiffResult diff(Set<Long> knownRunningTaskIds, List<ForgeJob> active); }
final class JobResolver {
    static JobExecution resolve(String org, ForgeJob job, ForgeRun run, String repo,
                                Optional<LogHead> head, Map<String, RunnerRow> runnersByUuid, Instant now);
    static Integer prNumber(ForgeRun run);   // event pull_request* et prettyRef "#298" → 298 ; sinon null
}
@ApplicationScoped class RunnersLoop {
    @Inject RunnersLoop(ForgeApi forge, JobExecutionRepository jobs, RunnerRowRepository runners,
                        RunnerStatusChangeRepository changes, QueueSampleRepository queue, LiveState live, Clock clock);
    void tick(String org);   // une passe complète pour une org
}
```
Comportement de `tick(org)` :
1. `listRunners` : upsert de `RunnerRow`. Un changement de statut ajoute un `RunnerStatusChange`.
2. `listActiveJobs` donne les waiting et les running.
3. `RunnerDiff` avec les task ids ouverts en base (`findByOrgAndStoppedAtNull`).
4. Pour chaque nouveau job running :
   - `getRun`, mis en cache par runId dans la boucle ;
   - `getJobLogHead`, puis `JobResolver.resolve` et `save`.
   - Si la tête de log est absente : `runnerId` reste null, `startedAt = now`, et le job est gardé dans
     `pendingResolution` (en mémoire, par taskId). Au tick suivant, on relit le log.
5. Pour chaque task fini : `getJob`, puis `stoppedAt` (steps, sinon `now`) et le statut final, et `save`.
6. Échantillon `QueueSample` par minute : à chaque tick, le max de waiting et de running dans la minute
   (upsert sur `org + minute`).
7. `live.update(org, s -> s.withRunners(runnerViews, waitingViews, now), new RunnersChanged(org))`.
   - capacité = `capacity_override` ou `observedCapacity` des intervalles sur 7 jours ;
   - les jobs running sont regroupés par runner.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void diffSeparatesNewFinishedAndWaiting() {
    var r = RunnerDiff.diff(Set.of(1L, 2L), List.of(job(2, "running"), job(3, "running"), job(4, "waiting")));
    assertEquals(List.of(3L), r.newRunning().stream().map(ForgeJob::taskId).toList());
    assertEquals(Set.of(1L), r.finishedTaskIds());
    assertEquals(List.of(4L), r.waiting().stream().map(ForgeJob::id).toList());
}
@Test void prNumberFromPrettyRef() {
    assertEquals(298, JobResolver.prNumber(run("pull_request", "#298")));
    assertNull(JobResolver.prNumber(run("push", "main")));
}
@Test void resolveUsesLogHeadForRunnerAndStart() { /* head uuid présent dans runnersByUuid → runnerId = id, startedAt = head.at */ }
// RunnersLoopIntegrationTest
@Test void newRunningJobIsRecordedWithItsRunner() { /* fake : 1 runner uuid U, 1 job running task 175790, log head avec U → job_execution(175790).runnerId == runner.id */ }
@Test void missingLogHeadIsRetriedNextTick() { /* tick 1 : log empty → runnerId null ; tick 2 : log dispo → runnerId renseigné, aucune exception */ }
@Test void openExecutionsAreReconciledAfterRestart() { /* job_execution ouvert en base, absent des actifs ; nouvelle instance de RunnersLoop → tick → stoppedAt renseigné depuis getJob */ }
@Test void runnerStatusChangeIsRecordedOnce() { /* idle → active : 1 changement ; 2e tick identique : aucun nouveau */ }
@Test void liveSnapshotGroupsRunningJobsPerRunner() { /* 2 jobs sur le même runner → RunnerView.running().size() == 2 */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest='RunnerDiffTest,JobResolverTest,RunnersLoopIntegrationTest'` → FAIL.
- [ ] **Step 3 : Implémenter** les signatures et le comportement ci-dessus.
- [ ] **Step 4 : Vérifier qu'ils passent.** Même commande → PASS.
- [ ] **Step 5 : Commit** → `feat(collect): runner loop with log-head runner resolution, job intervals and queue samples`

---

### Task 8 : Boucles PR/`main` et Sonar

**Files :**
- Create : `.../dashboard/collect/PullsLoop.java`, `SonarLoop.java`
- Test : `.../dashboard/collect/PullsLoopIntegrationTest.java`, `SonarLoopIntegrationTest.java`

**Interfaces :**
- Consumes : `ForgeApi`, `SonarApi`, `SonarKeys` (Tasks 3 et 4), les repositories `RepoRow`,
  `PullRequestState` et `SonarSnapshot` (Task 5), `LiveState` (Task 6).
- Produces :
```java
@ApplicationScoped class PullsLoop { void tick(String org); void refreshRepos(String org); }
@ApplicationScoped class SonarLoop { void tick(String org); }
```
- `refreshRepos` (toutes les 5 min, appelé par le planificateur) : `listRepos`, puis upsert de `RepoRow`, en
  ignorant les repos `archived`.
- `PullsLoop.tick`, pour chaque repo :
  - `getCombinedStatus(defaultBranch)` et le dernier commit (via `listCommits(…, page 1)`, 1er élément) ;
  - `listOpenPulls`, puis `getCombinedStatus(headSha)` pour chaque PR ;
  - `live.update(…, new PullsChanged(org))`.
  - Un `ForgeNotFoundException` sur un repo l'ignore : les autres repos continuent.
- `SonarLoop.tick` : pour chaque repo avec une clé, `measures` et `gate` du projet, puis
  `pullRequestGate` pour chaque PR ouverte. Ensuite l'upsert de `SonarSnapshot`, puis
  `live.update(…, new SonarChanged(org))`.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void mainAndPullStatesReachLiveSnapshot() { /* fake : repo vauban, main success, PR #298 pending → RepoView.mainState SUCCESS, pulls[0].state PENDING */ }
@Test void notFoundRepoDoesNotStopOthers() { /* fake : repo "empty" lève ForgeNotFoundException, repo vauban OK → vauban présent dans le snapshot, pas d'exception */ }
@Test void archivedReposAreSkipped() { /* … */ }
@Test void sonarMeasuresAndPrGateReachSnapshot() { /* FakeSonarApi : vidocq-vauban ncloc 41814 ; PR 298 gate ERROR → RepoView.sonar.ncloc 41814, PullView.sonarGate ERROR */ }
@Test void sonarDisabledLeavesSonarNull() { /* enabled() false → aucun appel, sonar null */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest='PullsLoopIntegrationTest,SonarLoopIntegrationTest'` → FAIL.
- [ ] **Step 3 : Implémenter** les signatures et le comportement ci-dessus.
- [ ] **Step 4 : Vérifier qu'ils passent.** Même commande → PASS.
- [ ] **Step 5 : Commit** → `feat(collect): pull request, main branch and Sonar loops`

---

### Task 9 : Boucle commits (rétro-chargement 90 jours + incrémental)

**Files :**
- Create : `.../dashboard/collect/CommitsLoop.java`, `.../dashboard/config/CommitterConfig.java`
  (bots et alias lus par MP Config : `dashboard.committers.bots` (CSV), `dashboard.committers.aliases`
  (CSV `nom=login`))
- Test : `.../dashboard/collect/CommitsLoopIntegrationTest.java`

**Interfaces :**
- Consumes : `ForgeApi.listCommits`, `listPullsUpdatedSince`, `pullAuthorForCommit` ; `CommitClassifier`
  (Task 2) ; les repositories `CommitStat`, `PrStat` et `RepoRow` (curseur `last_commit_at`) ; `LiveState`.
- Produces : `@ApplicationScoped class CommitsLoop { void tick(String org); }`
- Comportement :
  - `since` = `last_commit_at` du repo, sinon `now - 90 j`. On pagine jusqu'à une page vide.
  - Chaque commit est classé par `CommitClassifier`. Pour `AGENT_VIA_BOT`, `humanKey` vient de
    `pullAuthorForCommit`, sinon vaut `"?agent"`.
  - Upsert de `CommitStat` par `org/repo@sha`, puis mise à jour du curseur.
  - PR : `listPullsUpdatedSince(since)`, puis upsert de `PrStat`.
  - Publie `CommitsChanged`.
  - Le rétro-chargement est étalé : au plus 20 pages par tick et par org. Le curseur sert de reprise.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void firstTickBackfillsAndStoresClassification() { /* fake : 3 commits (yblazart+Opus, vidocq-bot, Vidocq Bot+Claude avec PR author "yblazart") → 3 CommitStat ; le 3e a humanKey "yblazart", kind AGENT_VIA_BOT */ }
@Test void secondTickIsIncremental() { /* 2e tick : listCommits reçoit since == last_commit_at du 1er tick */ }
@Test void backfillIsCappedPerTick() { /* fake : 30 pages → tick 1 lit 20 pages, tick 2 lit le reste */ }
@Test void agentViaBotWithoutPrIsUnattributed() { /* pullAuthorForCommit empty → humanKey "?agent" */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest=CommitsLoopIntegrationTest` → FAIL.
- [ ] **Step 3 : Implémenter** les signatures et le comportement ci-dessus.
- [ ] **Step 4 : Vérifier qu'ils passent.** Même commande → PASS.
- [ ] **Step 5 : Commit** → `feat(collect): committer stats loop with 90-day backfill and bot/agent attribution`

---

### Task 10 : Planificateur, backoff, santé et démarrage

**Files :**
- Create : `.../dashboard/collect/Backoff.java` (pur), `LoopRunner.java`, `CollectorScheduler.java`,
  `OrgRegistry.java`
- Test : `.../dashboard/collect/BackoffTest.java`, `LoopRunnerTest.java`

**Interfaces :**
- Consumes : les boucles des Tasks 7 à 9, `TrackedOrgRepository`, `LiveState`, `ChangeBus`, `StatsQueries.purge`.
- Produces :
```java
final class Backoff { static Duration next(Duration base, int consecutiveFailures, Optional<Duration> retryAfter); }
// base × 2^failures, plafonné à 15 min ; si retryAfter est présent : max(calcul, retryAfter)
final class LoopRunner {           // une boucle nommée, avec son état de santé
    LoopRunner(String name, Duration cadence, Runnable tick, Clock clock, Consumer<LoopHealth> onHealth);
    Duration runOnce();            // exécute le tick, met à jour la santé, renvoie le délai avant la prochaine passe
    LoopHealth health();
}
@ApplicationScoped class OrgRegistry {
    List<String> activeOrgs();                         // lu en base, mis en cache, invalidé sur OrgsChanged
    void seedIfEmpty(List<String> initialOrgs);        // DASHBOARD_INITIAL_ORGS au premier démarrage
}
@ApplicationScoped class CollectorScheduler {
    void onStart(@Observes Startup event);             // seed des orgs, puis planification
    void onStop(@Observes Shutdown event);
    List<LoopHealth> health();
}
```
- Cadences (MP Config, en secondes) :

| Clé | Défaut |
|---|---|
| `dashboard.collect.runners.seconds` | 15 |
| `dashboard.collect.pulls.seconds` | 90 |
| `dashboard.collect.repos.seconds` | 300 |
| `dashboard.collect.sonar.seconds` | 900 |
| `dashboard.collect.commits.seconds` | 3600 |
| `dashboard.collect.purge.seconds` | 86400 |

- Les boucles sont des `LoopRunner` par type ; chaque tick boucle sur `activeOrgs()`.
- Une erreur sur une org est loguée et compte dans la santé, sans interrompre les autres orgs.
- `ScheduledExecutorService`, un planificateur par boucle, avec les ticks exécutés sur des threads virtuels.
- La santé est publiée via `ChangeBus.publish(new HealthChanged())`.
- `CollectorScheduler` reste désactivable par `dashboard.collect.enabled=false` : les tests IT des Tasks 5
  à 9 le désactivent.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void backoffDoublesAndCaps() {
    assertEquals(Duration.ofSeconds(15), Backoff.next(Duration.ofSeconds(15), 0, Optional.empty()));
    assertEquals(Duration.ofSeconds(30), Backoff.next(Duration.ofSeconds(15), 1, Optional.empty()));
    assertEquals(Duration.ofSeconds(120), Backoff.next(Duration.ofSeconds(15), 3, Optional.empty()));
    assertEquals(Duration.ofMinutes(15), Backoff.next(Duration.ofSeconds(15), 10, Optional.empty()));
    assertEquals(Duration.ofSeconds(600), Backoff.next(Duration.ofSeconds(15), 1, Optional.of(Duration.ofSeconds(600))));
}
@Test void failingTickKeepsStateAndMarksStale() {
    // tick qui lève RateLimitedException(600 s) ; LiveState pré-rempli
    // → runOnce() == 600 s ; health().lastError non null ; health().stale(now + 1 h) ; LiveState inchangé
}
@Test void successResetsFailures() { /* échec puis succès → délai == cadence, lastSuccess == now */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest='BackoffTest,LoopRunnerTest'` → FAIL.
- [ ] **Step 3 : Implémenter** les signatures ci-dessus. `Clock` est injecté depuis `ClockProducer` (Task 6).
- [ ] **Step 4 : Vérifier qu'ils passent, puis faire un essai réel.** Même commande → PASS.
  Lancer `FORGEJO_TOKEN=… SONAR_TOKEN=… ./mvnw -ntp -pl forge-dashboard-app vidocq:dev`. Au bout de
  2 minutes, les logs montrent des ticks `runners`, `pulls`, `repos` et `sonar` réussis, et le
  rétro-chargement des commits en cours. Vérification en base :
  `select count(*) from job_execution` > 0 et `select count(*) from commit_stat` > 0.
- [ ] **Step 5 : Commit** → `feat(collect): scheduler with per-loop backoff, health and startup wiring`

---

### Task 11 : Connexion OIDC CodeFloe

**Files :**
- Create : `.../dashboard/auth/` :
  - `OidcSettings.java`, `Pkce.java`, `AuthorizationRequest.java`, `LoginState.java`, `DashboardUser.java`,
    `GroupGuard.java` ;
  - ports `TokenEndpoint.java`, `IdTokenVerifier.java`, avec `TokenResponse.java` et `VerifiedIdentity.java` ;
  - `CallbackHandler.java`, `CallbackResult.java` ;
  - adaptateurs `HttpTokenEndpoint.java` (`java.net.http`) et `CervantesIdTokenVerifier.java` ;
  - `OidcFilter.java` (`@WebFilter`), `CallbackServlet.java` (`@WebServlet("/oauth2/callback")`),
    `LogoutServlet.java` (`@WebServlet("/oauth2/logout")`), `ForbiddenPage.java` (HTML statique minimal).
- Create : `forge-dashboard-app/src/main/resources/META-INF/web.xml` : `cookie-config` (`http-only` true,
  `secure` true, `attribute` `SameSite=Lax`) et le `filter-mapping` d'`OidcFilter` en premier.
- Modify : `pom.xml` (+ `vidocq-runtime-cervantes-jwt-extension`), `module-info.java`
  (+ `requires io.vidocq.runtime.extensions.microprofile.cervantes; requires java.net.http;`),
  `vidocq.properties` :
  - `mp.jwt.verify.publickey.location=https://codefloe.com/login/oauth/keys`
  - `mp.jwt.verify.issuer=https://codefloe.com`
  - `mp.jwt.verify.audiences=${OIDC_CLIENT_ID}`
  - `mp.jwt.verify.publickey.algorithm=RS256`
  - `vidocq.servlet.session-timeout=43200`
- Test : `.../dashboard/auth/PkceTest.java`, `AuthorizationRequestTest.java`, `GroupGuardTest.java`,
  `CallbackHandlerTest.java` ; modifier `HealthTest` (Task 1)

**Interfaces :**
- Produces :
```java
record OidcSettings(URI issuer, String clientId, String clientSecret, URI publicUrl, Set<String> allowedGroups, Set<String> adminGroups) {
    URI redirectUri();   // publicUrl + "/oauth2/callback"
}
final class Pkce { static String newVerifier(); static String challengeS256(String verifier); }
final class AuthorizationRequest { static URI build(OidcSettings s, String state, String nonce, String codeChallenge); }
record LoginState(String state, String nonce, String verifier, String returnTo) { static final String ATTR = "dashboard.login"; }
record DashboardUser(String login, String name, String pictureUrl, Set<String> groups, boolean admin) { static final String ATTR = "dashboard.user"; }
final class GroupGuard { static boolean allowed(Set<String> userGroups, Set<String> allowed); }
interface TokenEndpoint { TokenResponse exchange(String code, String codeVerifier, URI redirectUri); }
record TokenResponse(String idToken, String accessToken) {}
interface IdTokenVerifier { VerifiedIdentity verify(String idToken); }   // lève IllegalArgumentException si invalide
record VerifiedIdentity(String login, String name, String picture, Set<String> groups, String nonce) {}
sealed interface CallbackResult {
    record Success(DashboardUser user, String returnTo) implements CallbackResult {}
    record Denied(String reason) implements CallbackResult {}           // → 403 ForbiddenPage
    record Restart() implements CallbackResult {}                       // → une seule redirection vers "/"
}
final class CallbackHandler {
    CallbackHandler(OidcSettings s, TokenEndpoint tokens, IdTokenVerifier verifier);
    CallbackResult handle(Map<String, String> query, LoginState saved /*nullable*/);
}
```
- Règles du handler :
  - `saved == null` → `Restart` ;
  - paramètre `error` présent → `Denied("CodeFloe a refusé : <error>")` ;
  - `state` ≠ `saved.state` → `Restart` ;
  - nonce faux → `Denied` ;
  - groupes hors `allowedGroups` → `Denied("Accès réservé aux groupes …")` ;
  - `admin` vaut `!disjoint(groups, adminGroups)` ;
  - `returnTo` n'est accepté que s'il commence par `/` sans `//`, sinon `/`.
- `OidcFilter` : laisse passer `/oauth2/*` et `/healthz`.
  - Avec un `DashboardUser` en session : la requête continue.
  - Sinon, pour une requête Vaadin UIDL ou XHR (`v-r=uidl` ou en-tête `X-Requested-With`) : réponse 401
    (Vaadin affiche « session expirée »).
  - Sinon : on crée le `LoginState`, on le range en session et on redirige (302) vers `AuthorizationRequest`.
- `CallbackServlet` : en cas de `Success`, `request.changeSessionId()`, on range `DashboardUser` dans la
  session, on retire `LoginState` et on redirige vers `returnTo`.
- `LogoutServlet` : `session.invalidate()`, puis une page « Déconnecté » avec un lien « Se reconnecter ».
- `CervantesIdTokenVerifier` : `@Inject JwtValidator`, puis `validate(idToken)`. Il lit les claims
  `preferred_username`, `name`, `picture`, `groups` (tableau JSON) et `nonce`.
- `HttpTokenEndpoint` : POST `application/x-www-form-urlencoded` vers
  `https://codefloe.com/login/oauth/access_token` (`grant_type=authorization_code`, `code`, `redirect_uri`,
  `client_id`, `client_secret`, `code_verifier`). Jamais de log du corps.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void pkceMatchesRfc7636Vector() {
    assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                 Pkce.challengeS256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    assertTrue(Pkce.newVerifier().matches("[A-Za-z0-9_-]{43,128}"));
}
@Test void authorizationUrlCarriesAllParameters() {
    var uri = AuthorizationRequest.build(settings(), "S", "N", "C");
    assertEquals("https://codefloe.com/login/oauth/authorize", uri.getScheme() + "://" + uri.getHost() + uri.getPath());
    var q = query(uri);
    assertEquals("code", q.get("response_type"));
    assertEquals("cid", q.get("client_id"));
    assertEquals("https://forge-dashboard.vidocq.dev/oauth2/callback", q.get("redirect_uri"));
    assertEquals("openid profile email groups", q.get("scope"));
    assertEquals("S256", q.get("code_challenge_method"));
    assertEquals("C", q.get("code_challenge")); assertEquals("S", q.get("state")); assertEquals("N", q.get("nonce"));
}
@Test void groupGuard() {
    assertTrue(GroupGuard.allowed(Set.of("Vidocq:devs"), Set.of("Vidocq:owners", "Vidocq:devs")));
    assertFalse(GroupGuard.allowed(Set.of("VidocqTools"), Set.of("Vidocq:owners", "Vidocq:devs")));
}
@Test void successfulCallbackBuildsAdminUser() { /* faux ports : identité yblazart, groupes [Vidocq:owners], nonce N → Success(user.admin == true, returnTo "/ci") */ }
@Test void missingLoginStateRestartsOnce() { assertInstanceOf(CallbackResult.Restart.class, handler.handle(Map.of("code","x","state","S"), null)); }
@Test void stateMismatchRestarts() { /* … */ }
@Test void userOutsideGroupsIsDenied() { /* groupes [VidocqTools] → Denied */ }
@Test void nonceMismatchIsDenied() { /* … */ }
@Test void openRedirectIsNeutralised() { /* saved.returnTo "https://evil.example" ou "//evil" → Success.returnTo == "/" */ }
// HealthTest (Task 1), à modifier :
@Test void rootRedirectsToCodefloe(VidocqApplication app) { /* GET "/" → 302, Location commence par https://codefloe.com/login/oauth/authorize */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app verify -Dtest='PkceTest,AuthorizationRequestTest,GroupGuardTest,CallbackHandlerTest,HealthTest'` → FAIL.
- [ ] **Step 3 : Implémenter** les signatures et le comportement ci-dessus. Lire `OIDC_CLIENT_ID`,
  `OIDC_CLIENT_SECRET`, `DASHBOARD_PUBLIC_URL`, `DASHBOARD_ALLOWED_GROUPS` et `DASHBOARD_ADMIN_GROUPS` via MP Config.
- [ ] **Step 4 : Vérifier qu'ils passent, puis faire un essai réel.** Même commande → PASS.
  Lancer avec `DASHBOARD_PUBLIC_URL=http://localhost:8080` et `OIDC_CLIENT_ID/SECRET` (depuis
  `DASHBOARD_OIDC_CLIENT_ID/SECRET` de `tokens.env`), puis `./mvnw vidocq:dev`. Dans le navigateur
  (Claude in Chrome), `http://localhost:8080/` doit enchaîner la connexion CodeFloe, le retour et la page
  du dashboard. `/oauth2/logout` doit déconnecter.
- [ ] **Step 5 : Commit** → `feat(auth): CodeFloe OIDC login with PKCE, Cervantes id_token validation and group guard`

---

### Task 12 : Socle UI (Instantiator CDI, layout, préférences, push)

**Files :**
- Create : `.../dashboard/ui/cdi/VaubanInstantiatorFactory.java`, `VaubanInstantiator.java`
- Create : `.../dashboard/ui/CurrentUser.java`, `UserPrefs.java` (service), `ViewMode.java`,
  `MainView.java` (remplace celui de la Task 1), `FreshnessBadge.java`, `LiveBinding.java`
- Create : `.../dashboard/ui/model/` (modèles purs des panneaux, remplis par les Tasks 13 à 15)
- Modify : `module-info.java` :
  - `provides com.vaadin.flow.di.InstantiatorFactory with io.vidocq.tools.dashboard.ui.cdi.VaubanInstantiatorFactory;`
  - `requires vaadin.tabs.flow; requires vaadin.button.flow; requires vaadin.avatar.flow; requires vaadin.menu.bar.flow;`
- Test : `.../dashboard/ui/UserPrefsIntegrationTest.java`, `.../dashboard/ui/LiveBindingTest.java`

**Interfaces :**
- Produces :
```java
enum ViewMode { PROJECTS, CI, COMMITTERS }
record UserPreferences(String login, ViewMode view, int periodDays /*7|30|90*/, String lastOrg /*nullable*/) {
    static UserPreferences defaults(String login);   // PROJECTS, 30, null
}
@ApplicationScoped class UserPrefs { UserPreferences load(String login); void save(UserPreferences p); }
final class CurrentUser { static Optional<DashboardUser> get(); }   // via VaadinSession.getCurrent().getSession().getAttribute(DashboardUser.ATTR)
final class LiveBinding {   // relie un composant attaché au ChangeBus
    static Registration bind(UI ui, ChangeBus bus, Predicate<ChangeEvent> interest, Runnable refreshInUi);
    // abonnement à l'attach, retrait au detach ; refreshInUi via ui.access ; plusieurs événements proches fusionnés (≤ 1 refresh / 500 ms)
}
public class VaubanInstantiatorFactory implements InstantiatorFactory { Instantiator createInstantitor(VaadinService s); }
class VaubanInstantiator extends DefaultInstantiator { <T> T getOrCreate(Class<T> type); } // CDI.current().select(type) si isResolvable(), sinon super
```
- `MainView` (`@Route("")`, `@Dependent`, injecté par constructeur : `LiveState`, `ChangeBus`, `UserPrefs`,
  `OrgRegistry`, `CollectorScheduler`, plus les panneaux des Tasks 13 à 15 via `Instance<…>`) :
  - en-tête : `Tabs` des orgs (`OrgRegistry.activeOrgs()`) et un onglet « + » si admin (Task 16) ;
  - au centre de l'en-tête : la bascule `Projets | CI | Commiters`, faite de 3 boutons groupés, et mémorisée ;
  - à droite : `FreshnessBadge` (« frais il y a N s », ou « périmé » en orange si une boucle est `stale`),
    puis l'avatar avec un menu « Se déconnecter » (`/oauth2/logout`) ;
  - zone de contenu : le panneau de la vue courante pour l'org courante.
- Au premier affichage, on restaure `view`, `periodDays` et `lastOrg`. Chaque changement est enregistré
  par `UserPrefs.save`.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void prefsRoundTrip() { /* save(yblazart, CI, 7, "VidocqTools") puis load → identique ; load(inconnu) == defaults */ }
@Test void burstOfEventsIsCoalesced() {
    // LiveBinding avec un exécuteur UI factice : 10 événements en 100 ms → 1 seul refresh dans les 600 ms
}
@Test void uninterestingEventsAreIgnored() { /* interest = RunnersChanged("Vidocq") ; PullsChanged → aucun refresh */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest='LiveBindingTest,UserPrefsIntegrationTest'` → FAIL.
- [ ] **Step 3 : Implémenter.** `LiveBinding` prend un `Consumer<Command>` d'accès UI injectable, pour pouvoir
  être testé sans UI réelle (en production : `ui::access`).
- [ ] **Step 4 : Vérifier qu'ils passent, puis vérifier dans le navigateur.** Même commande → PASS. Dans le
  navigateur :
  - les onglets Vidocq et VidocqTools s'affichent ;
  - la bascule change le contenu (provisoire) ;
  - la vue choisie est retrouvée après un rechargement ;
  - le badge de fraîcheur bouge tout seul, par push. Noter le transport utilisé dans l'onglet réseau :
    101 sur `/VAADIN/push`, ou repli long polling.
- [ ] **Step 5 : Commit** → `feat(ui): CDI instantiator, main layout with org tabs, view toggle, freshness and live push binding`

---

### Task 13 : Vue Projets et panneau PR

**Files :**
- Create : `.../dashboard/ui/model/ProjectRow.java`, `ProjectsModel.java`, `PullDetailsModel.java`
- Create : `.../dashboard/ui/ProjectsPanel.java` (`@Dependent`), `PullDetailsDrawer.java`
- Modify : `module-info.java` (+ `requires vaadin.grid.flow; requires vaadin.details.flow;` si utilisés)
- Test : `.../dashboard/ui/model/ProjectsModelTest.java`, `PullDetailsModelTest.java`

**Interfaces :**
- Consumes : `OrgSnapshot`/`RepoView`/`PullView` (Task 6) ; `JobExecutionRepository.findByOrgAndRepoAndPrNumberOrderByStartedAtDesc` (Task 5) ; `Formats` (Task 2).
- Produces :
```java
record ProjectRow(String repo, CiState main, String loc, String tests, String coverage, GateStatus gate,
                  int prGreen, int prRed, int prPending, String lastCommit, String htmlUrl) {}
final class ProjectsModel { static List<ProjectRow> rows(OrgSnapshot s, Instant now); }   // tri : main rouge d'abord, puis nom
record PullJobLine(String jobName, String runnerName /*"?" si inconnu*/, String status, String duration, String htmlUrl) {}
record PullDetails(String title, String author, CiState state, List<StatusContext> contexts, GateStatus sonarGate,
                   List<PullJobLine> lastRunJobs, String htmlUrl) {}
final class PullDetailsModel { static PullDetails of(PullView pr, List<JobExecution> jobs, Map<Long, String> runnerNames, Instant now); }
```
- Colonnes de la grille : repo, main (pastille), LOC, tests, couv., gate, PR (`1✅ 0❌ 1⏳`), dernier commit.
  Sans données Sonar, les colonnes affichent « — ».
- Un clic sur le compteur de PR déplie la liste des PR du repo. Un clic sur une PR ouvre `PullDetailsDrawer`
  (panneau latéral droit).

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void rowCountsPullStatesAndFormatsSonar() { /* vauban : ncloc 41814, tests 2077, cov 86.9, PR [SUCCESS, PENDING] → loc "41.8k", tests "2077", coverage "86.9%", prGreen 1, prPending 1 */ }
@Test void missingSonarShowsDash() { /* sonar null → loc "—", gate NONE */ }
@Test void redMainSortsFirst() { /* … */ }
@Test void pullDetailsListsJobsWithRunnerOrQuestionMark() { /* 2 jobs, un runnerId null → runnerName "?" */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest='ProjectsModelTest,PullDetailsModelTest'` → FAIL.
- [ ] **Step 3 : Implémenter.** Modèles purs, et vue construite à partir des modèles. La vue se rafraîchit via
  `LiveBinding` sur `PullsChanged(org)` et `SonarChanged(org)`.
- [ ] **Step 4 : Vérifier qu'ils passent, puis vérifier dans le navigateur.** Même commande → PASS. Dans le navigateur :
  - l'onglet Vidocq montre ses ~32 repos, avec l'état du main et les métriques de vauban conformes à Sonar ;
  - un clic sur une PR ouvre le panneau, avec ses jobs et leurs runners.
- [ ] **Step 5 : Commit** → `feat(ui): projects view with main state, Sonar metrics and pull request drawer`

---

### Task 14 : Vue CI, panneau runner, occupation

**Files :**
- Create : `.../dashboard/ui/model/CiBoardModel.java`, `RunnerCard.java`, `RunnerDetailsModel.java`
- Create : `.../dashboard/ui/CiPanel.java`, `RunnerDetailsDrawer.java`, `Sparkline.java` (SVG en ligne),
  `.../dashboard/collect/OccupancyService.java`
- Test : `.../dashboard/ui/model/CiBoardModelTest.java`, `RunnerDetailsModelTest.java`, `SparklineTest.java`,
  `.../dashboard/collect/OccupancyServiceIntegrationTest.java` (deux jobs en base sur un runner de capacité 2
  → `occupancy("Vidocq", 1 h).get(id).slotRatio()` égal au calcul attendu ; `hourly` a 24 valeurs)

**Interfaces :**
- Consumes : `OrgSnapshot` (runners, waiting) ; `StatsQueries.jobIntervals` / `statusChanges` et
  `OccupancyCalculator` (Tasks 5 et 6) ; `JobExecutionRepository.findByRunnerIdAndStartedAtGreaterThanOrderByStartedAtDesc`.
- Produces :
```java
record RunnerCard(long runnerId, String name, String status, String load /*"3/4"*/, List<JobView> running,
                  double occupancy, List<Double> hourlyOccupancy /*24 valeurs*/) {}
record CiBoard(List<RunnerCard> runners, List<JobView> waiting /*plus ancien d'abord*/, int waitingCount) {}
final class CiBoardModel { static CiBoard of(OrgSnapshot s, Map<Long, Occupancy> occupancy, Map<Long, List<Double>> hourly); }
record RunnerPrGroup(String repo, Integer prNumber, String ref, List<JobView> jobs) {}
final class RunnerDetailsModel { static List<RunnerPrGroup> groups(List<JobExecution> recent, int limit /*20 groupes*/); }
final class Sparkline { static String svg(List<Double> values /*0..1*/, int width, int height); }
@ApplicationScoped class OccupancyService {   // fichier collect/OccupancyService.java ; cache 60 s par (org, fenêtre)
    Map<Long, Occupancy> occupancy(String org, Duration window);   // par runnerId ; capacité = override ou observedCapacity sur 7 j
    Map<Long, List<Double>> hourly(String org);                    // 24 valeurs slotRatio, dernière heure en dernier
}
```
- Fenêtres d'occupation sélectionnables : 1 h, 24 h, 7 j (24 h par défaut). Le graphe horaire couvre
  toujours les 24 dernières heures.
- Le calcul se fait à l'ouverture de la vue et à chaque `RunnersChanged`, au plus une fois par minute
  (cache `@ApplicationScoped` par org et par fenêtre).
- Statuts des runners : `● actif`, `○ inactif`, `✕ hors ligne`. Les runners hors ligne sont rangés en dernier.
- Un clic sur un runner ouvre `RunnerDetailsDrawer` : jobs en cours et jobs récents groupés par PR, avec un
  lien CodeFloe. Un clic sur un groupe PR ouvre le `PullDetailsDrawer` de la Task 13.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void runnerCardShowsLoadAgainstCapacity() { /* runner capacity 4, 3 jobs → load "3/4" */ }
@Test void waitingIsOldestFirst() { /* … */ }
@Test void offlineRunnersSortLast() { /* … */ }
@Test void recentJobsGroupByPr() { /* 3 jobs vauban#298, 1 job vidocq push main → 2 groupes, #298 avec 3 jobs */ }
@Test void sparklineScalesToHeight() { /* svg([0.0, 1.0], 20, 10) contient un point y=10 et un point y=0 */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest='CiBoardModelTest,RunnerDetailsModelTest,SparklineTest,OccupancyServiceIntegrationTest'` → FAIL.
- [ ] **Step 3 : Implémenter** les signatures et le comportement ci-dessus.
- [ ] **Step 4 : Vérifier qu'ils passent, puis vérifier dans le navigateur.** Même commande → PASS. Dans le navigateur, sur l'onglet CI :
  - les runners Vidocq s'affichent avec leurs jobs en cours (comparer avec CodeFloe) ;
  - la file d'attente évolue toute seule ;
  - un clic sur `CodefloeRunnerMac` liste ses PR ;
  - depuis une PR, on retombe sur les runners.
- [ ] **Step 5 : Commit** → `feat(ui): CI view with runner load, queue, occupancy and runner↔PR drawers`

---

### Task 15 : Vue Commiters

**Files :**
- Create : `.../dashboard/ui/model/CommittersModel.java`, `CommitterRow.java`
- Create : `.../dashboard/ui/CommittersPanel.java`, `CommitterDetailsDrawer.java`
- Test : `.../dashboard/ui/model/CommittersModelTest.java`

**Interfaces :**
- Consumes : `StatsQueries.committerAggregates` / `prCounts` (Task 5) ; `CommitStatRepository` pour le détail
  (commits récents d'un humain) ; `UserPreferences.periodDays`.
- Produces :
```java
record CommitterRow(String humanKey, long commits, long additions, long deletions, long prsOpened, long prsMerged,
                    LinkedHashMap<String, Long> breakdown /*"seul" puis familles par volume décroissant*/) {}
record CommittersView(List<CommitterRow> humans, List<CommitterRow> automation, CommitterRow unattributed /*nullable, clé "?agent"*/) {}
final class CommittersModel { static CommittersView of(List<CommitterAggregate> aggregates, List<PrCounts> prs); }
```
- Les `BOT` vont dans `automation`. Les `HUMAN` et `AGENT_VIA_BOT` résolus vont dans `humans`, par `humanKey`.
- Les agrégats multi-agents (`"Claude Opus,Codex"`) sont comptés une fois dans chaque famille de la barre
  (la barre peut dépasser le total, ce qui est indiqué en infobulle), mais une seule fois dans `commits`.
- Tri par commits décroissants. Filtre : org courante ou toutes (case à cocher). Période : 7, 30 ou 90 jours,
  mémorisée.
- Barre empilée : un `Div` par segment, dont la largeur est le pourcentage. Couleurs fixes par famille.
- Infobulle : « Part des agents = minimum (commits sans trailer comptés comme humains) ».

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void humansAutomationAndUnattributedAreSeparated() { /* yblazart HUMAN, vidocq-bot BOT, ?agent AGENT_VIA_BOT → 1 humain, 1 automation, unattributed non null */ }
@Test void breakdownStartsWithAloneThenByVolume() { /* yblazart : seul 10, Claude Opus 30, Codex 5 → clés [seul, Claude Opus, Codex] */ }
@Test void agentViaBotResolvedJoinsHuman() { /* AGENT_VIA_BOT humanKey yblazart → ajouté à la ligne yblazart */ }
@Test void prCountsAreJoined() { /* … */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest=CommittersModelTest` → FAIL.
- [ ] **Step 3 : Implémenter** les signatures et le comportement ci-dessus.
- [ ] **Step 4 : Vérifier qu'ils passent, puis vérifier dans le navigateur.** Même commande → PASS. Dans le navigateur :
  - sur 30 jours, l'ordre de grandeur correspond aux chiffres de la spec (Yann ≈ 77 %, Claude Opus dominant) ;
  - la section Automatisation est séparée ;
  - le clic sur un humain affiche ses commits récents, avec les liens de session.
- [ ] **Step 5 : Commit** → `feat(ui): committers view with human/agent breakdown, automation and period filter`

---

### Task 16 : Gestion des orgs (onglet « + »)

**Files :**
- Create : `.../dashboard/collect/OrgAdmin.java`, `.../dashboard/ui/OrgAdminDialog.java`
- Modify : `module-info.java` (+ `requires vaadin.dialog.flow; requires vaadin.text.field.flow;`)
- Test : `.../dashboard/collect/OrgAdminIntegrationTest.java`

**Interfaces :**
- Consumes : `ForgeApi.canReadRunners` (Task 3), `TrackedOrgRepository`, `ChangeBus`, `OrgRegistry` (Task 10).
- Produces :
```java
sealed interface OrgAdminResult { record Added(String org) implements OrgAdminResult {} record Rejected(String reason) implements OrgAdminResult {} }
@ApplicationScoped class OrgAdmin {
    OrgAdminResult add(String org, DashboardUser by);   // admin requis ; nom normalisé (trim) ; refus si déjà suivie ou si le jeton ne lit pas les runners
    void deactivate(String org, DashboardUser by);      // admin requis ; active=false, données conservées
}
```
- Publie `OrgsChanged`. Les onglets se mettent à jour pour toutes les UI ouvertes.

- [ ] **Step 1 : Écrire les tests qui échouent**
```java
@Test void adminAddsAccessibleOrg() { /* fake canReadRunners("AkoyoGames") true → Added, présente dans activeOrgs */ }
@Test void inaccessibleOrgIsRejectedWithReason() { /* canReadRunners false → Rejected("Le jeton de service n'a pas accès aux runners de …") */ }
@Test void nonAdminIsRejected() { /* … */ }
@Test void duplicateIsRejected() { /* … */ }
@Test void deactivatedOrgDisappearsFromTabs() { /* … */ }
```
- [ ] **Step 2 : Vérifier qu'ils échouent.** `./mvnw -ntp -pl forge-dashboard-app test -Dtest=OrgAdminIntegrationTest` → FAIL.
- [ ] **Step 3 : Implémenter.** Le dialogue est ouvert par l'onglet « + » (admins uniquement). Le retrait se fait
  par un menu sur l'onglet.
- [ ] **Step 4 : Vérifier qu'ils passent.** Même commande → PASS. Dans le navigateur, ajouter puis retirer une org
  de test : l'onglet apparaît puis disparaît.
- [ ] **Step 5 : Commit** → `feat(ui): add and deactivate tracked orgs from the dashboard`

---

### Task 17 : Livraison (image, stack, CI, docs)

**Files :**
- Modify : `Dockerfile`.
  - Base `bellsoft/liberica-openjre-debian:25`, avec `wget` pour le healthcheck.
  - `COPY forge-dashboard-app/target/forge-dashboard-app-*/ /app/`
  - `ENV JAVA_OPTS="-Dvaadin.productionMode=true"`
  - `ENTRYPOINT ["/app/bin/forge-dashboard-app.sh"]`
  - `HEALTHCHECK` sur `http://localhost:8080/healthz`.
- Modify : `docker-compose.yml`.
  - Services `forge-dashboard` et `postgres:17` (volume nommé `forge-dashboard-db`, réseau interne
    `dashboard-internal`) ; `forge-dashboard` est aussi sur `reverseproxy_default`.
  - Variables : `FORGEJO_URL`, `FORGEJO_TOKEN`, `SONAR_URL`, `SONAR_TOKEN`, `OIDC_CLIENT_ID`,
    `OIDC_CLIENT_SECRET`, `DASHBOARD_PUBLIC_URL=https://forge-dashboard.vidocq.dev`, `DB_URL` / `DB_USER` /
    `DB_PASSWORD`, `DASHBOARD_ALLOWED_GROUPS`, `DASHBOARD_ADMIN_GROUPS`.
  - Pas de `container_name` en dur pour `postgres`.
- Modify : `.forgejo/workflows/build-deploy.yml`.
  - Retirer l'installation de Maven 4 RC et utiliser `./mvnw -ntp -B -U package -DskipTests`. Garder le
    pattern DinD, le runner `vidocq-dind`, catthehacker et la détection de `DOCKER_HOST`.
  - Java 25 (`liberica` ou `temurin`).
  - Les tests IT (Docker requis) tournent hors CI, sauf si le runner DinD les supporte. Le constater, sans
    supposer.
- Modify : `README.md` (lancement dev, variables, section « Contournements »), `CLAUDE.md` (stack et
  endpoints à jour : Vaadin, OIDC, `/healthz`, CodeFloe), `.dockerignore`.
- Modify : la mémoire `forge-dashboard-codeberg-migration.md`, si un fait nouveau et non dérivable apparaît.

- [ ] **Step 1 : Construire et lancer l'image en local**
Run : `./mvnw -ntp -U package -DskipTests && docker build -t forge-dashboard:dev .`
Puis `docker compose -f docker-compose.yml -f compose.local.yml up` (`compose.local.yml` est **non commité**, il
expose le port 8080 et pose `DASHBOARD_PUBLIC_URL=http://localhost:8080`).
Expected : `curl localhost:8080/healthz` → `OK`, et la connexion CodeFloe marche dans le navigateur.
- [ ] **Step 2 : Lint du workflow**
Relire le YAML et comparer au pattern de `vidocq-forgejo-ci-dind-pattern` (mémoire), en particulier la
normalisation des secrets collés avec `\n`.
- [ ] **Step 3 : Commit** → `build: JRE 25 image, Portainer stack with PostgreSQL and CI on Maven wrapper`

---

### Task 18 : Validation de bout en bout, push et retours écosystème, déploiement

- [ ] **Step 1 : Test d'inactivité du push.**
  1. Ouvrir le dashboard en local (Claude in Chrome), onglet CI.
  2. Laisser la page inactive 10 min.
  3. Vérifier que la page reçoit encore les mises à jour : le badge de fraîcheur bouge et les jobs changent.
  4. Noter le transport réel et toute boucle de reconnexion sur `/VAADIN/push`, dans l'onglet réseau ou dans
     `read_network_requests`.
- [ ] **Step 2 : Retours.**
  - Envoyer à `FOY` et `VAADIN` (SendMessage) les constats : transport, inactivité, reconnexions, erreurs
    console.
  - Si FOY a publié Jakarta WebSocket : refaire le test avec `-U` et vérifier le 101.
- [ ] **Step 3 : Revue de branche.** Lancer `./mvnw -ntp verify` en entier. Expected : tous les tests au vert.
  Ensuite, analyse Sonar (clé `vidocq-tools-forge-dashboard`) si le projet existe sur le serveur. Sinon,
  demander à l'utilisateur s'il faut le créer.
- [ ] **Step 4 : Déploiement (avec l'accord explicite de l'utilisateur au moment de le faire).**
  1. Pousser la branche (`! git push` par l'utilisateur si Claude est bloqué).
  2. Merger sur `main` selon son choix ; la CI construit l'image.
  3. Mettre à jour la stack Portainer `forge-dashboard` : nouveau compose et variables, dont
     `OIDC_CLIENT_ID/SECRET` tirés de `tokens.env`.
  4. Vérifier `https://forge-dashboard.vidocq.dev/healthz` → `OK` et la connexion de bout en bout.
- [ ] **Step 5 : Commit des éventuels correctifs** et mise à jour du README (contournements actifs, état du
  push).
