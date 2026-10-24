---
title: Use GATF from a coding agent
sidebar_position: 22
---

# Use GATF from a coding agent (MCP)

`gatf mcp` is a Model Context Protocol server over stdio. A coding agent can list the tests, lint a script, run the suite, read the last run's failures, summarise a trace, and record or crawl new scripts.

Register it with your agent, pointing `--root` at the project folder. For Claude Code:

```bash
claude mcp add gatf -- gatf mcp --root /path/to/project
```

Without the installer, use `java -jar gatf-alldep-5.0.0.jar mcp --root /path/to/project` as the command.

| Tool | Does |
|---|---|
| `list_tests` | Lists the project's API files and UI scripts |
| `lint` | Parses and compiles a script, returning diagnostics |
| `run` | Runs `gatf test`, with grep, tag, trace and retries options |
| `get_report` | The failed tests of the last run |
| `summarise_trace` | Condenses a trace zip: last actions, console errors, failed and slow requests, checks, web vitals |
| `record`, `crawl` | Start the recorder or crawler |
| `heal`, `plan`, `generate` | The [AI healer and planner](ai-healer-and-planner.md) tools; they return results and never write into the project |

Only protocol messages go to stdout; logs and run output go to stderr.
