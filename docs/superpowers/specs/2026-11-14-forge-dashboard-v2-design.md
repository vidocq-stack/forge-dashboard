# forge-dashboard v2 — design

Date : 2026-11-14 · Statut : en validation

## 1. Intention

Refaire de zéro le dashboard de l'écosystème Vidocq. L'actuel est mort : son conteneur interroge encore
`codeberg.org` alors que la forge est **CodeFloe** (`https://codefloe.com`, Forgejo 17.0.1) depuis 2026-08,
et il n'a ni historique ni vue CI.

Le nouveau dashboard sert deux usages, au choix de l'utilisateur par un bouton de bascule :

- **piloter la CI** : quels runners tournent, sur quoi, qui attend, quelle est l'occupation dans le temps
  (aujourd'hui, ~24 jobs en attente ou en cours pour 7 runners sur l'org Vidocq) ;
- **voir la santé de l'écosystème** : état du `main` de chaque projet, métriques Sonar, PR ouvertes.

S'y ajoutent les **stats par commiter**, qui distinguent le travail humain de celui des agents.

C'est aussi un **banc d'essai de Vidocq 0.4.0-SNAPSHOT** : Vaadin sur Foy, push, Cervantes, Cyrano,
Mansart Data, Flyway, dev services. Ce qui casse est remonté aux sessions concernées (FOY, VAADIN).

## 2. Objectifs, non-objectifs, anti-objectifs

**Objectifs**

- Un onglet par org suivie (au départ `Vidocq` et `VidocqTools`), et la possibilité d'en ajouter.
- Vue **Projets** : par repo, l'état du `main`, les lignes de code, les tests, la couverture, la quality
  gate Sonar, les PR ouvertes (vertes, rouges, en cours) et le dernier commit.
- Vue **CI** :
  - les runners et leur état (actif, inactif, hors ligne) ;
  - le job en cours de chaque runner (repo, PR, nom du job) ;
  - la file d'attente ;
  - l'occupation sur une période.
- **Navigation croisée** :
  - clic sur un runner → les PR et jobs qu'il construit ou a construits récemment ;
  - clic sur une PR → ses jobs et le runner de chacun.
- Vue **Commiters** :
  - par humain : commits, lignes +/−, PR ouvertes et mergées ;
  - pour chacun, une barre empilée « seul / agent X » à partir des trailers `Co-Authored-By` ;
  - une section « Automatisation » pour les bots ;
  - période au choix : 7, 30 ou 90 jours.
- Connexion par **OIDC CodeFloe**, accès limité à des groupes CodeFloe.
- **Préférences mémorisées par utilisateur** : vue choisie, période, dernier onglet d'org.
- **Mise à jour en direct** des écrans ouverts, par push Vaadin.
- Léger et pratique : une page, pas de rechargement, des détails dans un panneau latéral.

**Non-objectifs (v1)**

- Aucune action d'écriture sur CodeFloe : pas de relance ni d'annulation de job, pas de merge.
- Pas de webhooks. On pourra les ajouter plus tard sans rien casser (voir §4).
- Pas d'image jlink en v1 : on livre `vidocq:package` sur un JRE 25. Le jlink vient ensuite.
- Pas d'intégration Portainer ni des hôtes physiques des runners.

**Anti-objectifs** (ce qui serait un échec même si « ça marche »)

