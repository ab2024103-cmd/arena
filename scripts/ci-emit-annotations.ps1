# Emits GitHub Actions ::error:: annotations from a Gradle output log so that
# build failures remain diagnosable when the Actions log blob store is
# unreachable (annotations are served by the Checks API).
param(
    [Parameter(Mandatory = $true)][string]$Log
)

if (-not (Test-Path $Log)) { return }

$picked = New-Object System.Collections.Generic.List[string]

# RAW tail of the log minus stack frames / progress noise. 10 annotations of
# <=460 chars carry roughly the whole failure block.
$lines = Get-Content $Log -Tail 400 | Where-Object {
    $_ -notmatch '^\s+at |^\s*\.\.\.[0-9]+ more|^Run with |^Get more help|^> Run with|^Note: |^Warning: |^The system is '
}
$tail = ($lines | Select-Object -Last 120) -join "`n"

# Chunk into <=460-char pieces (annotation messages are size-capped).
$chunks = New-Object System.Collections.Generic.List[string]
$cur = ""
foreach ($line in ($tail -split "`n")) {
    $line = $line.TrimEnd("`r")
    if (($cur.Length + $line.Length + 1) -gt 440 -and $cur.Length -gt 0) {
        $chunks.Add($cur)
        $cur = ""
    }
    while ($line.Length -gt 440) {
        if ($cur.Length -gt 0) { $chunks.Add($cur); $cur = "" }
        $chunks.Add($line.Substring(0, 440))
        $line = $line.Substring(440)
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

# Raw tail -> job summary (public run page) for full-context debugging.
if ($env:GITHUB_STEP_SUMMARY) {
    Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value "### gradlew failure log tail"
    Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value '```text'
    Get-Content $Log -Tail 130 | ForEach-Object { Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value $_ }
    Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value '```'
}
