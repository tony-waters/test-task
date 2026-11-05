# Grilling: domain model and design decisions

A record of the design-tree grilling run against the brief, the supplied database dump and `TECHNOLOGIES.md`. Each round asks every question whose prerequisites are already settled. Answers are recorded under each question as they come in.

## Facts gathered before Round 1

Profiled from the dump (no rows are reproduced here):

- **Size**: 100 patients, 3,183 actions. Every action belongs to a known patient. There are no duplicate UUIDs, NHS numbers or hospital IDs.
- **Status columns**: 80 registered and not discharged, 16 registered and discharged, 3 invited only, and 1 discharged but never registered (patient entity id 42). No timestamp is out of order: registration is never before invitation, and discharge is never before registration.
- **Patient 42**: has **zero** actions. The "238 actions after discharge" figure is real, but it is spread across **12 of the 17 discharged patients** (between 2 and 61 each), not one patient. So acting after discharge is the norm, not a one-off anomaly.
- **Missing values**: date of birth is null for 24 patients, gender for 22, hospital ID for 20 and NHS number for 17. Every patient has at least one of the two identifiers. All NHS numbers pass the mod-11 check digit.
- **Ages**: dates of birth run from 1961 to 2007, so everyone was 18 or over when the dump was taken.
- **Action columns**: the dump's `action` table has three classifying columns:
  - `module_id`: ASSESSMENT, DIARY or PROGRAMME
  - `context`: 12 values, each belonging to exactly one module (for example FATIGUE is PROGRAMME, PHQ9 is ASSESSMENT, BLADDER is DIARY)
  - `activity`: STARTED, TASK-COMPLETED, TASK-SKIPPED, FINISHED, ABANDONED or COMPLETED
- **Repeated runs**: 59 patient/context pairs have more than one STARTED, 16 have both a FINISHED and an ABANDONED, and 9 have more than one FINISHED. 28 patient/assessment pairs were completed more than once.
- **Look-alike duplicates**: 1,592 actions share patient, timestamp, activity and context with another action, but have distinct UUIDs. The generator seems to put a run's tasks at the same time of day.
- **Suspect timestamps**: 47 actions have `when_recorded` within one second of their own `entity_created`, which is the moment the dump was generated (2026-01-16 13:00). Three are *after* it, so they were recorded in the future. This looks like future dates clamped to "now".
- **No actions** for 5 patients, including one who registered.
- **Time zones**: all timestamps are `timestamp without time zone`.
- **Repo state**: there is no Maven wrapper (`mvnw`, `.mvn/wrapper/`) and no source code yet.

---

## Round 1

❓ **Q1 - Canonical name for the recorded event**: The brief says *Action*. The dump's table is also `action`, but it has a column called `activity` holding the verb (STARTED, TASK-COMPLETED, ...). So the dump does not use "Activity" as a synonym for Action. It uses it for the *kind* of Action.

➡️ Yes: **Action** is the recorded event, and **Action type** is the verb (STARTED, TASK-COMPLETED, ...). "Activity" goes on the avoid list because it collides with "activity" in the brief's "active patients" metric.

✅ **Answer**: accepted as recommended.

---

