$ErrorActionPreference = 'Stop'
$recordFile = Join-Path $PSScriptRoot '安装记录.json'
if (-not (Test-Path -LiteralPath $recordFile)) { throw '找不到安装记录，停止操作。' }
$record = Get-Content -LiteralPath $recordFile -Raw -Encoding utf8 | ConvertFrom-Json
$instanceRoot = [IO.Path]::GetFullPath($record.instance).TrimEnd('\')
$backupRoot = [IO.Path]::GetFullPath($record.backup).TrimEnd('\')
$checked = @()
foreach ($entry in $record.files) {
    if (-not $entry.path.StartsWith('kubejs\') -or $entry.path.Contains('..')) { throw '安装记录含异常路径。' }
    $target = [IO.Path]::GetFullPath((Join-Path $instanceRoot $entry.path))
    $original = [IO.Path]::GetFullPath((Join-Path $backupRoot $entry.path))
    if (-not $target.StartsWith($instanceRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw '目标超出实例。' }
    if (-not $original.StartsWith($backupRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw '备份超出备份目录。' }
    if (Test-Path -LiteralPath $target) {
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLower() -ne $entry.installedSha256) { throw "文件已被修改，请人工核对：$target" }
    }
    if ($entry.existed -and -not (Test-Path -LiteralPath $original)) { throw "缺少原文件备份：$original" }
    $checked += [pscustomobject]@{target=$target;original=$original;existed=$entry.existed}
}
foreach ($entry in $checked) {
    if ($entry.existed) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $entry.target) -Force | Out-Null
        Copy-Item -LiteralPath $entry.original -Destination $entry.target -Force
    } elseif (Test-Path -LiteralPath $entry.target) {
        Remove-Item -LiteralPath $entry.target
    }
}
Write-Output '已撤回安装记录中的联动文件。存档、模组和其他配置未改动。'
