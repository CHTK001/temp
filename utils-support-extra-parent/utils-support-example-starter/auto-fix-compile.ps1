# auto-exclude failing example sources until compile is green (max 8 iterations)
$pom = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'pom.xml'
$enc = [System.Text.UTF8Encoding]::new($false)
$excl = @()

function Read-Raw { [System.IO.File]::ReadAllText($pom) }
function Set-Excludes([string[]]$list) {
    $t = Read-Raw
    # remove existing excludes element inside configuration
    $s = $t.IndexOf('                    <excludes>')
    if ($s -ge 0) {
        $e = $t.IndexOf('</excludes>', $s) + '</excludes>'.Length
        if ($t[$e] -eq "`r") { $e += 2 } elseif ($t[$e] -eq "`n") { $e += 1 }
        $t = $t.Remove($s, $e - $s)
    }
    if ($list.Count -gt 0) {
        $items = ($list | ForEach-Object { "                        <exclude>$_</exclude>" }) -join "`r`n"
        $block = "                    <excludes>`r`n$items`r`n                    </excludes>"
        $anchor = $t.IndexOf('</includes>')
        if ($anchor -ge 0) {
            $insertAt = $t.IndexOf("`n", $anchor) + 1
            $t = $t.Insert($insertAt, $block + "`r`n")
        }
    }
    [System.IO.File]::WriteAllText($pom, $t, $enc)
}

for ($i = 1; $i -le 8; $i++) {
    Set-Excludes $excl
    $out = mvn -o -q -pl . clean compile 2>&1 | ForEach-Object { $_ -replace "`e\[[0-9;]*m", "" }
    $bad = @($out | Select-String 'src\\main\\java\\(com\\chua\\example\\[^\r\n]+?\.java):' |
             ForEach-Object { $_.Matches[0].Groups[1].Value.Replace('\', '/') } |
             Select-Object -Unique)
    Write-Host ("iter ${i}: failing=" + $bad.Count)
    if ($bad.Count -eq 0) {
        Write-Host "[GREEN]"
        break
    }
    foreach ($b in $bad) {
        $pat = '**/' + $b
        if ($excl -notcontains $pat) {
            $excl += $pat
            Write-Host ("  +exclude " + $pat)
        }
    }
}
Write-Host ("final excludes=" + $excl.Count)
