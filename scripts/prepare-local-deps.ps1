# Restore only the three exact bridge dependencies from a user-supplied mods directory.
# This never modifies the game instance or downloads/executes mod code.
param(
    [Parameter(Mandatory = $true)][string]$ModsPath,
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskManifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'bridge-dependencies.json') -Raw | ConvertFrom-Json
$taskDestination = [IO.Path]::GetFullPath((Join-Path $taskRoot 'work\l13\bridge\libs'))
$taskSourceRoot = (Resolve-Path -LiteralPath $ModsPath).Path
$taskPlan = @()
foreach ($taskDependency in $taskManifest.dependencies) {
    if ($taskDependency.source -match '[/\\]' -or $taskDependency.target -match '[/\\]') { throw 'Dependency names must be plain filenames' }
    $taskSource = Join-Path $taskSourceRoot $taskDependency.source
    if (-not (Test-Path -LiteralPath $taskSource -PathType Leaf)) { throw "Required dependency missing: $($taskDependency.source)" }
    if ((Get-FileHash -LiteralPath $taskSource -Algorithm SHA256).Hash -ne $taskDependency.sha256) { throw "Source fingerprint mismatch: $($taskDependency.source)" }
    $taskTarget = Join-Path $taskDestination $taskDependency.target
    if (Test-Path -LiteralPath $taskTarget) {
        if (-not (Test-Path -LiteralPath $taskTarget -PathType Leaf)) { throw "Target is not a regular file: $($taskDependency.target)" }
        if ((Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash -ne $taskDependency.sha256) { throw "Existing dependency differs; no files copied: $($taskDependency.target)" }
    }
    $taskPlan += [pscustomobject]@{ Source=$taskSource; Target=$taskTarget; Sha256=$taskDependency.sha256 }
}
if (-not $ValidateOnly) {
    New-Item -ItemType Directory -Path $taskDestination -Force | Out-Null
    foreach ($taskFile in $taskPlan) {
        # overwrite=false also protects against a file appearing after validation.
        if (-not (Test-Path -LiteralPath $taskFile.Target)) { [IO.File]::Copy($taskFile.Source, $taskFile.Target, $false) }
        if ((Get-FileHash -LiteralPath $taskFile.Target -Algorithm SHA256).Hash -ne $taskFile.Sha256) { throw 'Copied dependency fingerprint mismatch' }
    }
}
[ordered]@{ dependencies=$taskPlan.Count; validated=$true; validateOnly=[bool]$ValidateOnly; destination='work/l13/bridge/libs' } | ConvertTo-Json -Compress
