# afterword - ~~not committed~~ - done after test

## NOTES:

- all AI was done using Opus5.5(Medium)
- I *instructed* Claude to maintain NOTES.md as a live document for future session. I was running /clear regularly (after every ticket). While I saw Claude writing to this document in the Claude UI, i only ever glanced at it (it was not created for me). This was committed (as suggested in AI_TASK.md) since it gave insight into my use of AI.
- I *instructed* Claude to add to PROBLEMS.md as we went along. I was clearing context regularly, and I did not want to lose the observations Claude made about issues in the repo that were not my current focus. PROBLEMS.md may have been filtered into future tickets/improvements. I did not commit this because I felt it was out of scope of the task.
- I *insisted* Claude did not git commit, and ask before doing quite a few other things (see .claud/settings.json).

"The Automation of AI should leave more time for Exploring." (Modern Software Engineering vid)

The code is functional. If this is a rush job we could consider pushing it into prod with the provisos mentioned later.

There are serious considerations though:
1. domain language inconsistent, and domain model unclear (Are we even building what the client is expecting?)
2. given (1) we lack somewhere/someone to seek clarification (this makes it seem like the Kobayashi Maru :)
3. code quality has issues, some edge tests are missing, may be other unknowns. How urgent is this release to the Business? Is this a fix-forward situation? What if the fix-forward is a complete code re-write, is it still important enough to the Business given these provisos - Is the Business aware of the potential problems? Is it an emergency release?

I wanted feedback here. Before moving on.

---

## ISSUES:

- no domain expert to ask questions of - this one felt the most impactful, as it led everything else. felt like i was building on uncomplete foundations.
- Code quality / maintainability - not so happy about the actual code here, some examples are (no particular order)...
  - use of variable names in methods is often single letter, IMHO better to use more meaningful names even for local variables (except when using 'i' in a for loop maybe :) - this is just me - need a team approach to this stuff. I see this as a failure of /grill-wirh-docs GLOSSARY.md not working like i may have wanted
  - very flat package structure, would benefit from some thoughtful division/refactoring. for example .patient mixes Controller and Domain stuff ... not a good conceptual framework package-wise. For example, the only 2 Entities are in different packages - doesnt feel domain-driven from human eyes. Would rather it was Web / Controller / Service / Domain orientated. The current structure doesnt make human sense. This is possibly the biggest issue in some ways. It is structured by topic (e.g. Patient) rather than architecture.
  - use of fully-qualified package names rather than imports (CopyBlock / java.util.ArrayList)
  - some of the methods are verbose. I would have refactored if time, extracted logic to private methods, tried to make it more readable. May have been good to do some Cyclomatic Complexity checks and refactoring (checks since done - see CYCLOMATICAL_COMPLEXITY_TOP_10.md).
  - no shared guidelines on how code looks (though we do run a google-code-formatter(sp?) for formatting) so unknown what is acceptable/agreed for this project. not just about formatting. no definition of done.
- check sonarqube
- didnt do TDD, I tried it with matt pocock skills recently, decided it would have taken too long if i needed 2/3 done. Instead I did Spec-driven development (spec created through grill-with-docs). Relied on (and did limited manual checks on) the tests that were created. Would have liked to have spent more time on the tests. There was at least one edge case not included (birth date in future). 
  - My thoughs after this process was that maybe MP skills did not have TDD integrated into the [main flow](https://www.aihero.dev/skills-grill-with-docs), so perhaps would have been quicker with a TDD/BDD focused solution. Following this insight would like to try [EXACT](https://www.codecentric.de/en/knowledge-hub/blog/exact-coding-with-ai) framework. (But dont want to lose 'grill-with-docs') 
  - again, no definition of done
- as sonarqube noticed, didnt do code test coverage (just forgot, wasnt a priority under pressure - done since but not commited)

## tests

- AgeTest does not test for birth dates being in the future.
- StatusTest does not appear to have enough checks for the number of possible permutations

---

# production ready?

no, but if this is urgent and we address the below, maybe.

- did not do the 100x test, so would need to do that, examine results, and think about indexing / SELECT complexity / code.

- need to address/fix the issues identified as I went along (PROBLEMS.md)

- no real security framework here yet

- no real secrets framework here yet

- uncertainty around the domain model

- maintainability/understandability (see above)
