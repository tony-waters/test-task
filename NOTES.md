# Session notes

Hand-off notes for future agent sessions. This file is about current state and gotchas. Decisions belong in `GRILLING.md`, terms in `GLOSSARY.md`, and lasting choices in `docs/adr/`.

## Where things stand (2026-11-05)

- **Design**: done. `GRILLING.md` holds the 38 settled decisions (Q1–Q38), with answers.
- **Spec**: GitHub issue #1.
- **Tickets**: sub-issues of #1, all labelled `ready-for-agent`. GitHub's dependency feature records the blocking links.
  - #2 Load the data and browse Patients. Unblocked; **start here**.
  - #3 Patient detail and timeline (blocked by #2)
  - #4 Dashboard (blocked by #2)
  - #5 Roles, CSV export and operability (blocked by #2; best done after #3 and #4)
- **Not ticketed on purpose**: the 100× volume generator, query plans and indexes (spec §11). They sit at the cut line. Search indexing (`pg_trgm`) is also deferred to that work.
- **Code**: none yet. Only an empty `pom.xml` (groupId `uk.bit1`) and the Maven wrapper.
- **Git**: nothing from these sessions is committed. The user commits; don't commit unless asked.
  - Uncommitted: `CLAUDE.md`, `GLOSSARY.md`, `GRILLING.md`, `NOTES.md`, `docs/`, `mvnw*`, `.mvn/`, plus edits to `.gitignore` and `.claude/hooks/test-guards.sh`.
  - Issue #1 links to `GLOSSARY.md` and `docs/adr/`, which don't resolve on GitHub until those files are pushed.

## Rules that are easy to break

- **The user writes `README.md`.** Never write or edit it, and never draft its prose. You may point out facts worth including.
- **Banned names**: the guard hooks and git hooks block certain names in file contents, paths and commit messages. The patterns live outside the repo, in `~/.task/forbidden-names.txt`. In practice this means never naming the company or product behind the task brief.
- **The brief and the dump are never committed.** That is `AI_TASK.md` and `dump.sql`, and also any pasted dump rows, for example in a migration, seed or fixture. The dump is read at runtime from a path outside the repo.
- Avoid copying real-looking patient data (names, NHS numbers) from the dump into docs or issues. Use placeholders such as `9990000000`.
- To check text before writing it anywhere public: `scripts/check-submission.sh stdin <path> < file`.

## Environment gotchas

- The Stop hooks run `./mvnw -q -DskipITs test` (only when files under `src/` or `pom.xml` change) and `.claude/hooks/test-guards.sh`. Both must pass before a turn can end. They passed at the end of the last session.
- System `mvn` 3.9.12 and Java 21 are installed. Docker availability was not checked.
- `gh` is 2.46, which has no `--parent` flag. For sub-issues and blocking links, use the REST API, as in `docs/agents/issue-tracker.md`.
- The repo is **public**, so issues and comments are visible to everyone.
- An untracked `.claude/settings.local.json.tmp.*` file is a stray from Claude Code. The user said to leave it alone and not ignore it.

## Data facts worth remembering

- The user's original notes (in `README.md`) say patient 42 has 238 post-discharge Actions. **That is wrong.** Patient 42 has zero Actions and is the one *skipped registration* case. The 238 post-discharge Actions are spread across 12 of the 17 discharged patients. The user knows; don't re-raise it, but don't repeat the error either.
- On the supplied data, expected test figures are:
  - Current-status buckets: Invited 3, Registered 80, Discharged 17.
  - Strict funnel: 100 → 96 → 16.
- The full profile is under "Facts gathered before Round 1" in `GRILLING.md`.
