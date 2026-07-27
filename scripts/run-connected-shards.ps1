[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("LocalNotepad_API35", "JustNotes_API36_PlayStore")]
    [string]$AvdName,

    [string]$OutputRoot = "D:\AndroidBuilds\JustNotes\v1.0.9-regression",

    [ValidateSet(4)]
    [int]$ShardCount = 4,

    [switch]$KeepEmulator,

    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = "D:\android\SDK"
$javaHome = "D:\android\jdk-17\jdk-17.0.19+10"
$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"
$gradle = Join-Path $projectRoot "gradlew.bat"
$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runRoot = Join-Path (Join-Path $OutputRoot $AvdName) $runStamp

foreach ($requiredPath in @($adb, $emulator, $gradle, $javaHome)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required path is missing: $requiredPath"
    }
}

New-Item -ItemType Directory -Path $runRoot -Force | Out-Null

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:PATH = "$javaHome\bin;$sdkRoot\platform-tools;$sdkRoot\emulator;$env:PATH"

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = & $adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed ($LASTEXITCODE): $($Arguments -join ' ')`n$output"
    }
    return $output
}

function Get-OnlineEmulatorSerials {
    param([string[]]$DeviceLines)

    if ($PSBoundParameters.ContainsKey("DeviceLines")) {
        $lines = $DeviceLines
    } else {
        $lines = & $adb devices
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to list adb devices."
        }
    }
    return @(
        $lines |
            Select-String -Pattern "^(emulator-\d+)\s+device$" |
            ForEach-Object { $_.Matches[0].Groups[1].Value }
    )
}

function Wait-ForSingleEmulator {
    param([TimeSpan]$Timeout)

    $deadline = [DateTimeOffset]::Now.Add($Timeout)
    while ([DateTimeOffset]::Now -lt $deadline) {
        $serials = @(Get-OnlineEmulatorSerials)
        if ($serials.Count -gt 1) {
            throw "More than one online emulator is present: $($serials -join ', ')"
        }
        if ($serials.Count -eq 1) {
            $bootCompleted = (& $adb -s $serials[0] shell getprop sys.boot_completed 2>$null).Trim()
            if ($LASTEXITCODE -eq 0 -and $bootCompleted -eq "1") {
                return $serials[0]
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $AvdName to finish booting."
}

function Assert-ExpectedAvd {
    param([string]$Serial)

    $reportedName = ((
        Invoke-Adb -Arguments @("-s", $Serial, "emu", "avd", "name")
    ) -join "`n").Trim()
    if ($reportedName -notlike "$AvdName*") {
        throw "Connected emulator $Serial is '$reportedName', expected '$AvdName'."
    }
}

function Read-TestCases {
    param([string]$XmlDirectory)

    $xmlFiles = @(Get-ChildItem -LiteralPath $XmlDirectory -Filter "*.xml" -File)
    if ($xmlFiles.Count -eq 0) {
        throw "No connected-test XML was produced in $XmlDirectory."
    }

    $cases = @()
    $suiteTotals = [ordered]@{ tests = 0; failures = 0; errors = 0; skipped = 0 }
    foreach ($xmlFile in $xmlFiles) {
        [xml]$document = Get-Content -LiteralPath $xmlFile.FullName -Raw
        foreach ($suite in @($document.testsuite)) {
            $suiteTotals.tests += [int]$suite.tests
            $suiteTotals.failures += [int]$suite.failures
            $suiteTotals.errors += [int]$suite.errors
            $suiteTotals.skipped += [int]$suite.skipped
            foreach ($testCase in @($suite.testcase)) {
                $cases += "$($testCase.classname)#$($testCase.name)"
            }
        }
    }

    return [pscustomobject]@{
        Cases = @($cases)
        Totals = [pscustomobject]$suiteTotals
    }
}

