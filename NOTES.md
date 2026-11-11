# Session notes

Hand-off notes for future agent sessions. This file is about current state and gotchas. Decisions belong in `GRILLING.md`, terms in `GLOSSARY.md`, and lasting choices in `docs/adr/`.

## Where things stand (2026-11-05)

- **Design**: done. `GRILLING.md` holds the 38 settled decisions (Q1–Q38), with answers.
- **Spec**: GitHub issue #1.
- **Tickets**: sub-issues of #1, all labelled `ready-for-agent`. GitHub's dependency feature records the blocking links.
  - #2 Load the data and browse Patients. **Closed 2026-11-05.** Problems found while building it are in `PROBLEMS.md`.
  - #3 Patient detail and timeline. **Closed 2026-11-05** (commit b3b3e7a). Its findings are PROBLEMS.md #12 to #16.
  - #4 Dashboard. **Closed 2026-11-05** (commit e9d110e). Its findings are PROBLEMS.md #17 to #19.
  - #5 Roles, CSV export and operability. **Closed 2026-11-05**, before being committed. Its findings are PROBLEMS.md #20 to #23, plus updates to #7 and #8.
  - #6 Volume: 100× generator, query plans and indexes (spec §11, the cut line). Created 2026-11-05, `ready-for-agent`, not blocked. It also carries the `pg_trgm` decision, the suspect-timestamp threshold scans, and the export's connection hold (PROBLEMS.md #3, #4, #13, #14, #17, #18, #20).
  - #8 Restructure packages by use case (ADR 0003). Created 2026-11-11. **Implemented on branch `package-by-use-case` (23a1a4f), deliberately not merged**: the user is trying a different approach on `main` first. Don't redo #8 here. That branch's `NOTES.md` has its "Decided in ticket #8" section. If the other approach wins, record it in a new ADR that supersedes 0003. The issue stays open. The pre-refactor code is preserved on branch `v1-original`.
  - **Alternative being tried on `main` (2026-11-11, uncommitted)**: layers then aggregate then kind, modelled on the user's other Spring Boot project. This is the option ADR 0003 rejected.
    - `domain/{common,patient,action}` holds entities, enums, domain rules (`Status`, `Age`, `SuspectTimestamps`), `London` and entity-only repositories.
    - `application/{common,patient,dashboard,export}` holds `*QueryService`s, the CSV export (`application/export`: `PatientExport`, `PatientCsv`; it reads through `PatientQueryService.stream`, never a repository), `PatientQueryRepository` (with the `PatientSearch` fragment, the Engagement and timeline queries), and read models plus query objects under `query/`. `application/common` holds the request-error types that used to sit in `web`.
    - `web/{patient,dashboard}` holds controllers, `PatientRequest` and `PatientListView`. `web/` keeps the cross-cutting pieces. `importer` and `security` are unchanged.
    - Commands go in `application/<aggregate>/command` when they arrive.
    - Data access goes through Spring Data repositories (user preference, 2026-11-11). `DashboardQueries` became `application.dashboard.DashboardQueryRepository`. The extraction-start query moved from `SuspectTimestamps` to `PatientQueryRepository.extractionStarted()`, which leaves `SuspectTimestamps` a pure domain rule with no `EntityManager`. Only `DumpLoader` still uses `JdbcTemplate`, for its `on conflict` upsert.
    - Dependencies point domain ← application ← web, strictly: `web` imports nothing from `domain` (filter options come from `PatientListQuery.STATUSES`/`MODULES`, the display zone from `application.common.DisplayZone`) and never a repository. Read models still carry domain enums such as `Status` through to the views. `importer` and `security` sit beside the layers (`security` may use `web`). `LayerDependencyTest` (ArchUnit) enforces all of this.
    - The comment in `V1__…sql` still names `patient.Status`. It is left as it is because editing an applied migration changes its Flyway checksum.
  - #7 Hardening. Created 2026-11-05, `ready-for-agent`, not blocked. Covers PROBLEMS.md #6, #7, #8, #11, #15 and #22: Compose DB port and default passwords, search terms in URLs (needs a decision against GRILLING Q19/Q21), unmatched-path log redaction, the timeline cursor's internal id, duplicate UUIDs in a dump, and an opt-in test against the real dump.
- **Code**: tickets #2 to #5 (schema, loader, patient list, patient detail with Engagement summary and keyset timeline, dashboard, UI + API, Compose, login and roles, CSV export, request logging, health probes). Run with `DUMP_FILE=$PWD/dump.sql docker compose up --build`; `DUMP_FILE` defaults to `../dump.sql`.
- **Git**: tickets #2, #3 and #4 are committed. The user commits; don't commit unless asked.
  - Uncommitted: `PROBLEMS.md` (untracked), edits to `NOTES.md`, and all of ticket #5.
  - Issue #1 links to `GLOSSARY.md` and `docs/adr/`, which don't resolve on GitHub until those files are pushed.

