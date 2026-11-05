$ErrorActionPreference = 'Stop'
$recordPath = Join-Path $PSScriptRoot '安装记录.json'
$record = Get-Content -LiteralPath $recordPath -Raw -Encoding utf8 | ConvertFrom-Json
$instancePath = [IO.Path]::GetFullPath($record.instance).TrimEnd('\')
$backupPath = [IO.Path]::GetFullPath($record.backup).TrimEnd('\')
function Hash-File([string]$file) { return (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() }
function Safe-Path([string]$basePath, [string]$relative) {
    if (-not $relative.StartsWith('kubejs/') -or $relative.Contains('..') -or $relative.Contains('\')) { throw '安装记录路径异常。' }
    $resolved = [IO.Path]::GetFullPath((Join-Path $basePath $relative.Replace('/', '\')))
    if (-not $resolved.StartsWith($basePath + '\', [StringComparison]::OrdinalIgnoreCase)) { throw '目标路径越界。' }
    $cursor = $resolved
    while ($cursor.Length -ge $basePath.Length) {
        if ((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "路径包含链接：$cursor" }
        $cursor = Split-Path -Parent $cursor
        if (-not $cursor) { break }
    }
    return $resolved
}
$restore = @()
foreach ($change in $record.changes) {
    $target = Safe-Path $instancePath $change.path
    $backup = Safe-Path $backupPath $change.path
    $current = if (Test-Path -LiteralPath $target -PathType Leaf) { Hash-File $target } else { $null }
    if ((Test-Path -LiteralPath $target) -and -not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "目标不是文件：$target" }
    if ($current -eq $change.beforeSha256) { continue } # Already original, including a not-yet-written new file.
    if ($current -ne $change.afterSha256) { throw "文件已发生额外修改，停止撤回：$target" }
    if ($change.beforeSha256) {
        if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) { throw "缺少备份：$backup" }
        if ((Hash-File $backup) -ne $change.beforeSha256) { throw "备份指纹不符：$backup" }
    }
    $restore += [pscustomobject]@{target=$target;backup=$backup;before=$change.beforeSha256;after=$change.afterSha256}
}
foreach ($entry in $restore) {
    $current = if (Test-Path -LiteralPath $entry.target -PathType Leaf) { Hash-File $entry.target } else { $null }
    if ($current -ne $entry.after) { throw "撤回期间文件发生变化：$($entry.target)" }
    if ($entry.before) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $entry.target) -Force | Out-Null
        Copy-Item -LiteralPath $entry.backup -Destination $entry.target -Force
        if ((Hash-File $entry.target) -ne $entry.before) { throw "恢复核对失败：$($entry.target)" }
    } else {
        Remove-Item -LiteralPath $entry.target
    }
}
$record.status = 'rolled_back'
$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $recordPath -Encoding utf8
Write-Output '已撤回第二阶段，恢复第一阶段文件。存档内容不回退。'
