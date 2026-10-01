param([switch]$CopyPassword)
$ErrorActionPreference = 'Stop'
$credentialFile = Join-Path $PSScriptRoot '..\.private\stats-admin.credential.xml'
if (-not (Test-Path -LiteralPath $credentialFile)) { throw 'Brak lokalnego pliku z hasłem panelu.' }
$credential = Import-Clixml -LiteralPath $credentialFile
Write-Output ('Panel: https://metin2bazar.pl/admin/stats')
Write-Output ('Login: ' + $credential.UserName)
if ($CopyPassword) {
    Set-Clipboard -Value $credential.GetNetworkCredential().Password
    Write-Output 'Hasło skopiowano do schowka. Wklej je w oknie logowania; po użyciu wyczyść schowek poleceniem Set-Clipboard -Value "".'
} else {
    Write-Output 'Aby skopiować hasło: .\ops\get-stats-login.ps1 -CopyPassword'
}
