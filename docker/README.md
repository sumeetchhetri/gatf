# Slim GATF Selenium node (recipe)

A ~400–500 MB WebDriver node — `chrome-headless-shell` + `chromedriver`, no VNC/desktop — versus the official `selenium/standalone-chrome` (~3.4 GB). GATF publishes no images; build and adapt this yourself.

```bash
docker build -t gatf-node-slim -f docker/Dockerfile.node-slim .
docker run --rm -p 4444:4444 --shm-size=1g gatf-node-slim
export SELENIUM_REMOTE_URL=http://localhost:4444
java -jar gatf-alldep-jar-5.0.0.jar -selenium gatf-config.xml
```

## Browser-binary caveat
`GATF_CHROME_BINARY` sets the binary for the **local** driver only — it does not apply to Grid, where the browser lives on the node. So tell the node's chromedriver which binary to launch by setting it in your GATF driver config capabilities:

```
goog:chromeOptions.binary = /usr/local/bin/chrome-headless-shell
```

Without it, chromedriver looks for a system Chrome (absent in this slim image).

## se:cdp / tracing
Standalone `chromedriver` serves plain WebDriver — fine for normal runs. GATF's trace recorders rely on the Grid's `se:cdp` capability, which raw chromedriver does not advertise. If you need tracing on a remote node, run a **Selenium standalone** here instead (add its jar + `java -jar selenium-server standalone --port 4444`, pointing it at the same `chrome-headless-shell`); that adds ~40 MB and the JVM. For local tracing, use Mode A/B (no container) — see `docs/browser-modes.md`.

## Footprints
| Image | Size |
|---|---|
| Official `selenium/standalone-chrome` 4.48 | ~3.4 GB |
| This slim node (headless-shell + chromedriver) | ~400–500 MB |
| + Selenium standalone (for se:cdp) | ~+80 MB |
