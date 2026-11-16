# Compteur WIP et onglet PR — design

## Intention

Deux demandes de l'utilisateur :

1. « les WIP rouge devrait pas compter. Faut un compteur WIP neutre » : dans la colonne PR de la vue Projets, une PR
   en brouillon rouge compte aujourd'hui dans la pastille rouge, et fait croire à un problème qui n'en est pas un.
2. « un onglet PR aussi pour avoir accès directement aux PR qui pointent sur les projets » : aujourd'hui il faut
   déplier chaque projet pour voir ses PR.

## Constat

- Un brouillon se reconnaît à son titre, comme dans Forgejo : il commence par `WIP:` ou `[WIP]` (`PullView.draft()`).
- Le tri de Projets ignore déjà un brouillon rouge (il ne fait pas remonter le projet), mais les compteurs le comptent.
- Les PR ouvertes de chaque projet sont dans le snapshot vivant (`RepoView.pulls`), avec leur état CI, leur gate Sonar,
  leur auteur et leur dernière mise à jour ; le panneau d'une PR existe (`PullDetailsDrawer`).

## Choix (approuvés sur « go »)

1. **Compteur WIP** : la colonne PR a quatre pastilles : vertes, rouges, en cours, puis **WIP**, grise et neutre. Tous les
   brouillons vont dans WIP, quel que soit leur état ; les trois pastilles de couleur ne comptent que les PR prêtes.
   L'info-bulle et le nom accessible disent les quatre nombres. Dans la liste dépliée d'un projet, un brouillon porte
   une pastille « WIP ».
2. **Onglet PR** : une quatrième vue, « PR », après Projets dans le sélecteur (Projets | PR | CI | Commiters), retenue
   dans les préférences comme les autres. Une ligne par PR ouverte de l'org :
   - pastille d'état CI (avec l'avancement du build en cours dans l'info-bulle), dépôt, « #n titre » (ouvre le panneau
     de la PR), pastille WIP pour un brouillon, auteur, gate Sonar, dernière mise à jour (« il y a 3 h »), avancement
     du build en cours (« 60 % · reste ~2 min ») ;
   - ordre : les rouges prêtes d'abord, puis les autres PR prêtes, puis les WIP ; dans chaque groupe, la plus récemment
     mise à jour d'abord ;
   - en direct : la liste suit les passes des PR et de Sonar (rechargée seulement si elle change), l'avancement suit
     les passes des runners sur place, comme dans Projets ;
   - sur téléphone : état, dépôt, PR et WIP ; auteur, gate, mise à jour et avancement reviennent sur un écran large ;
   - sans PR ouverte : « Aucune PR ouverte dans <org>. »
3. Sept langues, comme le reste.

## Non-objectifs

- Filtrer ou chercher dans la liste, voir les PR fermées ou fusionnées, agir sur une PR (fusion, relance).
- Changer la détection des brouillons (préfixes de titre de Forgejo).

## Découpage

- `ui/model` : `ProjectRow` gagne `prWip` ; `PullsModel` et `PullRow` pour l'onglet, purs.
- `ui` : `Badges.pulls` (quatrième pastille, `Theme.PILL_WIP` et sa règle CSS), `Badges.wip`, `ProjectsPanel` (pastille
  WIP de la liste dépliée), `PullsPanel`, `ViewMode.PULLS`, `MainView`.
