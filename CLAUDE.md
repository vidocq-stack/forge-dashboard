# forge-dashboard — Guide Claude

## Projet

Dashboard web des projets de la forge Vidocq (Forgejo `https://forge.vidocq.dev`) et de leurs
runners Actions. Hébergé par un runtime Vidocq, image Docker déployée sur Portainer.

## Choix d'archi actés

- **Runtime** : `io.vidocq.runtime:vidocq-runtime-core` (MicroProfile 7.1 + Vauban CDI + Cassini JAX-RS 4.0 + Chappe HTTP + Champollion JSON-B)
- **Frontend** : HTML server-rendered + [HTMX 2.x](https://htmx.org/) servi en statique depuis `META-INF/resources/`
- **Détection des specs implémentées par chaque repo** : topics Forgejo (ex. `jakarta-rest-4.0`, `microprofile-config-3.1`, `jakarta-servlet-6.1`)
- **Source de données runners** : API Forgejo `/api/v1/admin/actions/runners`
- **Stockage** : in-memory (cache TTL ~30s côté serveur, refresh HTMX côté client)

## Convention écosystème (héritée du parent `vidocq/CLAUDE.md`)

- **JPMS strict** — `module-info.java` propre, `exports` minimaux, pas de classpath
- **Génération de code statique** — pas de réflexion runtime quand on peut générer (Class-File API / APT)
- **Zéro/peu de dépendances externes** — Jakarta/MicroProfile uniquement + JDK 25 stdlib
- **TDD obligatoire** — rouge → vert → refactor
- **Virtual Threads** pour l'I/O (HTTP vers Forgejo)
- **Pas de secret commit** — `FORGEJO_TOKEN` injecté via env (Portainer)

## Structure (initiale)

```
forge-dashboard/
├── pom.xml                              ← parent Maven 4 (root="true")
├── Dockerfile                           ← image jlink + runtime
├── .sdkmanrc                            ← Java 25 + Maven 4.0.0-rc-5
└── forge-dashboard-app/                 ← module unique au démarrage
    ├── pom.xml
    └── src/main/
        ├── java/
        │   ├── module-info.java                                       (io.vidocq.tools.dashboard.app)
        │   └── io/vidocq/tools/dashboard/
        │       ├── App.java                                           (entrée — délègue à Vidocq.main)
        │       ├── forgejo/ForgejoClient.java                         (java.net.http + Champollion JSON-B)
        │       ├── model/                                             (records immutables)
        │       │   ├── RepoInfo.java
        │       │   ├── RunnerInfo.java
        │       │   └── CommitInfo.java
        │       └── web/DashboardResource.java                         (JAX-RS — page + fragments HTMX)
        └── resources/
            └── META-INF/resources/index.html                          (page statique)
```

Si la taille du module justifie une séparation, extraire en `forge-dashboard-api` (modèles) +
`forge-dashboard-core` (client Forgejo) + `forge-dashboard-app` (web).

## Configuration runtime

| Variable           | Obligatoire | Description                                           |
|--------------------|-------------|-------------------------------------------------------|
| `FORGEJO_URL`      | oui         | URL de base de la forge (ex. `https://forge.vidocq.dev`) |
| `FORGEJO_TOKEN`    | oui         | Token API Forgejo (scopes : `read:repository`, `read:user`, `read:org`, `read:admin` pour les runners) |
| `FORGEJO_ORGS`     | non         | Liste CSV d'orgs à scanner (défaut : toutes les orgs accessibles au token) |
| `DASHBOARD_PORT`   | non         | Port HTTP (défaut : `8080`)                          |
| `DASHBOARD_CACHE_TTL_SECONDS` | non | TTL du cache in-memory (défaut : `30`)        |

## Endpoints prévus

| Méthode | Path                  | Réponse                                          |
|---------|-----------------------|--------------------------------------------------|
| GET     | `/`                   | Page complète (HTML)                             |
| GET     | `/tiles/repos`        | Fragment HTML — liste des repos avec specs/PR/build/dernier commit |
| GET     | `/tiles/runners`      | Fragment HTML — état des runners Actions         |
| GET     | `/healthz`            | `200 OK`                                         |

## Build & run

```sh
sdk env
mvn -ntp install -DskipTests
FORGEJO_URL=https://forge.vidocq.dev FORGEJO_TOKEN=$(grep FORGEJO_TOKEN ~/.config/vidocq/tokens.env | cut -d= -f2) \
  mvn -pl forge-dashboard-app exec:java
```

## Docker / Portainer

```sh
docker build -t forge.vidocq.dev/vidocq-tools/forge-dashboard:0.1.0-SNAPSHOT .
docker push forge.vidocq.dev/vidocq-tools/forge-dashboard:0.1.0-SNAPSHOT
# puis création stack sur Portainer via API ou UI
```

## Roadmap M0 (premier déploiement utile)

1. Client Forgejo : `listRepos(org)`, `listRunners()`, `getLatestCommit(repo)`, `listOpenPRs(repo)`
2. Page `/` + fragment `/tiles/repos` (sans build status — juste topics + dernier commit + nb PR)
3. Page `/tiles/runners`
4. Dockerfile fonctionnel + `docker-compose.yml` pour Portainer
5. Auth — pour M0, dashboard accessible derrière un reverse-proxy authentifié (Traefik + forward-auth) ; pas d'auth applicative interne
