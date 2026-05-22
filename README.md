# forge-dashboard

Dashboard des projets et runners de la forge Vidocq (Forgejo). Vue d'ensemble en un coup d'œil :
specs implémentées par chaque repo, statut de build/PR, derniers commits, état des runners Actions.

- **Runtime** : [Vidocq](https://forge.vidocq.dev/vidocq/vidocq) (MicroProfile 7.1 / JAX-RS 4.0 via Cassini / JSON-B via Champollion / CDI Lite via Vauban)
- **Frontend** : HTML server-rendered + HTMX 2.x — zéro build JS
- **Détection des specs** : topics du repo Forgejo (ex. `jakarta-rest-4.0`, `microprofile-config-3.1`)
- **Déploiement** : image Docker poussée sur Portainer

## Prérequis

Voir `.sdkmanrc` — Java 25 + Maven 4.0.0-rc-5.

```sh
sdk env
```

## Build

```sh
mvn -ntp install -DskipTests
```

## Lancer en local

Le client Forgejo lit les variables d'environnement :

```sh
export FORGEJO_URL=https://forge.vidocq.dev
export FORGEJO_TOKEN=...        # token API (scope read:repository, read:user, read:org)
mvn -pl forge-dashboard-app exec:java
```

Puis ouvrir <http://localhost:8080/>.

## Build de l'image Docker

```sh
docker build -t forge-dashboard:0.1.0-SNAPSHOT .
```

## Déploiement Portainer

Le `docker-compose.yml` (à venir) sera consommable comme stack Portainer. Les secrets
(`FORGEJO_TOKEN`) sont injectés via variables d'environnement Portainer, jamais commit.

## Licence

Apache License 2.0 — voir [`LICENSE`](LICENSE).
