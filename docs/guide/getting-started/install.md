---
title: Install
sidebar_position: 2
---

# Install GATF

GATF 5.0.0 ships as a ZIP with a per-user installer for Linux, macOS and Windows. It needs no administrator rights.

## Linux and macOS

```bash
curl -fsSL https://github.com/sumeetchhetri/gatf/releases/latest/download/install.sh | bash
```

The installer:

- looks for Java 21 or newer (a JDK is preferred). If there is none, it offers to download a private Eclipse Temurin 21 into `~/.gatf` (`--jre` picks the smaller runtime);
- installs GATF under `~/.gatf/versions/5.0.0` and a `gatf` launcher in `~/.local/bin`, which it adds to your `PATH` in your shell profile;
- offers to set up the load tools.

Open a new terminal (or run the `export PATH=…` line the installer prints), then check the install:

```bash
gatf version
```

```text
gatf 5.0.0
openjdk version "21.0.12.1" 2026-08-18
```

The second line is your Java version.

Installer options go after `bash -s --`, for example `curl -fsSL …/install.sh | bash -s -- --yes --with-tools wperfk`:

| Option | Effect |
|---|---|
| `--yes` | Answer every prompt with the default (unattended install) |
| `--with-tools wperfk,vegeta,autocannon` | Set up these load tools now; `--no-tools` skips them |
| `--version X` | Install that release instead of the latest |
| `--zip FILE` | Install from a downloaded `gatf-<version>.zip` |
| `--jdk DIR` or `--java DIR` | Use this Java installation |
| `--jre` | When downloading Java, get the JRE instead of the JDK |
| `--uninstall`, `--uninstall --purge` | Remove GATF but keep the private Java and the tools cache; `--purge` removes all of `~/.gatf` |

## Windows

In PowerShell:

```powershell
irm https://github.com/sumeetchhetri/gatf/releases/latest/download/install.ps1 | iex
```

It installs per user under `%LOCALAPPDATA%\gatf` and adds it to your user `PATH`. Open a new terminal and run `gatf version`. To pass options, run the script as a script block:

```powershell
& ([scriptblock]::Create((irm https://github.com/sumeetchhetri/gatf/releases/latest/download/install.ps1))) -Yes -WithTools wperfk
```

Windows specifics (paths, load tools, pre-provisioning) are in [Running GATF on Windows](../../WINDOWS-SUPPORT.md).

## Offline install

Download `gatf-5.0.0.zip` from the [5.0.0 release](https://github.com/sumeetchhetri/gatf/releases/tag/5.0.0) on a machine with internet access, copy it over, then:

```bash
unzip gatf-5.0.0.zip
./gatf-5.0.0/install.sh --yes
```

On Windows, unzip it and run `powershell -ExecutionPolicy Bypass -File .\gatf-5.0.0\install.ps1`. The load tools are downloaded on first use, so on a machine without internet access set them up by hand: see [wperfk offline install](../../load-tools.md#offline--air-gapped-install).

## Set up the load tools

The installer offers this step, and you can run it at any time. It downloads each tool once, checks its SHA-256, and prints what each load engine resolves to on this machine:

```bash
gatf tools install wperfk
```

Without a tool name it sets up all of them (`wperfk`, `vegeta`, `autocannon`, and `wrk`/`wrk2` where a host binary or Docker exists). Tools are cached under `~/.gatf/tools/<os>-<arch>/`. See [Load tools](../../load-tools.md).

## Without the installer

The release also has the plain jar (`gatf-alldep-5.0.0.jar`). Any `gatf <command>` in these docs works as `java -jar gatf-alldep-5.0.0.jar <command>`, except the launcher-only commands: use `-configtool 9080 127.0.0.1 .` for `gatf ui`, and `java -cp gatf-alldep-5.0.0.jar com.gatf.executor.ext.PerfToolProvisioner wperfk` for `gatf tools install wperfk`. See the [CLI reference](../../reference/cli.md).

## From source

```bash
git clone https://github.com/sumeetchhetri/gatf.git
cd gatf
mvn -B install
```

The jar is `alldep-jar/target/gatf-alldep-jar-5.0.0.jar`. `mvn -B install -DskipTests` skips the tests; `-DskipFrontend=true` skips rebuilding the web console.

Next: [your first API test](first-api-test.md).