- Marteler CodeFloe, instance communautaire : un rate-limit ou un bannissement serait un échec.
- Fuiter un jeton (le jeton de service ou l'id_token) vers le navigateur ou dans les logs.
- Un écran lent ou lourd : la page doit s'ouvrir vite et rester fluide avec ~40 repos et ~12 runners.
- Contourner les bugs Vidocq en silence. Un bug trouvé est remonté, pas masqué.

## 3. Contraintes

- **Socle** :
  - Vidocq runtime **0.4.0-SNAPSHOT**, Java 25 (`sdk env` : `java=25-tem`), JPMS strict ;
  - Maven **3.10.0**, `modelVersion 4.0.0`, BOM `io.vidocq.runtime:vidocq-runtime-bom`,
    `vidocq-runtime-maven-plugin` en `<extensions>true</extensions>` ;
  - snapshots lus sur `https://maven.vidocq.dev/snapshots/`, déclaré dans `<repositories>` **et**
    `<pluginRepositories>`. Plus Sonatype.
- **UI** : Vaadin Flow **25.3.2** sur Foy, recette `VidocqTools/vaadin-on-vidocq` :
  - `vaadin-prod-bundle` en mode production, sans Node ;
  - `vidocq.app.layer=all` ;
  - `build-frontend` lié à `process-classes` ;
  - un fournisseur SLF4J (`slf4j-jdk14`) ;
  - `vidocq-runtime-vaadin-extension`.
- **Pas de CDI Full** : `vaadin-cdi` repose sur une extension portable, que Vauban (CDI Lite) ne charge pas.
- **Pas de Jakarta WebSocket** dans Foy aujourd'hui (vidocq#65). La session FOY l'ajoute, voir §5.6.
- **Pas de Jakarta Security** (vidocq#63, cible 1.1.0). La connexion OIDC est donc écrite dans l'appli
  (§5.5).
- **Pas de Jakarta Concurrency** : planification par `ScheduledExecutorService` et threads virtuels.
- **Persistance** : ne pas mélanger un `@Repository` Mansart Data et un `EntityManager` Noether dans une même
  transaction (deux connexions physiques). On n'utilise que Mansart Data.
- **TDD** : rouge → vert → refactor. Pas de secret commité.

## 4. Approches considérées (collecte des données)

1. **Interrogation périodique à plusieurs vitesses** — **retenue par l'utilisateur.** Le dashboard
   interroge CodeFloe et Sonar à des cadences adaptées à chaque donnée, stocke tout dans PostgreSQL et pousse
   les changements aux écrans ouverts. Il n'y a rien à exposer vers l'extérieur et la charge reste maîtrisée.
   En contrepartie, le délai d'affichage va de 15 s (runners) à 1–2 min (PR).
2. **Webhooks CodeFloe et interrogation des runners** : quasi instantané pour les PR et les commits, mais il
   faut que le dashboard soit joignable depuis codefloe.com, avec un secret de signature et un webhook par
   org. Les événements sont perdus pendant un redémarrage, ce qui impose un rattrapage par interrogation, et
   les runners n'ont pas d'événement. Écartée pour la v1, mais compatible plus tard avec l'approche 1.
3. **Interrogation au moment de l'affichage, avec un cache** (le dashboard actuel) : sans historique, elle
   ne permet ni l'occupation dans le temps ni les stats par commiter. Écartée.

## 5. Conception

### 5.1 Découpage

```
forge-dashboard/
├── pom.xml                      (parent, modelVersion 4.0.0, import BOM Vidocq + vaadin-bom)
├── forge-dashboard-core/        (module io.vidocq.tools.dashboard.core)
│   ├── forge/      clients Cyrano CodeFloe (Forgejo API v1) + DTO JSON-B
│   ├── sonar/      client Cyrano Sonar + DTO
│   ├── domain/     records immuables (Org, Repo, PullRequest, Runner, JobExecution, CommitStat…)
│   ├── collect/    boucles de collecte, résolution runner↔job, parsing trailers
│   ├── store/      repositories Jakarta Data (Mansart) + migrations Flyway (db/migration)
│   └── live/       état courant en mémoire + bus de changements (écouteurs pour l'UI)
└── forge-dashboard-app/         (module io.vidocq.tools.dashboard.app, vidocq.mainModule)
    ├── auth/       filtre OIDC, servlet de callback, logout, garde de groupes
    ├── ui/         vues Vaadin (@Route), AppShell (@Push), composants
    ├── cdi/        Instantiator Vaadin adossé à Vauban
    └── resources/  vidocq.properties
```

L'ancien code (JAX-RS Cassini, HTMX, `static/index.html`) est supprimé.

### 5.2 Sources de données

**CodeFloe (Forgejo API v1)**, avec un jeton de service (`FORGEJO_TOKEN`). Il doit être owner des orgs
suivies, car les endpoints runners l'exigent.

| Donnée | Endpoint |
|---|---|
| Repos d'une org | `GET /orgs/{org}/repos` (paginé) |
| Runners d'une org | `GET /orgs/{org}/actions/runners` → `id, uuid, name, status (idle/active/offline), labels, version` |
| Jobs actifs d'une org | `GET /orgs/{org}/actions/runners/jobs` → jobs `waiting` et `running` avec `repo_id, run_id, task_id, name, runs_on, html_url` |
| Run (pour la PR) | `GET /repos/{o}/{r}/actions/runs/{run_id}` → `event, prettyref (#298), commit_sha, title, started, stopped, status` |
| Jobs d'un run | `GET /repos/{o}/{r}/actions/runs/{run_id}/jobs` |
| Détail ou fin d'un job | `GET /repos/{o}/{r}/actions/jobs/{job_id}` (statut final, `steps[].started/stopped`) |
| Runner d'un job | `GET /repos/{o}/{r}/actions/jobs/{job_id}/logs` : 1re ligne `… Runner <uuid> (version:…) received task <task_id> of job …` |
| PR ouvertes | `GET /repos/{o}/{r}/pulls?state=open` |
| État CI d'un commit | `GET /repos/{o}/{r}/commits/{sha}/status` (état combiné et contextes) |
| Commits (stats) | `GET /repos/{o}/{r}/commits?sha={default}&since=…&stat=true` |
| PR pour les stats | `GET /repos/{o}/{r}/pulls?state=all&sort=recentupdate` |

L'API ne donne pas le runner d'un job. Il est donc déduit de la 1re ligne du log : on la lit **une seule
fois par `task_id`** et le résultat est conservé, puisque cette correspondance ne change jamais. L'uuid est
rapproché de `ActionRunner.uuid`. Seuls les jobs `running` sont concernés.

**Un runner exécute plusieurs jobs à la fois** (`capacity` > 1, constaté le 2026-11-14 : 13 jobs en cours
sur 6 runners, dont 4 sur `CodefloeRunnerMac`). La relation runner → jobs en cours est donc **1:N**, et
l'API ne donne pas la capacité. Elle vient d'une surcharge par runner en base, sinon du maximum de jobs
simultanés observé sur 7 jours (au minimum 1). L'UI affiche « 3/4 » à côté du runner, et sa ligne liste
tous les jobs en cours.

**Sonar** (`https://sonar.vidocq.dev`, `SONAR_TOKEN`, authentification Basic `token:`) :

- `GET /api/measures/component?component={key}&metricKeys=ncloc,tests,coverage,test_failures,test_errors,bugs,vulnerabilities,code_smells,duplicated_lines_density,alert_status`
- `GET /api/qualitygates/project_status?projectKey={key}`, et `&pullRequest={n}` pour une PR
- Correspondance repo → clé : `vidocq-<repo>` par défaut, pour l'org Vidocq. Elle est surchargeable par
  repo en base, avec une valeur « aucune ». Aujourd'hui, aucun repo VidocqTools n'est dans Sonar : la
  colonne affiche « — ».

### 5.3 Collecte (approche 1)

Un `Collector` démarre au boot (`@Observes Startup`) et planifie des boucles sur un
`ScheduledExecutorService`. Les appels HTTP se font sur des threads virtuels.

| Boucle | Cadence | Contenu |
|---|---|---|
| `runners` | 15 s | runners et jobs actifs de chaque org. Pour chaque nouveau job `running` : run (une fois par run), puis tête de log (une fois par task). Pour chaque job disparu de la liste : détail du job pour son statut final et ses horodatages. |
| `prs` | 90 s | repos de chaque org (5 min suffisent), PR ouvertes, état combiné de la tête de chaque PR et de la tête de `main` |
| `sonar` | 15 min | mesures et gate pour chaque repo qui a une clé, gate de chaque PR ouverte |
| `commits` | 60 min | nouveaux commits des branches par défaut depuis le dernier vu. Rétro-chargement de 90 jours au premier démarrage (≈16 000 commits, ≈350 appels, étalés). PR créées et mergées. |

Discipline vis-à-vis de CodeFloe :

- au plus 4 appels simultanés ;
- respect de `429` et `Retry-After`, avec un backoff exponentiel par boucle ;
- `If-None-Match` quand l'endpoint renvoie un `ETag` ;
- le rétro-chargement est étalé.

Une boucle en erreur n'arrête pas les autres. Son dernier succès et sa dernière erreur s'affichent dans un
petit indicateur de fraîcheur, en pied de page.

**Occupation** : on n'échantillonne pas, on stocke des **intervalles**.

- Une `job_execution` = un `task_id`, un runner, un repo, un run, une PR éventuelle, `started_at`,
  `stopped_at` et le statut.
- `started_at` vient de la 1re ligne du log, sinon de la 1re observation en `running`. `stopped_at` vient
  du détail final du job.
- Les changements d'état d'un runner (`idle`/`active`/`offline`) sont enregistrés dans `runner_status_change`.
- Occupation d'un runner sur une fenêtre = somme des intervalles de jobs ÷ (capacité × (fenêtre − temps
  hors ligne)), soit le taux de slots occupés. On affiche aussi la part du temps où il avait **au moins un**
  job.
