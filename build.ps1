param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'), [string[]]$GradleArgs = @())
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$jdkPath = Get-ChildItem -Directory "$projectRoot/.tools/jdk-*" | Select-Object -First 1
if (-not $jdkPath) { throw 'Run uv run tools/install_toolchain.py first.' }
$env:JAVA_HOME = $jdkPath.FullName
$env:ANDROID_HOME = "$projectRoot/.tools/android-sdk"
$env:GRADLE_USER_HOME = "$projectRoot/.tools/gradle-cache"
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
Push-Location $projectRoot
try {
    & "$projectRoot/.tools/gradle-8.11.1/bin/gradle.bat" @Tasks @GradleArgs --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
