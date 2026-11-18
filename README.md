# forge-dashboard

Tableau de bord des orgs CodeFloe de l'écosystème Vidocq (`Vidocq`, `VidocqTools`, et d'autres qu'on
ajoute depuis l'interface). Un onglet par org, quatre vues :

- **Projets** : état du `main`, lignes de code, tests, couverture, quality gate Sonar, PR ouvertes
  vertes/rouges/en cours, et à part les brouillons (titre qui commence par `WIP` ou `[WIP]`), en gris : un brouillon rouge
  ne compte pas en rouge. Un clic sur une PR montre ses jobs et le runner qui les exécute. Les projets en
  erreur d'abord (`main` rouge, PR prête rouge, gate en échec), puis ceux qui ont une PR prête ouverte, puis
  les autres ; dans chaque groupe, le mieux couvert d'abord.
- **PR** : toutes les PR ouvertes de l'org, les rouges prêtes d'abord, puis les autres prêtes, puis les
  brouillons ; état, dépôt, auteur, gate Sonar, dernière mise à jour et avancement du build en cours. Les tags de
  composant en tête du titre, après un éventuel `WIP` ou `[WIP]` (`[VAUBAN][CASSINI] refactor(spi): …`), passent en
  pastilles neutres avant le « #n titre court » (le titre sans marqueur WIP ni tags, le titre entier en info-bulle) ;
  sans tag, le composant d'une PR est son dépôt. Au-dessus de la grille, un filtre « Composant » (liste déroulante
  effaçable) ne garde que les PR du composant choisi, parmi ceux des PR ouvertes, triés ; vide, toutes les PR.
- **CI** : runners avec leur activité, leur charge et leur occupation (1 h / 24 h / 7 j), file d'attente.
  Un clic sur un runner montre les PR qu'il construit.
