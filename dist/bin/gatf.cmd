@echo off
rem    Copyright 2013-2026, Sumeet Chhetri
rem
rem    Licensed under the Apache License, Version 2.0 (the "License");
rem    you may not use this file except in compliance with the License.
rem    You may obtain a copy of the License at
rem
rem    http://www.apache.org/licenses/LICENSE-2.0
rem
rem    Unless required by applicable law or agreed to in writing, software
rem    distributed under the License is distributed on an "AS IS" BASIS,
rem    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem    See the License for the specific language governing permissions and
rem    limitations under the License.
rem
rem GATF launcher (Windows). JDK: %GATF_JAVA_HOME%, else the one pinned by install.ps1 in
rem %LOCALAPPDATA%\gatf\gatf.conf.cmd, else java on PATH. Extra JVM flags: %GATF_JAVA_OPTS%.
setlocal
set "GATF_DIR=%~dp0.."
set "JAR=%GATF_DIR%\lib\gatf.jar"
if "%GATF_HOME%"=="" set "GATF_HOME=%LOCALAPPDATA%\gatf"
if "%GATF_JAVA_HOME%"=="" if exist "%GATF_HOME%\gatf.conf.cmd" call "%GATF_HOME%\gatf.conf.cmd"
set "JAVA=java"
if not "%GATF_JAVA_HOME%"=="" set "JAVA=%GATF_JAVA_HOME%\bin\java.exe"
if "%GATF_UI_HOST%"=="" set "GATF_UI_HOST=127.0.0.1"

if /i "%~1"=="ui" goto :ui
if /i "%~1"=="tools" goto :tools
if /i "%~1"=="version" goto :version
if /i "%~1"=="--version" goto :version
if "%~1"=="" goto :usage
if /i "%~1"=="help" goto :usage
"%JAVA%" %GATF_JAVA_OPTS% -jar "%JAR%" %*
exit /b %ERRORLEVEL%

:ui
set "PORT=%~2"
if "%PORT%"=="" set "PORT=9080"
set "DIR=%~3"
if "%DIR%"=="" set "DIR=."
"%JAVA%" %GATF_JAVA_OPTS% -jar "%JAR%" -configtool %PORT% %GATF_UI_HOST% "%DIR%"
exit /b %ERRORLEVEL%

:tools
if /i "%~2"=="install" (
  "%JAVA%" -cp "%JAR%" com.gatf.executor.ext.PerfToolProvisioner %3 %4 %5 %6 %7 %8 %9
) else (
  "%JAVA%" -cp "%JAR%" com.gatf.executor.ext.PerfToolProvisioner %2 %3 %4 %5 %6 %7 %8 %9
)
exit /b %ERRORLEVEL%

:version
if exist "%GATF_DIR%\VERSION" (set /p V=<"%GATF_DIR%\VERSION") else (set "V=unknown")
echo gatf %V%
"%JAVA%" -version 2>&1
exit /b 0

:usage
echo gatf ui [port] [dir]          web console (default 9080, current dir)
echo gatf tools install [tool...]  set up load tools (vegeta autocannon)
echo gatf version                  GATF + JDK versions
echo gatf ^<args^>                   passed to the jar (-executor ^| -selenium ^| -listener ...)
exit /b 0
