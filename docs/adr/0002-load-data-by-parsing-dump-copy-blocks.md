# Load source data by parsing the dump's COPY blocks, not by restoring it

The supplied data is a `pg_dump`. We may not commit it or rely on its DDL. So the app's own loader reads the file from a path given at runtime, parses only the two `COPY ... FROM stdin` blocks, and upserts rows by UUID into the schema our migrations create. Re-running it converges on the file's contents and never deletes anything.

## Considered Options

- **Restore with `psql` into a staging schema, then `INSERT ... SELECT`**: rejected. The dump's `\restrict` line needs psql 18+. Restoring would also execute the dump's DDL, and the loading logic would sit outside the tested Java code.
- **Insert-only (never overwrite)**: rejected in favour of upsert, so that a corrected source file can be re-applied.
