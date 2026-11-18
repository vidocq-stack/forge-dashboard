# Composants des PR et commits importés — design

Date : 2026-11-18. Demande : préparer forge-dashboard au monorepo Vidocq (week-end du 2026-11-21/22, plan
`vidocq-workspace/MONOREPO.md`), en restant compatible avec les dépôts séparés d'aujourd'hui.

## Pourquoi

- Le monorepo `Vidocq/vidocq` absorbe 21 dépôts. Ses PR portent un tag par composant touché, après un `[WIP]`
  éventuel : `[WIP] [NOETHER] fix(persistence): … (#123)`, `[VAUBAN][CASSINI] refactor(spi): …`. Sans lecture des
  tags, l'onglet PR ne dit plus quel composant une PR touche.
- L'import réécrit les SHA (`git filter-repo`) mais garde auteur, dates et message. La boucle des commits relirait
  sous `vidocq` les commits des autres dépôts plus récents que son curseur : la vue Commiters les compterait deux fois.

## Décisions de Yann

- Double comptage : une protection dans le code (pas de SQL ponctuel en prod).
- Vue par composant : tout de suite, compatible avec les dépôts séparés et avec le monorepo.

## Design

### Commits déjà comptés

Avant d'enregistrer un commit, la boucle des commits cherche un commit déjà stocké avec la même date d'auteur, le même
e-mail d'auteur et le même message, sous un autre identifiant (`org/repo@sha`), quels que soient le dépôt et l'org.
S'il existe, le commit n'est pas enregistré : il ne compte pas deux fois. Le curseur du dépôt avance quand même.
Cas couverts : l'import du week-end, l'arrivée plus tard d'erasmus, tassis, verne, et la sortie de Flyway vers VCU.
Un commit relu sous son propre identifiant est mis à jour comme avant. Pas de migration : l'index sur `authored_at`
existe.

### Composant d'une PR

- **Tags** : après un marqueur WIP éventuel en tête (`[WIP]`, ou le mot WIP comme aujourd'hui), la suite de
  `[TAG]` (lettres majuscules, chiffres, `-`, `_`), espaces permis entre eux. `[WIP]` n'est pas un tag.
- **Composants** : les tags en minuscules ; sans tag, le dépôt de la PR. Ainsi « vauban » désigne aujourd'hui les PR
  du dépôt vauban et demain les PR `[VAUBAN]` du monorepo.
- **Titre court** : le titre sans marqueur WIP ni tags, ni le `:` ou les espaces qui les suivent ; le titre entier
  s'il ne resterait rien.

### Onglet PR

- Dans la cellule PR : les tags en pastilles neutres (texte en majuscules, comme dans le titre), puis « #n titre
  court » ; la pastille WIP d'un brouillon reste. Plusieurs pastilles passent à la ligne si la place manque.
  L'info-bulle du titre garde le titre entier.
- Au-dessus de la grille, un filtre « Composant » (liste déroulante effaçable) : les composants des PR ouvertes de
  l'org, triés ; vide = toutes les PR. Il n'est pas mémorisé. Si le composant choisi n'a plus de PR ouverte, le filtre
  s'efface.

### Vue Projets

La liste dépliée d'un projet montre les mêmes pastilles et le même titre court.

## Hors champ

- Sous-lignes par composant dans la vue Projets.
- Mesures Sonar par dossier, table tag → chemin du monorepo (pas encore publiée ; la lecture des tags n'en a pas
  besoin).
- Le titre dans le panneau d'une PR et dans celui d'un runner reste le titre entier.

## Réalisation

Implémentée par Mistral Vibe (Mistral Large 4), piloté par Claude Code tâche par tâche : plan
`docs/superpowers/plans/2026-11-18-components.md`. Revue du diff et tests relancés par Claude après chaque tâche.
