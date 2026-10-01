# Informacja o prywatności — Metin2 Bazar

Stan: 1 października 2026. Treść i mechanizm zgody opublikowane 1 października 2026 na metin2bazar.pl i lustrze metin2market.mazikox.pl. Źródłem tekstu jest E:\metin-market-web\content\pages.json; generator tworzy podstronę /dane-w-przegladarce/ dostępną ze stopki.

## Zakres informacji

Siedem krótkich sekcji: działanie katalogu, opcjonalne statystyki, ulubione, dane z gry, hosting i wiadomości, prawa, administrator i kontakt. Administrator: Krystian Mazurek, krystianmaz0101@gmail.com. Kontakt jest w wyraźnie oznaczonej sekcji details dostępnej ze spisu treści. Zwinięcie ogranicza ekspozycję wizualną; dane są publiczne w HTML.

Nie opisujemy kont, zakupów, newslettera ani reklam, których serwis nie ma. Nie twierdzimy, że wszystkie dane są anonimowe. IP występuje w logach. Produkcyjna kontrola baz wykazała brak niepustych owner_name; tekst nie deklaruje zbierania nicków. Nazwy sklepów mogą wymagać obsługi zgłoszenia, jeżeli identyfikują osobę.

## Podstawy i minimalizacja

- Działanie katalogu i logi: art. 6 ust. 1 lit. f RODO. Cel: dostępność katalogu, diagnostyka i przeciwdziałanie nadużyciom. Dane techniczne są potrzebne do obsługi połączenia i analizy incydentów; nie wykorzystujemy ich do marketingu. Ograniczony dostęp do VPS oraz retencja do 90 dni ograniczają ingerencję. Sprzeciw wymaga indywidualnego rozpatrzenia. Przegląd logowania powinien usuwać niepotrzebne pola, jeżeli późniejszy audyt wykaże ich nadmiar.
- Statystyki: art. 6 ust. 1 lit. a RODO. Przygotowany frontend nie ładuje Umami przed zgodą. Wybór i czas zapisuje lokalnie na 365 dni, osobno dla każdej domeny/urządzenia. Wycofanie zgody przeładowuje stronę, jeżeli tracker był uruchomiony; nie cofa zakończonych żądań. Odmowa i brak dostępu do pamięci nie włączają trackera. Inna domena i środowisko deweloperskie nie uruchamiają statystyk.
- Ulubione: lokalny zapis na żądanie użytkownika do usunięcia; brak synchronizacji i marketingu.
- Korespondencja: uzasadniony interes w udzieleniu odpowiedzi; realizacja praw osób — obowiązek prawny. Ręczne usuwanie po zakończeniu sprawy, dłuższe przechowywanie tylko przy konkretnej podstawie prawnej lub potrzebie rozstrzygnięcia roszczeń.
- Ewentualne dane osobowe w ofertach: źródło to publiczny rynek gry, cel porównywanie ofert. Publikowanie zwykłych cen, przedmiotów i lokalizacji gry zwykle nie identyfikuje osoby. Nie wolno traktować publicznej dostępności jako automatycznego zwolnienia; zgłoszenie identyfikowalności wymaga rozpatrzenia, a zbędne dane osoby należy usunąć lub zanonimizować.

## Retencja i dostawcy

Do 90 dni dla dziennika VPS oraz 12 miesięcy dla zdarzeń tej witryny Umami zostało wdrożone na VPS; szczegóły w retencja-danych.md. Nie usuwa to danych katalogu ani lokalnych ulubionych. Informacje sesji zachowują powiązanie z aktywnością z ostatnich 12 miesięcy.

OVHcloud zapewnia infrastrukturę. Umami jest samodzielnie hostowane, bez przesyłania wyników do usługi SaaS Umami. Gmail obsługuje korespondencję; opisano Google i możliwe transfery poza EOG oraz podlinkowano oficjalne mechanizmy transferów. Nie deklarujemy nieistniejącej umowy Google Workspace dla prywatnego Gmail.

