# Cyclomatic complexity: top 10 methods

Measured 2026-11-05 over `src/main/java` (203 methods). Test code is not included.

- Average complexity: **2.06**
- Methods at 10 or above (the usual warning threshold): **4**

## Method

Each method scores 1, plus 1 for every `if`, `for`, `while`, `case`, `catch`, `&&`, `||` and `?:`. This is the McCabe count that PMD and lizard use. `else if` counts once. Lambdas count towards the method they sit in. A small script did the counting, and the top result was checked by hand.

## Rating bands

These bands come from the Software Engineering Institute's long-standing guide:

| Score | Meaning |
|---|---|
| 1–10 | Simple and low-risk |
| 11–20 | Moderately complex, worth a look |
| 21–50 | Complex and high-risk |
| 50+ | In practice impossible to test fully |

McCabe, who created the measure, proposed 10 as the upper limit per method. NIST's guidance on his method (SP 500-235) also says 10, and allows up to 15 if there's a good reason. PMD and Checkstyle both use 10 as their default limit.

The score is also the minimum number of tests needed to run every independent path through a method.

## Results

Paths are relative to `src/main/java/uk/bit1/engagement/`. Lines are non-blank lines in the method body.

| # | Complexity | Lines | Method | Main source of branches |
|---|---|---|---|---|
| 1 | **22** | 47 | `CopyBlockParser.unescape` (`importer/CopyBlockParser.java:78`) | 7-way `switch` on escape characters, with hex and octal loops that each have 2–3 conditions joined by `&&` |
| 2 | **17** | 49 | `PatientListQuery.parse` (`patient/PatientListQuery.java:58`) | Checks 9 request parameters, each with a blank check, a parse and an error |
| 3 | **11** | 28 | `WeeklyActiveQuery.resolve` (`dashboard/WeeklyActiveQuery.java:62`) | Nested null checks with `?:` for filling in missing range ends, plus the 520-week cap |
| 4 | **11** | 22 | `RequestLogFilter.endpoint` (`web/RequestLogFilter.java:95`) | Picking the logged path pattern, long-path cut-off, and per-segment redaction with `\|\|` chains |
| 5 | 8 | 34 | `PatientSearchImpl.where` (`patient/PatientSearchImpl.java:98`) | Optional filter clauses |
| 6 | 8 | 25 | `PatientListQuery.toParams` (`patient/PatientListQuery.java:140`) | Includes each parameter only when it isn't the default |
| 7 | 8 | 14 | `PatientCsv.escape` (`patient/PatientCsv.java:102`) | Checks for characters that need CSV quoting |
| 8 | 7 | 38 | `CopyBlockParser.parse` (`importer/CopyBlockParser.java:30`) | Line-by-line state handling for COPY blocks |
| 9 | 7 | 18 | `TimelineCursor.decode` (`action/TimelineCursor.java:32`) | Format checks and `catch` blocks |
| 10= | 6 | 22 | `RequestLogFilter.write` (`web/RequestLogFilter.java:67`) | |
| 10= | 6 | 20 | `PatientListQuery.parseEnums` (`patient/PatientListQuery.java:190`) | |
| 10= | 6 | 16 | `WeeklyActiveQuery.parse` (`dashboard/WeeklyActiveQuery.java:35`) | |

Three methods tie for 10th place at 6, so all of them are listed.

## Observations

- **`CopyBlockParser.unescape`** scores highest, but most of that comes from the escape-character `switch`. A table of escape cases scores high without being hard to read, so it's a weaker case for refactoring than the number suggests.
- **`PatientListQuery.parse`** is the real hotspot. `toParams` and `parseEnums` in the same file are both in the top 10 as well. `parse` is mostly the same pattern repeated for each parameter (if not blank, parse it, add an error if it fails), so its complexity grows with every new parameter.
- **`WeeklyActiveQuery.resolve`** packs the most logic into the fewest lines: 11 on 28 lines, with nested `?:` null-handling. That makes it the hardest of the top four to reason about.
- Everything below these is 5 or lower, so complexity is low overall.
