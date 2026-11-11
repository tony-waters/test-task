# Problems log

Working notes for the README's "Problems identified in the implementation" section. Each entry is a finding in this codebase, with how it was confirmed. These are notes, not README text: the README is written by the repo owner.

Fields per entry: **where**, **what is wrong**, **production cost**, **status**, **how found**.

Current state: tickets #2 (load data, browse Patients), #3 (patient detail and timeline), #4 (dashboard) and #5 (roles, CSV export, logging, health).

---

## 1. Page offset overflow in the patient list (correctness)

- **Where**: `PatientListService.find`, `GET /api/patients` and `/patients`, parameter `page`.
- **What**: the parser accepts any page ≥ 0, and the offset was `page * size` in `int`. `page=2147483647&size=25` went negative and gave a 500. `page=171798692&size=25` wrapped to offset 4 and returned real rows for a page that doesn't exist.
- **Cost**: low severity. Wrong rows rather than an error is the worse case: a client paging by number could show the wrong patients without noticing.
- **Status**: fixed. The offset is now a `long`, and pages past the end come back empty without running the rows query. Covered by `PatientApiTest.aPageNumberWhoseOffsetOverflowsAnIntGivesAnEmptyPage`.
- **How found**: Codex review of the working tree.

## 2. A literal `+` in a search was lost when sorting or paging (correctness)

- **Where**: `PatientListView.url`, the sort and page links on `/patients`.
- **What**: links were built with `UriComponentsBuilder...encode()`. Query-component encoding leaves `+` alone, and the next request decodes it as a space. So a search for `a+b` became `a b` as soon as the user clicked a column header.
- **Cost**: low. The search changes without the user seeing it, and the result count changes for no visible reason.
- **Status**: fixed. Every value is encoded strictly (`UriUtils.encode`). Covered by `PatientPageTest.encodesALiteralPlusInLinksSoTheSearchSurvives`.
- **How found**: Codex review of the working tree.

## 3. Last Action time scans the whole action table once per patient (performance)

- **Where**: `PatientListService.SELECT`, the correlated `max(a.recordedAt)` subquery. Migration `V1__create_patient_and_action.sql` has no index on `action.patient_id`.
- **What**: `EXPLAIN` on the loaded dump shows `Seq Scan on action` inside the per-patient SubPlan. With `sort=lastActionAt`, it runs for every matching patient before the limit, so the cost is patients × actions. The Module filter's `exists` has the same shape. On other sorts it runs for the page's rows only (up to 100).
- **Cost**: fine at 100 patients and 3,183 actions. At the 100× volume (about 10,000 patients and 318,000 actions), sorting by last Action means about 10,000 scans of 318,000 rows per page request. That's seconds per click, and it competes with every other query.
- **Status**: left. Indexes were deliberately deferred to the volume work (GRILLING Q20, spec §11), where query plans should decide them. The likely fix is an index on `action (patient_id, recorded_at)`.
- **How found**: `EXPLAIN` against PostgreSQL 18 with the real dump loaded, and `\di` to list the indexes.

## 4. Free-text search can't use any index (performance)

- **Where**: `PatientListService.where`: `lower(x) like '%term%'` on names and hospital ID, and `replace(nhs_number, ' ', '') = ?`.
- **What**: `EXPLAIN` shows a sequential scan of `patient`. A leading wildcard can't use a btree, and the NHS match wraps the column in a function, so a plain index on `nhs_number` wouldn't help either.
- **Cost**: negligible at 100 rows. At 10,000 it's a full scan per keystroke-search. Linear, not catastrophic.
- **Status**: left on purpose (GRILLING Q20: `pg_trgm` deferred). The NHS case could be fixed more cheaply by normalising the number at load time (strip spaces) and indexing that column.
- **How found**: `EXPLAIN` on the loaded dump.

## 5. Loader errors can write patient data into the logs (security)

