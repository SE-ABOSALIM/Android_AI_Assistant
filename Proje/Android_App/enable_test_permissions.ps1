param(
    [string]$PackageName = "com.example.anroidaiassistant",
    [string]$ServiceClass = ".MyAccessibilityService",
    [string]$MainActivityClass = ".MainActivity",
    [string]$Serial = "",
    [switch]$NoForceStop,
    [switch]$NoLaunch,
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

function Invoke-AdbOptional {
    param(
        [string]$AdbPath,
        [string[]]$Arguments
    )

    try {
        Invoke-Adb -AdbPath $AdbPath -Arguments $Arguments | Out-Null
        return $true
    } catch {
        Write-Host ("Warning: " + $_.Exception.Message)
        return $false
    }
}

function Get-EnabledAccessibilityServices {
    param([string]$AdbPath)

    $currentServices = Invoke-Adb -AdbPath $AdbPath -Arguments @(
        "shell", "settings", "get", "secure", "enabled_accessibility_services"
    )

    if (-not $currentServices -or $currentServices -eq "null") {
        return @()
    }

    return @($currentServices.Split(":") | Where-Object { $_ -and $_ -ne "null" })
}

function Set-EnabledAccessibilityServices {
    param(
        [string]$AdbPath,
        [string[]]$Services
    )

    $mergedServices = (@($Services) | Where-Object { $_ } | Select-Object -Unique) -join ":"
    if ($mergedServices) {
        Invoke-Adb -AdbPath $AdbPath -Arguments @(
            "shell", "settings", "put", "secure", "enabled_accessibility_services", $mergedServices
        ) | Out-Null
        return
    }

    Invoke-AdbOptional -AdbPath $AdbPath -Arguments @(
        "shell", "settings", "delete", "secure", "enabled_accessibility_services"
    ) | Out-Null
}

function Remove-ServiceAliases {
    param(
        [string[]]$Services,
        [string[]]$Aliases,
        [string]$PackageName
    )

    return @($Services | Where-Object {
        $entry = $_
        if ($Aliases -contains $entry) {
            return $false
        }
        if ($entry -like "$PackageName/*" -and $entry -like "*MyAccessibilityService*") {
            return $false
        }
        return $true
    })
}

$adbPath = Get-AdbPath
$serviceClassName = $ServiceClass
if ($serviceClassName.StartsWith(".")) {
    $serviceClassName = $PackageName + $serviceClassName
}

$mainActivityClassName = $MainActivityClass
if ($mainActivityClassName.StartsWith(".")) {
    $mainActivityClassName = $PackageName + $mainActivityClassName
}

$serviceShort = "$PackageName/$ServiceClass"
$serviceComponent = "$PackageName/$serviceClassName"
$serviceAliases = @($serviceShort, $serviceComponent) | Select-Object -Unique
$mainActivityComponent = "$PackageName/$mainActivityClassName"

Write-Host "Using package: $PackageName"
Write-Host "Using service: $serviceComponent"
Write-Host "Using main activity: $mainActivityComponent"

Invoke-Adb -AdbPath $adbPath -Arguments @("wait-for-device") | Out-Null

if (-not $NoForceStop) {
    Invoke-AdbOptional -AdbPath $adbPath -Arguments @(
        "shell", "am", "force-stop", $PackageName
    ) | Out-Null
}

$serviceEntries = Get-EnabledAccessibilityServices -AdbPath $adbPath
$serviceEntries = Remove-ServiceAliases -Services $serviceEntries -Aliases $serviceAliases -PackageName $PackageName
Set-EnabledAccessibilityServices -AdbPath $adbPath -Services $serviceEntries

if ($serviceEntries.Count -eq 0) {
    Invoke-AdbOptional -AdbPath $adbPath -Arguments @(
        "shell", "settings", "put", "secure", "accessibility_enabled", "0"
    ) | Out-Null
}

if (-not $DryRun) {
    Start-Sleep -Milliseconds 900
}

$serviceEntries = Get-EnabledAccessibilityServices -AdbPath $adbPath
$serviceEntries = Remove-ServiceAliases -Services $serviceEntries -Aliases $serviceAliases -PackageName $PackageName
$serviceEntries += $serviceComponent
Set-EnabledAccessibilityServices -AdbPath $adbPath -Services $serviceEntries

Invoke-Adb -AdbPath $adbPath -Arguments @(
    "shell", "settings", "put", "secure", "accessibility_enabled", "1"
) | Out-Null

if (-not $DryRun) {
    Start-Sleep -Milliseconds 1200
}

Invoke-AdbOptional -AdbPath $adbPath -Arguments @(
    "shell", "appops", "set", $PackageName, "SYSTEM_ALERT_WINDOW", "allow"
) | Out-Null

Invoke-AdbOptional -AdbPath $adbPath -Arguments @(
    "shell", "appops", "set", $PackageName, "WRITE_SETTINGS", "allow"
) | Out-Null

$runtimePermissions = @(
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.READ_CONTACTS",
    "android.permission.CALL_PHONE"
)

foreach ($permission in $runtimePermissions) {
    Invoke-AdbOptional -AdbPath $adbPath -Arguments @(
        "shell", "pm", "grant", $PackageName, $permission
    ) | Out-Null
}

if (-not $NoLaunch) {
    Invoke-AdbOptional -AdbPath $adbPath -Arguments @(
        "shell", "am", "start", "-n", $mainActivityComponent,
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER"
    ) | Out-Null
}

Write-Host ""
Write-Host "Test permissions applied."
Write-Host "- Accessibility service: toggled off and enabled"
Write-Host "- Appear on top: allowed"
Write-Host "- Camera: granted"
Write-Host "- Microphone: granted"
Write-Host "- Contacts: granted"
Write-Host "- Make phone calls: granted"
if (-not $NoLaunch) {
    Write-Host "- App relaunched"
}
