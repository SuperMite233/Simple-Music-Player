param(
    [ValidateSet('light', 'dark', 'amber', 'system')][string]$Theme = 'light',
    [ValidateSet('full', 'pages', 'scroll', 'refresh', 'smoke')][string]$Scope = 'full',
    [ValidatePattern('^[a-z0-9-]+$')][string]$RunName = 'refresh-light',
    [string]$AndroidRoot = 'D:\Android',
    [ValidatePattern('^emulator-\d+$')][string]$Serial = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
$smpRoot = Split-Path $PSScriptRoot -Parent
$smpAdb = Join-Path $AndroidRoot 'SDK\platform-tools\adb.exe'
$smpApp = Join-Path $smpRoot 'app\build\outputs\apk\debug\app-debug.apk'
$smpAudit = Join-Path $smpRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$smpOutput = Join-Path $smpRoot 'build\visual-audit'
$smpRunOutput = Join-Path $smpOutput $RunName
if (Test-Path -LiteralPath $smpRunOutput) { throw "Evidence already exists at $smpRunOutput. Choose a new -RunName to preserve the previous audit." }
if (!(Test-Path -LiteralPath $smpApp) -or !(Test-Path -LiteralPath $smpAudit)) {
    throw 'Run tools/android.ps1 -Task Audit before the visual audit.'
}
# Synthetic fixtures run only in the isolated debug package; formal app data is never opened.
foreach ($smpApk in @($smpApp, $smpAudit)) {
    & $smpAdb -s $Serial install -r $smpApk
    if ($LASTEXITCODE -ne 0) { throw 'Visual audit APK installation failed' }
}
& $smpAdb -s $Serial shell am force-stop com.supermite.smp.debug
$smpResult = & $smpAdb -s $Serial shell am instrument -w -e theme $Theme -e scope $Scope -e run $RunName `
    com.supermite.smp.debug.test/com.supermite.smp.VisualAuditInstrumentation
$smpResult | Write-Output
$smpInstrumentationSucceeded = $LASTEXITCODE -eq 0 -and ($smpResult -join "`n") -match 'Captured \d+ screens, 0 errors'
if (!(Test-Path -LiteralPath $smpOutput)) { New-Item -ItemType Directory -Path $smpOutput | Out-Null }
& $smpAdb -s $Serial pull "/sdcard/Android/data/com.supermite.smp.debug/files/visual-audit/$RunName" $smpOutput
if ($LASTEXITCODE -ne 0) { throw 'Visual audit evidence download failed' }
$smpReport = Get-Content -Raw -LiteralPath (Join-Path $smpOutput "$RunName\report.json") | ConvertFrom-Json
$smpReport | Add-Member -NotePropertyName apkSha256 -NotePropertyValue ((Get-FileHash -Algorithm SHA256 -LiteralPath $smpApp).Hash.ToLowerInvariant())
$smpReport | Add-Member -NotePropertyName androidApi -NotePropertyValue ((& $smpAdb -s $Serial shell getprop ro.build.version.sdk).Trim())
$smpReport | Add-Member -NotePropertyName instrumentationSha256 -NotePropertyValue ((Get-FileHash -Algorithm SHA256 -LiteralPath $smpAudit).Hash.ToLowerInvariant())
if ([string]::IsNullOrWhiteSpace($smpReport.version)) { throw 'Instrumentation report is missing BuildConfig.VERSION_NAME.' }
$smpReport | ConvertTo-Json -Depth 100 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $smpOutput "$RunName\report.json")
if (!$smpInstrumentationSucceeded -or $smpReport.errors -ne 0 -or @($smpReport.assertions | Where-Object { !$_.ok }).Count -gt 0) {
    throw "Audit failed; inspect the instrumentation output and $smpRunOutput\report.json."
}
Write-Output "Evidence: $smpOutput\$RunName; $($smpReport.screens.Count) screenshots."