- **Where**: `DumpLoader.load` → `date()`, `utc()` and `UUID.fromString()`. Spring logs the exception when load mode fails at startup.
- **What**: the loader logs only counts, and `DumpFormatException` never includes row values. But the JDK parse exceptions include the input text. Checked: `LocalDate.parse("1980-13-15")` throws `Text '1980-13-15' could not be parsed...`. So one malformed date of birth in a dump puts that DOB in the loader's log. That breaks the logging rule (GRILLING Q25: never DOB, names, NHS number or hospital ID).
- **Cost**: medium. Logs are usually kept longer and read more widely than the database, and shipped off-box. It only happens on a bad file, so it's rare but plausible.
- **Status**: fixed. `CopyBlock.Row` has typed accessors (`uuid`, `date`, `utc`). A value that doesn't parse raises `DumpFormatException("COPY patient row 2, column date_of_birth: not a date")`, without the value. The JDK exception is deliberately not kept as the cause, because Spring logs the whole cause chain. The load rolls back. Covered by `CopyBlockParserTest.aValueThatDoesNotParseIsReportedWithoutTheValue` and `DumpLoaderIntegrationTest.aMalformedDateOfBirthFailsTheLoadWithoutLoggingTheValue`.
- **How found**: reading `DumpLoader`, then checking the JDK messages with a scratch program.

## 6. Duplicate UUIDs within one dump fail the whole load (correctness / data)

- **Where**: `DumpLoader.upsert`, `insert ... on conflict (uuid) do update`.
- **What**: if one file has the same UUID twice, PostgreSQL rejects the whole statement ("ON CONFLICT DO UPDATE command cannot affect row a second time"). The load rolls back with a raw SQL error that doesn't say which table or how many rows. The supplied dump has no duplicates, so this hasn't happened.
- **Cost**: low likelihood, but loading would be blocked until someone hand-edits the file, and the error doesn't help them find the row.
- **Status**: left. The cheap fix is to detect duplicates while staging and fail with a count, or pick one deterministically and report it. Which is right depends on the source system's guarantees, and we don't have those.
- **How found**: reading the upsert, then reproducing the PostgreSQL behaviour on a scratch table.

## 7. Patient data is open to anyone who can reach the port (security)

- **Where**: every endpoint (`/patients`, `/api/patients`, `/v3/api-docs`). `compose.yaml` publishes 8080 and also 5432, with a default database password of `engagement`.
- **What**: there's no authentication yet. The list returns names, NHS number, hospital ID and date of birth. The database port is published on all host interfaces.
- **Cost**: high if deployed like this: full patient-identifiable data to anyone on the network.
- **Status**: partly fixed in ticket #5. Every endpoint except `/actuator/health` now needs a signed-in CLINICIAN or ADMIN. Still left: the 5432 publish exists for the local run path. It should be bound to `127.0.0.1` or dropped. The database and user passwords are local defaults (`engagement`, `clinician`, `admin`) unless the environment sets them.
- **How found**: reading `compose.yaml` and the controllers.

## 8. Search terms, which can be NHS numbers or names, travel in the URL (security / design)

- **Where**: `PatientListQuery.toParams`, `/patients?q=...`, `GET /api/patients?q=...`.
- **What**: GRILLING Q19/Q21 put all filter state in the URL so it survives a refresh and can be shared. But `q` is often an NHS number or a surname. URLs end up in browser history, reverse-proxy access logs and `Referer` headers. That conflicts with Q25: log search terms only as their length. The app's own logs are safe; the infrastructure around it may not be.
- **Cost**: medium in a real deployment. Identifiers leak into systems that aren't governed as patient data.
- **Since ticket #5**: the app's request log records `q` only as `searchLength`, and the CSV export link on the list page carries `q` in its URL too, like the other links.
- **Status**: left. It's a genuine trade-off between shareable URLs and identifier leakage, so it belongs in the README as a decision. Mitigations: POST the search form, or keep `q` out of the shareable URL and send `Referrer-Policy: no-referrer`.
- **How found**: comparing the decisions in `GRILLING.md` against each other.

## 9. Lower-casing the search term depends on the JVM locale (correctness, minor)