- **Commiters** : commits, lignes et PR par personne, avec la part faite avec un agent (trailers
  `Co-Authored-By`) et l'automatisation à part. Un commit importé d'un autre dépôt (même auteur, même date et même
  message, SHA réécrit par l'import) ne compte qu'une fois.

La vue choisie, la période, la dernière org et la langue sont mémorisées par utilisateur.

Sur un téléphone (fenêtre de moins de 720 px de large), le choix de la vue passe sous les onglets et les
tableaux gardent l'essentiel : repo, `main`, gate et PR pour les projets ; état, repo et PR pour les PR ;
commiter, commits et part pour les commiters. Le reste revient en tournant le téléphone (ou sur un écran plus large).

## Langues

L'interface existe en français, anglais, allemand, espagnol, italien, néerlandais et polonais. Elle prend la première
de ces langues dans la liste du navigateur, variantes régionales comprises (`de-CH` donne l'allemand, `fr-CA,en-US` le
français), et l'anglais si aucune n'y figure. Chacun peut en choisir une dans le menu de son avatar (« Langue ») : le
choix est mémorisé, « Navigateur » revient à la langue du navigateur. Les pages de connexion et de déconnexion suivent
la langue du navigateur. Les données restent telles que CodeFloe les donne : noms d'org, de repo et de runner, titres
de PR, auteurs, familles d'agents.

Les textes sont dans `forge-dashboard-app/src/main/resources/i18n/` : `messages.properties` (anglais, le repli), puis
un fichier `messages_<langue>.properties` par langue (`de`, `es`, `fr`, `it`, `nl`, `pl`), en UTF-8, au format
`MessageFormat` (`{0}` pour une valeur, les apostrophes écrites telles quelles). Toutes les traductions sauf le
français ont été écrites par Claude : corrections bienvenues de la part de locuteurs natifs, par une PR qui ne touche
qu'au fichier de leur langue. Un test vérifie que chaque langue de `Messages.LANGUAGES` a les clés et les paramètres
de l'anglais. Pour ajouter une langue : son fichier, et son entrée dans `Messages.LANGUAGES` (`i18n/Messages.java`).

## Stack

- **Runtime** : [Vidocq](https://codefloe.com/Vidocq) 0.4.0-SNAPSHOT (Vauban CDI Lite à la compilation,
  Foy Servlet, Cyrano MP Rest Client, Ravel MP Config, Mansart Data + pool, Flyway, Cervantes MP JWT).
- **UI** : Vaadin Flow 25, mode production, push serveur (voir « Contournements »). Thème Lumo, puis
  `styles/dashboard.css` (`src/main/resources/META-INF/resources/`) : clair, très contrasté, pastilles et
  sélecteurs encadrés.
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
# tokens.env pose FORGEJO_URL sur l'ancienne forge : on le remet sur CodeFloe.
FORGEJO_URL=https://codefloe.com FORGEJO_TOKEN=$CODEFLOE_BOT_TOKEN SONAR_TOKEN=$SONAR_USER_TOKEN \
OIDC_CLIENT_ID=$DASHBOARD_OIDC_CLIENT_ID OIDC_CLIENT_SECRET=$DASHBOARD_OIDC_CLIENT_SECRET \
DASHBOARD_PUBLIC_URL=http://localhost:8080 \
  ./mvnw -ntp -pl forge-dashboard-app clean vidocq:dev -Dvidocq.dev.hotReload=false
```

Sans `VIDOCQ_POOL_URL`, `vidocq:dev` démarre un conteneur PostgreSQL. Les tests tournent en continu, à chaque
modification de `src/test`, sur leur propre conteneur PostgreSQL : ils ne touchent pas à la base de
l'application qui tourne. Ouvrir <http://localhost:8080/> :
la page renvoie vers CodeFloe pour la connexion (l'application OAuth2 doit accepter
`http://localhost:8080/oauth2/callback` comme URI de redirection).

Les sessions sont en mémoire. Une page ouverte dont la session est perdue, après un redémarrage par exemple,
se recharge d'elle-même et repasse par CodeFloe. En local, les cookies de `localhost` ne dépendent pas du
port : une autre application ouverte sur `localhost` qui pose son propre `JSESSIONID` remplace celui du
dashboard, avec le même effet.

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

Le compose lit les variables du shell (`FORGEJO_TOKEN`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `DB_PASSWORD`…),
comme pour le mode dev. Après un `source` de `tokens.env`, remettre `FORGEJO_URL=https://codefloe.com`.

## CI/CD

`.forgejo/workflows/build-deploy.yml`, sur un push sur `main` (runner `vidocq-dind`) :
`./mvnw verify` → image `registry.vidocq.dev/vidocq-tools/forge-dashboard:0.2.0-SNAPSHOT` et
`main-<sha7>` → webhook Portainer (pull et redémarrage). Les tests conditionnent l'image : rien n'est
poussé ni déployé s'ils échouent. Chaque exécution des tests démarre un PostgreSQL de dev services sur le
démon DinD. Si le runner ne le permet pas, le job échoue sans rien déployer.

Secrets du repo : `DOCKER_REG_URL`, `DOCKER_REG_USER`, `DOCKER_REG_PASSWORD`, `PORTAINER_WEBHOOK_URL`.

## Contournements

Chacun tombe quand le correctif est publié ; le code renvoie au ticket là où il contourne.

- **Port et base par variables du runtime** (vidocq#562) : la configuration cœur lue au démarrage ne
  développait pas `${VAR:défaut}`. D'où `VIDOCQ_HTTP_PORT` au lieu d'un `DASHBOARD_PORT`, et
  `VIDOCQ_POOL_*` au lieu de `vidocq.pool.url=${DB_URL}`. Le correctif est fusionné et arrive avec le
  prochain SNAPSHOT.
- **Connexion OIDC à la main** (vidocq#63) : sans Jakarta Security dans Vidocq, un filtre servlet
  (`OidcFilter`) fait le flux code + PKCE. Cervantes vérifie l'`id_token`.
- **Mode dev sans rechargement** : `-Dvidocq.dev.hotReload=false`. Un IDE qui réécrit `target/classes`
  (jdtls) déclenche une recharge sans les services générés (Cyrano, Mansart).

## Licence

Apache License 2.0 — voir [`LICENSE`](LICENSE).
