# ============================================================
#  ClipForge AI Lite — Gradle Wrapper + Structure Fix
#  Run this ONCE, then re-open the project in Android Studio
# ============================================================

$root = "C:\Users\HomePC\dev\clipforge-ai-lite"

Write-Host "Fixing ClipForge AI Lite project structure..." -ForegroundColor Cyan

function Write-File($path, $content) {
    $dir = Split-Path $path
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
}

# ── 1. Gradle Wrapper properties ─────────────────────────────────────────────
Write-File "$root\gradle\wrapper\gradle-wrapper.properties" @'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'@
Write-Host "  [OK] gradle-wrapper.properties" -ForegroundColor Green

# ── 2. gradlew.bat (Windows) ──────────────────────────────────────────────────
Write-File "$root\gradlew.bat" @'
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
'@
Write-Host "  [OK] gradlew.bat" -ForegroundColor Green

# ── 3. Download the real gradle-wrapper.jar ───────────────────────────────────
$jarPath = "$root\gradle\wrapper\gradle-wrapper.jar"
if (!(Test-Path $jarPath)) {
    Write-Host "  Downloading gradle-wrapper.jar..." -ForegroundColor Yellow
    try {
        $url = "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
        Invoke-WebRequest -Uri $url -OutFile $jarPath -UseBasicParsing
        Write-Host "  [OK] gradle-wrapper.jar downloaded" -ForegroundColor Green
    } catch {
        Write-Host "  [WARN] Could not download gradle-wrapper.jar. Android Studio will fix this on first sync." -ForegroundColor Yellow
    }
}

# ── 4. local.properties ───────────────────────────────────────────────────────
# Android Studio needs this to know where the SDK is
$sdkPaths = @(
    "$env:LOCALAPPDATA\Android\Sdk",
    "$env:USERPROFILE\AppData\Local\Android\Sdk",
    "C:\Users\HomePC\AppData\Local\Android\Sdk"
)
$sdkDir = $null
foreach ($p in $sdkPaths) {
    if (Test-Path $p) { $sdkDir = $p; break }
}

if ($sdkDir) {
    $sdkDirEscaped = $sdkDir.Replace("\", "\\")
    Write-File "$root\local.properties" "sdk.dir=$sdkDirEscaped"
    Write-Host "  [OK] local.properties -> $sdkDir" -ForegroundColor Green
} else {
    Write-File "$root\local.properties" "sdk.dir=C\:\\Users\\HomePC\\AppData\\Local\\Android\\Sdk"
    Write-Host "  [WARN] SDK path not found — edit local.properties manually if Gradle fails" -ForegroundColor Yellow
}

# ── 5. Verify the app module structure ───────────────────────────────────────
$required = @(
    "$root\settings.gradle.kts",
    "$root\build.gradle.kts",
    "$root\app\build.gradle.kts",
    "$root\gradle\libs.versions.toml",
    "$root\gradle\wrapper\gradle-wrapper.properties",
    "$root\app\src\main\AndroidManifest.xml"
)

Write-Host ""
Write-Host "  Verifying critical files..." -ForegroundColor Cyan
$allGood = $true
foreach ($f in $required) {
    if (Test-Path $f) {
        Write-Host "  [OK] $(Split-Path $f -Leaf)" -ForegroundColor Green
    } else {
        Write-Host "  [MISSING] $f" -ForegroundColor Red
        $allGood = $false
    }
}

# ── 6. File count ─────────────────────────────────────────────────────────────
$fileCount = (Get-ChildItem -Path $root -Recurse -File).Count

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  FIX COMPLETE — $fileCount files total" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  NOW DO THIS IN ANDROID STUDIO:" -ForegroundColor Yellow
Write-Host "  1. Close the current project" -ForegroundColor White
Write-Host "     File -> Close Project" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Re-open it properly:" -ForegroundColor White
Write-Host "     File -> Open -> C:\Users\HomePC\dev\clipforge-ai-lite" -ForegroundColor Gray
Write-Host "     (select the ROOT folder, not app/)" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. When prompted, click 'Trust Project'" -ForegroundColor White
Write-Host ""
Write-Host "  4. Wait for Gradle sync (bottom progress bar)" -ForegroundColor White
Write-Host "     First sync downloads ~500MB of deps" -ForegroundColor Gray
Write-Host ""
if (!$allGood) {
    Write-Host "  [WARNING] Some files are missing — re-run setup_clipforge.ps1 first" -ForegroundColor Red
}
Write-Host "========================================" -ForegroundColor Cyan
