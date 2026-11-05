param([string]$ModsPath = 'C:\Users\hp\AppData\Roaming\.minecraft-forge-3\mods')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$instanceMods = $ModsPath
$auditRoot = Join-Path $PSScriptRoot 'audit'
$report = @()
foreach ($pattern in @('dimdoors-*.jar','DungeonsArise-*.jar','kubejs-forge-*.jar')) {
    $jar = @(Get-ChildItem -LiteralPath $instanceMods -Filter $pattern)
    if ($jar.Count -ne 1) { throw "Expected one target JAR: $pattern" }
    $archive = [IO.Compression.ZipFile]::OpenRead($jar[0].FullName)
    try {
        $textResources = [ordered]@{}
        $names = @($archive.Entries | ForEach-Object FullName)
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -match '(^data/.*\.json$|^assets/.*/lang/(en_us|zh_cn)\.json$|^META-INF/(mods.toml|MANIFEST.MF)$|mixins.*\.json$)') {
                $reader = [IO.StreamReader]::new($entry.Open())
                try { $textResources[$entry.FullName] = $reader.ReadToEnd() } finally { $reader.Dispose() }
            }
        }
        [IO.File]::WriteAllText((Join-Path $auditRoot ($jar[0].BaseName + '.json')),($textResources | ConvertTo-Json -Depth 30))
        [IO.File]::WriteAllLines((Join-Path $auditRoot ($jar[0].BaseName + '.index.txt')),$names)
        $report += [pscustomobject]@{file=$jar[0].Name;sha256=(Get-FileHash -LiteralPath $jar[0].FullName -Algorithm SHA256).Hash;classes=@($names | Where-Object { $_ -like '*.class' }).Count;structures=@($names | Where-Object { $_ -match '^data/.*/structures/.*\.nbt$' }).Count;metadata=$textResources['META-INF/mods.toml']}
    } finally { $archive.Dispose() }
}
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $auditRoot 'target-versions.json') -Encoding utf8
$report | Select-Object file,classes,structures,sha256 | ConvertTo-Json
