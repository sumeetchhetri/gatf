---
title: Mail and HTTP simulators
sidebar_position: 18
---

# Check emails and callbacks with the simulators

GATF can start an embedded mail server (SMTP and IMAP) and an HTTP server for the duration of a run, so a test can check that the application sent an email or called a webhook.

```json title="gatf-config-ui.json"
{
  "testCaseDir": "tests",
  "outFilesDir": "out",
  "isSeleniumExecutor": true,
  "seleniumScripts": ["signup.sel"],
  "mailSimulator": {
    "enabled": true,
    "login": "qa",
    "password": "qa-password",
    "smtpPort": 3025,
    "imapPort": 3143
  },
  "htttpServerSimulator": {
    "enabled": true,
    "port": 8085,
    "body": "{\"ok\": true}"
  }
}
```

Point the application's SMTP settings at port 3025 and its webhook or SMS gateway URL at `http://<gatf-host>:8085`. Note the key's spelling: `htttpServerSimulator`, with three `t`s. The HTTP simulator answers every request with status 200 and the configured body.

```sel title="signup.sel"
open chrome
goto https://shop.example.com/signup
type "new@example.com" label@Email
click role@button[name='Sign up']
mail 'welcome mail' 'noreply@shop.example.com' 'new@example.com' 'Welcome' 'Confirm your address (.*)' 30000
http 'sms callback' 'shop' '+15550100' 'Your code is (.*)' 30000
```

`mail` waits (up to the timeout, in ms) for a message with that sender, recipient and subject whose body matches the regular expression; `http` does the same for a request the HTTP simulator received. All fields: [MailSimulatorConfig and HttpServerSimulatorConfig](../reference/configuration.md#mailsimulatorconfig).
