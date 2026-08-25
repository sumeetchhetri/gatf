# Dogfood — GATF testing its own /app console

A tiny seleasy suite where **GATF drives its own React console UI**. Non-gating; a showcase that the
`/app` SPA is exercisable end-to-end by GATF itself. Needs a running `/app` server + a Selenium Grid.

`tests/app-console.sel` opens the console, then walks the primary tabs (Config → the config form,
Test Files → the file view, Execute → the mode toggle, Reports), asserting each view renders. With
tracing on, each run drops a `trace.zip` you can open in the console's own Trace Viewer — GATF
tracing GATF.

## Run it

```bash
# 1. build the shaded jar + launch a GATF /app server (the target under test)
mvn -q -pl alldep-jar -am package -DskipTests
bash e2e/start-gatf-server.sh 9223 &                 # serves http://localhost:9223/app/

# 2. (optional) point tests/app.props at it — default is http://localhost:9223/app/

# 3. run the suite against a Selenium Grid
export SELENIUM_REMOTE_URL=http://localhost:4444     # GATF's native Grid var
bash dogfood/run.sh
```

`run.sh` resolves the shaded jar + `JAVA_HOME`, generates `gatf-config.gen.xml` (absolute paths +
the `app-console.sel` selenium script + trace on), and invokes `java -jar <jar> -selenium <config>`
from `dogfood/` — the same executor path as `tools/smoke/trace-e2e.sh`. Exit code is GATF's.

**Env var note:** the dogfood uses `SELENIUM_REMOTE_URL` (GATF's own Grid variable). That's the
*opposite* of the Playwright e2e, which uses `GATF_SELENIUM_GRID` precisely because Playwright would
otherwise grab `SELENIUM_REMOTE_URL` to route its own browser. Different tools, different variables.
