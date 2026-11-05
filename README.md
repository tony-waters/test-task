# test-task - Tony Waters

## How to run the app

### container path

> DUMP_FILE=$PWD/dump.sql docker compose up --build

App should be available at:

> http://localhost:8080

use admin/admin or clinician/clinician

### local path

You need a PostgresSql running at localhost:5432 - passwords all `engagement`:

> docker compose up -d db

Then:

> ./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.load.file=$PWD/dump.sql

from the project root.

### prerequisits with versions

- Spring Boot 4.x
- Flyway 12.4.0
- PostgresSQL JDBC Driver 42.7.14
- springdoc-openapi 3.0.2
- Testcontainers 2.0.5

### how to load test the data

N/A - did not complete

### how to run the tests

> ./mvnw clean test

## problems identified in the implementation

- no indexes on DB yet (performance)
    - ok for 100 patients, but not good in real world quantities
- CSV export holds DB connection for complete download, and no greyed out button so user could click it again (performance)
  - experienced this myself in prod. users often click the button again, also more than one user may be online using it - cache? 
- NHS numbers and names may be left in browser history as they travel in the URL (security)
- Duploicate UUIDs cause dump to fail

Ran out of time here - can discuss the above in more detail.

## ambiguities in the task

- I struggled with the domain being ill defined. See `## Issues and choices` below

## what I would not ship

- some of this is purposefully 'prototype' - like passwords hard coded in Java code.

## How I worked

- Used /mattpocock-skills:grill-with-docs pointing at AI_TAKS.md, dump.sql, and TECHNOLOGIES.md (The full transcript is in GRILLING.md). This created the Spec as Issue #1 on GitHub.
- Used the Spec to create other issues (I asked for 3-4 to keep it manageable).
- Iterated over each Issue until it was done.
- ran /codex:review before each code commit
- ran /security-review occasionally
- 
---

# NOTES:

# Completed Ticket 5

https://github.com/tony-waters/test-task/issues/5

- security review came back clean, and 'improve-codebase-architecture' identified some improvements, dont think i will have time to implement those, but should turn them into tickets - going to stay focused on task for now
- decided to implement Ticket #5
- run failed (Claude missed it for some reason)
- need to update README with suggested details from AI_TASK.md

# Ticket #5?

- ran a /security-review on the whole repo first, should probably have done this per commit
- ran /mattpocock-skills:imporve-codebase-architecture - not been so keen on the structure, but its functional. would like to maybe be more explicit with packages, for example, theres no explicit 'domain'. Want to see what that skill picks up before moving on, then make a judgement call on the next 45 minutes

# Completed ticket #4

https://github.com/tony-waters/test-task/issues/4

- UI looks functional (localhost:8080)
- would like to pause here and do some other checks
- looks like I am three quarters done

# Completed ticket #3

https://github.com/tony-waters/test-task/issues/3

- Suspect timestamps found - very near each other.
- ran a /codex:review
- checked the code manually, though at this point it is hard to manually review all the code quickly.
- may not have helped myself by grouping tickets together (originally, it would have been 8-9 tickets)

# Completed ticket #2

https://github.com/tony-waters/test-task/issues/2

Once Claude had finished did /codex:review since this nearly always finds some issues.

Looked over code and tests.
- Noticed there was no PatientRepository class, which I expected to see - looks like it will be added by ticket #3 - so waiting until then.
- A malformed date ended up in the logs, so fixed that as its a security issue
- Claude wanted to add indexes to DB straight away - told it to hold off
- Duplicate UUIDs cause the dump to fail - fixed
- Search terms travel in the URL - could contain names, nhs numbers, etc. - left for now
- Claude did not get the docker image to work, so I tried it locally - looked like it may have been to do with my VPN, so switched that off while I complete this task. - that helped it complete, but still failed (looks like its looking for dump.sql in the wrong place), threw it back to Claud to fix now its running
- Context is feeling sluggish, starting to think i should have broken this ticket into 2. - going to commit this and clear context before next ticket.

# Create tickets

Created 4 tickets:
https://github.com/tony-waters/test-task/issues/2
https://github.com/tony-waters/test-task/issues/3
https://github.com/tony-waters/test-task/issues/4
https://github.com/tony-waters/test-task/issues/5

Going to clear the session now and start work on the first actual ticket,

---

# Create a Spec

A spec is useful if I want to create tickets - which is itself useful when controlling agent context (i.e. can /clear context after each ticket.)

I used /mattpocock-skills:grill-with-docs to create it.

The grilling process is shown in GRILLING.md if interested.

The spec is in ticket form https://github.com/tony-waters/test-task/issues/1

Next step is to creat tickets from the spec and start on them.


---

# Initial Thoughts

## guardrails

Have added Claude and Git hooks to act as guardrails.

## general

Anticipating the hardest thing for me is the time constraint. i want to explore the domain model. not sure how I would do that without other sources of reference, so leaning towards what is easily inferred from dump.sql - 2 tables, PATIENT and ACTIVITY/ACTION - focused instead on getting 2 thirds done, which would at least be a UI of some sort (no CSV download maybe).

Investigating the supplied documents I can see a number of issues that I need ot make decisions about:

## Issues and choices

### ISSUE: ambiguous timestamps in dump.sql

### DECISION:
Time zone wise, UTC feels like an obvious choice. Its a common standard. If the times were not UTC we would probably want to make them so.

Prioritising getting something up and running, I would probably go with hard coding GMT/BST(London) as the time zone for now, for front end stuff.

### ISSUE: dump.sql anomalies and code design:

(1) dump.sql has issues, so there is a clear initial choice about *whether to include those issues in the design* or not. My thoughts are:

- my initial thought here is no, just clean up the data, keep the DB tight, dont let the messy data drive the domain design
- alternatively, perhaps this kind of data input is common (maybe we don't have control over the source), in which case the system should be able to deal with it
- also  "don't assume clean data, handle it deliberately" ... using supplied messy data to make the app more robust is suggested

(2) Patient 42: DISCHARGE is supposedly the final stage, do Patients only have one go at this? the data for this patient suggests not (238 Actions/Activities after DISCHARGE). Makes sense that Patients can go through this process more than once, suggesting a major gap in the domain model suggested by dump.sql, or a massive anomaly for one patient.

### DECISION:

(1) Handle the messy data in the code. See dump.sql as a gift from the real world.

(2) Because of time limitations, stick with the limited domain model suggested by dump.sql for now. Be prepared to see Patient 42 and the 238 items as an anomaly.

---

### ISSUE: domain language is inconsistent.

(1) Domain language seems to differ between dump.sql and AI_TASK.md:

- Action (AI_TASK) and Activity (dump)
- Action Module (AI_TASK) and Context / Activity (dump)

This is inconsistent and confusing. It would usually be a priority to clear this up for me by reaching out to other stakeholders/users/developer/business/documentation/etc.. Looks like may have to live with it here and just make a decision.

### DECISION:
make a judgement call and be consistent with that, even if it is not the right one. We can always refactor as we learn more about the domain. Include the decision in GLOSSARY.md so AI can reference it in future sessions.

---

