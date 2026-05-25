# forge-dashboard — Guide Claude

## Projet

Dashboard web des projets Vidocq (désormais hébergés sur Codeberg `https://codeberg.org`, orgs
`vidocq` / `VidocqTools` — Forgejo) et de leurs runners Actions. Code source du dashboard hébergé sur
Codeberg, image Docker déployée sur Portainer via un runtime Vidocq.

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
| `FORGEJO_URL`      | oui         | URL de base de la forge surveillée (ex. `https://codeberg.org`) |
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
FORGEJO_URL=https://codeberg.org FORGEJO_TOKEN=$(grep FORGEJO_TOKEN ~/.config/vidocq/tokens.env | cut -d= -f2) \
  mvn -pl forge-dashboard-app exec:java
```

## Docker / Portainer

```sh
docker build -t "$DOCKER_REG_URL/vidocq-tools/forge-dashboard:0.1.0-SNAPSHOT" .
docker push "$DOCKER_REG_URL/vidocq-tools/forge-dashboard:0.1.0-SNAPSHOT"
# puis création stack sur Portainer via API ou UI
```

## CI/CD (Forgejo Actions — sur Codeberg)

`.forgejo/workflows/build-deploy.yml` tourne sur **Codeberg** (Actions à activer dans
`Repo Settings → Actions`) : push sur `main` → `mvn install` (SNAPSHOTs Vidocq upstream consommés en
**anonyme** depuis le Central Portal Snapshots, repo déclaré dans le pom) → build & push image
`$DOCKER_REG_URL/vidocq-tools/forge-dashboard:0.1.0-SNAPSHOT` + tag `main-<sha7>` →
POST sur le webhook Portainer (force pull + restart container).

Secrets requis dans `Repo Settings → Actions → Secrets` (côté Codeberg) :

> Les SNAPSHOTs Vidocq upstream sont lus en **anonyme** sur le Central Portal — aucun secret Maven requis.

| Secret | Usage |
|---|---|
| `DOCKER_REG_URL` | hôte du registry Docker cible (ex. `forge.vidocq.dev`) |
| `DOCKER_REG_USER` | user du registry Docker |
| `DOCKER_REG_PASSWORD` | token / mot de passe du registry |
| `PORTAINER_WEBHOOK_URL` | webhook container Portainer (id=1, type=container) |

**Prérequis upstream** : `cassini`, `chappe`, `vauban`, `champollion`, `ravel`,
`vidocq-runtime-core`, `vidocq-runtime-cassini-rest-extension` doivent être publiés
en SNAPSHOT sur le Central Portal Snapshots (`https://central.sonatype.com/repository/maven-snapshots/`).
Sinon `mvn install` échoue.

## Roadmap M0 (premier déploiement utile)

1. Client Forgejo : `listRepos(org)`, `listRunners()`, `getLatestCommit(repo)`, `listOpenPRs(repo)` ✅
2. Page `/` + fragment `/tiles/repos` (sans build status — juste topics + dernier commit + nb PR) ✅
3. Page `/tiles/runners` ✅ (placeholder, API publique absente)
4. Dockerfile fonctionnel + `docker-compose.yml` pour Portainer ✅
5. Auth — pour M0, dashboard accessible derrière un reverse-proxy authentifié (Traefik + forward-auth) ; pas d'auth applicative interne
6. Build status main + dernière PR ✅
7. CI/CD Forgejo Actions ✅ (à valider quand les SNAPSHOTs upstream seront publiés)
