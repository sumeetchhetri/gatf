# restful-api.dev — authenticated API sample

`restful-api-dev.xml` is a runnable GATF suite against the real JWT-secured API at
https://api.restful-api.dev. It demonstrates the full authenticated-API testing flow:

1. **Register** / **Login** → capture the issued JWT into the workflow variable `${authToken}`.
2. **Authenticated CRUD** on a private collection — every call carries
   `x-api-key: ${apiKey}`, the query flag `?auth-type=jwt`, and `Authorization: Bearer ${authToken}`.
3. **Negative check** — the `?status=401` override proves the client handles auth failures.

## 1. Get your API key

`x-api-key` is per-user. Sign in at https://restful-api.dev to obtain yours. Every authenticated
endpoint requires it.

## 2. Define the five variables

The suite uses `${apiKey}`, `${apiEmail}`, `${apiPassword}`, `${apiName}`, `${collectionName}`.
`${authToken}`, `${userId}`, and `${objectId}` are captured at runtime — don't set those.

Add the five to `gatf-config.xml` under `<gatfTestDataConfig>`. **Format matters:** `globalVariables`
is a plain Jackson map (no custom serializer), so the map key is the **element name** —
`<apiKey>…</apiKey>`, *not* the `<property key="apiKey">` form the `<headers>` blocks use (those have a
custom serializer). Replace the empty `<globalVariables/>` with:

```xml
<gatfTestDataConfig>
  <globalVariables>
    <apiKey>REPLACE_WITH_YOUR_X_API_KEY</apiKey>
    <apiEmail>gatf-demo@example.com</apiEmail>
    <apiPassword>SecurePass123!</apiPassword>
    <apiName>GATF Demo</apiName>
    <collectionName>gatf-demo-objects</collectionName>
  </globalVariables>
</gatfTestDataConfig>
```

(If you prefer not to touch the config, replace the `${…}` references directly in
`restful-api-dev.xml` instead.)

## 3. Run in API mode

The sample project ships in Selenium mode. For the API suite, set:

```xml
<isSeleniumExecutor>false</isSeleniumExecutor>
```

Then run GATF against this project as usual. `isOrderByFileName=true` runs `auth.xml`,
`restful-api-dev.xml`, then `users.xml`; this suite is self-contained (captures its own token) so
order doesn't matter.

## Repeat runs

`Register` creates the account and only succeeds the first time — a duplicate email returns an error,
so on later runs it may report **FAILED** while everything else passes. `Login` is the authoritative
token source and runs every time. For a clean repeat run, comment out the Register case or point
`apiEmail` at a fresh address.

## Daily limits

restful-api.dev allows 100 authenticated requests/user/day on the free tier. This suite makes 8 calls
per run.
