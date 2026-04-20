param(
    [string]$PackageName = "com.example.anroidaiassistant",
    [string]$ServiceClass = ".MyAccessibilityService",
    [string]$Serial = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Get-AdbPath {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        return $adbCommand.Source
    }

    $sdkRoots = @()

    $localPropertiesPath = Join-Path $PSScriptRoot "local.properties"
    if (Test-Path $localPropertiesPath) {
        $sdkLine = Get-Content $localPropertiesPath | Where-Object { $_ -like "sdk.dir=*" } | Select-Object -First 1
        if ($sdkLine) {
            $rawSdkPath = $sdkLine.Substring("sdk.dir=".Length).Trim()
            $rawSdkPath = $rawSdkPath -replace "\\:", ":"
            $rawSdkPath = $rawSdkPath -replace "\\\\", "\"
            $sdkRoots += $rawSdkPath
        }
    }

    if ($env:ANDROID_SDK_ROOT) {
        $sdkRoots += $env:ANDROID_SDK_ROOT
    }

    if ($env:ANDROID_HOME) {
        $sdkRoots += $env:ANDROID_HOME
    }

    foreach ($sdkRoot in ($sdkRoots | Select-Object -Unique)) {
        if (-not $sdkRoot) {
            continue
        }

        $candidate = Join-Path $sdkRoot "platform-tools\\adb.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "adb was not found in PATH. Open Android Studio terminal or add platform-tools to PATH."
}

function Invoke-Adb {
    param(
        [string]$AdbPath,
        [string[]]$Arguments
    )

    $fullArguments = @()
    if ($Serial) {
        $fullArguments += "-s"
        $fullArguments += $Serial
    }
    $fullArguments += $Arguments

    if ($DryRun) {
        Write-Host ("DRY RUN > adb " + ($fullArguments -join " "))
        return ""
    }

    $output = & $AdbPath @fullArguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ("adb failed: " + ($output | Out-String).Trim())
    }

    return ($output | Out-String).Trim()
}

$adbPath = Get-AdbPath
$serviceShort = "$PackageName/$ServiceClass"
$serviceLong = "$PackageName/$($PackageName + $ServiceClass.TrimStart('.'))"

Write-Host "Using package: $PackageName"
Write-Host "Using service: $serviceShort"

Invoke-Adb -AdbPath $adbPath -Arguments @("wait-for-device") | Out-Null

$currentServices = Invoke-Adb -AdbPath $adbPath -Arguments @(
    "shell", "settings", "get", "secure", "enabled_accessibility_services"
)

$serviceEntries = @()
if ($currentServices -and $currentServices -ne "null") {
    $serviceEntries = $currentServices.Split(":") | Where-Object {
        $_ -and $_ -ne "null"
    }
}

$hasService = $serviceEntries -contains $serviceShort -or $serviceEntries -contains $serviceLong
if (-not $hasService) {
    $serviceEntries += $serviceShort
}

$mergedServices = ($serviceEntries | Select-Object -Unique) -join ":"

Invoke-Adb -AdbPath $adbPath -Arguments @(
    "shell", "settings", "put", "secure", "enabled_accessibility_services", $mergedServices
) | Out-Null

Invoke-Adb -AdbPath $adbPath -Arguments @(
    "shell", "settings", "put", "secure", "accessibility_enabled", "1"
) | Out-Null

Invoke-Adb -AdbPath $adbPath -Arguments @(
    "shell", "appops", "set", $PackageName, "SYSTEM_ALERT_WINDOW", "allow"
) | Out-Null

Write-Host ""
Write-Host "Test permissions applied."
Write-Host "- Accessibility service: enabled"
Write-Host "- Appear on top: allowed"
