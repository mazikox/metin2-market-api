# Prywatny raport użycia

Raport na żądanie z istniejących logów Caddy. Nie dodaje skryptów, identyfikatorów ani zdarzeń do strony, nie zmienia zgody Umami i nie uruchamia publicznego panelu. IP są używane tylko w pamięci procesu na VPS do grupowania wpisów; lokalny HTML nie zawiera adresów ani zapytań. Oznaczenia odwiedzających są lokalne dla wygenerowanego raportu, nie trwałe identyfikatory.

Uruchomienie z katalogu E:\metin-market-api:

```powershell
& ./ops/show-usage-report.ps1
```

Wynik: usage-report.html, poza Git. Klucz SSH jest tym samym kluczem, którego używano do wdrożeń; skrypt nie tworzy dodatkowych uprawnień ani poświadczeń. Zdalny procesor jest w /home/debian/metin2bazar-preparation/usage_report.py.

Główny licznik pokazuje różne IP, które wykonały udane żądanie wyników katalogu. Tabela grupuje liczbę pobrań i szacowanych wyszukiwań na IP w ostatnich 7 dniach. Bez rozpoznawania osoby lub łączenia z innymi danymi. Samo otwarcie strony i automatyczne pobranie startowe nie potwierdzają ręcznego użycia. Boty rozpoznawane po nagłówku mogą zmienić ten nagłówek i obejść filtr.

Wyszukiwanie: pierwsza strona z query/vnum, bez pierwszego automatycznego zapytania Pandory dla danego IP i bez duplikatu tej samej frazy w odstępie poniżej dwóch sekund. Odświeżenia, ponowienia i współdzielenie IP ograniczają dokładność. Podpowiedzi, statystyki cen i paginacja są wykluczone. To nie rejestr wszystkich kliknięć „Szukaj”. Przed wprowadzeniem proxy /backend wcześniejsze wywołania API nie trafiały do tego źródła logów; historyczne zera nie dowodzą braku wyszukiwań.

Cztery testy procesora przeszły na VPS (Python z systemową bazą stref czasowych). Lokalny Python Windows nie ma pakietu tzdata; procesor uruchamia się na VPS, a lokalny skrypt wyłącznie pobiera gotowy raport.

Odczyt diagnostyczny nie stanowi rozstrzygnięcia podstawy dla stałego monitorowania zachowania po IP. Nie deklarować, że każde śledzenie po IP jest zwolnione ze zgody. Wytyczne EROD 2/2023, pkt 54–56, wymagają oceny konkretnego rozwiązania. Przed stałym panelem/profilami trzeba ustalić podstawę, zakres, retencję raportów i zaktualizować informację o celu przetwarzania. W obecnym zadaniu nie wdrażano trwałych profili, harmonogramu tworzenia raportów ani publicznej usługi.
