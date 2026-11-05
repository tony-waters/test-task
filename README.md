
# Create a Spec

A spec is useful if I want to create tickets - which is itself useful when controlling agent context (i.e. can /clear context after each ticket.)

I used /mattpocock-skills:grill-with-docs to create it.

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

