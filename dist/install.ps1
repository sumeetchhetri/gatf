#    Copyright 2013-2026, Sumeet Chhetri
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
# GATF installer — Windows (x64 / ARM64), Windows PowerShell 5.1+ or PowerShell 7.
#   online :  irm https://github.com/sumeetchhetri/gatf/releases/latest/download/install.ps1 | iex
#             (options when piped: set $env:GATF_VERSION / GATF_YES=1 / GATF_JDK / GATF_WITH_TOOLS first,
#              or: & ([scriptblock]::Create((irm <url>))) -Yes -WithTools vegeta,autocannon)
#   offline:  powershell -ExecutionPolicy Bypass -File .\gatf-<ver>\install.ps1
# Per-user (no admin): %LOCALAPPDATA%\gatf, user PATH. Needs Java 21+ — a JRE is enough (tests compile with
# the bundled ECJ); only the online debugger needs a JDK (jdk.jdi + jdk.jdwp.agent). Detects installed Java
# (preferring a JDK), else offers a private Temurin 21 download (JDK by default, -Jre for the smaller runtime).
[CmdletBinding()]
param(
    [string]$Version = $(if ($env:GATF_VERSION) { $env:GATF_VERSION } else { 'latest' }),
    [string]$Zip = '',
    [Alias('Java')][string]$Jdk = $env:GATF_JDK,
    [switch]$Jre = ($env:GATF_JRE -eq '1'),
    [switch]$Yes = ($env:GATF_YES -eq '1'),
    [string]$WithTools = $env:GATF_WITH_TOOLS,
    [switch]$NoTools,
    [switch]$Uninstall,
    [switch]$Purge
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest is very slow with the progress bar in 5.1
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch {}

$MinJava = 21
$IsWin = ($env:OS -eq 'Windows_NT')
$Releases = if ($env:GATF_RELEASE_BASE) { $env:GATF_RELEASE_BASE } else { 'https://github.com/sumeetchhetri/gatf/releases' }
$Adoptium = if ($env:GATF_ADOPTIUM_API) { $env:GATF_ADOPTIUM_API } else { 'https://api.adoptium.net/v3' }
$Prefix = if ($env:GATF_HOME) { $env:GATF_HOME } elseif ($IsWin) { Join-Path $env:LOCALAPPDATA 'gatf' } else { Join-Path $HOME '.gatf' }
$BinShim = Join-Path $Prefix 'bin'
$JavaExe = if ($IsWin) { 'java.exe' } else { 'java' }

function Say($m) { Write-Host $m }
function Die($m) { throw "GATF install: $m" }
function Ask($q) {
    if ($Yes) { return $true }
    try { $a = Read-Host "$q [y/N]" } catch { return $false }
    return ($a -match '^(y|yes)$')
}
# run a native exe capturing stdout+stderr as text without tripping ErrorActionPreference=Stop (PS 5.1)
function Run-Capture([string]$exe, [string[]]$argv) {
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { $o = & $exe @argv 2>&1 | ForEach-Object { "$_" } } catch { $o = @() } finally { $ErrorActionPreference = $old }
    return ($o -join "`n")
}
function Get-Sha256([string]$f) { (Get-FileHash -Algorithm SHA256 -LiteralPath $f).Hash.ToLowerInvariant() }
function Fetch([string]$url, [string]$out) { Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $out }

# ------------------------------------------------------------------ uninstall
if ($Uninstall) {
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    if ($IsWin -and $userPath) {
        $kept = ($userPath -split ';' | Where-Object { $_ -and ($_ -ne $BinShim) }) -join ';'
        [Environment]::SetEnvironmentVariable('Path', $kept, 'User')
    }
    if ($Purge) { Remove-Item -Recurse -Force -LiteralPath $Prefix -ErrorAction SilentlyContinue; Say "Removed $Prefix (GATF, private JDK, tool cache)." }
    else {
        foreach ($p in 'versions', 'current', 'bin', 'gatf.conf.cmd') { Remove-Item -Recurse -Force -LiteralPath (Join-Path $Prefix $p) -ErrorAction SilentlyContinue }
        Say "Removed GATF (kept $Prefix\jdk and $Prefix\tools; -Purge removes all)."
    }
    return
}

# ------------------------------------------------------------------ platform
$cpu = if ($env:PROCESSOR_ARCHITEW6432) { $env:PROCESSOR_ARCHITEW6432 } elseif ($env:PROCESSOR_ARCHITECTURE) { $env:PROCESSOR_ARCHITECTURE } else { 'AMD64' }
$AArch = switch -Regex ($cpu) { 'ARM64' { 'aarch64' } 'AMD64|x64' { 'x64' } default { Die "unsupported CPU $cpu" } }
$AOs = if ($IsWin) { 'windows' } else { 'linux' }   # non-Windows only for testing this script under pwsh

# ------------------------------------------------------------------ JDK detection
function Test-Jdk([string]$h) {
    if (-not $h) { return $false }
    $j = Join-Path (Join-Path $h 'bin') $JavaExe
    if (-not (Test-Path -LiteralPath $j)) { return $false }
    $props = Run-Capture $j @('-XshowSettings:properties', '-version')
    $m = [regex]::Match($props, 'java\.specification\.version\s*=\s*(\d+)')
    if (-not $m.Success -or [int]$m.Groups[1].Value -lt $MinJava) { return $false }
    if ($AArch -eq 'aarch64' -and $props -match 'os\.arch\s*=\s*(amd64|x86_64)') { Write-Warning "skipping x64 JDK at $h (emulated on ARM64)"; return $false }
    return $true
}
function Test-Debugger([string]$h) {   # the online debugger needs these JDK-only modules
    $mods = Run-Capture (Join-Path (Join-Path $h 'bin') $JavaExe) @('--list-modules')
    return (($mods -match '(?m)^jdk\.jdi@') -and ($mods -match '(?m)^jdk\.jdwp\.agent@'))
}
function Get-JdkCandidates {
    $c = New-Object System.Collections.Generic.List[string]
    if ($env:GATF_NO_SYSTEM_JDK -eq '1') { Get-ChildItem -Directory -LiteralPath (Join-Path $Prefix 'jdk') -ErrorAction SilentlyContinue | ForEach-Object { $c.Add($_.FullName) }; return $c }   # installer self-test hook
    foreach ($v in @($env:GATF_JAVA_HOME, $env:JAVA_HOME)) { if ($v) { $c.Add($v) } }
    $cmd = Get-Command $JavaExe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd) { $c.Add((Split-Path (Split-Path $cmd.Source -Parent) -Parent)) }
    if ($IsWin) {
        foreach ($k in 'HKLM:\SOFTWARE\JavaSoft\JDK', 'HKLM:\SOFTWARE\JavaSoft\Java Development Kit') {
            Get-ChildItem $k -ErrorAction SilentlyContinue | ForEach-Object { $h = (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).JavaHome; if ($h) { $c.Add($h) } }
        }
        foreach ($k in 'HKLM:\SOFTWARE\Eclipse Adoptium\JDK', 'HKLM:\SOFTWARE\Microsoft\JDK', 'HKLM:\SOFTWARE\Azul Systems\Zulu') {
            Get-ChildItem $k -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $h = (Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue).Path; if ($h) { $c.Add($h.TrimEnd('\')) } }
        }
        foreach ($d in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")) {
            Get-ChildItem -Directory -LiteralPath $d -ErrorAction SilentlyContinue | ForEach-Object { $c.Add($_.FullName) }
        }
    }
    Get-ChildItem -Directory -LiteralPath (Join-Path $Prefix 'jdk') -ErrorAction SilentlyContinue | ForEach-Object { $c.Add($_.FullName) }
    return $c
}
function Find-Jdk {   # prefer a runtime that can also run the debugger, else any Java 21+
    $c = Get-JdkCandidates
    foreach ($h in $c) { if ((Test-Jdk $h) -and (Test-Debugger $h)) { return $h } }
    foreach ($h in $c) { if (Test-Jdk $h) { return $h } }
    return $null
}

function Get-Temurin {
    $img = if ($Jre) { 'jre' } else { 'jdk' }
    $metaUrl = "$Adoptium/assets/latest/$MinJava/hotspot?os=$AOs&architecture=$AArch&image_type=$img&vendor=eclipse"
    Say "Fetching Temurin $MinJava ($AOs/$AArch) metadata from Adoptium..."
    $meta = Invoke-RestMethod -UseBasicParsing -Uri $metaUrl
    $pkg = @($meta)[0].binary.package          # the archive (binary.installer is the .msi)
    if (-not $pkg.link -or -not $pkg.checksum) { Die "no Temurin $MinJava $img for $AOs/$AArch" }
    $jdkRoot = Join-Path $Prefix 'jdk'; New-Item -ItemType Directory -Force -Path $jdkRoot | Out-Null
    $arc = Join-Path $jdkRoot ('.download' + [IO.Path]::GetExtension($pkg.name))
    Say "Downloading $($pkg.link)"
    Fetch $pkg.link $arc
    if ((Get-Sha256 $arc) -ne $pkg.checksum.ToLowerInvariant()) { Remove-Item -Force $arc; Die 'Java download checksum mismatch' }
    $before = @(Get-ChildItem -Directory -LiteralPath $jdkRoot | ForEach-Object { $_.FullName })
    if ($arc.EndsWith('.zip')) { Expand-Archive -LiteralPath $arc -DestinationPath $jdkRoot -Force } else { & tar -xzf $arc -C $jdkRoot }
    Remove-Item -Force $arc
    $new = Get-ChildItem -Directory -LiteralPath $jdkRoot | Where-Object { $before -notcontains $_.FullName } | Select-Object -First 1
    if (-not $new -or -not (Test-Jdk $new.FullName)) { Die 'downloaded Java failed the checks' }
    return $new.FullName
}

# ------------------------------------------------------------------ locate the GATF payload
$work = Join-Path ([IO.Path]::GetTempPath()) ('gatf-install-' + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $work | Out-Null
try {
    $src = $null
    $scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { $null }
    if ($Zip) {
        if (-not (Test-Path -LiteralPath $Zip)) { Die "no such zip: $Zip" }
        Expand-Archive -LiteralPath $Zip -DestinationPath $work -Force
    } elseif ($scriptDir -and (Test-Path -LiteralPath (Join-Path (Join-Path $scriptDir 'lib') 'gatf.jar'))) {
        $src = $scriptDir                                        # running from an extracted zip (offline)
    } else {
        $base = if ($Version -eq 'latest') { "$Releases/latest/download" } else { "$Releases/download/$Version" }
        Say "Downloading GATF ($Version)..."
        $z = Join-Path $work 'gatf.zip'
        Fetch "$base/gatf.zip" $z
        Fetch "$base/gatf.zip.sha256" "$z.sha256"
        $want = ((Get-Content -LiteralPath "$z.sha256" -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
        if ((Get-Sha256 $z) -ne $want) { Die 'gatf.zip checksum mismatch' }
        Expand-Archive -LiteralPath $z -DestinationPath $work -Force
    }
    if (-not $src) {
        $hit = Get-ChildItem -Directory -LiteralPath $work | Where-Object { Test-Path -LiteralPath (Join-Path (Join-Path $_.FullName 'lib') 'gatf.jar') } | Select-Object -First 1
        if (-not $hit) { Die 'payload not found (expected lib\gatf.jar)' }
        $src = $hit.FullName
    }
    $ver = if (Test-Path -LiteralPath (Join-Path $src 'VERSION')) { (Get-Content -LiteralPath (Join-Path $src 'VERSION') -Raw).Trim() } else { 'unknown' }

    # -------------------------------------------------------------- JDK
    $jh = $null
    if ($Jdk) { if (-not (Test-Jdk $Jdk)) { Die "-Jdk $Jdk is not a Java $MinJava+ runtime" }; $jh = $Jdk }
    else { $jh = Find-Jdk }
    if (-not $jh) {
        Say "No Java $MinJava+ found."
        if ($Zip -or ($src -eq $scriptDir)) {
            $p = ''; try { $p = Read-Host "Path to a Java $MinJava+ home (JAVA_HOME), or Enter to download Temurin $MinJava" } catch {}
            if ($p) { if (-not (Test-Jdk $p)) { Die "$p is not a Java $MinJava+ runtime" }; $jh = $p }
        }
        if (-not $jh) {
            $what = if ($Jre) { 'JRE (smaller; no online debugger)' } else { 'JDK (includes the online debugger)' }
            if (-not (Ask "Download Eclipse Temurin $MinJava $what into $Prefix\jdk (private, no system changes)?")) { Die "Java $MinJava+ is required - rerun with -Jdk <dir> or -Yes" }
            $jh = Get-Temurin
        }
    }
    Say "Using Java: $jh"
    if (-not (Test-Debugger $jh)) { Write-Warning 'this Java has no jdk.jdi/jdk.jdwp.agent: everything works except the online debugger (use a JDK via -Jdk <dir> for that)' }

    # -------------------------------------------------------------- install
    $dest = Join-Path (Join-Path $Prefix 'versions') $ver
    Remove-Item -Recurse -Force -LiteralPath $dest -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    foreach ($p in 'lib', 'bin', 'VERSION', 'LICENSE') { $s = Join-Path $src $p; if (Test-Path -LiteralPath $s) { Copy-Item -Recurse -Force -LiteralPath $s -Destination $dest } }
    $current = Join-Path $Prefix 'current'
    if (Test-Path -LiteralPath $current) { (Get-Item -LiteralPath $current).Delete() }
    if ($IsWin) { New-Item -ItemType Junction -Path $current -Target $dest | Out-Null } else { New-Item -ItemType SymbolicLink -Path $current -Target $dest | Out-Null }
    Set-Content -LiteralPath (Join-Path $Prefix 'gatf.conf.cmd') -Value "@set `"GATF_JAVA_HOME=$jh`"" -Encoding Ascii
    New-Item -ItemType Directory -Force -Path $BinShim | Out-Null
    Set-Content -LiteralPath (Join-Path $BinShim 'gatf.cmd') -Encoding Ascii -Value "@call `"$current\bin\gatf.cmd`" %*"
    if ($IsWin) {
        $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
        if (-not (($userPath -split ';') -contains $BinShim)) {
            [Environment]::SetEnvironmentVariable('Path', ((@($userPath, $BinShim) | Where-Object { $_ }) -join ';'), 'User')
            Say "Added $BinShim to your user PATH - open a new terminal."
        }
        $env:Path = "$BinShim;$env:Path"
    }
    Say "Installed GATF $ver -> $dest  (launcher: $BinShim\gatf.cmd)"

    # -------------------------------------------------------------- optional load tools
    if (-not $NoTools -and $IsWin) {
        $tools = $WithTools
        if (-not $tools -and (Ask 'Also set up the load-test tools (vegeta, and autocannon with a private Node.js)?')) { $tools = 'vegeta,autocannon' }
        if ($tools) {
            & (Join-Path $BinShim 'gatf.cmd') tools install @($tools -split ',')
            if ($LASTEXITCODE -ne 0) { Write-Warning 'tool setup failed - they will auto-provision on first use' }
        }
    }
    Say 'Done. Try:  gatf version   |   gatf ui   (then open http://127.0.0.1:9080/)'
} finally {
    Remove-Item -Recurse -Force -LiteralPath $work -ErrorAction SilentlyContinue
}