- **Where**: `PatientListService.where`: `query.search().toLowerCase()`.
- **What**: it uses the default locale. Under a Turkish locale, `"TITLE".toLowerCase()` gives `tıtle` (dotless ı). Checked with a scratch program. PostgreSQL's `lower()` doesn't do that, so searches containing `I` would stop matching.
- **Cost**: low. It needs a non-English server locale.
- **Status**: fixed with `toLowerCase(Locale.ROOT)`. `PatientApiTest.searchMatchesWhateverTheServerLocale` sets the JVM default to Turkish and searches `MIDNIGHT`. It was confirmed to fail without the fix and pass with it.
- **How found**: reading the code.

## 10. The Docker image didn't build, so `docker compose up` had never run (testing / running)

- **Where**: `Dockerfile`, the `./mvnw -q -B dependency:go-offline` step.
- **What**: the step exits 1 inside the build, on both attempts. The cause hasn't been diagnosed yet. The local path works: the jar loaded the real dump into PostgreSQL 18, giving 100 patients, 3,183 actions and the expected 3 / 80 / 17 split, and a second run reported 100 and 3,183 unchanged. But the container path in the ticket's acceptance criteria hasn't been shown to work.
- **Cost**: blocks the primary way of running the submission.
- **Status**: fixed. The same build passed on its own, so the Maven step wasn't broken in itself. The likely cause: `loader` and `app` each had `build: .` for the same image tag, so compose ran two identical builds in parallel, both downloading every dependency, and `-q` hid whatever went wrong. Now only `loader` builds; `app` reuses the image (`pull_policy: never`). Maven runs with `-ntp` instead of `-q`, so errors show, and with download retries. This is an inference: the original error was never seen.
- **Checked after the fix**: `DUMP_FILE=$PWD/dump.sql docker compose up -d --build` built once, loaded 100 patients and 3,183 actions, the loader exited 0, and then the app started. `/patients` gave 200. `/api/patients` gave 100 in total and 3 / 80 / 17 by status. `/v3/api-docs` gave 200, and `size=7` gave a 400 ProblemDetail. `docker compose run --rm loader` reported everything unchanged.
- **Still worth knowing**: `DUMP_FILE` defaults to `../dump.sql`. Without it, compose stops with "bind source path does not exist" (deliberate: `create_host_path: false`). There's still no `.env`, so the variable has to be passed on every `up`.
- **How found**: running `docker compose build`, then building the `build` stage on its own.

## 11. No automated test runs against the real dump (testing)

- **Where**: `src/test`. Every integration test uses the synthetic `StandardData` (30 patients).
- **What**: the dump can't be committed, so the figures that matter (3 / 80 / 17, funnel 100 → 96 → 16) are checked only by hand. The synthetic data covers the awkward shapes (skipped registration, post-discharge Actions, look-alike duplicates, BST boundary, unknown demographics). It can't catch a mismatch between the parser and the real file's quirks.
- **Cost**: a regression in parsing the real file would only show up when someone loads it by hand.
- **Status**: left. Possible fix: an opt-in test that runs when an env var points at the dump, and is skipped otherwise.
- **How found**: reviewing what the suite does and doesn't exercise.

## 12. "Suspect timestamp" counts 3 under the glossary rule, not 47 (data modelling, for ticket #3)

- **Where**: `action.source_created_at`, which stores each row's own `entity_created` from the dump.
- **What**: the glossary defines a suspect timestamp as "at or after the moment the data was extracted". Comparing each Action to its own row's `entity_created` flags 3 Actions. `GRILLING.md` gives 47 as "within one second of `entity_created`": 44 of those are a few milliseconds *before* it. So the two documents disagree, and the per-row column makes the rule depend on row creation order, not on one extraction moment.
- **Cost**: the flag count on the patient detail page and in the README will be wrong one way or the other until this is decided.
- **Status**: fixed in ticket #3. The rule is now "recorded at, or within one second before, the moment extraction began". Extraction began at the earliest `source_created_at` in either table (13:00:37.3 on the supplied dump). The rows' own stamps span about 4.4 seconds, so a single moment is needed. Using the *latest* stamp would flag only 17. On the supplied data the new rule flags 47 Actions across 3 Patients. The nearest ordinary Action is an hour earlier, so the one-second margin doesn't change the count. GLOSSARY updated. Covered by `SuspectTimestampsTest`, `PatientDetailApiTest.flagsSuspectTimestamps`, and checked against the real dump through the API.
- **How found**: counting `recorded_at >= source_created_at` on the loaded dump.

