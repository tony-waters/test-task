# Session notes

Hand-off notes for future agent sessions. This file is about current state and gotchas. Decisions belong in `GRILLING.md`, terms in `GLOSSARY.md`, and lasting choices in `docs/adr/`.

## Where things stand (2026-11-05)

- **Design**: done. `GRILLING.md` holds the 38 settled decisions (Q1–Q38), with answers.
- **Spec**: GitHub issue #1.
- **Tickets**: sub-issues of #1, all labelled `ready-for-agent`. GitHub's dependency feature records the blocking links.
  - #2 Load the data and browse Patients. **Closed 2026-11-05.** Problems found while building it are in `PROBLEMS.md`.
  - #3 Patient detail and timeline. **Closed 2026-11-05** (commit b3b3e7a). Its findings are PROBLEMS.md #12 to #16.
  - #4 Dashboard. **Built 2026-11-05, not yet closed.** The user reviews and closes it. Its findings are PROBLEMS.md #17 to #19.
  - #5 Roles, CSV export and operability (blocked by #2; best done after #3 and #4)
- **Not ticketed on purpose**: the 100× volume generator, query plans and indexes (spec §11). They sit at the cut line. Search indexing (`pg_trgm`) is also deferred to that work.
- **Code**: tickets #2, #3 and #4 (schema, loader, patient list, patient detail with Engagement summary and keyset timeline, dashboard, UI + API, Compose). Run with `DUMP_FILE=$PWD/dump.sql docker compose up --build`; `DUMP_FILE` defaults to `../dump.sql`.
- **Git**: tickets #2 and #3 are committed. The user commits; don't commit unless asked.
  - Uncommitted: `PROBLEMS.md` (untracked) and edits to `NOTES.md`.
  - Issue #1 links to `GLOSSARY.md` and `docs/adr/`, which don't resolve on GitHub until those files are pushed.

## Decided in ticket #3

- **Suspect timestamp rule** (user, 2026-11-05): recorded at, or within 1 s before, the moment extraction *began*, which is the earliest `source_created_at` in either table. That flags 47 on the dump. It is not each row's own `entity_created`, and not the latest one, which would flag 17. The code is `action.SuspectTimestamps`.
- The PatientRepository / `PatientSearch` fragment refactor carried over from #2 is done. `PatientListService` is gone, and `PatientService` is the shared service.
- JPQL constructor expressions keep the fully qualified class name (`select new uk.bit1...EngagementRow(...)`). The user tried Spring Data's implicit DTO rewrite and chose to keep the explicit form.
- A Codex review of #3 found two things. Unfamiliar Action types had no column on the detail page: fixed with `EngagementSummary.columnActionTypes()`. A screenshot showing real dump data was removed and never committed. Any future screenshot must use synthetic data.
- At close, 105 tests pass.

## Decided in ticket #4

- Status buckets, funnel and medians are computed in Java (`StatusCounts`, `Funnel`) from one projection of every Patient's journey timestamps. Weekly active is grouped in SQL (`DashboardQueries.activeByWeek`), and Java fills empty weeks (`WeeklyActive`). The range logic is in `WeeklyActiveQuery`.
- Weekly-active `from`/`to` are London dates that snap to their ISO weeks. A missing end defaults to the week of the first or last Action, and never makes the range run backwards. The range is capped at 520 weeks: a range the user chose in full that is too wide is a 400, and an end filled in from the data is pulled in to fit (Codex finding, fixed).
- Medians are reported in whole seconds (`seconds`, `n`). The page shows hours under two days, days after that.
- At hand-off, 146 tests pass and nothing from #4 is committed.

## Rules that are easy to break

- **The user writes `README.md`.** Never write or edit it, and never draft its prose. You may point out facts worth including.
- **Banned names**: the guard hooks and git hooks block certain names in file contents, paths and commit messages. The patterns live outside the repo, in `~/.task/forbidden-names.txt`. In practice this means never naming the company or product behind the task brief.
- **The brief and the dump are never committed.** That is `AI_TASK.md` and `dump.sql`, and also any pasted dump rows, for example in a migration, seed or fixture. The dump is read at runtime from a path outside the repo.
- Avoid copying real-looking patient data (names, NHS numbers) from the dump into docs or issues. Use placeholders such as `9990000000`.
- To check text before writing it anywhere public: `scripts/check-submission.sh stdin <path> < file`.

## Environment gotchas

- **Thymeleaf restricted attributes**: in `th:attr`, `th:data-*` and similar, Thymeleaf 3.1 rejects bean references (`@fmt`) and anything that reads like a static call, such as `t(` in `weekStart(`. Put formatted text in `th:text` instead.
- The Chrome extension was not connected in the #4 session. `google-chrome --headless=new --screenshot=...` works for visual checks.

- The Stop hooks run `./mvnw -q -DskipITs test` (only when files under `src/` or `pom.xml` change) and `.claude/hooks/test-guards.sh`. Both must pass before a turn can end. They passed at the end of the last session.
- System `mvn` 3.9.12 and Java 21 are installed. Docker availability was not checked.
- `gh` is 2.46, which has no `--parent` flag. For sub-issues and blocking links, use the REST API, as in `docs/agents/issue-tracker.md`.
- The repo is **public**, so issues and comments are visible to everyone.
- An untracked `.claude/settings.local.json.tmp.*` file is a stray from Claude Code. The user said to leave it alone and not ignore it.

## Data facts worth remembering

- The user's original notes (in `README.md`) say patient 42 has 238 post-discharge Actions. **That is wrong.** Patient 42 has zero Actions and is the one *skipped registration* case. The 238 post-discharge Actions are spread across 12 of the 17 discharged patients. The user knows; don't re-raise it, but don't repeat the error either.
- On the supplied data, expected test figures are:
  - Current-status buckets: Invited 3, Registered 80, Discharged 17.
  - Suspect-timestamp Actions 47 (3 Patients), post-discharge Actions 238, skipped registration 1.
  - Strict funnel: 100 → 96 → 16.
- The full profile is under "Facts gathered before Round 1" in `GRILLING.md`.