- La file d'attente est échantillonnée toutes les 15 s, sous forme d'un compteur agrégé par minute.

**`live/`** : la collecte met à jour un état courant immuable (un snapshot par org), remplacé en bloc. Un
changement publie un événement typé (`RunnersChanged(org)`, `PullRequestsChanged(org, repo)`,
`SonarChanged`…) aux écouteurs inscrits. Chaque UI ouverte s'inscrit, et se désinscrit au détachement.

### 5.4 Données (PostgreSQL, Flyway)

Tables principales (le détail des colonnes est laissé au constructeur) :

- `tracked_org` : org suivie, ordre d'affichage, active ou non.
- `repo` : org, nom, branche par défaut, archivé, clé Sonar (surcharge éventuelle).
- `runner`, `runner_status_change`.
- `job_execution` (voir §5.3), `queue_sample` (minute, org, waiting, running).
- `pull_request_state` : dernier état connu des PR ouvertes (aussi en mémoire, la table sert au redémarrage).
- `sonar_snapshot` : dernières mesures par repo ou PR.
- `commit_stat` : sha, repo, date, auteur humain résolu, login CodeFloe, additions, deletions,
  famille d'agent (nullable), lien `Claude-Session` (nullable), bot ou non.
- `pr_stat` : PR créées et mergées par login et par jour.
- `user_pref` : login, vue (`PROJECTS|CI|COMMITTERS`), période (7/30/90), dernier onglet d'org.

