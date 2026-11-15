# Avancement et temps restant des builds — design

## Intention

Voir où en est un build en cours (PR ou `main`) : un pourcentage et une estimation du temps restant, à côté du temps
écoulé qu'on affiche déjà. Rien à décider ni à déclencher : seulement voir (« juste voir avec une estimation de temps
restant »).

## Constat

- Forgejo 17 ne donne aucun avancement : un job attend, tourne ou est fini.
- `job_execution` garde chaque job (org, dépôt, nom du job, PR, ref, statut final, début, fin) depuis la v2
  (2026-11-14), 180 jours.
- Les jobs en cours sont dans `OrgSnapshot.runners[].running` (`JobView`) ; on les montre dans les cartes de la vue
  CI, le panneau d'un runner (« En cours ») et les « Jobs du dernier run » d'une PR, avec le seul temps écoulé.

## Choix (pris sur « go », sans aller-retour)

1. **Durée habituelle d'un job** : médiane des durées (`stopped_at - started_at`) des 10 dernières exécutions réussies
   (`status = 'success'`) du même job — même org, même dépôt, même nom de job. Au moins 3 exécutions, sinon pas
   d'estimation.
2. **Estimation** : avancement = écoulé ÷ habituel, plafonné à 99 % ; reste = habituel − écoulé. Au-delà de
   l'habituel, on dit de combien on le dépasse au lieu d'un reste.
3. **Texte** : « 62 % · reste ~3 min » ; au-delà : « +2 min sur l'habitude ». Le reste et le dépassement s'arrondissent
   à la minute supérieure, ou aux 10 s supérieures sous une minute. Sept langues.
4. **Où** :
   - partout où un job en cours est affiché : carte de runner (vue CI), panneau d'un runner, jobs du dernier run d'une
     PR ;
   - vue Projets : info-bulle de la pastille `main` en cours, et lignes de PR dépliées en cours. Pour un build, on
     prend son job en cours au plus long reste. Les jobs de `main` sont les jobs en cours du dépôt sans PR.
5. **Sans historique** (moins de 3 réussites) : rien de plus qu'aujourd'hui.

## Non-objectifs

- Estimer les jobs pas encore démarrés (étapes suivantes d'un workflow, file d'attente) : Forgejo ne les annonce pas ;
  un build en plusieurs étapes est donc sous-estimé.
- Barre de progression graphique, alertes, historique des estimations.

## Découpage

- `domain` : l'estimation (`JobEta`) et la médiane (`UsualDurations`), pures.
- `store/StatsQueries` : les durées des dernières réussites par job d'une org.
- `live/JobView` porte la durée habituelle (nulle sans historique), remplie par `RunnersLoop`.
- `domain/Formats` : le texte ; `ui` : les quatre emplacements.