## 13. The suspect-timestamp threshold scans both tables on every detail and timeline request (performance)

- **Where**: `SuspectTimestamps.threshold()`: `least(min(patient.source_created_at), min(action.source_created_at))`.
- **What**: neither column is indexed, so each `/patients/{uuid}` and `/api/patients/{uuid}/actions` request reads every row of both tables to find a value that only changes when a dump is loaded.
- **Cost**: nothing at 3,183 Actions. At the 100× volume, it's a 318,000-row scan per patient view, before the real work. The page view runs it twice (detail and timeline).
- **Status**: left. The cheap fix is for the loader to record the extraction start once per load (a one-row table, or a column on a load-history table) and read that. Another option is an index on `action (source_created_at)`. Either belongs with the volume work.
- **How found**: writing the query. With no index on either column, it can only be two full scans.

## 14. The timeline has no index on its keyset (performance)

- **Where**: `TimelineActions.newest` / `olderThan` (was `ActionRepository`): `where patient_id = ? and (recorded_at, id) < (?, ?) order by recorded_at desc, id desc limit 51`.
- **What**: this is the same missing `action (patient_id, recorded_at)` index as #3. Keyset paging was chosen (GRILLING Q31) so deep pages stay as cheap as the first one, but without the index every page sorts the Patient's Actions after a full table scan. So the paging design brings no benefit yet.
- **Cost**: as #3. The fix is the index `action (patient_id, recorded_at desc, id desc)`, which serves the timeline, the last-Action subquery and the Engagement summary.
- **Status**: left, with #3, for the volume work.
- **How found**: reading the query against the migration.

## 15. The timeline cursor carries the internal Action id (design, minor)

- **Where**: `TimelineCursor.encode`: base64url of `v1|<recorded time>|<id>`.
- **What**: GRILLING keeps UUIDs as the only identifiers exposed. The cursor is opaque to clients, but anyone can decode it and see the bigint surrogate key, and so learn roughly how many Actions exist.
- **Cost**: low. It reveals row counts, not patient data.
- **Status**: left. The ticket specifies (recorded time, id). Tie-breaking on the UUID would avoid it. Sealing the cursor (an HMAC) would also stop clients from crafting their own.
- **How found**: reviewing the cursor format against GRILLING Q29.

## 16. Display formatting followed the JVM locale (correctness, minor)

- **Where**: `DisplayFormat`: the `d MMM yyyy` formatters and `toLowerCase()` in `gender` and `label`.
- **What**: this is the same class of bug as #9. Month names came from the default locale, so they would be French or Turkish on a server running in that locale. Gender and label lower-casing would hit the dotless ı.
- **Cost**: low.
- **Status**: fixed. The formatters use `Locale.UK` and lower-casing uses `Locale.ROOT`.
- **How found**: reading the class while adding the timeline formats.

## 17. Weekly-active counts have no index on recorded time (performance)

- **Where**: `DashboardQueries.activeByWeek` and `actionSpan`: a range filter on `action.recorded_at`, then `min`/`max` over it.
- **What**: there is no index on `recorded_at` alone. The `(patient_id, recorded_at, id)` index planned in #14 leads with `patient_id`, so it doesn't serve a whole-population range scan. Every dashboard view reads the whole action table twice.
- **Cost**: nothing at 3,183 Actions. At 100× volume, two full scans of about 318,000 rows per dashboard view.
- **Status**: left for the volume work. Either an index on `action (recorded_at)` (which `min`/`max` can also use), or pre-aggregating weekly counts at load time, since the data only changes when a dump is loaded.
- **How found**: writing the query against the migration.

## 18. Patient-level dashboard figures read every Patient into memory (performance, design)

- **Where**: `DashboardQueries.journeys()`, which feeds `StatusCounts` and `Funnel`.
- **What**: the Status buckets, the funnel and the medians are computed in Java from every Patient's three journey timestamps, not by SQL aggregates. This was deliberate: the rules (strict progression, skipped registration, median over completers) are pure functions with unit tests, and Patients are the small table.
- **Cost**: at 100× volume, 10,000 rows of three timestamps per dashboard view: well under a megabyte, one statement. It would matter at millions of Patients. Then `count(*) filter (...)` and `percentile_cont(0.5)` would do the same in the database.
- **Status**: accepted.
- **How found**: design choice while building ticket #4.

