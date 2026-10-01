$ErrorActionPreference = 'Stop'
$sshKey = Join-Path $env:USERPROFILE '.ssh\mazikox_github_actions'
$reportPath = Join-Path $PSScriptRoot '..\usage-report.html'
$sshArguments = @('-i', $sshKey, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', 'debian@146.59.63.158')
$reportHtml = & ssh @sshArguments 'set -eu; sudo journalctl -u caddy --since "7 days ago" --no-pager -o cat | python3 /home/debian/metin2bazar-preparation/usage_report.py'
if ($LASTEXITCODE -ne 0) { throw 'Nie udało się odczytać raportu z VPS.' }
[IO.File]::WriteAllText([IO.Path]::GetFullPath($reportPath), ($reportHtml -join [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
Write-Output ('Zapisano prywatny raport: ' + [IO.Path]::GetFullPath($reportPath))