Rétention : `job_execution` et `runner_status_change` sur 180 jours, `queue_sample` sur 30 jours, les stats
de commits sans limite. Une purge quotidienne s'en charge.

### 5.5 Authentification (OIDC CodeFloe)

- Le fournisseur est `https://codefloe.com` (découverte `/.well-known/openid-configuration`). Clés à
  `/login/oauth/keys`, en RS256. Portées `openid profile email groups`, flux authorization code avec
  **PKCE S256**.
- Une **application OAuth2 est à déclarer sur CodeFloe** (application confidentielle). URLs de retour :
  `${DASHBOARD_PUBLIC_URL}/oauth2/callback` **et** `http://localhost:8080/oauth2/callback`, cette dernière
  pour `vidocq:dev` et les tests manuels. Forgejo accepte plusieurs URLs de retour par application. Il n'y a
  **aucun contournement de la connexion**, même en dev.
- **Filtre Foy** (`@WebFilter("/*")`) : sans session authentifiée, il redirige vers `authorize`, avec
  `state`, `nonce` et `code_verifier` en `HttpSession`. Il exempte `/oauth2/*` et `/healthz`.
- **Servlet de callback** :
  - elle vérifie `state`, puis échange le code (`java.net.http`, `client_secret` côté serveur) ;
  - elle **valide l'id_token avec le `JwtValidator` de Cervantes** (issuer, audience = client_id,
    signature JWKS, expiration) et vérifie `nonce` ;
  - elle lit `preferred_username`, `name`, `picture` et `groups` ;
  - elle **change l'id de session**, puis redirige vers la page demandée au départ.