## 19. A wide weekly-active range could build a huge series of zeros (robustness)

- **Where**: `WeeklyActiveQuery`.
- **What**: the series has one entry per week, empty or not, so `?from=0001-01-01&to=9999-12-31` would build about 520,000 entries.
- **Cost**: memory and response size from one request, if nothing limited it.
- **Status**: fixed. A range the user chose in full that is wider than 520 weeks (about ten years) is a 400. An end filled in from the data is pulled in to fit instead. With no range chosen, that means the most recent 520 weeks. Covered by `WeeklyActiveQueryTest` and `DashboardApiTest`.
- **Follow-up (Codex review)**: the first version rejected a too-wide range even when the data supplied it. If the Actions spanned more than 520 weeks, `/dashboard` with no parameters threw. The page's fallback then retried the same default and threw again, so the user got a 500 rather than the dashboard. Fixed by clamping, as above, so working out the range never fails. `DashboardApiTest.actionsSpanningMoreThanTheCapStillShowTheMostRecentWeeks` covers it.
- **How found**: thinking about the date-range parameters while writing validation.

## 20. The export holds a database connection for as long as the download takes (performance / robustness)

- **Where**: `PatientExport.write`, called from `GET /api/patients/export.csv`.
- **What**: the export streams from one read-only transaction through a cursor, as specified. The connection stays checked out until the last row has been written to the client. A slow or stalled client keeps it open, and Hikari's default pool is 10 connections.
- **Cost**: nothing at 100 Patients (the full export took about 100 ms locally). At volume, a handful of slow downloads could take the whole pool, and every page would then wait for a connection.
- **Status**: left. Options: a statement or transaction timeout for the export, a separate small pool for exports, or writing to a temporary file first and then sending it (which gives up streaming to the client, but not from the database).
- **How found**: thinking through what streaming from a transaction means for the pool.

## 21. API requests check the password with bcrypt every time (performance)

- **Where**: `SecurityConfig.users`: passwords are stored bcrypt-encoded and the API uses HTTP Basic, which re-authenticates on every request.
- **What**: bcrypt is deliberately slow, and Basic sends the password with each call, so every API request pays for one bcrypt check. Browser sessions (form login) pay it once.
- **Cost**: a fixed cost per API request. It would show in the request log's `durationMs` for scripted API clients.
- **Status**: accepted. The two-user, in-memory setup was chosen for the exercise (GRILLING Q24). A real deployment would use tokens from an identity provider.
- **How found**: design review while wiring HTTP Basic.

## 22. An unmatched URL path is logged with plain lower-case words kept (security, minor)

- **Where**: `RequestLogFilter.endpoint`.
- **What**: matched requests log the route pattern (`/api/patients/{uuid}`), never the raw path. A request that no route matches, or that security rejects before Spring MVC, logs its path instead, with every segment that isn't a UUID or a plain lower-case word replaced by `{redacted}`. So `/patients/9990000000` and `/patients/Smith` are redacted, but a name typed in lower case into a path would be logged.
- **Cost**: low. It needs someone to type an identifier into the path by hand, in lower case.
- **Status**: left. A stricter version would keep only segments from a fixed list of the app's own path words.
- **How found**: writing the redaction rule and its test.

## 23. The export changes values that start with a formula character (data, minor)

- **Where**: `PatientCsv.escape`.
- **What**: a field starting with `=`, `+`, `-`, `@`, tab or carriage return gets a leading apostrophe, so a spreadsheet won't run a name like `=HYPERLINK(...)` as a formula (CSV injection). The cost is that such a value isn't byte-for-byte what is stored. No exported column normally starts with one of these characters.
- **Cost**: a script that reads the CSV and wants the raw values has to strip the apostrophe.
- **Status**: accepted. Admins are expected to open the export in a spreadsheet.
- **How found**: deliberate while writing the CSV encoder.