Lokalizacja centrum danych, warunki powierzenia OVH i stan automatycznych backupów/snapshotów nie zostały potwierdzone w panelu klienta; użytkownik nie zna tych ustawień. Tekst nie zawiera obietnicy „dane nigdy nie opuszczają EOG” ani deklaracji usunięcia kopii w terminie, którego nie zweryfikowano. Przed uznaniem całości organizacyjnej za zamkniętą trzeba sprawdzić te ustawienia i objąć istniejące kopie okresem retencji. Brak potwierdzenia nie jest dowodem braku kopii.

## Weryfikacja i wdrożenie

Build TypeScript/Vite przeszedł. Sześć testów mechanizmu zgody obejmuje brak zgody, odmowę, udzielenie/cofnięcie, wygaśnięcie, inne domeny, niedostępną pamięć i zmianę w innej karcie. UI sprawdzono lokalnie. Testy: node --test scripts/privacy-preferences.test.mjs.

Nową treść i helper trzeba wdrożyć razem. Aktualnie publiczny stary frontend nie został w tej zmianie zaktualizowany i może nadal uruchamiać dotychczasową analitykę. Polityka przygotowana dla nowego wydania nie jest potwierdzeniem zgodności starego wdrożenia.

## Źródła i wzorce

- RODO, art. 12–14 i 6: https://eur-lex.europa.eu/legal-content/PL/TXT/?uri=CELEX:32016R0679
- EROD, technologie śledzenia także bez cookies: https://www.edpb.europa.eu/documents/guideline/guidelines-22023-on-technical-scope-of-art-53-of-eprivacy-directive_en
- Przykład krótkich sekcji, danych/celów/dostawców/retencji/praw: https://plausible.io/privacy
- Przykład metinowy, prywatność w §6: https://hlbot.net/rules.pdf
- Google, transfery i dostęp do opisu zabezpieczeń: https://policies.google.com/privacy/frameworks?hl=pl

Przykłady służą układowi i stylowi. Ich technologie i podstawy prawne nie zostały przeniesione automatycznie do naszego serwisu.
## Potwierdzenie kopii OVH — 1 października 2026

Zrzut panelu konkretnego VPS przesłany przez użytkownika potwierdza automatyczny backup codziennie o 01:30 UTC i jeden dostępny punkt z 1 października 2026 o 01:30. Oferta siedmiu dni dotyczy Premium, nie aktualnej konfiguracji. Brak zamontowanego punktu nie oznacza braku kopii. Do treści dodano krótką informację o odtwarzaniu po awarii i pozostawaniu usuniętych danych w ostatniej kopii do zastąpienia kolejną. Nie deklarujemy bezwarunkowego usunięcia po dokładnie 24 godzinach: harmonogram nie gwarantuje powodzenia następnego backupu.

To zastępuje wcześniejszą niewiedzę o automatycznym backupie OVH. Stan osobnych snapshotów, ewentualnych ręcznych kopii, lokalizacja centrum danych i warunki powierzenia nadal nie zostały potwierdzone.
## Publikacja

1 października 2026 opublikowano frontend wraz z mechanizmem zgody i informacją o kopii OVH. Przed publikacją przeszło 8 testów klienta API, 6 testów zgody i build. Poprzednie pliki zachowano w /var/www/metin2bazar-previous-20261001T131842Z oraz archiwum frontend-before-privacy w /home/debian/metin2bazar-preparation/.

Publicznie potwierdzono HTTP 200 dla strony, prywatności i API trzech rynków na obu domenach, noindex starego lustra i 301 www do nowej domeny. W przeglądarce produkcyjnej potwierdzono brak skryptu Umami przed zgodą, jego załadowanie po zgodzie oraz brak po cofnięciu i przeładowaniu. To zastępuje wcześniejsze uwagi o nieopublikowanej treści i dotychczasowej automatycznej analityce frontendu.