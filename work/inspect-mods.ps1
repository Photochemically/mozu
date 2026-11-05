param([string]$ModsPath = 'C:\mods')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$outDir = Join-Path $PSScriptRoot 'mod-audit'
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$summary = @()
foreach ($file in Get-ChildItem -LiteralPath $ModsPath -Filter '*.jar') {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $resources = [ordered]@{}
        $names = @($zip.Entries | ForEach-Object { $_.FullName })
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -match '(^data/.*\.json$|^assets/.*(lang/(en_us|zh_cn)\.json|/data/.*\.json)$|META-INF/mods\.toml$)') {
                $reader = [System.IO.StreamReader]::new($entry.Open())
                try { $resources[$entry.FullName] = $reader.ReadToEnd() } finally { $reader.Dispose() }
            }
        }
        $name = $file.BaseName
        [IO.File]::WriteAllText((Join-Path $outDir ($name + '.json')), ($resources | ConvertTo-Json -Depth 50 -Compress))
        [IO.File]::WriteAllLines((Join-Path $outDir ($name + '.index.txt')), $names)
        $summary += [pscustomobject]@{file=$file.Name;sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash;resources=$resources.Count}
    } finally { $zip.Dispose() }
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outDir 'manifest.json') -Encoding utf8
$summary | Format-Table -AutoSize
