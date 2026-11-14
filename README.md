# forge-dashboard

Tableau de bord des orgs CodeFloe de l'écosystème Vidocq (`Vidocq`, `VidocqTools`, et d'autres qu'on
ajoute depuis l'interface). Un onglet par org, trois vues :

- **Projets** : état du `main`, lignes de code, tests, couverture, quality gate Sonar, PR ouvertes
  vertes/rouges/en cours. Un clic sur une PR montre ses jobs et le runner qui les exécute.
- **CI** : runners avec leur activité, leur charge et leur occupation (1 h / 24 h / 7 j), file d'attente.
  Un clic sur un runner montre les PR qu'il construit.
- **Commiters** : commits, lignes et PR par personne, avec la part faite avec un agent (trailers
  `Co-Authored-By`) et l'automatisation à part.

La vue choisie, la période et la dernière org sont mémorisées par utilisateur.

## Stack

- **Runtime** : [Vidocq](https://codefloe.com/Vidocq) 0.4.0-SNAPSHOT (Vauban CDI Lite à la compilation,
  Foy Servlet, Cyrano MP Rest Client, Ravel MP Config, Mansart Data + pool, Flyway, Cervantes MP JWT).
- **UI** : Vaadin Flow 25, mode production, push serveur (voir « Contournements »).
- **Connexion** : OpenID Connect avec CodeFloe (code + PKCE). L'`id_token` est vérifié par Cervantes. Les
  groupes CodeFloe (`org:team`) filtrent l'accès et donnent les droits d'administration.
- **Données** : collecte périodique de l'API CodeFloe (Forgejo 17) et de Sonar, stockée dans PostgreSQL.
  Cadences : runners 15 s, PR 90 s, repos 5 min, Sonar 15 min, commits 1 h.

## Prérequis

Java 25 (voir `.sdkmanrc`) et Docker pour les tests d'intégration et le mode dev (PostgreSQL démarré par
les dev services Vidocq). Maven passe par le wrapper `./mvnw`.

```sh
sdk env
```

## Build et tests

```sh
./mvnw -ntp clean verify                # tests unitaires et d'intégration (Docker requis)
./mvnw -ntp -U clean package -DskipTests
```

`package` produit la distribution `forge-dashboard-app/target/forge-dashboard-app-<version>/` (`bin/`,
`lib/`, `app/`) et son zip. Toujours partir d'un `clean` : un build incrémental après une compilation en
échec peut garder des services générés périmés.

## Lancer en dev

```sh
set -a; source ~/.config/vidocq/tokens.env; set +a
FORGEJO_TOKEN=$CODEFLOE_BOT_TOKEN SONAR_TOKEN=$SONAR_USER_TOKEN \
OIDC_CLIENT_ID=$DASHBOARD_OIDC_CLIENT_ID OIDC_CLIENT_SECRET=$DASHBOARD_OIDC_CLIENT_SECRET \
DASHBOARD_PUBLIC_URL=http://localhost:8080 \
  ./mvnw -ntp -pl forge-dashboard-app clean vidocq:dev -Dvidocq.dev.hotReload=false
```

Sans `VIDOCQ_POOL_URL`, `vidocq:dev` démarre un conteneur PostgreSQL. Ouvrir <http://localhost:8080/> :
la page renvoie vers CodeFloe pour la connexion (l'application OAuth2 doit accepter
`http://localhost:8080/oauth2/callback` comme URI de redirection).

## Variables

