param(
    [string]$PackageRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) 'outputs\联动试用版-v0.2.0'),
    [string]$PreviousPackageRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) 'outputs\联动试用版-v0.1.0'),
    [string]$InstanceRoot = 'C:\Users\hp\AppData\Roaming\.minecraft-forge-3',
    [switch]$PreflightOnly
)
$ErrorActionPreference = 'Stop'
$instancePath = [IO.Path]::GetFullPath($InstanceRoot).TrimEnd('\')
$payloadPath = [IO.Path]::GetFullPath((Join-Path $PackageRoot '安装内容')).TrimEnd('\')
$manifest = Get-Content -LiteralPath (Join-Path $PackageRoot '文件清单.json') -Raw -Encoding utf8 | ConvertFrom-Json
$previous = Get-Content -LiteralPath (Join-Path $PreviousPackageRoot '文件清单.json') -Raw -Encoding utf8 | ConvertFrom-Json
$recordFile = Join-Path $PackageRoot '安装记录.json'
if (Test-Path -LiteralPath $recordFile) { throw '已有本版本安装记录，停止重复安装以保留原备份。' }
function Resolve-PayloadPath([string]$basePath, [string]$relative) {
    if (-not $relative.StartsWith('kubejs/') -or $relative.Contains('..') -or $relative.Contains('\')) { throw "异常清单路径：$relative" }
    $resolved = [IO.Path]::GetFullPath((Join-Path $basePath $relative.Replace('/', '\')))
    if (-not $resolved.StartsWith($basePath + '\', [StringComparison]::OrdinalIgnoreCase)) { throw "路径越界：$resolved" }
    $cursor = $resolved
    while ($cursor.Length -ge $basePath.Length) {
        if (Test-Path -LiteralPath $cursor) {
            if ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "目标含链接，需另行核对：$cursor" }
        }
        $cursor = Split-Path -Parent $cursor
        if (-not $cursor) { break }
    }
    return $resolved
}
function Hash-File([string]$file) { return (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() }
$old = @{}
$new = @{}
foreach ($entry in $previous.files) {
    $target = Resolve-PayloadPath $instancePath $entry.path
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "旧版文件缺失，停止覆盖：$target" }
    if ((Hash-File $target) -ne $entry.sha256) { throw "旧版文件已有改动，停止覆盖：$target" }
    $old[$entry.path] = $entry
}
$changes = @()
foreach ($entry in $manifest.files) {
    if ($new.ContainsKey($entry.path)) { throw "重复清单路径：$($entry.path)" }
    $new[$entry.path] = $entry
    $source = Resolve-PayloadPath $payloadPath $entry.path
    $target = Resolve-PayloadPath $instancePath $entry.path
    if ((Hash-File $source) -ne $entry.sha256) { throw "发布文件指纹不符：$source" }
    if ($old.ContainsKey($entry.path)) {
        if ($old[$entry.path].sha256 -eq $entry.sha256) { continue }
        $before = $old[$entry.path].sha256
    } else {
        if (Test-Path -LiteralPath $target) { throw "新增文件与现有文件重名，停止覆盖：$target" }
        $before = $null
    }
    $changes += [pscustomobject]@{path=$entry.path;action='write';beforeSha256=$before;afterSha256=$entry.sha256}
}
foreach ($entry in $previous.files) {
    if (-not $new.ContainsKey($entry.path)) {
        $changes += [pscustomobject]@{path=$entry.path;action='remove';beforeSha256=$entry.sha256;afterSha256=$null}
    }
}
if ($PreflightOnly) {
    [pscustomobject]@{status='preflight_passed';changedFiles=$changes.Count;preservedFiles=$manifest.files.Count-@($changes | Where-Object action -eq 'write').Count} | ConvertTo-Json
    return
}
$taskRoot = Split-Path -Parent $PSScriptRoot
$backupVersion = ([string]$manifest.version) -replace '[^0-9A-Za-z.-]', '_'
$backupPath = Join-Path $taskRoot ('work\instance-backup-v' + $backupVersion + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backupPath | Out-Null
foreach ($change in $changes) {
    if ($change.beforeSha256) {
        $target = Resolve-PayloadPath $instancePath $change.path
        $backup = Resolve-PayloadPath $backupPath $change.path
        New-Item -ItemType Directory -Path (Split-Path -Parent $backup) -Force | Out-Null
        Copy-Item -LiteralPath $target -Destination $backup
        if ((Hash-File $backup) -ne $change.beforeSha256) { throw "备份核对失败：$backup" }
    }
}
$record = [pscustomobject]@{version=$manifest.version;previousVersion=$previous.version;status='prepared';instance=$instancePath;backup=$backupPath;changes=$changes;preservedFiles=$manifest.files.Count-@($changes | Where-Object action -eq 'write').Count}
$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $recordFile -Encoding utf8
try {
    foreach ($change in $changes) {
        $target = Resolve-PayloadPath $instancePath $change.path
        # Recheck immediately before each mutation so a concurrent user edit is preserved.
        if ($change.beforeSha256) {
            if ((Hash-File $target) -ne $change.beforeSha256) { throw "安装期间文件发生变化：$target" }
        } elseif (Test-Path -LiteralPath $target) { throw "安装期间出现同名文件：$target" }
        if ($change.action -eq 'write') {
            $source = Resolve-PayloadPath $payloadPath $change.path
            New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
            Copy-Item -LiteralPath $source -Destination $target -Force
            if ((Hash-File $target) -ne $change.afterSha256) { throw "安装文件核对失败：$target" }
        } else {
            Remove-Item -LiteralPath $target
        }
    }
    $record.status = 'installed'
} catch {
    $record.status = 'incomplete'
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $recordFile -Encoding utf8
    throw
}
$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $recordFile -Encoding utf8
[pscustomobject]@{status=$record.status;version=$record.version;changedFiles=$changes.Count;preservedFiles=$record.preservedFiles;backup=$backupPath} | ConvertTo-Json
