@echo off
setlocal DisableDelayedExpansion
title Pandora Market Updater
cd /d E:\metin-market-api

:menu
cls
echo ==========================================
echo        PANDORA MARKET - UPDATE MENU
echo ==========================================
echo.
echo [1] Pobierz SCANNER_TOKEN z VPS przez SSH
echo [2] Uruchom update_market.py
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
set "SCANNER_TOKEN="

for /f "usebackq delims=" %%T in (`ssh debian@146.59.63.158 "sed -n 's/^SCANNER_TOKEN=//p' /home/debian/metin2-market-api/.env"`) do set "SCANNER_TOKEN=%%T"

echo.
if defined SCANNER_TOKEN (
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
if not defined SCANNER_TOKEN (
    echo [BLAD] Token nie jest zaladowany.
    echo Najpierw wybierz opcje 1.
    echo.
    pause
    goto :menu
)

echo Uruchamiam update_market.py...
echo.
python .\update_market.py

echo.
echo Skrypt zakonczyl dzialanie.
pause
goto :menu

:end
endlocal
exit /b