- **Garde de groupes** : `DASHBOARD_ALLOWED_GROUPS`, par défaut `Vidocq:owners,Vidocq:devs`. Hors liste,
  la réponse est une page 403 claire. `DASHBOARD_ADMIN_GROUPS`, par défaut `Vidocq:owners`, autorise
  l'ajout et le retrait d'orgs.
- Le jeton d'accès de l'utilisateur n'est **pas** utilisé pour lire CodeFloe. Toutes les données viennent
  du jeton de service, et tous les utilisateurs autorisés voient les mêmes données.
- Logout : on invalide la session locale. Pas de déconnexion côté CodeFloe en v1.
- Forme du code : l'ensemble prend la forme d'un `HttpAuthenticationMechanism` Jakarta Security (une
  interface interne miroir), pour être remplacé par `@OpenIdAuthenticationMechanismDefinition` quand
  vidocq#63 sera livré.
- Cookie de session `Secure`, `HttpOnly`, `SameSite=Lax`. La protection CSRF est celle de Vaadin.

### 5.6 UI Vaadin

**Push** :

- `@Push(transport = Transport.WEBSOCKET_XHR)` sur l'`AppShellConfigurator`.
- Tant que Foy n'a pas Jakarta WebSocket, le client devrait retomber en long polling (à vérifier pendant
  le build). Quand la session FOY publiera son SNAPSHOT (elle prévient FORGE-DASHBOARD), le dashboard
  basculera sans changer de code.
- Critère de validation : un 101 sur `/VAADIN/push`, la page inactive plus de 5 min reçoit encore un push.
- **La v1 peut être livrée en long polling** en attendant Foy (décision utilisateur). Si le repli
  automatique vers le long polling ne marche pas sur Foy, c'est un bug à remonter selon §5.11, et on le
  configure explicitement en attendant.
