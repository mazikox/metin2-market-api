param([switch]$CopyPassword, [switch]$Notify)
$ErrorActionPreference = 'Stop'
try {
    $credentialFile = Join-Path $PSScriptRoot '..\.private\stats-admin.credential.xml'
    if (-not (Test-Path -LiteralPath $credentialFile)) { throw 'Nie znaleziono lokalnego pliku z haslem panelu.' }
    $credential = Import-Clixml -LiteralPath $credentialFile
    if ($CopyPassword) {
        Set-Clipboard -Value $credential.GetNetworkCredential().Password
        if ($Notify) {
            Add-Type -AssemblyName System.Windows.Forms
            [System.Windows.Forms.MessageBox]::Show("Haslo zostalo skopiowane do schowka.`n`nLogin: $($credential.UserName)`nPanel: https://metin2bazar.pl/admin/stats`n`nWklej haslo w oknie logowania (Ctrl+V).", 'Metin2 Bazar - haslo panelu') | Out-Null
        } else {
            Write-Output 'Haslo skopiowane. Wklej je w oknie logowania panelu (Ctrl+V).'
        }
    } else {
        Write-Output 'Panel: https://metin2bazar.pl/admin/stats'
        Write-Output ('Login: ' + $credential.UserName)
        Write-Output 'Kliknij skrot na pulpicie: Haslo panelu Metin2 Bazar.'
        Write-Output 'Lub uruchom: .\ops\get-stats-login.ps1 -CopyPassword'
    }
} catch {
    if ($Notify) {
        Add-Type -AssemblyName System.Windows.Forms
        [System.Windows.Forms.MessageBox]::Show('Nie udalo sie skopiowac hasla. Plik musi byc dostepny na tym komputerze i na koncie Windows, na ktorym zostal utworzony.', 'Metin2 Bazar - problem z haslem') | Out-Null
        exit 1
    }
    throw
}
