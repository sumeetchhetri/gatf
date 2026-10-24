---
title: Test documents and traceability
sidebar_position: 8
---

# Test-case documents, traceability and coverage

`gatf docs` turns the configured tests into manual-style test cases, a requirement traceability matrix and coverage figures:

```bash
gatf docs
```

```text
Documented 3 test case(s); 0 requirement(s), 0 covered; status from /home/you/project/out/gatf-results.json
  /home/you/project/gatf-docs/gatf-testcases.xlsx
  /home/you/project/gatf-docs/gatf-testcases.csv
  /home/you/project/gatf-docs/gatf-traceability.csv
  /home/you/project/gatf-docs/gatf-coverage.csv
```

- **Test Cases**: ID, module, source, title, tags, requirements, preconditions, steps and expected results. An API case's request becomes the steps and its assertions and captures the expected results; each UI script and subtest becomes a test case, with commands in plain words and `??` assertions as expected results.
- **Traceability**: requirement → test cases, with pass/fail/not-run counts and a status (Passing, Failing, Partially run, Not run, Not covered).
- **Coverage**: totals and percentages, per module.

## Linking requirements

Tag tests with requirement IDs: `@REQ-101`, a Jira-style `@PROJ-12`, or `@req:<anything>` (`--req-pattern` changes the rule). Tags go in an API case's name or description, a script's header comments, or the comment above a subtest:

```xml title="tests/login.xml"
<TestCases>
  <TestCase>
    <name>Login works @REQ-101</name>
    <url>auth/login</url>
    <method>POST</method>
    <expectedResCode>200</expectedResCode>
  </TestCase>
</TestCases>
```

`--requirements reqs.csv` (columns `id`, `title`, `module`) adds the full list so uncovered requirements show up; `--requirements-jql '<JQL>'` reads it from Jira using `GATF_JIRA_URL` and `GATF_JIRA_TOKEN` (add `GATF_JIRA_USER` for basic auth). Status comes from the last run's `gatf-results.json`, or `--results <file>`.

`gatf test --reporter junit,json,docs` writes the documents after every run, with that run's status, into `<out>/docs`. The console's Reports view has a **Test cases (xlsx)** download.
