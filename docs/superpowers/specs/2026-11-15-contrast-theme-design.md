# Thème contrasté (option A) — design court

## Intention

« Plus pro, plus joli, sur PC et sur mobile » : d'abord **plus de contraste**. L'interface est en Lumo par défaut (texte
secondaire gris clair, séparations à peine visibles, statuts en petits points et emoji). L'utilisateur a choisi, sur
maquettes, l'option **A — clair, très contrasté** (`.superpowers/brainstorm/…/content/contrast.html`, non commité).

## Objectifs

- Texte presque noir (`#0b0f14`), texte secondaire foncé (`#3a4250`), bordures marquées (`#c9ced6`).
- **En-tête** séparé du contenu par un trait de 2 px ; onglet d'org actif en gras, souligné de 3 px ; bascule
  Projets | CI | Commiters en bloc encadré, bouton actif plein (fond `#0b0f14`, texte blanc).
- **Tableaux** (Projets, Commiters) : en-têtes de colonnes en petites capitales grasses sur fond `#e9ecf1`, trait
  sous l'en-tête, lignes séparées par un trait ; dans Projets, **lignes en erreur** (les mêmes que celles que le tri
  met en tête : main rouge, PR rouge hors brouillon, gate ERROR) teintées `#fdf0f0` avec un liseré rouge de 4 px.
- **Couleurs de statut** plus franches : vert `#0a7a3d`, rouge `#c81e1e`, orange `#b45309`.
- **Pastilles** : gate OK (fond vert pâle, bordure verte), ERROR (fond rouge plein, texte blanc), WARN (orange) ;
  compteur de PR en trois pastilles vertes · rouges · en cours (zéro en gris) au lieu de « 1✅ 1❌ 0⏳ ».
- CI, panneaux latéraux et dialogue d'ajout d'org héritent des mêmes couleurs et pastilles.
- Mobile : mêmes règles ; vérifié dans Chrome à 390 px et en pleine largeur.

## Non-objectifs

Thème sombre, changement de mise en page ou de contenu, nouvelles fonctions. Les styles de mise en page pilotés par
le code (bascule étroit/large, `flex-basis`, `min-width` des onglets) restent en Java : des tests les vérifient.

## Approches

1. **Feuille de style du dashboard, chargée après Lumo** (retenue) : variables Lumo redéfinies et classes CSS pour
   l'en-tête, les pastilles, les tableaux ; les styles de présentation écrits en Java passent en classes.
2. Styles laissés en Java, couleurs changées sur place : thème dispersé dans une dizaine de fichiers, pas de règles
   sur les parties internes de la grille (en-têtes, lignes) — écartée.
3. Thème Lumo complet empaqueté (dossier `themes/`) : plus lourd pour le même résultat, et Vaadin 25 privilégie
   `@StyleSheet` — écartée.

## Décisions de construction (proposées, à corriger)

- Fichier `forge-dashboard-app/src/main/resources/META-INF/resources/styles/dashboard.css`, chargé par
  `@StyleSheet("styles/dashboard.css")` sur `DashboardAppShell`, après `Lumo.STYLESHEET`. Foy sert
  `META-INF/resources/` des racines de l'application ; à vérifier d'abord en mode production (image Docker). Si ce
  n'est pas servi : prévenir la session FOY, contourner en servant le fichier autrement.
- Lignes en erreur de la grille Projets : `Grid.setPartNameGenerator` → partie `error`, stylée par
  `vaadin-grid::part(error)` ; la règle d'erreur est celle du tri (`ProjectsModel`), exposée sur `ProjectRow`.
- Composants repérés par des classes `dash-*` (`dash-header`, `dash-view-toggle`, `dash-pill`, `dash-pill-ok`…).
- Tests : présence des classes et parties (`error` sur les bonnes lignes, pastilles du gate et des PR) ; le rendu se
  vérifie dans Chrome.

## Laissé au constructeur

Valeurs exactes des espacements et tailles, au plus près de la maquette A ; choix des sélecteurs internes de Vaadin
(`::part`) pour les tableaux et les onglets.
