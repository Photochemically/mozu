# Refresh only the development acceptance documents/marker. Never installs files or launches Minecraft.
$ErrorActionPreference = 'Stop'
$taskWorkspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$taskDirectory = [IO.Path]::GetFullPath((Join-Path $taskWorkspace 'outputs\L13验收辅助'))
$taskTarget = [IO.Path]::GetFullPath((Join-Path $taskDirectory '七类验收入口-准备包.zip'))
$taskTemporary = [IO.Path]::GetFullPath((Join-Path $taskDirectory '七类验收入口-准备包更新中.zip'))
foreach ($taskPath in @($taskDirectory,$taskTarget,$taskTemporary)) {
    if (-not $taskPath.StartsWith($taskWorkspace + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Output outside workspace' }
}
$taskBuild = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'audit\build-summary.json') -Raw | ConvertFrom-Json
$taskJar = Join-Path $PSScriptRoot ('bridge\build\libs\' + $taskBuild.jar)
if ((Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash -ne $taskBuild.sha256) { throw 'Build receipt no longer matches JAR' }
[IO.Directory]::CreateDirectory($taskDirectory) | Out-Null
$taskEntries = [ordered]@{
    'README.md'='ACCEPTANCE_FIXTURES.md'
    'WDA_COMPLETENESS_STAGE1.md'='WDA_COMPLETENESS_STAGE1.md'
    'WDA_RESOURCE_ALIASES.md'='WDA_RESOURCE_ALIASES.md'
    'NATIVE_ROOM_RESERVATIONS.md'='NATIVE_ROOM_RESERVATIONS.md'
    'NATIVE_ROOM_ROUTES.md'='NATIVE_ROOM_ROUTES.md'
    'WDA_TEMPLATE_GAPS.md'='WDA_TEMPLATE_GAPS.md'
    'WATER_ENVIRONMENTS.md'='WATER_ENVIRONMENTS.md'
    'SHIP_BOARDING.md'='SHIP_BOARDING.md'
    'UNDERGROUND_ENTRIES.md'='UNDERGROUND_ENTRIES.md'
    'AERIAL_ENVIRONMENTS.md'='AERIAL_ENVIRONMENTS.md'
    'MECHANICAL_BRIDGE_ENTRY.md'='MECHANICAL_BRIDGE_ENTRY.md'
    'SMALL_BLIMP_ENTRY.md'='SMALL_BLIMP_ENTRY.md'
    'MUSHROOM_MINE_ENTRY.md'='MUSHROOM_MINE_ENTRY.md'
    'SCORCHED_WELL_ENTRY.md'='SCORCHED_WELL_ENTRY.md'
    'TYPHON_DIVE_ENTRY.md'='TYPHON_DIVE_ENTRY.md'
    'LIGHTHOUSE_ENTRY.md'='LIGHTHOUSE_ENTRY.md'
    'WINDMILL_ENTRY.md'='WINDMILL_ENTRY.md'
    'MERCHANT_CAMP_ENTRY.md'='MERCHANT_CAMP_ENTRY.md'
    'ILLAGER_CAMP_ENTRY.md'='ILLAGER_CAMP_ENTRY.md'
    'GROUND_HOUSE_ENTRIES.md'='GROUND_HOUSE_ENTRIES.md'
    'BATHHOUSE_ENTRY.md'='BATHHOUSE_ENTRY.md'
    'FISHING_HUT_ENTRY.md'='FISHING_HUT_ENTRY.md'
    'test-fixtures/expedition-development-world.json'='test-fixtures/expedition-development-world.json'
}
$taskStream = [IO.File]::Open($taskTemporary,[IO.FileMode]::Create)
$taskArchive = [IO.Compression.ZipArchive]::new($taskStream,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($taskEntry in $taskEntries.GetEnumerator()) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,(Join-Path $PSScriptRoot $taskEntry.Value),$taskEntry.Key,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $taskArchive.Dispose(); $taskStream.Dispose() }
$taskCheck = [IO.Compression.ZipFile]::OpenRead($taskTemporary)
try {
    if ($taskCheck.Entries.Count -ne $taskEntries.Count) { throw 'Unexpected package entries' }
    foreach ($taskEntry in $taskEntries.GetEnumerator()) {
        $taskInput = $taskCheck.GetEntry($taskEntry.Key).Open()
        $taskDigest = [Security.Cryptography.SHA256]::Create()
        try { $taskActual = [BitConverter]::ToString($taskDigest.ComputeHash($taskInput)).Replace('-','') }
        finally { $taskDigest.Dispose(); $taskInput.Dispose() }
        if ($taskActual -ne (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $taskEntry.Value) -Algorithm SHA256).Hash) { throw 'Packaged document mismatch' }
    }
} finally { $taskCheck.Dispose() }
Move-Item -LiteralPath $taskTemporary -Destination $taskTarget -Force
$taskReceipt = [ordered]@{
    tests=($taskBuild.suites | Measure-Object -Property tests -Sum).Sum
    buildLog=$taskBuild.buildLog; jarBytes=$taskBuild.bytes; jarSha256=$taskBuild.sha256
    helperPackage='outputs/L13验收辅助/七类验收入口-准备包.zip'; helperSha256=(Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash
    helperEntries=@($taskEntries.Keys); runtimeTested=$false; installed=$false
}
$taskReceipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'audit\acceptance-fixtures-artifact.json') -Encoding utf8
[ordered]@{ tests=$taskReceipt.tests; helperEntries=$taskEntries.Count; helperSha256=$taskReceipt.helperSha256; installed=$false } | ConvertTo-Json -Compress
