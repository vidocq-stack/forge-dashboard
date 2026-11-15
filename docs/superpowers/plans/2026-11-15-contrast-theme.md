# Thème contrasté (option A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the dashboard the high-contrast look of mockup A, on PC and phone, with one stylesheet loaded after Lumo.

**Architecture:** `META-INF/resources/styles/dashboard.css` redefines Lumo's colour tokens and styles classes named in one
Java class, `ui/Theme`; the views put those classes on their components instead of inline presentation styles. Error
rows of the projects grid get a grid part from the same rule as the sort. A contract test checks that every class and
part the views use has a rule in the stylesheet.

**Tech Stack:** Vaadin Flow 25.3 (Lumo, `@StyleSheet`, `Grid.setPartNameGenerator`), Foy (serves `META-INF/resources/`), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-15-contrast-theme-design.md`

## Global Constraints

- Colours, verbatim from the spec: text `#0b0f14`, secondary text `#3a4250`, borders `#c9ced6`, header cells `#e9ecf1`,
  error rows `#fdf0f0` with a 4 px red left edge, green `#0a7a3d`, red `#c81e1e`, orange `#b45309`.
- Header: 2 px bottom rule; selected org tab bold, 3 px underline; view toggle framed, active button filled `#0b0f14`
  with white text.
- Pills (from mockup A): OK `#dcf5e5` on `#065f2e`, 1 px `#0a7a3d` border; ERROR `#c81e1e`, white text; WARN and
  pending `#fdecc8` on `#7a3a00`; zero `#eef0f3` on `#8a929e`.
- Layout styles driven by code stay in Java (`flex-basis`, `min-width`, narrow/wide toggles): tests read them.
- Non-goals: dark theme, layout or content changes, new features.
- CSS class names start with `dash-` and are constants of `ui/Theme`; the error grid part is `error`.
- TDD for every behaviour change; a pure CSS value tweak found in the browser check is a ledger ruling, not a test.
- Commits end with the session's attribution lines.
- `$S/mvn-test.sh <log> [tests]` runs `./mvnw -ntp -o -pl forge-dashboard-app clean verify` (filter: comma-separated
  classes) and ends the log with `EXIT <code>`; `$S/build-image.sh` builds `forge-dashboard:dev` (`IMAGE <code>`);
  `$W` is the plan's workspace directory.

## Review Focus

1. The stylesheet not served by the production image (Foy, Vaadin production mode): the page silently keeps the old
   look — Task 1 checks the computed text colour in the Docker image before anything else is built on it.
2. The framed view toggle and period buttons on a 390 px phone in German and Polish (longest labels): they must wrap
   or shrink, never overflow the header — Task 5 checks both languages at 390 px.
3. Overlays (avatar menu, language submenu, tab context menu, tooltips, org dialog) after the token change: text must
   stay readable on its background — Task 5 opens each one.
4. A project with no PR: its counter button is disabled; its three zero pills must stay legible, not washed out —
   Task 5 looks at one; Task 3's test pins the zero class.
5. An unfolded error row (its PR list open): the details row must not inherit a broken tint or border — Task 5
   unfolds a red project.

---

### Task 1: The dashboard stylesheet, loaded after Lumo

**Files:**
- Create: `forge-dashboard-app/src/main/resources/META-INF/resources/styles/dashboard.css`
- Create: `forge-dashboard-app/src/main/java/io/vidocq/tools/dashboard/ui/Theme.java`
- Modify: `forge-dashboard-app/src/main/java/io/vidocq/tools/dashboard/ui/DashboardAppShell.java`
- Test: `forge-dashboard-app/src/test/java/io/vidocq/tools/dashboard/ui/DashboardAppShellTest.java`, `.../ui/ThemeTest.java`

**Interfaces:**
- Produces: `final class Theme` with `static final String STYLESHEET = "styles/dashboard.css"` and
  `static final List<String> CLASSES` (every `dash-*` class later tasks add, empty for now) and
  `static final List<String> PARTS` (grid parts, empty for now); `static String css()` in `ThemeTest` reads the file.

- [ ] **Step 1: Write the failing tests**

`DashboardAppShellTest.dashboardStylesheetComesAfterLumo`: the `@StyleSheet` values of `DashboardAppShell`, in order,
equal `List.of(Lumo.STYLESHEET, Theme.STYLESHEET)`.

`ThemeTest`:
- `stylesheetIsAWebResourceOfTheApp`: `DashboardAppShell.class.getResourceAsStream("/META-INF/resources/" + Theme.STYLESHEET)` is not null.
- `contrastColours`: the CSS contains `--lumo-body-text-color: #0b0f14`, `--lumo-secondary-text-color: #3a4250`,
  `--lumo-success-color: #0a7a3d`, `--lumo-error-color: #c81e1e`, `--lumo-warning-color: #b45309`.
- `everyThemeClassAndPartIsStyled`: for each of `Theme.CLASSES` the CSS contains `"." + name`; for each of
  `Theme.PARTS`, `"::part(" + name`.

