param(
    [ValidateSet('Build', 'Check', 'Test', 'Lint', 'Install', 'Emulator', 'Audit', 'Release')]
    [string]$Task = 'Check',
    [string]$AndroidRoot = 'D:\Android',
    [string]$Avd = 'Medium_Phone',
    [switch]$UseSystemProxy
)

$ErrorActionPreference = 'Stop'
$smpProject = Split-Path $PSScriptRoot -Parent
$smpJavaHome = Join-Path $AndroidRoot 'Android Studio\jbr'
$smpSdk = Join-Path $AndroidRoot 'SDK'
$smpJava = Join-Path $smpJavaHome 'bin\java.exe'
$smpAdb = Join-Path $smpSdk 'platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $smpJava) -or !(Test-Path -LiteralPath $smpAdb)) {
    throw "Android Studio JBR or SDK not found under $AndroidRoot"
}
$env:JAVA_HOME = $smpJavaHome
$env:ANDROID_HOME = $smpSdk

if ($Task -eq 'Emulator') {
    $env:ANDROID_AVD_HOME = Join-Path $AndroidRoot 'AVD'
    $smpDevices = & $smpAdb devices
    if ($smpDevices -match '^emulator-\d+\s+device') { Write-Output 'An emulator is already connected.'; return }
    Start-Process -FilePath (Join-Path $smpSdk 'emulator\emulator.exe') -WindowStyle Hidden `
        -ArgumentList @('-avd', $Avd, '-no-window', '-no-audio', '-no-snapshot-load', '-no-snapshot-save')
    Write-Output "Starting $Avd without restoring a stale ADB snapshot; user data is retained."
    return
}

$smpJvmArguments = @('--enable-native-access=ALL-UNNAMED')
if ($UseSystemProxy) {
    $smpProxy = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings'
    if ($smpProxy.ProxyEnable -eq 1) {
        $smpProxyAddress = ($smpProxy.ProxyServer -split ';' | Where-Object { $_ -match '^(https=)?[^=]+:\d+$' } | Select-Object -First 1) -replace '^https=', ''
        if ($smpProxyAddress -match '^(?<proxyHost>[^:/;]+):(?<proxyPort>\d+)$') {
            $smpJvmArguments += "-Dhttps.proxyHost=$($Matches.proxyHost)", "-Dhttps.proxyPort=$($Matches.proxyPort)",
                "-Dhttp.proxyHost=$($Matches.proxyHost)", "-Dhttp.proxyPort=$($Matches.proxyPort)"
        }
    }
}
$smpTasks = switch ($Task) {
    'Build' { @(':app:assembleDebug') }
    'Install' { @(':app:assembleDebug') }
    'Test' { @(':app:testDebugUnitTest') }
    'Release' { @(':app:assembleRelease', ':app:lintRelease') }
    'Audit' { @(':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:testDebugUnitTest', ':app:lintDebug') }
    'Lint' { @(':app:lintDebug') }
    default { @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug') }
}
Push-Location $smpProject
try {
    & $smpJava @smpJvmArguments -classpath (Join-Path $smpProject 'gradle\wrapper\gradle-wrapper.jar') `
        org.gradle.wrapper.GradleWrapperMain @smpTasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    if ($Task -eq 'Install') {
        & $smpAdb install -r (Join-Path $smpProject 'app\build\outputs\apk\debug\app-debug.apk')
        if ($LASTEXITCODE -ne 0) { throw 'APK installation failed' }
        & $smpAdb shell am start -n 'com.supermite.smp.debug/com.supermite.smp.MainActivity'
    }
} finally { Pop-Location }
