# Composants d'une PR d'après ses fichiers — design

Date : 2026-11-23. Demande : dans le monorepo `Vidocq/vidocq`, calculer aussi les composants d'une PR d'après les
dossiers des fichiers qu'elle modifie, pas seulement d'après les tags de son titre. Affichage seulement.

## Pourquoi

Un titre peut oublier un composant touché : la vue par composant (pastilles, filtre « Composant ») doit montrer ce que
la PR touche vraiment.

## Décisions de Yann

- Affichage seulement : on ne renomme pas les PR, on n'alerte pas, on ne touche pas à la CI du monorepo.

## Approches

- **Union titre ∪ fichiers, pastilles neutres** (retenue) : l'information sans alerte ; l'info-bulle dit l'origine.
- Union avec pastille d'avertissement pour un composant absent du titre : écartée, Yann ne veut pas d'alerte.
- Bouton qui corrige le titre, ou contrôle dans la CI du monorepo : écartés (hors champ).

## Design

- **Carte des composants** : `components.json` à la racine de la branche par défaut d'un dépôt (`components[].tag`,
  `components[].path`). Relu quand le dernier commit de cette branche change ; absent (404) = pas de carte. Un JSON
  illisible = pas de carte.
- **Composant d'un fichier** : l'entrée dont le dossier contient le fichier (chemin égal ou préfixe suivi de `/`),
  le plus long s'il y en a plusieurs ; aucun hors des dossiers (`.forgejo/`, `.gitignore`…).
- **Fichiers d'une PR** : `/repos/{o}/{r}/pulls/{n}/files`, toutes les pages, lus seulement pour un dépôt qui a une
  carte, et relus seulement quand le commit de tête de la PR change. En mémoire (pas de migration) ; relus après un
  redémarrage. Si une lecture (carte ou fichiers) échoue, on garde la dernière lue, ou, s'il n'y en a pas, la PR n'a
  que les composants de son titre pendant ce tour ; on réessaie au suivant. Une limite de débit (429) termine le tour,
  pour que la boucle attende comme CodeFloe le demande.
- **Composants d'une PR** : les tags du titre (ordre du titre), puis ceux des fichiers absents du titre (ordre de
  `components.json`) ; en minuscules pour le filtre ; sans aucun, le dépôt, comme avant.
- **Pastilles** (onglet PR et vue Projets) : une par composant, même style neutre. Info-bulle : « Composant : vauban
  (titre) », « (fichiers modifiés) » ou « (titre et fichiers modifiés) », dans les 7 langues.
- Les dépôts sans `components.json` ne changent pas.

## Hors champ

Renommer les PR, signaler les titres incomplets, sous-lignes par composant, mesures Sonar par dossier.