- [ ] **Step 2: Run them, expect RED** (no `Theme`, no file: compilation fails; then, with `Theme` stubbed, assertions fail)

Run: `$S/mvn-test.sh $W/t1-red.log 'DashboardAppShellTest,ThemeTest'`
Expected: `EXIT 1`, failures in `dashboardStylesheetComesAfterLumo`, `stylesheetIsAWebResourceOfTheApp`, `contrastColours`.

- [ ] **Step 3: Implement**

`@StyleSheet(Theme.STYLESHEET)` after `@StyleSheet(Lumo.STYLESHEET)` on the shell. The CSS sets, on `html`, the five
tokens above plus `--lumo-header-text-color: #0b0f14`, `--lumo-error-text-color` and `--lumo-success-text-color` in the
darker shades of the pills (`#a51717`, `#065f2e`), and a `--dash-border: #c9ced6` used by later rules.

- [ ] **Step 4: Run them, expect GREEN**, then the whole suite: `$S/mvn-test.sh $W/t1-suite.log` → `EXIT 0`.

- [ ] **Step 5: Production check (Review Focus 1)**

`$S/build-image.sh` → `IMAGE 0`; start the local stack (`compose.local.yml`), sign in in Chrome, then in the page
`getComputedStyle(document.body).color` → `rgb(11, 15, 20)` and the network shows `styles/dashboard.css` 200.
If not served: tell the FOY session, rule a workaround in the ledger, apply it, check again.

- [ ] **Step 6: Commit** — `feat(ui): a dashboard stylesheet after Lumo, darker text and status colours`

### Task 2: Error rows of the projects grid

**Files:**
- Modify: `.../ui/model/ProjectRow.java`, `.../ui/model/ProjectsModel.java`, `.../ui/ProjectsPanel.java`, `.../ui/Theme.java`, `dashboard.css`
- Test: `.../ui/model/ProjectsModelTest.java`, `.../ui/ProjectsPanelIntegrationTest.java`

**Interfaces:**
- Consumes: `ProjectsModel.severity(RepoView)` (0 red main, 1 red non-draft PR, 2 gate ERROR, 3 none).
- Produces: `ProjectRow` gains a last component `boolean error` (`severity < 3`); `Theme.ERROR_ROW = "error"`, listed in `Theme.PARTS`.

- [ ] **Step 1: Write the failing tests**

`ProjectsModelTest.errorRowsAreTheOnesSortedFirst`: rows for a red main, a red non-draft PR, a gate ERROR → `error()`
true; a project whose only red PR is `WIP: x`, and a healthy one → false. Update `rowCountsPullStatesAndFormatsSonar`'s
expected `ProjectRow` with `false`.

`ProjectsPanelIntegrationTest.errorRowsGetTheErrorPart`: the panel's grid `getPartNameGenerator()` gives `"error"` for
a `ProjectRow` with `error` true and `null` for one with false.

- [ ] **Step 2: Run, expect RED** — `$S/mvn-test.sh $W/t2-red.log 'ProjectsModelTest,ProjectsPanelIntegrationTest'`

- [ ] **Step 3: Implement** — the record component; `row()` sets it from `severity`; the grid's part name generator;
the grid drops `LUMO_ROW_STRIPES` (separators replace stripes). CSS, for every grid: header cells `#e9ecf1`, small
bold capitals, 1.5 px `#0b0f14` rule under the header row, body cells separated by `var(--dash-border)`; for the
`error` part, background `#fdf0f0`, and on its first column cell an inset 4 px `#c81e1e` left edge.

- [ ] **Step 4: Run, expect GREEN**, then the whole suite.

- [ ] **Step 5: Commit** — `feat(ui): projects in error stand out in the grid`

### Task 3: Gate and PR pills

**Files:**
- Modify: `.../ui/Badges.java`, `.../ui/ProjectsPanel.java`, `.../ui/model/ProjectRow.java` (drop `pullCounts()`), `.../ui/Theme.java`, `dashboard.css`, the seven `i18n/messages*.properties`
- Test: `.../ui/BadgesTest.java` (new)

**Interfaces:**
- Produces: `Badges.gate(GateStatus) : Span` with classes `dash-pill` + `dash-pill-ok | dash-pill-err | dash-pill-warn`
  (NONE: text `—`, class `dash-muted`, no pill); `Badges.pulls(ProjectRow, Messages) : Span` of class `dash-pills`
  holding three `Span`s (green, red, pending counts) of class `dash-pill` + `dash-pill-ok | dash-pill-err |
  dash-pill-pending`, or `dash-pill-zero` for a 0, and a `title` from key `projects.pullCounts`.
  `Theme` constants `PILL, PILL_OK, PILL_ERR, PILL_WARN, PILL_PENDING, PILL_ZERO, PILLS, MUTED`, all in `CLASSES`.