❓ **Q2 - Name for `module_id` vs `context`**: The brief's "filter by Action module" lines up with `module_id` (ASSESSMENT / DIARY / PROGRAMME). `context` is the specific thing inside a module: the Fatigue programme, the Bladder diary, the PHQ9 assessment. "Context" is vague and clashes with "bounded context" in these docs. Options:
- (a) **Module** + **Topic**
- (b) **Module** + **Context** (keep the dump's name)
- (c) **Module** + **Instrument** (fits assessments, but is odd for diaries)

➡️ Yes to (a): **Module** = ASSESSMENT / DIARY / PROGRAMME, and **Topic** = the named programme, diary or assessment within a Module. The column stays `context` only in the import mapping.

✅ **Answer**: accepted as recommended.

---

❓ **Q3 - What Discharge means for later Actions**: Your notes treat activity after discharge as an anomaly of one patient. The data shows 238 Actions after discharge across 12 of 17 discharged patients. Options:
- (a) Discharge is terminal for status, but Actions after it are kept, shown, and counted in weekly-active figures. The timeline marks them as "after discharge".
- (b) Treat them as bad data and exclude them from aggregates.
- (c) Model re-admission (multiple care episodes). The data has no episode concept to support this.

➡️ Yes to (a). Record it in the README as a data finding, not a bug.

✅ **Answer**: accepted as recommended.

---

❓ **Q4 - Derived status rule**: Status is derived from the timestamps, latest stage wins: Discharged if `when_discharged` is set, else Registered if `when_registered` is set, else Invited. That makes patient 42 (discharged, never registered) **Discharged**, with a "skipped registration" data-quality flag.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q5 - Dashboard buckets**: Options:
- (a) **Mutually exclusive by current status**: Invited 3, Registered 80, Discharged 17 (including patient 42), total 100. The totals add up to the patient count.
- (b) Cumulative ("ever reached"): Invited 100, Registered 96, Discharged 17.

➡️ Yes to (a) for the dashboard, labelled "current status" in the UI. The funnel (a later question) is cumulative by nature.

✅ **Answer**: accepted as recommended.

---

❓ **Q6 - Time zone meaning**: Your notes lean towards UTC storage with London display. Proposal:
- The dump's naive timestamps are **UTC instants**, stored as `timestamptz`.
- Grouping (weeks), date-range filtering and display all use **Europe/London**.
- Weeks are ISO weeks (Monday start) in London time.
- Age is computed against today's date in London.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q7 - How the data gets loaded**: The dump can't be committed, and its DDL must not be relied on. Options:
- (a) A Java loader, run as a Spring command or profile, that takes the dump's path, parses only the two `COPY` blocks, and upserts by UUID (`ON CONFLICT DO NOTHING/UPDATE`). This is safe to run twice.
- (b) Restore the dump into a staging schema with `psql`, then run `INSERT ... SELECT ... ON CONFLICT`. Its `\restrict` line needs psql 18+, which is fragile.

➡️ Yes to (a). Docker Compose mounts the dump file from outside the repo.

✅ **Answer**: accepted as recommended.

---

❓ **Q8 - Identifiers**: The dump has a bigint `entity_id` (the foreign-key target) and a UUID `id`. Proposal: the UUID is the only identifier exposed in URLs and the API, so "malformed identifier" means "not a UUID". Our own schema keeps a bigint surrogate primary key for joins. The dump's `entity_id` is used only during import to resolve the patient foreign key.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q9 - Look-alike duplicate Actions**: 1,592 Actions match another on patient, timestamp, Action type and Topic, but have different UUIDs. Options:
- (a) Treat them as distinct (UUID is identity).
- (b) Deduplicate on the natural key.

➡️ Yes to (a). Same-time tasks are plausible for a generator and for a real app (bulk sync). Document it.

✅ **Answer**: accepted as recommended.

---

❓ **Q10 - Actions with suspect timestamps**: 47 Actions were stamped at dump-generation time, and 3 are in the future relative to it. Options:
- (a) Keep and include them, add a data-quality flag, and document them.
- (b) Exclude them from weekly-active and engagement figures.
- (c) Drop them at import.

➡️ Yes to (a).

✅ **Answer**: accepted as recommended.

---

❓ **Q11 - Engagement summary granularity**: Topics are started repeatedly (59 pairs) and can be both FINISHED and ABANDONED. Options:
- (a) Summarise per Module, and per Topic within it: counts per Action type, plus first and last Action time. No notion of a "run".
- (b) Reconstruct **Runs** (STARTED → FINISHED/ABANDONED) and report completion per run.

➡️ Yes to (a) for now. Runs are a possible later refinement.

✅ **Answer**: accepted as recommended.

---

❓ **Q12 - Missing demographics**: Proposal:
- A null date of birth means age is "Unknown". It is excluded from any age statistics and is never guessed.
- A null gender shows as "Not recorded".
- Search covers given name, family name, NHS number and hospital ID, treating nulls as absent.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q13 - Scope cut line (the "two thirds")**: Proposed build order:
1. Migrations and loader
2. Patient list and detail (API and UI)
3. Dashboard, funnel and weekly-active
4. Error handling and OpenAPI
5. Auth with two roles
6. Tests (unit, Testcontainers, N+1 guard)
7. Docker Compose

Candidates to cut or leave partial, described honestly in the README: the 100× volume generator with query plans, CSV streaming export, and structured slow-request logging.

➡️ Yes: this order, with **100× volume + query plan** and **CSV export** as the first to cut.

✅ **Answer**: accepted as recommended.

---

❓ **Q14 - UI technology**: `TECHNOLOGIES.md` wants a UI early. Options:
- (a) Server-rendered **Thymeleaf** pages over the same services as the JSON API, plus Chart.js. Filter and page state live naturally in the URL query string.
- (b) A separate SPA (React/Vite) calling the REST API.

➡️ Yes to (a): fewer moving parts, and URL-shareable state comes for free.


✅ **Answer**: accepted as recommended.
### Round 1 outcome

- All 14 recommendations accepted.
- `GLOSSARY.md` created with the resolved terms: Patient, Invitation, Registration, Discharge, Status, Skipped registration, Action, Action type, Module, Topic, Post-discharge Action, Suspect timestamp, Active patient, Engagement summary.
- ADR candidates (hard to reverse, surprising, a real trade-off): time zone interpretation (Q6) and the dump-parsing loader (Q7). Both were offered; neither has been written yet.

---

## Round 2

❓ **Q15 - Funnel shape for patient 42**: The funnel is cumulative: Invited 100 → Registered 96 → Discharged. Patient 42 reached Discharged without passing through Registered. Options:
- (a) A strict-progression funnel: Discharged = 16, the patients who registered *and* were discharged. Patient 42 is excluded from the funnel and footnoted. Conversions are 96/100 and 16/96.
- (b) Count 42 in Discharged (17). The Discharged bar then isn't a subset of Registered, and 17/96 overstates conversion.

➡️ Yes to (a). The dashboard's current-status buckets still show 42 as Discharged, so all 100 patients are accounted for there.

✅ **Answer**: accepted as recommended.

---

❓ **Q16 - Median transition times**: A median is taken only over patients who completed that transition: 96 for Invited→Registered, 16 for Registered→Discharged. Patients still waiting are excluded. This understates real waiting time (survivorship bias), so the UI shows the sample size next to each median.

➡️ Yes. The bias goes in the README as an ambiguity.

✅ **Answer**: accepted as recommended.

---

❓ **Q17 - Weekly-active date range**: Which weeks does the time series cover? Today (Nov 2026) is roughly ten months after the last Action (Jan 2026). Options:
- (a) From the week of the first Action to the week of the last Action. Empty weeks inside that range show as 0.
- (b) From the first Action to the current week. This adds a long tail of zeros.
- (c) A user-chosen date range, defaulting to (a).

➡️ Yes to (c), defaulting to (a).

✅ **Answer**: accepted as recommended.

---

❓ **Q18 - Where Status lives**: Status must be derived, but the list also has to filter and sort by it at 100× volume. Options:
- (a) A Postgres generated column (`GENERATED ALWAYS AS (...) STORED`), mapped read-only in JPA and indexable. The derivation rule is also implemented in a pure Java function for unit tests, and a test checks the two agree.
- (b) Compute it in JPQL with a `CASE` expression on every query.
- (c) Store it at import time as a plain column.

➡️ Yes to (a).

✅ **Answer**: accepted as recommended.

---

❓ **Q19 - Patient-list filter semantics**:
- **Status**: one or more current Statuses.
- **Module**: patients with at least one Action in that Module.
- **Invitation date range**: London calendar dates, both ends inclusive.
- Filters combine with AND, and multiple values within one filter combine with OR.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q20 - Free-text search semantics**:
- Case-insensitive substring match on given and family name.
- Match on NHS number, ignoring spaces, so `999 000 0000` matches `9990000000`.
- Case-insensitive substring match on hospital ID.
- At 100× volume this needs `pg_trgm` GIN indexes, added via a migration.

➡️ Yes.

✅ **Answer**: accepted for the matching rules, but **no database indexing yet**. The `pg_trgm` indexes are deferred until the 100× volume work, where the query plans will show whether they are needed.

---

❓ **Q21 - List sorting and defaults**: The sortable columns are family name, given name, Invitation date, Status, age, and last Action time. The default sort is Invitation date, newest first. Unknown values (age, last Action) always sort last. Page sizes are 10, 25, 50 or 100, defaulting to 25. Any other sort key or page size returns a 400 error.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q22 - Loader re-run semantics**: Both options are safe to run twice:
- (a) **Upsert**: insert or update by UUID, so the database converges on the file.
- (b) **Insert only**: never overwrite.

Either way, rows missing from the file are not deleted.

➡️ Yes to (a). The loader logs counts only (inserted, updated, unchanged) and never logs row contents.

✅ **Answer**: accepted as recommended.

---

❓ **Q23 - How the loader is triggered**: Proposal:
- The same jar runs in a `load` mode: `--app.load.file=/path/to/dump`.
- That mode loads the data and then exits.
- In Docker Compose it runs as a one-shot service that the app waits on, with the dump bind-mounted from outside the repo.
- The local path uses the same command through `./mvnw spring-boot:run`.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q24 - Authentication**: Proposal:
- Form login for the UI and HTTP Basic for the API.
- Two in-memory users defined in config: `clinician` (role CLINICIAN, read-only) and `admin` (role ADMIN, which adds CSV export).
- Passwords come from environment variables, with documented defaults for local use.
- Nothing in the app writes patient data, so "administrative" means only export (and loading, which runs outside the web app).

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q25 - What may appear in logs**:
- Patient UUIDs only. Never names, NHS numbers, hospital IDs or date of birth.
- Free-text search terms are logged only as their length.
- Each request log line is structured JSON with a request ID, user role, endpoint, status and duration.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q26 - PostgreSQL version**: The dump came from PostgreSQL 18.1, so use PostgreSQL 18 in Compose and in Testcontainers.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q27 - Repo housekeeping (the Stop-hook failures)**:
- Generate the Maven wrapper (`mvnw` plus `.mvn/wrapper/`) as part of scaffolding Spring Boot 4.
- Fix the guard test by pointing it at an already-ignored path (`target/notes.txt`), rather than inventing a `data/` ignore rule.

➡️ Yes to both.

✅ **Answer**: accepted as recommended.

---

❓ **Q28 - Decision records (ADRs)**: Two decisions are hard to reverse, would surprise a future reader, and were real trade-offs: the time zone interpretation (Q6) and the dump-parsing loader (Q7). Should both be written as ADRs under `docs/adr/`?

➡️ Yes.

✅ **Answer**: accepted as recommended.

### Round 2 outcome

- All recommendations accepted, except Q20: the search matching rules stand, but indexing is deferred.
- Q27 done:
  - The guard test now uses `target/notes.txt`, and all 88 guard tests pass.
  - `mvn wrapper:wrapper` generated `mvnw` and `.mvn/wrapper/maven-wrapper.properties`.
  - `./mvnw test` runs and exits 0. There are no tests yet.
- Q28 done:
  - `docs/adr/0001-timestamps-are-utc-displayed-in-london.md`
  - `docs/adr/0002-load-data-by-parsing-dump-copy-blocks.md`

---

## Round 3

❓ **Q29 - REST API shape**:
- `GET /api/patients` (filters, paging and sorting as query parameters)
- `GET /api/patients/{uuid}` (detail, including Status, age, Engagement summary and data-quality flags)
- `GET /api/patients/{uuid}/actions` (the paged timeline)
- `GET /api/dashboard/status`, `/api/dashboard/funnel`, `/api/dashboard/weekly-active?from=&to=`
- `GET /api/patients/export.csv`, which takes the same filters as the list (ADMIN only)

The OpenAPI description is at `/v3/api-docs`, with Swagger UI at `/swagger-ui.html`. There is no versioning prefix.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q30 - Error response format**: Spring's built-in RFC 9457 `ProblemDetail` for every error, with an `errors` array of `{parameter, message}` for validation failures:
- 404: unknown patient
- 400: malformed UUID, or a bad filter, sort or page value
- 401 / 403: authentication and authorisation failures, in the same format

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q31 - Timeline paging**: Options:
- (a) **Keyset paging** on (recorded time, id) with an opaque cursor. It is stable while data changes, and stays fast for the most active patients at 100×.
- (b) Offset paging (`page`/`size`), which is simpler and consistent with the list.

Default order is newest first, 50 per page.

➡️ Yes to (a).

✅ **Answer**: accepted as recommended.

---

❓ **Q32 - CSV export contents**: The export has the same columns as the list, including names, NHS number, hospital ID and date of birth. It is ADMIN only. It streams from a read-only transaction using a JPA `Stream` with a fetch size. Each export writes one audit log line (user, filter summary, row count) with no identifiers.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q33 - Generating 100× volume**:
- A `generate` mode in the same jar: `--app.generate.scale=100`.
- It produces synthetic Patients and Actions shaped like the source data: the same Status mix, Action-type mix and Topic distribution, with fake identifiers that are valid mod-11 NHS numbers from the 999 test range.
- It uses a fixed random seed, and writes with batched inserts.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q34 - N+1 guard test**: A Testcontainers integration test enables Hibernate statistics, loads a page of the patient list and asserts a fixed statement count, whatever the page size. Raising the page size from 10 to 100 must not change the count.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q35 - Package structure**: Package by feature under `uk.bit1.engagement`: `patient`, `action`, `dashboard`, `importer`, `generator`, `security` and `web`. The UI controllers and the REST controllers sit side by side over shared services.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q36 - Health and readiness**: Spring Boot Actuator, with liveness and readiness probe groups. Readiness includes the database. `health`, `info` and `metrics` are exposed, and only health is public.

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q37 - Age definition**: Age is the Patient's current age in whole years, calculated from today's London date. There is no "age at Invitation".

➡️ Yes.

✅ **Answer**: accepted as recommended.

---

❓ **Q38 - Dashboard scope**: The dashboard aggregates always cover the whole population. They ignore the patient-list filters. Only weekly-active takes a date range.

➡️ Yes.

✅ **Answer**: accepted as recommended.

### Round 3 outcome

All ten recommendations accepted.

---

## Session close

The design tree has no open branches left, and the user confirmed we share an understanding ("yes, all good"). Decisions Q1 to Q38 are settled. The only deliberate deferral is search indexing (Q20), which waits for the 100× volume work.

Artifacts produced:

- `GLOSSARY.md`: domain language
- `docs/adr/0001-timestamps-are-utc-displayed-in-london.md`
- `docs/adr/0002-load-data-by-parsing-dump-copy-blocks.md`

For the README, written by the user: data findings worth recording include the patients with Actions after Discharge (Q3), patient 42's skipped registration (Q4, Q15), the look-alike duplicate Actions (Q9), the suspect timestamps (Q10) and the survivorship bias in the median transition times (Q16).
