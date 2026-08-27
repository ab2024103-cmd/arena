# Emits GitHub Actions ::error:: annotations from a Gradle output log so that
# build failures remain diagnosable when the Actions log blob store is
# unreachable (annotations are served by the Checks API).
param(
    [Parameter(Mandatory = $true)][string]$Log
)

if (-not (Test-Path $Log)) { return }

$picked = New-Object System.Collections.Generic.List[string]

# Failing tests first (name + exception message line).
Select-String -Path $Log -Pattern ' FAILED( |$)' -Context 0,1 | Select-Object -First 8 | ForEach-Object {
    $picked.Add($_.Line)
    if ($_.Context.PostContext) { $picked.Add([string]$_.Context.PostContext[0]) }
}

# Kotlin compiler errors next (most actionable).
Select-String -Path $Log -Pattern '^(e: |error)' | Select-Object -First 6 | ForEach-Object { $picked.Add($_.Line) }

# Then the Gradle failure summary: only the meaningful lines (task names,
# causes, descriptions) - never stack frames.
$failureTail = Get-Content $Log | Select-String -Pattern '^FAILURE: Build failed' -Context 0,45
if ($failureTail) {
    $failureTail[0].Context.PostContext | Where-Object {
        $_ -match '^(FAILURE:|\* What went wrong:|\* Try:|> |Execution failed for task|Details:|e: |Caused by:|\* Exception is:)' -and
        $_ -notmatch '^\s+at '
    } | Select-Object -First 14 | ForEach-Object { $picked.Add($_) }
}

if ($picked.Count -eq 0) {
    Get-Content $Log -Tail 15 | Where-Object { $_ -notmatch '^\s+at ' } | ForEach-Object { $picked.Add($_) }
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

# Raw tail -> job summary (public run page) for full-context debugging.
if ($env:GITHUB_STEP_SUMMARY) {
    Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value "### gradlew failure log tail"
    Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value '```text'
    Get-Content $Log -Tail 130 | ForEach-Object { Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value $_ }
    Add-Content -Path $env:GITHUB_STEP_SUMMARY -Value '```'
}