- Key `projects.pullCounts`: en `{0} green, {1} red, {2} running`; fr `{0} vertes, {1} rouges, {2} en cours`;
  de `{0} grün, {1} rot, {2} laufend`; es `{0} verdes, {1} rojas, {2} en curso`; it `{0} verdi, {1} rosse, {2} in corso`;
  nl `{0} groen, {1} rood, {2} bezig`; pl `zielone: {0}, czerwone: {1}, w toku: {2}`.

- [ ] **Step 1: Write the failing tests** (`BadgesTest`)

- `gateIsAPill`: OK → text `OK`, classes contain `dash-pill`, `dash-pill-ok`; ERROR → `dash-pill-err`; WARN →
  `dash-pill-warn`; NONE and null → text `—`, class `dash-muted`, not `dash-pill`.
- `pullsAreThreePills`: a row with 1 green, 1 red, 0 pending, in French → child texts `["1", "1", "0"]`, classes
  `dash-pill-ok`, `dash-pill-err`, `dash-pill-zero`; title `1 vertes, 1 rouges, 0 en cours`.
- `zeroPullsAreThreeZeroPills` (Review Focus 4): 0/0/0 → three `dash-pill-zero`.

- [ ] **Step 2: Run, expect RED** — `$S/mvn-test.sh $W/t3-red.log 'BadgesTest,TranslationFilesTest'`

- [ ] **Step 3: Implement** — the two methods; the PR counter button takes `Badges.pulls(row, messages)` as its
content; the PR drawer's Sonar line keeps calling `Badges.gate`. CSS for the pills with the Global Constraints colours,
`font-variant-numeric: tabular-nums`, 4 px radius; `dash-muted` is `var(--lumo-secondary-text-color)`.

- [ ] **Step 4: Run, expect GREEN**, then the whole suite.

- [ ] **Step 5: Commit** — `feat(ui): gate and pull request counts as pills`

### Task 4: Header, toggles, cards and muted text

**Files:**
- Modify: `.../ui/MainView.java`, `.../ui/CiPanel.java`, `.../ui/CommittersPanel.java`, `.../ui/Parts.java`, `.../ui/ProjectsPanel.java`, `.../ui/OrgAdminDialog.java`, `.../ui/Theme.java`, `dashboard.css`
- Test: `.../ui/MainViewIntegrationTest.java`, `.../ui/CiPanelIntegrationTest.java`, `.../ui/CommittersPanelIntegrationTest.java`

**Interfaces:**
- Produces: `Theme.HEADER = "dash-header"`, `Theme.TOGGLE = "dash-toggle"`, `Theme.CARD = "dash-card"`, in `CLASSES`.

- [ ] **Step 1: Write the failing tests**

- `MainViewIntegrationTest.headerAndViewToggleCarryTheThemeClasses`: the header layout has class `dash-header` and no
  inline `border-bottom`; the parent of the « Projets » button has class `dash-toggle`.
- `CiPanelIntegrationTest.windowButtonsAreAToggle`: the parent of the window buttons has `dash-toggle`.
- `CommittersPanelIntegrationTest.periodButtonsAreAToggle`: the parent of the period buttons has `dash-toggle`.

- [ ] **Step 2: Run, expect RED** — `$S/mvn-test.sh $W/t4-red.log 'MainViewIntegrationTest,CiPanelIntegrationTest,CommittersPanelIntegrationTest'`

- [ ] **Step 3: Implement** — classes on the header, the three toggle groups and the runner card (its inline border
goes to CSS); `Parts.muted`, the PR author and the dialog hint use `Theme.MUTED` instead of an inline colour. CSS:
header 2 px `#0b0f14` bottom rule; in it, selected `vaadin-tab` bold with a 3 px `#0b0f14` underline and other tabs
`#3a4250`; `.dash-toggle` framed 1.5 px `#0b0f14`, 6 px radius, buttons dark text, the `primary` one filled
`#0b0f14` with white text; `.dash-card` 1 px `var(--dash-border)`.

- [ ] **Step 4: Run, expect GREEN**, then the whole suite.

- [ ] **Step 5: Commit** — `feat(ui): a ruled header, framed toggles and cards`

### Task 5: Browser check on PC and phone, docs

**Files:**
- Modify: `README.md` (one line: the look lives in `styles/dashboard.css`), `CLAUDE.md` (UI bullet: Lumo then `styles/dashboard.css`, classes in `ui/Theme`), `dashboard.css` (tweaks)

- [ ] **Step 1:** `$S/build-image.sh` → `IMAGE 0`; local stack up; Chrome signed in.
- [ ] **Step 2:** full width, French: Projects (a red row unfolded — Review Focus 5), CI, Committers, a PR drawer, a
  runner drawer, the org dialog, avatar menu with the language submenu, a tab's context menu, a tooltip (Review Focus 3).
- [ ] **Step 3:** 390 px (iframe on `/healthz` as before), German then Polish: header and toggles do not overflow
  (Review Focus 2); a project with no PR shows legible zero pills (Review Focus 4).
- [ ] **Step 4:** each CSS-only tweak a ledger ruling; any behaviour change goes back through a failing test.
- [ ] **Step 5:** docs; whole suite; commit `docs: the dashboard's look`; stop the local stack.