## Decided in ticket #3

- **Suspect timestamp rule** (user, 2026-11-05): recorded at, or within 1 s before, the moment extraction *began*, which is the earliest `source_created_at` in either table. That flags 47 on the dump. It is not each row's own `entity_created`, and not the latest one, which would flag 17. The code is `domain.action.SuspectTimestamps`.
- The PatientRepository / `PatientSearch` fragment refactor carried over from #2 is done. `PatientListService` is gone, and `PatientService` (now `application.patient.PatientQueryService`) is the shared service.
- JPQL constructor expressions keep the fully qualified class name (`select new uk.bit1...EngagementRow(...)`). The user tried Spring Data's implicit DTO rewrite and chose to keep the explicit form.
- A Codex review of #3 found two things. Unfamiliar Action types had no column on the detail page: fixed with `EngagementSummary.columnActionTypes()`. A screenshot showing real dump data was removed and never committed. Any future screenshot must use synthetic data.
- At close, 105 tests pass.

## Decided in ticket #4

- Status buckets, funnel and medians are computed in Java (`StatusCounts`, `Funnel`) from one projection of every Patient's journey timestamps. Weekly active is grouped in SQL (`DashboardQueryRepository.activeByWeek`, was `DashboardQueries`), and Java fills empty weeks (`WeeklyActive`). The range logic is in `WeeklyActiveQuery`.
- Weekly-active `from`/`to` are London dates that snap to their ISO weeks. A missing end defaults to the week of the first or last Action, and never makes the range run backwards. The range is capped at 520 weeks: a range the user chose in full that is too wide is a 400, and an end filled in from the data is pulled in to fit (Codex finding, fixed).
- Medians are reported in whole seconds (`seconds`, `n`). The page shows hours under two days, days after that.
- At close, 146 tests pass.

## Decided in ticket #5

- Two filter chains in `security.SecurityConfig`. `/api/**` and `/actuator/**` use HTTP Basic and answer 401/403 as ProblemDetail (`ProblemResponses`, because they happen before MVC). Everything else uses form login. The API chain also accepts the form-login session, so the JSON links and Swagger UI work from the browser. Basic requests don't create sessions.
- Users: `clinician` (CLINICIAN) and `admin` (ADMIN only, which the rules treat as a superset). Passwords from `CLINICIAN_PASSWORD` / `ADMIN_PASSWORD`, local defaults `clinician` / `admin`. Export and all Actuator endpoints except health are ADMIN.
- **Tests run as a signed-in CLINICIAN by default**: `@IntegrationTest` carries `@WithMockUser`. Override per class or method, or use `httpBasic(...)` / `anonymous()`. `SecurityTest` uses the real credentials.
- Logging is Spring Boot structured logging (`logging.structured.format.console: logstash`), so *every* log line is JSON, including the loader's. `RequestLogFilter` writes the request line; the request ID is in the MDC. The role is captured by `RoleCaptureFilter` inside each chain, because the security context is cleared before the outer filter logs. The endpoint is the MVC route pattern; the Patient UUID is a separate `patient` field.
- Export: `PatientExport` (read-only transaction, audit line on logger `uk.bit1.engagement.audit`) over `PatientSearch.stream`, a `getResultStream()` with Hibernate's fetch-size hint (`app.export.fetch-size`, default 500). `PatientExportTest.readsThroughADatabaseCursor` proves the cursor via `pg_cursors`; it was confirmed to fail with the hint removed. CSV times are London local with offset; formula-like values get a leading apostrophe.
- Checked on the real dump (new build on port 8081 against the Compose database): export 100 rows, `status=REGISTERED` 80. 421 identifier values from the export were searched for in the app log: none found.
- **Load mode has no web server**, so anything web-only must be `@ConditionalOnWebApplication`. `SecurityConfig` wasn't at first, and the Compose loader failed to start (no `HttpSecurity` bean). Every other integration test starts the web app, so `LoadModeTest` now starts the app the way the loader does.
- At hand-off, 175 tests pass and nothing from #5 is committed. `DUMP_FILE=$PWD/dump.sql docker compose up -d --build` was checked after the fix: the loader exits 0 (100 / 3,183 inserted), readiness is UP, 3 / 80 / 17, clinician export 403, admin export 100 rows.

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
