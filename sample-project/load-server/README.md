# Sample REST server (load-test target)

A tiny, **dependency-free** Node HTTP/JSON server for exercising GATF's load /
performance / concurrent engines and the API executor. Node core only — no
`npm install`, runs on any Node 16+.

## Run

```bash
node sample-project/load-server/server.js                 # http://localhost:8880
node sample-project/load-server/server.js --port 9000 --latency 25 --jitter 15
FAIL_RATE=0.05 node sample-project/load-server/server.js  # 5% of /api/* -> 500
```

| flag | env | default | meaning |
|------|-----|---------|---------|
| `--port <n>` | `PORT` | `8880` | listen port |
| `--latency <ms>` | `LATENCY_MS` | `0` | base delay on every response |
| `--jitter <ms>` | `JITTER_MS` | `0` | +/- random delay on top of base |
| `--fail-rate <f>` | `FAIL_RATE` | `0` | fraction (0..1) of `/api/*` calls returning HTTP 500 |
| `--quiet` | – | – | suppress the per-second RPS line |

It prints a `req/s` line each second and shuts down cleanly on Ctrl-C.

## Routes

| method | path | response |
|--------|------|----------|
| GET | `/health` | `200 {status,uptimeMs,requests}` |
| POST | `/auth/login` | `200 {token,username}` — accepts any creds, issues a token |
| GET | `/api/users` | `200 [ {id,name,role}, … ]` |
| POST | `/api/users` | `201 {id,name,role}` (body echoed; `Location` header) |
| GET | `/api/users/:id` | `200 {id,…}` or `404` |
| PUT | `/api/users/:id` | `200 {id,…}` or `404` |
| DELETE | `/api/users/:id` | `200 {message,id}` or `404` |
| GET | `/api/slow?ms=N` | `200` after an extra `N` ms (default 250) |
| ANY | `/api/echo` | `200 {method,query,headers,body}` |
| GET | `/api/status/:code` | returns that HTTP `code` |
| GET | `/metrics` | `200 {requests,byRoute,startedAt}` |

## Load-test it with GATF

`../gatf-config-load.xml` is an **API-mode** executor config pointing at this
server (`apiBaseUrl=http://localhost:8880`) with load testing on: 25 simulated
users, 5s ramp-up, 30s run, 6 report samples. The suite it runs is
`../tests/load/load-suite.xml` — login (capture token), list, create, and a
deliberately slow endpoint so the latency percentiles are non-trivial.

```bash
# 1) start the server
node sample-project/load-server/server.js --latency 5 --jitter 5

# 2) in another shell, run GATF's API executor against gatf-config-load.xml
#    (repoint the absolute *BasePath values in that file to your checkout first)
```

The load engine only activates when `loadTestingEnabled=true` **and**
`loadTestingTime > 10000` (ms) — both already set in `gatf-config-load.xml`.
Watch the server's `req/s` line climb, then open the load report (percentile
tables + sample runs) from GATF's Reports view.

## Quick smoke (no GATF)

```bash
node sample-project/load-server/server.js --quiet &
curl -s localhost:8880/health
TOKEN=$(curl -s -XPOST localhost:8880/auth/login -d '{"username":"u"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s localhost:8880/api/users
curl -s -XPOST localhost:8880/api/users -H "Authorization: Bearer $TOKEN" -d '{"name":"x"}'
curl -s "localhost:8880/api/slow?ms=100"
kill %1
```