function Get-ExpectedTestInventory {
    param([string]$Serial)

    $debugApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    $testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"

    Push-Location $projectRoot
    try {
        $inventoryBuildLog = Join-Path $runRoot "inventory-build.txt"
        $previousErrorActionPreference = $ErrorActionPreference
        try {
            # Capture the build output instead of emitting it from this
            # inventory function; emitted native output would be mixed into
            # the returned test-identity array by PowerShell.
            $ErrorActionPreference = "Continue"
            $inventoryBuildOutput = & $gradle `
                assembleDebug `
                assembleDebugAndroidTest `
                --no-daemon `
                --console=plain 2>&1
            $inventoryBuildExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        $inventoryBuildOutput |
            Set-Content -LiteralPath $inventoryBuildLog -Encoding utf8
        if ($inventoryBuildExitCode -ne 0) {
            throw "Unable to assemble APKs for the connected-test inventory."
        }
    } finally {
        Pop-Location
    }

    Invoke-Adb -Arguments @("-s", $Serial, "install", "-r", "-t", $debugApk) | Out-Null
    Invoke-Adb -Arguments @("-s", $Serial, "install", "-r", "-t", $testApk) | Out-Null

    # A cold emulator boot preserves the AVD data partition. Clear any app and
    # test-package state left by manual smoke tests or an earlier failed gate so
    # the connected candidate always starts from the same clean baseline.
    foreach ($packageName in @("com.brianyeh.justnotes", "com.brianyeh.justnotes.test")) {
        $clearResult = ((
            Invoke-Adb -Arguments @("-s", $Serial, "shell", "pm", "clear", $packageName)
        ) -join "`n").Trim()
        if ($clearResult -ne "Success") {
            throw "Unable to clear pre-gate state for $packageName on $Serial."
        }
    }

    $inventoryLog = Join-Path $runRoot "test-inventory.txt"
    $inventoryOutput = @(
        Invoke-Adb -Arguments @(
            "-s",
            $Serial,
            "shell",
            "am",
            "instrument",
            "-w",
            "-r",
            "-e",
            "log",
            "true",
            "-e",
            "listTestsForOrchestrator",
            "true",
            "com.brianyeh.justnotes.test/com.example.notepad.JustNotesTestRunner"
        )
    )
    $inventoryOutput | Set-Content -LiteralPath $inventoryLog -Encoding utf8

    $currentClass = $null
    $currentTest = $null
    $declaredCounts = [System.Collections.Generic.HashSet[int]]::new()
    $discovered = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $inventoryOutput) {
        if ($line -match "^INSTRUMENTATION_STATUS: class=(.+)$") {
            $currentClass = $Matches[1].Trim()
        } elseif ($line -match "^INSTRUMENTATION_STATUS: test=(.+)$") {
            $currentTest = $Matches[1].Trim()
        } elseif ($line -match "^INSTRUMENTATION_STATUS: numtests=(\d+)$") {
            [void]$declaredCounts.Add([int]$Matches[1])
        } elseif ($line -match "^INSTRUMENTATION_STATUS_CODE: 1$") {
            if ([string]::IsNullOrWhiteSpace($currentClass) -or [string]::IsNullOrWhiteSpace($currentTest)) {
                throw "The AndroidJUnitRunner inventory emitted an incomplete test identity."
            }
            $discovered.Add("$currentClass#$currentTest")
            $currentClass = $null
            $currentTest = $null
        }
    }

    if ($declaredCounts.Count -ne 1) {
        throw "The AndroidJUnitRunner inventory did not report one stable numtests value."
    }
    $declaredCount = @($declaredCounts)[0]
    $unique = @($discovered | Sort-Object -Unique)
    if ($discovered.Count -ne $unique.Count) {
        throw "The AndroidJUnitRunner inventory contains duplicate test identities."
    }
    if ($declaredCount -ne $unique.Count) {
        throw "The AndroidJUnitRunner inventory declared $declaredCount tests but discovered $($unique.Count)."
    }

    $unique | Set-Content -LiteralPath (Join-Path $runRoot "expected-tests.txt") -Encoding utf8
    return $unique
}

