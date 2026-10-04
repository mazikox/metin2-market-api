@echo off
setlocal DisableDelayedExpansion
title Beavium Market Updater
cd /d E:\metin-market-api

:menu
cls
echo ==========================================
echo        BEAVIUM MARKET - UPDATE MENU
echo ==========================================
echo.
echo [1] Pobierz SCANNER_TOKEN_BEAVIUM z VPS przez SSH
echo [2] Uruchom update_market.py dla Beavium
echo [0] Wyjdz
echo.
choice /c 120 /n /m "Wybierz opcje: "

if errorlevel 3 goto :end
if errorlevel 2 goto :run
if errorlevel 1 goto :token

:token
cls
echo Pobieranie tokena z VPS...
echo Zostaniesz poproszony o haslo SSH.
echo.
set "SCANNER_TOKEN_BEAVIUM="

for /f "usebackq delims=" %%T in (`ssh -i "%USERPROFILE%\.ssh\mazikox_github_actions" debian@146.59.63.158 "sed -n 's/^SCANNER_TOKEN_BEAVIUM=//p' /home/debian/metin2-market-api/.env"`) do set "SCANNER_TOKEN_BEAVIUM=%%T"

echo.
if defined SCANNER_TOKEN_BEAVIUM (
    echo [OK] Token zostal zaladowany do tej sesji.
    echo Token NIE zostanie wyswietlony ani zapisany do pliku.
) else (
    echo [BLAD] Nie udalo sie pobrac tokena.
)

echo.
pause
goto :menu

:run
cls
if not defined SCANNER_TOKEN_BEAVIUM (
    echo [BLAD] Token nie jest zaladowany.
    echo Najpierw wybierz opcje 1 lub ustaw zmienna SCANNER_TOKEN_BEAVIUM.
    echo.
    pause
    goto :menu
)

echo Uruchamiam update_market.py dla Beavium...
echo.
python .\update_market.py --server beavium

echo.
echo Skrypt zakonczyl dzialanie.
pause
goto :menu

:end
endlocal
exit /b
