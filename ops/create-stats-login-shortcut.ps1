$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'get-stats-login.ps1'
if (-not (Test-Path -LiteralPath $scriptPath)) { throw 'Brak skryptu kopiowania hasla.' }
$desktopPath = [Environment]::GetFolderPath('Desktop')
$shortcutPath = Join-Path $desktopPath 'Haslo panelu Metin2 Bazar.lnk'
if (Test-Path -LiteralPath $shortcutPath) {
    throw 'Skrot juz istnieje. Nie nadpisano go.'
}
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$shortcut.Arguments = '-NoProfile -STA -WindowStyle Hidden -File "' + $scriptPath + '" -CopyPassword -Notify'
$shortcut.WorkingDirectory = Split-Path $PSScriptRoot -Parent
$shortcut.WindowStyle = 7
$shortcut.Description = 'Kopiuje haslo panelu Metin2 Bazar. Haslo jest zaszyfrowane dla tego konta Windows.'
$shortcut.IconLocation = (Join-Path $env:SystemRoot 'System32\shell32.dll') + ',47'
$shortcut.Save()
Write-Output ('Utworzono skrot: ' + $shortcutPath)