| Variable | Obligatoire | Défaut | Rôle |
|---|---|---|---|
| `FORGEJO_URL` | non | `https://codefloe.com` | Forge surveillée |
| `FORGEJO_TOKEN` | oui | | Jeton de service : lecture des repos, PR, commits, et des runners de chaque org (owner de l'org) |
| `SONAR_URL` | non | `https://sonar.vidocq.dev` | Serveur Sonar (clé projet `vidocq-<repo>`) |
| `SONAR_TOKEN` | non | | Jeton Sonar, pour lire mesures et quality gates |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | oui | | Application OAuth2 CodeFloe |
| `DASHBOARD_PUBLIC_URL` | oui | | URL publique, base de l'URI de redirection `/oauth2/callback` |
| `DASHBOARD_ALLOWED_GROUPS` | non | `Vidocq:owners,Vidocq:devs` | Groupes CodeFloe autorisés |
| `DASHBOARD_ADMIN_GROUPS` | non | `Vidocq:owners` | Groupes qui peuvent ajouter ou retirer une org |
| `VIDOCQ_HTTP_PORT` | non | `8080` | Port HTTP |
| `VIDOCQ_POOL_URL` / `VIDOCQ_POOL_USERNAME` / `VIDOCQ_POOL_PASSWORD` | oui hors dev | | PostgreSQL |

Dans la stack Portainer, la base se règle avec `DB_URL`, `DB_USER` et `DB_PASSWORD`. Le compose les passe
en `VIDOCQ_POOL_*`.

## Endpoints

| Path | Rôle |
|---|---|
| `/` | Interface Vaadin (connexion CodeFloe requise) |
| `/oauth2/callback`, `/oauth2/logout` | Retour de CodeFloe, déconnexion |
| `/healthz` | `OK` (sans connexion, pour le healthcheck) |

## Image et stack Portainer

```sh
./mvnw -ntp -U clean package -DskipTests
docker build -t forge-dashboard:dev .
```

L'image (`bellsoft/liberica-openjre-debian:25`, utilisateur non-root) contient la distribution et lance
`bin/forge-dashboard-app.sh`. `docker-compose.yml` est la stack Portainer : `forge-dashboard` et
`postgres:17` (volume `forge-dashboard-db`, réseau interne `dashboard-internal`) ; `forge-dashboard` est
aussi sur `reverseproxy_default`, derrière NPM (`forge-dashboard.vidocq.dev` → `forge-dashboard:8080`).
Les secrets sont des variables de la stack, jamais dans le dépôt.

Pour lancer la stack en local, un `compose.local.yml` non commité remplace l'image par
`forge-dashboard:dev`, publie le port 8080, pose `DASHBOARD_PUBLIC_URL=http://localhost:8080` et remplace
le réseau externe du proxy par un réseau local :

```sh
docker compose -f docker-compose.yml -f compose.local.yml -p forge-dashboard-local up -d
```

## CI/CD

`.forgejo/workflows/build-deploy.yml`, sur un push sur `main` (runner `vidocq-dind`) :
`./mvnw package` → image `registry.vidocq.dev/vidocq-tools/forge-dashboard:0.2.0-SNAPSHOT` et
`main-<sha7>` → webhook Portainer (pull et redémarrage). Les tests tournent ensuite en simple
observation (`continue-on-error`) : on ne sait pas encore si les tests d'intégration, qui ont besoin de
Docker, passent sur le runner DinD.

Secrets du repo : `DOCKER_REG_URL`, `DOCKER_REG_USER`, `DOCKER_REG_PASSWORD`, `PORTAINER_WEBHOOK_URL`.

## Contournements

Chacun tombe quand le correctif est publié ; le code renvoie au ticket là où il contourne.

- **Push en long polling** (vidocq#65) : Foy n'a pas encore Jakarta WebSocket ; la montée en WebSocket
  reçoit un 501 d'Atmosphere. `DashboardAppShell` force `Transport.LONG_POLLING`.
- **Port et base par variables du runtime** (vidocq#562) : la configuration cœur lue au démarrage ne
  développait pas `${VAR:défaut}`. D'où `VIDOCQ_HTTP_PORT` au lieu d'un `DASHBOARD_PORT`, et
  `VIDOCQ_POOL_*` au lieu de `vidocq.pool.url=${DB_URL}`. Le correctif est fusionné et arrive avec le
  prochain SNAPSHOT.
- **Connexion OIDC à la main** (vidocq#63) : sans Jakarta Security dans Vidocq, un filtre servlet
  (`OidcFilter`) fait le flux code + PKCE. Cervantes vérifie l'`id_token`.
- **Mode dev sans rechargement** : `-Dvidocq.dev.hotReload=false`. Un IDE qui réécrit `target/classes`
  (jdtls) déclenche une recharge sans les services générés (Cyrano, Mansart).
  `-Dvidocq.dev.continuousTesting=false` coupe aussi les tests en continu si on n'en veut pas.

## Licence

Apache License 2.0 — voir [`LICENSE`](LICENSE).
