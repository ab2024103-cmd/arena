# Emits GitHub Actions ::error:: annotations from a Gradle output log so that
# build failures remain diagnosable when the Actions log blob store is
# unreachable (annotations are served by the Checks API).
param(
    [Parameter(Mandatory = $true)][string]$Log
)

if (-not (Test-Path $Log)) { return }

$picked = New-Object System.Collections.Generic.List[string]

# Kotlin compiler errors first (most actionable).
Select-String -Path $Log -Pattern '^(e: |error)' | Select-Object -First 6 | ForEach-Object { $picked.Add($_.Line) }

# Then the Gradle failure summary (task name + root causes).
$failureTail = Get-Content $Log | Select-String -Pattern '^FAILURE: Build failed' -Context 0,45
if ($failureTail) {
    $failureTail[0].Context.PostContext | ForEach-Object { $picked.Add($_) }
}

if ($picked.Count -eq 0) {
    Get-Content $Log -Tail 12 | ForEach-Object { $picked.Add($_) }
}

$text = [string]::Join("`n", $picked)
if ($text.Length -gt 4800) { $text = $text.Substring($text.Length - 4800) }

# Chunk into <=460-char pieces (annotation messages are size-capped).
$chunks = New-Object System.Collections.Generic.List[string]
$cur = ""
foreach ($line in ($text -split "`n")) {
    $line = $line.TrimEnd("`r")
    if (($cur.Length + $line.Length + 1) -gt 460 -and $cur.Length -gt 0) {
        $chunks.Add($cur)
        $cur = ""
    }
    while ($line.Length -gt 460) {
        if ($cur.Length -gt 0) { $chunks.Add($cur); $cur = "" }
        $chunks.Add($line.Substring(0, 460))
        $line = $line.Substring(460)
    }
    if ($cur.Length -gt 0) { $cur += "`n" }
    $cur += $line
}
if ($cur.Length -gt 0) { $chunks.Add($cur) }

$i = 0
foreach ($c in $chunks) {
    if ($i -ge 10) { break }
    if ($c.Trim().Length -gt 0) {
        Write-Output ("::error::" + $c)
        $i++
    }
}
