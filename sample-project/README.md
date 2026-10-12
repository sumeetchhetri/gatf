# GATF sample project

A small, self-contained GATF project showing how a real suite is organised: a hierarchy of seleasy (UI)
and API tests, cross-file reuse (`import` / `include` / `subtest`), and project configuration with a
properties file and a dynamic-props file.

## Layout

```
sample-project/
  gatf-config.xml          # executor config (XStream). testCaseDir=tests, out=out, a chrome driver,
                           # the trace recorders, and gatfTestDataConfig.globalVariables (${...} for API)
  tests/
    api/
      auth.xml             # POST /auth/login -> captures the token into ${authToken}
      users.xml            # GET/POST /users, reusing ${authToken}; JSONPath assertions
    ui/
      app.properties       # config vars (baseUrl / username / password / ...) loaded via `config`
      dynamic.props        # dynamic code vars loaded via `dynprops`
      setup.sel            # includable: open browser + config + dynprops + import auth + doLogin
      login-flow.sel       # flagship flow: include setup + import nav + navigate + create + assert
      lib/
        auth.sel           # subtests: doLogin / doLogout
        nav.sel            # subtest:  openAppointments
```

## seleasy features shown

- `subtest "name" { ... }` + `@call "name"` — reusable blocks
- `import <path>` — bring in another script's subtests/functions
- `include <path>` — inline another script (the setup boilerplate)
- `config <file.properties>` — load a properties file as `${...}` provider variables
- `dynprops <file.props>` — load dynamic code variables as `${...}`
- element targeting: `id@` / `css@` / `xpath@` / `text@` / `name@` / `class@`
- conditional presence: `??+` (wait until no longer found) / `??-` (wait until found) / `??N` (wait up to N s)
- assertions: `?? <el> visible | text ="..." | enabled | ...`
- data: `randomize alphauc N` / `randomize range MIN MAX`; `execjs '...'`

Paths for `import` / `include` / `config` / `dynprops` resolve relative to the file that references them.

## API workflow

`auth.xml` logs in and captures the response `token` node into `${authToken}` via
`workflowContextParameterMap`; `users.xml` then sends `Authorization: Bearer ${authToken}`.
`isOrderByFileName=true` runs `auth.xml` before `users.xml`. `${username}` / `${password}` /
`${apiBaseUrl}` come from `gatf-config.xml`'s `globalVariables`.

## Running (from this folder)

- **UI:** the Selenium executor runs the `.sel` files under `tests/` (a Grid via `SELENIUM_REMOTE_URL`,
  else a local driver on `PATH`).
- **API:** the API executor runs the `.xml` files under `tests/`.
- **Console / SPA:** point GATF at this folder (it reads `gatf-config.xml`) and use **Execute**; reports and
  trace bundles land in `out/`.

The demo URLs (`demo.gatf.example`) are placeholders — repoint `baseUrl` / `apiBaseUrl` in `gatf-config.xml`
and `app.properties` at a real environment before running.
