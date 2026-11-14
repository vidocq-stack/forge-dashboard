# forge-dashboard — Guide Claude

## Projet

Tableau de bord des orgs CodeFloe de l'écosystème Vidocq (`https://codefloe.com`, Forgejo 17 ; orgs
`Vidocq`, `VidocqTools`, d'autres ajoutées depuis l'UI) : état des projets, PR, runners Actions et
occupation, stats par commiter. Code sur CodeFloe (`VidocqTools/forge-dashboard`), image déployée sur
Portainer derrière NPM (`forge-dashboard.vidocq.dev`). Codeberg et `forge.vidocq.dev` sont obsolètes.

Spec : `docs/superpowers/specs/` ; plan v2 : `docs/superpowers/plans/2026-11-14-forge-dashboard-v2.md`.

## Choix d'archi actés

- **Runtime** : Vidocq 0.4.0-SNAPSHOT — Vauban CDI Lite (build-time), Foy Servlet, Cyrano MP Rest Client,
  Ravel MP Config, Mansart Data + pool, Flyway, Cervantes MP JWT.
- **UI** : Vaadin Flow 25 (mode production), push `LONG_POLLING` (WORKAROUND vidocq#65), beans CDI via
  `ui/cdi/VaubanInstantiator`.
- **Connexion** : OIDC CodeFloe, code + PKCE, par `auth/OidcFilter` (en attendant Jakarta Security,
  vidocq#63) ; `id_token` vérifié par Cervantes ; accès et admin par groupes CodeFloe `org:team`.
- **Données** : boucles de collecte (`collect/`) sur l'API CodeFloe et Sonar, cadences étagées, stockage
  PostgreSQL (Flyway `db/migration`), état vivant en mémoire (`live/`) poussé à l'UI via un `ChangeBus`.
- **Runner ↔ job** : Forgejo 17 n'expose pas le runner d'un job ; il est lu dans l'en-tête du log
  (`<html_url>/logs`, Range).
- **Commiters** : humain = auteur git, agents = trailers `Co-Authored-By`.

## Packages (`io.vidocq.tools.dashboard`)

| Package | Rôle |
|---|---|
| `forge`, `forge/cyrano` | Port `ForgeApi` et clients Cyrano CodeFloe (API + pages web pour les logs) |
| `sonar`, `sonar/cyrano` | Port Sonar et client |
| `domain` | Règles pures : classification des commits, occupation, en-tête de log… |
| `collect` | Boucles de collecte, backoff, ordonnanceur, admin des orgs |
| `live` | Snapshots en mémoire et `ChangeBus` |
| `store` | Entités et repositories Mansart, requêtes de stats |
| `auth` | OIDC, session, groupes |
| `ui`, `ui/model`, `ui/cdi` | Vues Vaadin, modèles de vue purs (testés sans Vaadin), instanciateur CDI |
| `config` | Alias et bots des commiters |
| `web` | `/healthz` |

## Convention écosystème (héritée du parent `vidocq/CLAUDE.md`)

- **JPMS strict** — `module-info.java` propre, `exports` minimaux, `opens` seulement quand Mansart, Vaadin
  ou Vidocq le demandent
- **Génération de code statique** — pas de réflexion runtime quand on peut générer
- **Peu de dépendances externes** — Jakarta/MicroProfile, Vaadin, JDK 25
- **TDD obligatoire** — rouge → vert → refactor
- **Virtual Threads** pour l'I/O
- **Pas de secret commité** — tokens et secret OIDC en variables (Portainer, `~/.config/vidocq/tokens.env`
  en local), jamais dans les fixtures

## Configuration runtime

Voir le tableau du README. L'essentiel : `FORGEJO_TOKEN`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`,
`DASHBOARD_PUBLIC_URL`, `VIDOCQ_POOL_URL/USERNAME/PASSWORD` (le compose les remplit depuis
`DB_URL/DB_USER/DB_PASSWORD`), `VIDOCQ_HTTP_PORT`. En local : `CODEFLOE_BOT_TOKEN`, `SONAR_USER_TOKEN`,
`DASHBOARD_OIDC_CLIENT_ID/SECRET` de `tokens.env`.

## Endpoints

| Path | Réponse |
|---|---|
| `/` | UI Vaadin (login CodeFloe requis) |
| `/oauth2/callback`, `/oauth2/logout` | Flux OIDC |
| `/healthz` | `OK`, sans login |

## Build & run

```sh
sdk env
./mvnw -ntp clean verify                                   # Docker requis (PostgreSQL des dev services)
./mvnw -ntp -pl forge-dashboard-app clean vidocq:dev -Dvidocq.dev.hotReload=false -Dvidocq.dev.continuousTesting=false
./mvnw -ntp -U clean package -DskipTests && docker build -t forge-dashboard:dev .
```

Toujours `clean` : jdtls réécrit `target/classes` et perd les services générés (Cyrano, Mansart).

## Docker / Portainer / CI

- `Dockerfile` : `bellsoft/liberica-openjre-debian:25`, distribution `target/forge-dashboard-app-*/`,
  utilisateur non-root, healthcheck `/healthz`.
- `docker-compose.yml` : stack Portainer `forge-dashboard` + `postgres:17` (réseaux `dashboard-internal` et
  `reverseproxy_default`). `compose.local.yml` (non commité) pour la lancer en local.
- `.forgejo/workflows/build-deploy.yml` : runner `vidocq-dind` + catthehacker + détection `DOCKER_HOST`,
  `./mvnw verify` (les tests conditionnent l'image), push
  `registry.vidocq.dev/vidocq-tools/forge-dashboard:0.2.0-SNAPSHOT` et `main-<sha7>`, webhook Portainer.
- Déploiement et push : uniquement avec l'accord explicite de l'utilisateur.
