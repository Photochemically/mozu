$ErrorActionPreference='Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$packageRoot = Join-Path $taskRoot 'outputs\联动试用版-v0.1.0'
$payloadRoot = Join-Path $packageRoot '安装内容'
$instanceRoot = 'C:\Users\hp\AppData\Roaming\.minecraft-forge-3'
$manifest = Get-Content -LiteralPath (Join-Path $packageRoot '文件清单.json') -Raw -Encoding utf8 | ConvertFrom-Json
$backupRoot = Join-Path $taskRoot ('work\instance-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$records = @()
foreach ($entry in $manifest.files) {
    $relative = $entry.path.Replace('/', '\')
    if (-not $relative.StartsWith('kubejs\') -or $relative.Contains('..')) { throw 'Unexpected package path' }
    $source = [IO.Path]::GetFullPath((Join-Path $payloadRoot $relative))
    $target = [IO.Path]::GetFullPath((Join-Path $instanceRoot $relative))
    if (-not $target.StartsWith($instanceRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Target outside instance' }
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLower()
    if ($hash -ne $entry.sha256) { throw "Package changed: $relative" }
    $existed = Test-Path -LiteralPath $target
    if ($existed) {
        $backup = Join-Path $backupRoot $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $backup) -Force | Out-Null
        Copy-Item -LiteralPath $target -Destination $backup
    }
    $records += [pscustomobject]@{path=$relative;existed=$existed;installedSha256=$hash}
}
$record = [pscustomobject]@{instance=$instanceRoot;backup=$backupRoot;files=$records}
$record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $packageRoot '安装记录.json') -Encoding utf8
foreach ($entry in $records) {
    $target = Join-Path $instanceRoot $entry.path
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $payloadRoot $entry.path) -Destination $target -Force
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLower() -ne $entry.installedSha256) { throw "Installed hash mismatch: $($entry.path)" }
}
Write-Output "Installed and verified $($records.Count) files. Original configs and JARs were not modified."
