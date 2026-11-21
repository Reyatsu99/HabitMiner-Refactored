# Repository structure and branch guide

## Default branch

`main` is the canonical integration branch and the repository's default branch. It contains the integrated HabitMiner Extended source and should remain buildable. Changes should normally enter through a pull request or a reviewed commit, then be validated by the Android workflow.

At the time of this guide, `main` and `harsh-habitminer-extended` are synchronized after the sequential habit-mining integration.

## Source branches

| Branch | Purpose | Handling |
|---|---|---|
| `main` | Canonical application source | Default branch; keep tested and current |
| `harsh-habitminer-extended` | Extended integration branch | Currently synchronized with `main`; use only if a separate integration branch is needed |
| `feat/context-aware-habit-mining` | Sequential pattern-mining feature branch | Integrated through PR #5; retained as historical feature work |
| `HM-Extended` | Earlier Extended snapshot | Historical branch; not a release source |
| `HM-Extended-copy` | Duplicate of the earlier Extended snapshot | Same commit as `HM-Extended`; do not develop on both |

The two `HM-Extended*` branches are intentionally retained as historical references. They should not be merged into `main` again without reviewing their ancestry and tree differences.

## Generated-output branches

These branches are workflow outputs, not application source branches:

- `ci-out-HM-Extended`
- `ci-out-feat-context-aware-habit-mining`
- `ci-out-harsh-habitminer-extended`
- `ci-outputs`
- `ci-screenshots`

The Android workflow creates a branch named `ci-out-<source-branch>` for non-pull-request builds. The older `ci-outputs` and `ci-screenshots` branches are retained legacy output snapshots. Never merge any `ci-*` branch into application source.

## Application source map

All Android application code lives under `android/app/src/main/java/com/habitminer/`:

| Package | Responsibility |
|---|---|
| `analytics/` | Pure Kotlin analytics and statistical helpers |
| `collection/` | Usage-event ingestion, sensors, and device events |
| `data/` | Room entities, DAOs, preferences, import/export |
| `di/` | Hilt dependency-injection modules |
| `domain/` | Domain rules and app identity |
| `engine/` | Analysis orchestration, habit discovery, sequential pattern mining |
| `goals/` | Goals, intentions, and reminders |
| `mindful/` | Mindful pause and focus sessions |
| `proactive/` | Prompts, nudges, and notifications |
| `repository/` | Data access and repository abstractions |
| `sources/` | Optional/external data sources |
| `ui/` | Jetpack Compose screens and design system |
| `update/` | Release checking and installation |
| `widget/` | Home-screen widget |

Tests are under `android/app/src/test/java/com/habitminer/`. Build configuration and the Gradle wrapper are under `android/`. CI and release automation are under `.github/workflows/`.

## Documentation and reference material

- `docs/ARCHITECTURE.md`, `docs/METHODOLOGY.md`, `docs/FINDINGS.md`, and `docs/BUGS.md`: current design and analysis documentation.
- `docs/SEQUENTIAL_HABIT_MINING.md`: algorithm behavior, safeguards, and limitations.
- `docs/archive/`: historical version-specific README files.
- `docs/reports/`: generated or standalone project reports.
- `docs/reference/`: supporting reference material that is not application source.

The root `README.md` is the single project entry point. Do not add version-specific README files or generated reports to the root.

## CI expectations

The `Android build` workflow runs debug/release APK builds, unit tests, Room schema reporting, and APK artifact upload. Pull requests run validation but do not push generated-output branches. Release publication is triggered by version tags matching `vMAJOR.MINOR.PATCH` and requires the configured signing secrets.

Do not treat a workflow run as passed until its conclusion is `success`. Do not force-update `main` to resolve divergence; use a reviewed merge or a fast-forward only when GitHub confirms that `main` is an ancestor of the intended target.