function Find-LogcatHazards {
    param([string]$LogcatPath)

    $logcatText = Get-Content -LiteralPath $LogcatPath -Raw
    $hazards = [System.Collections.Generic.List[string]]::new()
    $linePatterns = @(
        "ANR in com\.brianyeh\.justnotes",
        "am_anr.*com\.brianyeh\.justnotes",
        "am_crash.*com\.brianyeh\.justnotes",
        "keyDispatchingTimedOut",
        "InputDispatcher.*unresponsive",
        "INSTRUMENTATION_ABORTED",
        "INSTRUMENTATION_FAILED",
        "shortMsg=Process crashed",
        "Process com\.brianyeh\.justnotes .* has died",
        "emulator.*offline",
        "package service unavailable"
    )
    foreach ($pattern in $linePatterns) {
        foreach ($match in [regex]::Matches($logcatText, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
            $hazards.Add($match.Value)
        }
    }

    $fatalPattern = "FATAL EXCEPTION:[^\r\n]*(?:\r?\n[^\r\n]*){0,8}?\r?\n[^\r\n]*Process:\s*com\.brianyeh\.justnotes(?:\s|,)"
    foreach ($match in [regex]::Matches(
        $logcatText,
        $fatalPattern,
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )) {
        $hazards.Add($match.Value)
    }

    return @($hazards)
}

function Assert-RunnerHelpers {
    $single = @(
        Get-OnlineEmulatorSerials -DeviceLines @(
            "List of devices attached",
            "emulator-5554 device"
        )
    )
    if ($single.Count -ne 1 -or $single[0] -ne "emulator-5554") {
        throw "Single-emulator parsing self-test failed."
    }

    $multiple = @(
        Get-OnlineEmulatorSerials -DeviceLines @(
            "List of devices attached",
            "emulator-5554 device",
            "emulator-5556 device"
        )
    )
    if ($multiple.Count -ne 2) {
        throw "Multiple-emulator parsing self-test failed."
    }

    $hazardFixture = [System.IO.Path]::GetTempFileName()
    try {
        @"
07-27 12:00:00.000 E AndroidRuntime: FATAL EXCEPTION: main
07-27 12:00:00.001 E AndroidRuntime: Process: com.brianyeh.justnotes, PID: 1234
"@ | Set-Content -LiteralPath $hazardFixture -Encoding utf8
        $fatalHazards = @(Find-LogcatHazards -LogcatPath $hazardFixture)
        if ($fatalHazards.Count -ne 1) {
            throw "Multiline app-crash detection self-test failed."
        }

        "07-27 12:00:00.000 I ActivityManager: normal app output" |
            Set-Content -LiteralPath $hazardFixture -Encoding utf8
        $cleanHazards = @(Find-LogcatHazards -LogcatPath $hazardFixture)
        if ($cleanHazards.Count -ne 0) {
            throw "Clean-logcat detection self-test failed."
        }
    } finally {
        Remove-Item -LiteralPath $hazardFixture -Force -ErrorAction SilentlyContinue
    }

    Write-Host "Connected regression helper self-test PASS." -ForegroundColor Green
}

if ($SelfTest) {
    Assert-RunnerHelpers
    return
}

$existingEmulators = @(Get-OnlineEmulatorSerials)
if ($existingEmulators.Count -ne 0) {
    throw "Close all running emulators before this gate. Found: $($existingEmulators -join ', ')"
}

$emulatorProcess = Start-Process `
    -FilePath $emulator `
    -ArgumentList @("-avd", $AvdName, "-no-snapshot-load", "-no-boot-anim") `
    -WindowStyle Hidden `
    -PassThru

$serial = $null
$allCases = [System.Collections.Generic.List[string]]::new()
$shardSummaries = [System.Collections.Generic.List[object]]::new()

try {
    $serial = Wait-ForSingleEmulator -Timeout ([TimeSpan]::FromMinutes(5))
    Assert-ExpectedAvd -Serial $serial
    $env:ANDROID_SERIAL = $serial

    # Keep the normal software keyboard enabled while suppressing the
    # first-run stylus-handwriting education popup. On clean Google images
    # that system popup can overlap an app menu focus transition and stall
    # InputDispatcher for roughly 30 seconds, which contaminates otherwise
    # deterministic keyboard and navigation regression tests.
    Invoke-Adb -Arguments @(
        "-s", $serial, "shell", "settings", "put", "secure",
        "show_ime_with_hard_keyboard", "1"
    ) | Out-Null
    Invoke-Adb -Arguments @(
        "-s", $serial, "shell", "settings", "put", "secure",
        "stylus_handwriting_enabled", "0"
    ) | Out-Null
    $imeVisibilitySetting = ((
        Invoke-Adb -Arguments @(
            "-s", $serial, "shell", "settings", "get", "secure",
            "show_ime_with_hard_keyboard"
        )
    ) -join "`n").Trim()
    $stylusHandwritingSetting = ((
        Invoke-Adb -Arguments @(
            "-s", $serial, "shell", "settings", "get", "secure",
            "stylus_handwriting_enabled"
        )
    ) -join "`n").Trim()
    if ($imeVisibilitySetting -ne "1" -or $stylusHandwritingSetting -ne "0") {
        throw "Unable to establish deterministic IME settings on $serial."
    }

    $expectedTests = @(Get-ExpectedTestInventory -Serial $serial)

    Push-Location $projectRoot
    try {
        for ($shardIndex = 0; $shardIndex -lt $ShardCount; $shardIndex += 1) {
            $shardRoot = Join-Path $runRoot "shard-$shardIndex"
            $xmlCopy = Join-Path $shardRoot "xml"
            $htmlCopy = Join-Path $shardRoot "html"
            $sourceXml = Join-Path $projectRoot "app\build\outputs\androidTest-results\connected\debug"
            $sourceHtml = Join-Path $projectRoot "app\build\reports\androidTests\connected\debug"
            New-Item -ItemType Directory -Path $xmlCopy -Force | Out-Null
            New-Item -ItemType Directory -Path $htmlCopy -Force | Out-Null
            Remove-Item -LiteralPath $sourceXml -Recurse -Force -ErrorAction SilentlyContinue
            Remove-Item -LiteralPath $sourceHtml -Recurse -Force -ErrorAction SilentlyContinue

            Invoke-Adb -Arguments @("-s", $serial, "logcat", "-c") | Out-Null

            $gradleLog = Join-Path $shardRoot "gradle.txt"
            $previousErrorActionPreference = $ErrorActionPreference
            try {
                # Windows PowerShell 5 converts native stderr into terminating
                # NativeCommandError records when ErrorActionPreference is Stop.
                # Keep collecting the complete Gradle result so failed shards
                # still preserve XML, HTML, and logcat evidence below.
                $ErrorActionPreference = "Continue"
                $gradleOutput = & $gradle `
                    connectedDebugAndroidTest `
                    "-Pandroid.testInstrumentationRunnerArguments.numShards=$ShardCount" `
                    "-Pandroid.testInstrumentationRunnerArguments.shardIndex=$shardIndex" `
                    --no-daemon 2>&1 |
                    Tee-Object -FilePath $gradleLog
                $gradleExitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $previousErrorActionPreference
            }

            $logcatPath = Join-Path $shardRoot "logcat.txt"
            Invoke-Adb -Arguments @("-s", $serial, "logcat", "-d", "-v", "threadtime") |
                Set-Content -LiteralPath $logcatPath -Encoding utf8

            if (Test-Path -LiteralPath $sourceXml) {
                Copy-Item -Path (Join-Path $sourceXml "*") -Destination $xmlCopy -Recurse -Force
            }
            if (Test-Path -LiteralPath $sourceHtml) {
                Copy-Item -Path (Join-Path $sourceHtml "*") -Destination $htmlCopy -Recurse -Force
            }

            if ($gradleExitCode -ne 0) {
                throw "Connected shard $shardIndex failed with Gradle exit code $gradleExitCode."
            }

            $testResult = Read-TestCases -XmlDirectory $xmlCopy
            $totals = $testResult.Totals
            if ($totals.tests -ne $testResult.Cases.Count) {
                throw "Shard $shardIndex XML declared $($totals.tests) tests but contained $($testResult.Cases.Count) cases."
            }
            if ($totals.failures -ne 0 -or $totals.errors -ne 0 -or $totals.skipped -ne 0) {
                throw "Shard $shardIndex was not clean: failures=$($totals.failures), errors=$($totals.errors), skipped=$($totals.skipped)."
            }

            $gradleText = $gradleOutput -join "`n"
            $startedMatch = [regex]::Match($gradleText, "Starting\s+(\d+)\s+tests?\s+on")
            if (-not $startedMatch.Success) {
                throw "Shard $shardIndex did not report its started test count."
            }
            $startedCount = [int]$startedMatch.Groups[1].Value
            if ($startedCount -ne $totals.tests) {
                throw "Shard $shardIndex started $startedCount tests but XML contains $($totals.tests)."
            }

            $hazards = @(Find-LogcatHazards -LogcatPath $logcatPath)
            foreach ($match in [regex]::Matches(
                $gradleText,
                "INSTRUMENTATION_ABORTED|INSTRUMENTATION_FAILED|shortMsg=Process crashed",
                [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
            )) {
                $hazards += $match.Value
            }
            if ($hazards.Count -ne 0) {
                $hazards | Set-Content -LiteralPath (Join-Path $shardRoot "hazards.txt") -Encoding utf8
                throw "Shard $shardIndex logcat contains $($hazards.Count) forbidden hazard line(s)."
            }

            foreach ($testCase in $testResult.Cases) {
                $allCases.Add($testCase)
            }
            $shardSummaries.Add([pscustomobject]@{
                shard = $shardIndex
                started = $startedCount
                tests = $totals.tests
                failures = $totals.failures
                errors = $totals.errors
                skipped = $totals.skipped
                hazards = 0
            })
        }
    } finally {
        Pop-Location
    }

    $duplicates = @($allCases | Group-Object | Where-Object Count -gt 1)
    if ($duplicates.Count -ne 0) {
        $duplicates | Format-Table Name, Count -AutoSize | Out-String |
            Set-Content -LiteralPath (Join-Path $runRoot "duplicate-tests.txt") -Encoding utf8
        throw "Connected shards produced duplicate test cases."
    }

    $actualTests = @($allCases | Sort-Object -Unique)
    $missingTests = @($expectedTests | Where-Object { $_ -notin $actualTests })
    $unexpectedTests = @($actualTests | Where-Object { $_ -notin $expectedTests })
    if ($missingTests.Count -ne 0 -or $unexpectedTests.Count -ne 0) {
        $missingTests |
            Set-Content -LiteralPath (Join-Path $runRoot "missing-tests.txt") -Encoding utf8
        $unexpectedTests |
            Set-Content -LiteralPath (Join-Path $runRoot "unexpected-tests.txt") -Encoding utf8
        throw "Connected shard union differs from inventory: missing=$($missingTests.Count), unexpected=$($unexpectedTests.Count)."
    }

    $summary = [pscustomobject]@{
        avd = $AvdName
        serial = $serial
        shardCount = $ShardCount
        expectedTests = $expectedTests.Count
        uniqueTests = $actualTests.Count
        failures = 0
        errors = 0
        skipped = 0
        hazards = 0
        completedAt = [DateTimeOffset]::Now.ToString("o")
        shards = @($shardSummaries)
    }
    $summary | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $runRoot "summary.json") -Encoding utf8
    $summary | Format-List | Out-String | Write-Host
    Write-Host "Connected regression PASS: $runRoot" -ForegroundColor Green
} finally {
    Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue
    if (-not $KeepEmulator -and $serial) {
        & $adb -s $serial emu kill | Out-Null
    } elseif (-not $KeepEmulator -and $emulatorProcess -and -not $emulatorProcess.HasExited) {
        Stop-Process -Id $emulatorProcess.Id -Force
    }
}