- Les mises à jour passent par `ui.access(...)` depuis les écouteurs du bus `live/`.
- Ni ces threads ni les callbacks WebSocket de Foy (lot 1, d'après la session FOY) n'ont de contexte CDI
  request ou session actif. Aucun bean `@RequestScoped` ou `@SessionScoped` ne doit donc être atteignable
  depuis le chemin du push :
  - services, bus et clients sont `@ApplicationScoped` ;
  - vues et composants sont `@Dependent` ;
  - l'identité et les préférences sont des attributs de `VaadinSession`.

**CDI** : un `Instantiator` (via `VaadinServiceInitListener` / `InstantiatorFactory`) résout les vues
`@Route` et leurs composants par `CDI.current().select(type)` quand le type est un bean, et délègue à
l'instantiator par défaut sinon. Les vues sont `@Dependent` et injectées par constructeur. Ce code sera
proposé plus tard à Vaadin et à `vidocq-runtime-vaadin-extension`, sous forme d'une BCE pour les scopes.

**Mise en page** :

```
[Vidocq] [VidocqTools] [+ org]      ( Projets | CI | Commiters )      ● frais il y a 8 s   yblazart ▾
─ Projets ─────────────────────────────────────────────────────────────────────────────────────────────
 repo        main  LOC    tests  couv.  gate  PR ouvertes      dernier commit
 vauban      ✅    41.8k  2077   86.9%  OK    1✅ 0❌ 1⏳      il y a 2 h — docs: @ClientProxies…
─ CI ──────────────────────────────────────────────────────────────────────────────────────────────────
 Runners                                       │ File d'attente (24)
 ● Foix       vauban #298 downstream(cassini)  │  vauban #298  downstream (Vidocq/ravel)    3 min
 ● Mac        vidocq #541 build                │  …
 ○ Kikanux    inactif         occ. 24 h ▁▃▇█▆▃ │
 ✕ Montreuil  hors ligne                       │ Occupation par runner : 1 h / 24 h / 7 j
─ Commiters (30 j) ────────────────────────────────────────────────────────────────────────────────────
 Yann       4052 commits  ███████████▓▓▓▒░  seul / Opus / Sonnet / Fable / Codex   PR 1070
 Antoine     455 commits  ████▓▓▒          seul / Copilot / Opus / Sonnet
 Automatisation : vidocq-bot 449, Vidocq CI Bot 64, Copilot Agent 12
```

- **Bascule de vue** `Projets | CI | Commiters`, mémorisée dans `user_pref`. Ajouter Commiters comme
  troisième position est un choix de l'implémenteur.
- **Onglets d'org** et onglet « + » (réservé aux admins), qui vérifie l'accès du jeton de service à
  l'org avant de l'ajouter.
- **Panneau latéral runner** : job en cours, jobs récents groupés par PR (repo, #PR, nom du job, statut,
  durée, lien CodeFloe), occupation du runner.
- **Panneau latéral PR** : état combiné et contextes, jobs du dernier run avec le runner de chacun, gate
  Sonar de la PR, lien CodeFloe.
- **Commiters** :
  - choix de la période (7, 30, 90 jours) et filtre org ou toutes ;
  - le clic sur un humain ouvre un panneau avec ses repos les plus actifs et ses commits récents (lien
    `Claude-Session` si présent).
- Composants Vaadin core uniquement, sans add-on commercial. Les mini-courbes (sparklines) sont dessinées
  en SVG ou CSS maison, sans Vaadin Charts.

### 5.7 Résolution des commiters

- Humain = auteur git du commit, rattaché au login CodeFloe quand l'API le donne. Sinon, regroupement
  par adresse mail, avec une table d'alias en config ou en base (ex. `yblazart` ≡ `Yann Blazart`).
- Agent = trailers `Co-Authored-By` / `Co-authored-by` dont le nom correspond à une famille connue
  (`Claude Opus|Sonnet|Fable|Haiku`, `Codex`/`OpenAI Codex`, `Copilot`/`Copilot App`, `Mistral Vibe`…).
  On normalise la famille : on retire la version et « (1M context) ». Un nom inconnu est classé comme
  co-auteur humain.
- Bot = auteur dans une liste configurable (`vidocq-bot`, `Vidocq CI Bot`, `Vidocq Bot`, `BOT`,
  `Copilot Agent`…). Attention, « Sabot-Durand » contient « bot » : la détection se fait sur la liste
  exacte, pas par sous-chaîne.
- **Bot auteur avec un trailer d'agent** : c'est une session d'agent commitée sous une identité de bot. Par
  exemple, ce repo commite en `Vidocq Bot` avec `Co-Authored-By: Claude …`. Cela reste rare : ~6 commits
  sur 30 jours, tous repos confondus. Le commit est attribué à l'humain qui a ouvert la PR qui l'a amené
  (`GET /repos/{o}/{r}/commits/{sha}/pull`), sinon à « Non attribué (agent) ». Il n'est jamais rangé en
  « Automatisation ». Choix de l'implémenteur.
- `commit_stat` garde les **champs bruts** (nom et mail de l'auteur, login, trailers bruts). La
  classification peut ainsi être recalculée si les règles changent.
- Les agents sont un **minimum** : un commit fait par un agent sans trailer est compté comme humain seul.
  L'UI le dit en infobulle.

### 5.8 Configuration

| Variable | Défaut | Rôle |
|---|---|---|
| `FORGEJO_URL` | `https://codefloe.com` | forge surveillée |
| `FORGEJO_TOKEN` | — (obligatoire) | jeton de service (owner des orgs) |
| `DASHBOARD_INITIAL_ORGS` | `Vidocq,VidocqTools` | orgs créées au premier démarrage si la table est vide |
| `SONAR_URL` / `SONAR_TOKEN` | `https://sonar.vidocq.dev` / — | Sonar (optionnel : sans jeton, colonnes « — ») |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | — | application OAuth2 CodeFloe |
| `DASHBOARD_PUBLIC_URL` | — | URL publique (URL de retour OIDC) |
| `DASHBOARD_ALLOWED_GROUPS` / `DASHBOARD_ADMIN_GROUPS` | `Vidocq:owners,Vidocq:devs` / `Vidocq:owners` | autorisations |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | — | PostgreSQL (dev services en dev) |
| `DASHBOARD_PORT` | `8080` | écoute HTTP |

Les variables sont lues via MP Config (Ravel). Les cadences de collecte sont aussi en config.

### 5.9 Build, livraison, déploiement

- `.sdkmanrc` : `java=25-tem`, `maven=3.10.0`. `./mvnw` (wrapper 3.10.0).
- **Dev** : `./mvnw vidocq:dev` avec PostgreSQL démarré automatiquement par les dev services Vidocq
  (Testcontainers), si ça fonctionne. Sinon, un `docker compose -f compose.dev.yml` minimal.
- **Image** : `vidocq:package`, puis une image `bellsoft/liberica-openjre-debian:25` (ou équivalent JRE 25)
  qui lance `bin/forge-dashboard.sh` avec `-Dvaadin.productionMode=true`. Healthcheck sur `/healthz`
  (servlet simple, hors authentification).
- **Stack Portainer** (`docker-compose.yml`) : `forge-dashboard` et `postgres:17` avec un volume nommé et
  un réseau interne. Le dashboard est en plus sur `reverseproxy_default` (NPM). Les secrets passent par les
  variables d'environnement Portainer.
- **Exposition** : NPM a déjà un proxy host `forge-dashboard.vidocq.dev` → `forge-dashboard:8080`
  (`proxy_host/16.conf`), mais ce nom **n'existe pas dans le DNS public**. `sonar.vidocq.dev` et
  `grafana.vidocq.dev` pointent sur `90.55.135.48`, et la zone `vidocq.dev` n'est pas accessible avec le
  jeton Cloudflare disponible. Il faut donc ajouter l'enregistrement DNS chez le gestionnaire de la zone,
  puis activer le certificat TLS du proxy host dans NPM. `DASHBOARD_PUBLIC_URL=https://forge-dashboard.vidocq.dev`.
- **CI** (`.forgejo/workflows/build-deploy.yml`) : Maven 3.10 via le wrapper, snapshots
  `maven.vidocq.dev`, image et webhook Portainer, en conservant le pattern DinD existant (runner
  `vidocq-dind`, catthehacker, détection de `DOCKER_HOST`).

### 5.10 Tests

- **Unitaires** (JUnit 6) :
  - parsing de la 1re ligne de log (runner uuid, task) ;
  - normalisation des familles d'agents et des bots, résolution des humains ;
  - agrégation des états (main, PR) ;
  - calcul d'occupation à partir d'intervalles ;
  - planification et backoff.
- **Clients** : contre un faux serveur HTTP local (`com.sun.net.httpserver`), alimenté par des réponses
  réelles enregistrées et anonymisées.
- **Persistance** : sur PostgreSQL réel (dev services ou Testcontainers), avec les migrations Flyway
  appliquées.
- **UI** : un test de fumée qui démarre l'appli et vérifie que `/` redirige vers CodeFloe sans session et
  que `/healthz` répond 200. La validation visuelle et celle du push se font à la main dans le navigateur.
- Analyse Sonar avant push, selon la convention de l'écosystème, si le projet est enregistré dans Sonar.
  Sinon, le créer (clé `vidocq-tools-forge-dashboard`, choix de l'implémenteur).

### 5.11 Boucles de retour vers l'écosystème

- **FOY** : Jakarta WebSocket (vidocq#65) en cours. Il me préviendra à la publication. Je lui renvoie ce
  que je constate sur le push.
- **VAADIN** : constats sur le push (inactivité longue, reconnexions) et sur l'`Instantiator`, pour la
  section « Known limits » de `vaadin.adoc` et `BUG.md`.
- **Bug d'un SNAPSHOT Vidocq qui bloque** (décision utilisateur) :
  1. avertir la session **VIDOCQ** (SendMessage), avec la reproduction minimale ;
  2. si elle peut corriger rapidement, attendre le SNAPSHOT corrigé en avançant sur autre chose ;
  3. sinon, consigner le bug (le `BUG.md` du repo concerné, via la session ou la skill `/log-bug`),
     contourner **ouvertement** (commentaire `// WORKAROUND <id du bug>` et note dans le README), puis
     retirer le contournement quand le correctif est publié.
- Jamais de contournement silencieux.

## 6. Décisions et origine

| Décision | Origine |
|---|---|
| Vidocq 0.4.0-SNAPSHOT, UI Vaadin, connexion OAuth CodeFloe | utilisateur |
| Un onglet par org, orgs ajoutables ; contenu des vues Projets et CI ; navigation runner↔PR ; occupation ; stats commiters | utilisateur |
| Tester le push plutôt que de rafraîchir périodiquement ; pas de long polling si possible | utilisateur |
| Jakarta WebSocket ajouté à Foy par la session FOY | utilisateur |
| Cervantes pour valider l'id_token | utilisateur (suggestion) et vérification |
| Runner d'un job déduit de la 1re ligne du log | proposé, accepté |
| Occupation par une collecte planifiée ; PostgreSQL | utilisateur |
| Deux vues avec bascule mémorisée par utilisateur | utilisateur |
| `Instantiator` CDI dans l'appli ; BCE proposée à Vaadin plus tard | proposé, accepté |
| Stats commiters : humain = auteur git, agents = trailers, bots à part, période 7/30/90 | proposé, accepté |
| Collecte par interrogation périodique (approche 1) | utilisateur |
| v1 livrable en long polling en attendant le WebSocket de Foy | utilisateur |
| Bug Vidocq bloquant : avertir VIDOCQ, attendre si correctif rapide, sinon tracer et contourner ouvertement | utilisateur |
| Runner 1:N jobs, capacité observée ou surchargée, occupation en créneaux | choix de l'implémenteur (constaté en direct) |
| Bot auteur avec trailer d'agent → attribué à l'auteur de la PR ; champs bruts conservés | choix de l'implémenteur |
| URL de retour `localhost` pour le dev, aucun contournement de la connexion | choix de l'implémenteur |
| Mansart Data (et non Noether), Flyway, Cyrano, deux modules Maven, `vidocq:package` d'abord | choix de l'implémenteur, annoncé sans objection |
| Cadences, rétention, intervalles plutôt qu'échantillons, Commiters en 3e position de la bascule, groupes par défaut, jeton de service unique | choix de l'implémenteur |

## 7. Laissé au constructeur

- Le détail des colonnes, des index et des requêtes d'agrégation (SQL natif via le pool si JDQL ne suffit
  pas).
- Le style visuel exact (thème Lumo, couleurs d'état), dans l'esprit « léger ».
- La structure fine des paquets et le nommage.
- La forme du faux serveur HTTP de test.

## 8. Questions ouvertes

- DNS public de `forge-dashboard.vidocq.dev` et certificat TLS dans NPM : à faire par l'utilisateur
  (zone non accessible).
- Application OAuth2 CodeFloe : création sous le compte `yblazart`, par l'implémenteur, avec l'accord de
  l'utilisateur. Rangement de l'id et du secret à décider.
