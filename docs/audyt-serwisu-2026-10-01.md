# Audyt Metin Market — 1 października 2026

**Wniosek: właściwa strona `https://metin2market.mazikox.pl` działa publicznie przez poprawne HTTPS i pobiera dane z API. Może być indeksowana, ale ma konkretne braki SEO, informacji dla użytkowników i bezpieczeństwa. Przed szerszym udostępnianiem należy ograniczyć ryzyko przeciążenia, zaktualizować zależności i poprawić wskazane braki.**

## Zakres i granice oceny

Sprawdzono kod `E:/metin-market-api` (HEAD `ad3dc69`) i `E:/metin-market-web` (HEAD `e0639ba`), konfigurację Docker i wdrożeń, publiczną stronę `metin2market.mazikox.pl`, API `api.mazikox.pl`, skrypt analityczny oraz lokalny frontend w przeglądarce. API badano około 11:21–11:24, a właściwą domenę strony około 11:36–11:37 czasu Europe/Warsaw. Wyniki dostępności opisują stan w tych chwilach.

Po doprecyzowaniu przez użytkownika zakres skorygowano: `metin-market-web` jest repozytorium frontendu, a właściwą publiczną stroną jest `metin2market.mazikox.pl`. Wstępne błędy TLS `mazikox.pl` i brak DNS `web.mazikox.pl` dotyczą innych adresów i nie stanowią usterek dostępności ocenianej strony.

Przeprowadzono zwykłe zapytania HTTP, małe odrzucane żądania importu bez tokenu, testy istniejącego kodu oraz sprawdzenie zależności. Nie wykonywano testów obciążeniowych ani skutecznych importów do produkcji. Nie zmieniono kodu aplikacji, DNS, konfiguracji VPS ani produkcyjnych danych.

Nie sprawdzono konfiguracji Caddy na VPS, reguł zapory, uprawnień produkcyjnej bazy, rzeczywistych backupów, zabezpieczeń SSH, historii sekretów w Git ani Google Search Console. Nie potwierdzono zgodności lokalnego HEAD z dokładnym obrazem uruchomionym na VPS. Wnioski wynikające wyłącznie z konfiguracji repozytorium są wyraźnie oznaczone.

## Wyniki publicznych sprawdzeń

| Sprawdzenie | Wynik | Znaczenie |
| --- | --- | --- |
| HTTPS `metin2market.mazikox.pl` | 200; poprawne TLS w Node i przeglądarce | Właściwa strona jest publicznie dostępna. |
| HTTP `metin2market.mazikox.pl` | 308 do HTTPS | Poprawne przekierowanie. |
| `/robots.txt` właściwej strony | 200, `text/html`, identyczny HTML jak `/` | Brak prawdziwego pliku robots; zbyt szeroki fallback SPA. |
| `/sitemap.xml` właściwej strony | 200, `text/html`, identyczny HTML jak `/` | Brak prawdziwej mapy XML. |
| Nieistniejąca ścieżka właściwej strony | 200 z HTML strony głównej | Niepoprawna obsługa nieistniejących adresów. |
| HTTPS `api.mazikox.pl` | Poprawne połączenie; `/` zwraca 404 | Brak strony głównej API sam w sobie nie jest usterką. |
| HTTP `api.mazikox.pl` | 308 do HTTPS | Poprawne przekierowanie. |
| Oferty Pandora | 200; `totalElements: 22815`; pierwsza oferta z datą 2026-09-15 | API działa; wartość opisuje wynik tego zapytania, nie liczbę wszystkich historycznych rekordów. |
| Oferty Elder | 200; `totalElements: 30192`; pierwsza oferta z datą 2026-09-28 | API działa. |
| Oferty Beavium | 200; `totalElements: 0` | Endpoint działa, lecz brak opublikowanych ofert w odpowiedzi. |
| Obcy Origin w GET API | 403 | CORS odrzuca obcą domenę w sprawdzonym żądaniu. |
| Origin `metin2market.mazikox.pl` | 200 z odpowiednim `Access-Control-Allow-Origin` | CORS pozwala właściwemu frontendowi korzystać z API. |
| `size=101`, `page=-1` | 400 | Walidacja paginacji działa. |
| Nieznany serwer | 404 | Nie ma swobodnego wyboru schematu bazy przez URL. |
| Poprawny strukturalnie, pusty import Elder bez tokenu | 401 | Zapis jest chroniony tokenem. |
| Import `{}` lub niepoprawny JSON bez tokenu | 400 | Parsowanie/walidacja następuje przed kontrolą tokenu w kontrolerze. |
| `/actuator/health`, `/v3/api-docs` | 404 | W tych miejscach nie stwierdzono publicznego panelu diagnostycznego. |
| `api.mazikox.pl/robots.txt` | 404 | Nie ma pliku pod tym adresem; samo 404 nie blokuje indeksowania. |

Ocenę właściwej domeny oparto na kliencie Node z domyślną weryfikacją certyfikatu oraz na działającej stronie w przeglądarce. Błąd narzędzia web przy próbie otwarcia strony nie został potraktowany jako dowód jej niedostępności, ponieważ te dwa sprawdzenia potwierdziły działanie.

## Problemy i zalecenia według priorytetu

### 1. Ważny dla SEO: fallback SPA obejmuje nieistniejące pliki i adresy

Pod `https://metin2market.mazikox.pl/robots.txt`, `/sitemap.xml` i `/audit-nonexistent-page-20261001` serwer zwrócił identyczny, 884-bajtowy HTML strony głównej ze statusem 200. Frontend nie ma routingu, który rozpoznawałby te adresy jako inne strony. Nie jest to dowód obecnego wykluczenia z Google, lecz błąd obsługi URL oraz źródło duplikatów lub soft 404.

**Działanie:** dodać prawdziwe pliki robots i sitemap; skonfigurować fallback wyłącznie dla faktycznych tras aplikacji. Dla pozostałych ścieżek i nieistniejących zasobów zwracać 404. Ustawić canonical i świadomą politykę parametrów URL.

**Odbiór:** `/` oraz rzeczywiste widoki serwerów działają; robots jest tekstem, sitemap poprawnym XML, a nieistniejąca ścieżka zwraca 404. Nie trzeba tworzyć subdomeny `web`, aby udostępniać ten frontend.

### 2. Wysoki: deserializacja importu przed uwierzytelnieniem

W [ImportController.java](E:/metin-market-api/src/main/java/com/mazikox/metin_market_api/ingestion/api/ImportController.java:44) token jest sprawdzany wewnątrz metody przyjmującej `@Valid @RequestBody`. Spring wcześniej odczytuje, deserializuje i waliduje JSON. Publiczne sprawdzenia 400/401 potwierdzają tę kolejność. [ImportRequest.java](E:/metin-market-api/src/main/java/com/mazikox/metin_market_api/ingestion/api/ImportRequest.java:12) nie ogranicza liczby obserwacji, ofert i elementów zagnieżdżonych przez `@Size`.

To nie jest wykazane obejście autoryzacji zapisu. Jest to możliwość zużywania zasobów przed odrzuceniem nieuprawnionego żądania. Nie wysyłano dużych payloadów do produkcji.

**Działanie:** sprawdzać token w filtrze lub warstwie bezpieczeństwa przed odczytem body; ograniczyć całkowity rozmiar żądania w proxy i aplikacji; ustalić limity tablic i tekstów według rzeczywistych partii skanera. Dodatkowo ograniczyć częstotliwość i równoległość importów. Rozważyć dostęp do `/internal/**` przez prywatną sieć lub z adresów skanerów.

### 3. Wysoki: brak ograniczeń kosztu publicznych zapytań w kodzie

[ItemSearchController.java](E:/metin-market-api/src/main/java/com/mazikox/metin_market_api/market/api/ItemSearchController.java:35) ogranicza stronę do 100 rekordów i liczbę VNUM do 100, lecz nie długość `query` ani głębokość paginacji. Repozytorium nie zawiera limitowania częstotliwości zapytań ani skonfigurowanego limitu czasu wykonywania SQL.

[JdbcMarketRepository.java](E:/metin-market-api/src/main/java/com/mazikox/metin_market_api/market/infrastructure/jdbc/JdbcMarketRepository.java:173) wykonuje podpowiedzi na `SELECT DISTINCT` z historycznego `shop_listing`; jedna podpowiedź uruchamia liczenie, wyszukiwanie rodzin i wyszukiwanie przedmiotów. Wyszukiwanie ofert agreguje dopasowane dane, zanim zwróci małą stronę. Zwykły indeks `lower(item_name)` nie odpowiada bezpośrednio wyrażeniu `unaccent(lower(...)) LIKE '%...%'`.

**Działanie:** dodać limity na poziomie proxy/API, maksymalną długość frazy, timeout SQL, cache podpowiedzi/statystyk i osobny katalog przedmiotów. Zweryfikować plan przez `EXPLAIN ANALYZE` na kopii bazy i dobrać indeksy do rzeczywistych zapytań. Timeout fetch w przeglądarce nie zastępuje timeoutu pracy bazy.

Nie potwierdzono, czy Caddy lub inna infrastruktura wdraża już rate limiting. Brak nagłówka limitów nie dowodzi jego braku. Zalecenia odpowiadają zagrożeniu opisanemu przez [OWASP API4](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/).

### 4. Ważny: zależności z opublikowanymi podatnościami

Sprawdzono 59 rozwiązywanych przez Maven zależności runtime w OSV. Wynik zawiera 8 dopasowań advisory do wersji bibliotek:

| Biblioteka | Wersja | Dopasowania | Ocena w kontekście kodu |
| --- | --- | --- | --- |
| Tomcat embed core | 11.0.24 | CVE-2026-65182, CVE-2026-65905, CVE-2026-68525 | Dotyczą reguł bezpieczeństwa kontenera lub FORM/DIGEST. Nie stwierdzono używania tych mechanizmów do ochrony importu. |
| Jackson databind | 3.1.5 | CVE-2026-91777, CVE-2026-83557, CVE-2026-68497, CVE-2026-91776, CVE-2026-19032 | Wymagają m.in. określonego polimorfizmu, tożsamości obiektów lub pól XML/Path. Nie znaleziono tych typów ani adnotacji w modelu importu. |

W bazach część z tych podatności ma kategorię high/critical. **Nie jest to dowód, że w obecnym serwisie da się wykonać odpowiadający im atak.** Jest to potwierdzenie używania wersji objętych advisory.

W [dokumentacji bezpieczeństwa Tomcata](https://tomcat.apache.org/security-11.html) są również nowsze poprawki w 11.0.26, w tym zależne od protokołu i konfiguracji. Zalecany punkt wyjścia aktualizacji to co najmniej 11.0.26, z weryfikacją zgodności całego zestawu Spring Boot. Dla wymienionych advisory Jacksona poprawki w gałęzi 3.1 obejmuje 3.1.7; potwierdza to np. [advisory producenta dotyczące forward references](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24). Aktualizować spójnie przez obsługiwany zestaw zależności/BOM i ponownie uruchomić testy.

`npm audit` zgłosił 3 podatne pakiety: Vite 5.4.21 (high), esbuild i pośrednio plugin React (moderate). `npm audit --omit=dev` zgłosił 0 podatności. Są to głównie ryzyka serwera deweloperskiego/narzędzi, a wdrożenie kopiuje statyczne pliki `dist`. Nie wykazano podatności statycznej strony przez samą obecność Vite. Zaktualizować narzędzia; nie wystawiać `vite dev` ani `vite preview` jako serwera produkcyjnego. Zobacz [advisory Vite](https://github.com/vitejs/vite/security/advisories/GHSA-fx2h-pf6j-xcff) i [instrukcję wdrażania Vite](https://vite.dev/guide/static-deploy).

Pełne wyniki lokalne: `target/audit-runtime-dependencies.txt`, `target/audit-osv-runtime-results.json`, `target/audit-osv-advisories.json`. To skan pakietów, nie systemu operacyjnego ani obrazów produkcyjnych.

### 5. Średni: parametr `api` pozwala podmienić źródło danych w produkcji

[api.ts](E:/metin-market-web/src/api.ts:4) odczytuje dowolny adres z `?api=...` także w buildzie produkcyjnym. Potwierdzono tę logikę również w publicznym pliku `/assets/index-Dpn-QISE.js`. Link pod oficjalną domeną może skłonić frontend do pobierania ofert i statystyk z serwera osoby trzeciej, jeśli tamten serwer pozwoli na CORS. [servers.ts](E:/metin-market-web/src/servers.ts:14) zachowuje ten parametr podczas zmiany serwera.

Możliwe skutki to fałszywe ceny/oferty, przekazanie zapytań wyszukiwania innemu operatorowi i awaria interfejsu po wadliwej odpowiedzi. Nie wykazano wykonania JavaScript z danych: React standardowo wyświetla tekst z escapowaniem. To nie jest SSRF backendu.

**Działanie:** dopuścić override wyłącznie lokalnie w DEV; produkcyjnie korzystać z ustalonego API. Dodać walidację struktury odpowiedzi, jeśli dane mogą pochodzić z różnych źródeł.

### 6. Ważny przed publikacją: informacje o prywatności i operatorze

W stopce i treści frontendu brak polityki prywatności, identyfikacji operatora oraz kontaktu do zgłoszeń. [index.html](E:/metin-market-web/index.html:12) automatycznie ładuje analitykę z `analytics.mazikox.pl`. Pobrany skrypt identyfikuje się mechanizmami Umami i zbiera m.in. adres strony, referrer, tytuł, język i rozmiar ekranu. W tagu nie ustawiono opcji wyłączania query string ani respektowania DNT. Ulubione zapisują się w localStorage.

Nie ustalono konfiguracji serwera analitycznego, przetwarzania IP, retencji logów ani wszystkich operacji na storage. Nie można z tego wyprowadzić automatycznego wniosku, że zawsze potrzebny jest baner cookies lub zgoda na każdą funkcję. Trzeba ocenić faktyczne przetwarzanie i zasady dostępu do urządzenia; sam brak cookies nie zamyka tej oceny.

**Działanie:** opisać administratora i kontakt, cele i podstawy przetwarzania, analitykę i logi, retencję, odbiorców, prawa użytkownika oraz localStorage. Gdy przetwarzane są dane osobowe, obowiązki informacyjne wynikają z [art. 13–14 RODO](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32016R0679). Zweryfikować też charakter nicków/ownerName w danych rynku i ewentualne obowiązki wobec graczy. Regulamin dostosować do rzeczywiście świadczonej usługi; nie zakładać, że każdy katalog musi mieć rozbudowany regulamin sklepu.

Repozytorium zawiera liczne grafiki przedmiotów, ale nie wykazano dokumentacji prawa do ich publicznego wykorzystania. Należy zweryfikować pochodzenie i warunki użycia grafik, nazw i danych skanera oraz jasno opisać nieoficjalny charakter projektu. Audyt nie rozstrzyga, że doszło do naruszenia praw.

### 7. Indeksowanie: możliwe już teraz, lecz obecnie słabe SEO

Publiczny HTML oraz nagłówki `metin2market.mazikox.pl` nie zawierały `noindex` ani `X-Robots-Tag`; strona zwróciła 200 i wyrenderowała treść oraz oferty w przeglądarce. Nie wykazano ogólnej blokady indeksowania. Nie sprawdzono, czy Google ma już stronę w indeksie; brak wyniku w pojedynczym wyszukiwaniu `site:` nie rozstrzyga tej kwestii.

Google potrafi renderować JavaScript, więc React SPA może być indeksowana. Warunkami są dostępność, poprawna odpowiedź oraz treść możliwa do zindeksowania; nie gwarantują one dodania strony do indeksu. [Wymagania Google](https://developers.google.com/search/docs/essentials/technical), [JavaScript SEO](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics).

Stwierdzone braki w repozytorium i publicznym HTML:

- Brak `robots.txt`, `sitemap.xml`, canonical oraz metadanych Open Graph. Brak robots/sitemap sam w sobie nie zabrania indeksowania.
- Początkowy HTML zawiera pusty `#root`; treść i oferty wymagają wykonania JS oraz udanego fetch API.
- Ogólny tytuł początkowy „Metin Market — dark catalog” i mało informacyjny opis.
- Serwery wybiera się przez `select`; brak zwykłych linków HTML do ich widoków. Robot nie odkrywa ich tak łatwo jak linków `<a href>`.
- Wyszukiwanie, strona wyników i szczegóły przedmiotu nie mają własnych trwałych adresów. Robot może odkryć stronę główną, ale nie otrzymuje katalogu adresów poszczególnych przedmiotów.

**Działanie:** ustalić domenę kanoniczną, poprawić title/description, dodać linki do serwerów, sitemap dla rzeczywistych publicznych podstron, spójne canonical i świadomą politykę parametrów. Jeśli celem jest ruch na frazy przedmiotów, wprowadzić trwałe strony przedmiotów/rodzin oraz SSR lub prerender istotnej treści. Nie indeksować masowo każdej kombinacji filtrów. API można wyłączyć z indeksowania przez `X-Robots-Tag: noindex`, jeśli JSON nie ma trafiać do wyszukiwarki; robots.txt nie jest zabezpieczeniem dostępu.

Po poprawkach sprawdzić `/robots.txt`, `/sitemap.xml`, nagłówki `X-Robots-Tag`, kody prawdziwych i nieistniejących stron oraz renderowanie w Google Search Console. HTTPS właściwej domeny już działa.

### 8. Średni: polityka nagłówków bezpieczeństwa

Publiczne odpowiedzi JSON API nie zawierały `Strict-Transport-Security`, `X-Content-Type-Options` ani polityki indeksowania. HTML właściwej strony również nie zawierał HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy ani Permissions-Policy; nie znaleziono CSP w znaczniku meta. CSP otrzymana ze skryptem analitycznym nie zastępuje CSP dokumentu strony.

**Działanie:** dodać przemyślane HSTS; `includeSubDomains` dopiero po sprawdzeniu wszystkich używanych subdomen. Dla API ustawić `nosniff` i ewentualne `X-Robots-Tag`. Dla HTML wdrożyć CSP, `frame-ancestors`, Referrer-Policy i Permissions-Policy. CSP musi uwzględniać API, analitykę i style używane przez frontend; wdrożenie polityki bez tej weryfikacji może zepsuć stronę.

Publiczny plik JS z hashem w nazwie nie miał nagłówka Cache-Control ani kompresji w odpowiedzi na `Accept-Encoding: gzip`. Zwrócił około 170 kB bez kompresji. Dodać długi cache dla wersjonowanych zasobów i kompresję, zachowując krótkie odświeżanie HTML. To poprawa wydajności, nie samodzielna podatność.

### 9. Średni: zbyt szerokie uprawnienia bazy według Compose

[compose.yaml](E:/metin-market-api/compose.yaml:3) używa `metin_market` jako `POSTGRES_USER` i tego samego konta do połączeń aplikacji. Przy inicjalizacji oficjalnego obrazu ten użytkownik jest tworzony jako superuser — opisuje to [dokumentacja obrazu PostgreSQL](https://hub.docker.com/_/postgres). Stan już istniejącej produkcyjnej bazy może być inny i nie był sprawdzony.

**Działanie:** zweryfikować produkcyjne role; oddzielić konto migracji od konta aplikacji. Nadać aplikacji tylko potrzebne prawa do odczytu i importu w odpowiednich schematach. Oddzielne schematy z jednym superuserem nie dają izolacji uprawnień po kompromitacji procesu.

### 10. Średni: niepełna odporność operacyjna i wdrożenia

W Compose nie ma polityki restartu usług, limitów pamięci/CPU ani healthcheck API. Wolumen zapewnia trwałość bazy, ale nie jest kopią zapasową. W repo istnieje osobny skrypt z backupem i rollbackiem migracji, jednak bieżący workflow backendu go nie wywołuje. README opisuje bramkę `confirm_multiserver_migration`, której nie ma w obecnym workflow.

Frontend jest publikowany przez usunięcie dotychczasowych plików i skopiowanie nowych, co tworzy okno niedostępności. Workflow frontendu wykonuje build, ale pomija istniejące `npm test`. Oba wdrożenia pobierają klucz hosta SSH przez `ssh-keyscan` w trakcie tego samego połączenia, zamiast weryfikować wcześniej zaufany fingerprint.

**Działanie:** restart usług, monitoring dostępności i importów, regularne backupy poza VPS i próbne odtworzenie, atomowe przełączanie wydań frontendu, możliwość rollbacku backendu, testy frontendu w CI i przypięty zaufany klucz hosta. Uzgodnić dokumentację z faktycznym procesem. Nie stwierdzono, że na VPS nie ma osobnych backupów czy monitoringu — to pozostaje do sprawdzenia.

### 11. Średni: sortowanie i filtr mapy dotyczą tylko 8 pobranych wyników

[App.tsx](E:/metin-market-web/src/App.tsx:240) filtruje i sortuje `rawItems`, czyli bieżącą stronę o rozmiarze 8. „Najdroższe / szt.” nie wyszukuje najdroższych ofert całego wyniku. Filtr mapy może ukryć wszystkie 8 pozycji, mimo że kolejne strony mają pasujące oferty. Licznik nadal pokazuje liczbę przed filtrowaniem, a poprzedni filtr mapy utrzymuje się po zmianie zapytania.

**Działanie:** przenieść sortowanie i filtr mapy do API przed paginacją albo wyraźnie opisać ich zakres jako bieżącą stronę; resetować niepasujące filtry.

### 12. Średni: zbiorcze statystyki cen mogą wprowadzać w błąd

[App.tsx](E:/metin-market-web/src/App.tsx:153) uśrednia średnie dla VNUM bez wag, wybiera środkową medianę z median i sumuje liczbę sklepów między wariantami. Ta sama placówka może występować w kilku wariantach. Mediana median nie jest medianą wszystkich cen; średnia średnich nie jest średnią całego zbioru. Przy parzystej liczbie median wybierany jest jeden element. W razie błędu endpointu statystyk UI bez oznaczenia przechodzi na statystyki 8 aktualnie wyświetlanych ofert.

**Działanie:** pokazywać statystyki osobno dla VNUM albo liczyć uzgodnione statystyki zbiorcze na backendzie; podać zakres i sposób liczenia, a fallback oznaczyć jako statystyki bieżącej strony.

### 13. Średni: dostępność klawiaturą

Lokalnie potwierdzono: po otwarciu szczegółów fokus zostaje na przycisku poza dialogiem, a Tab przechodzi do następnej oferty w tle. `aria-modal=true` nie zastępuje przeniesienia i ograniczenia fokusu. W podpowiedziach `role=listbox` otacza zwykłe przyciski, bez poprawnego zestawu combobox/option i obsługi strzałek.

**Działanie:** przenosić fokus do panelu, ograniczyć tabulację do jego kontrolek, wyłączyć interakcję z tłem i przywrócić fokus po zamknięciu. Wdrożyć spójny wzorzec klawiatury wyszukiwarki. Escape już zamyka panel. Lokalny widok przy szerokości 390 px mieścił się bez poziomego overflow; nie jest to pełny audyt WCAG ani wszystkich urządzeń.

### 14. Użytkownicy potrzebują wyraźniejszego statusu świeżości danych

Próbka Pandory pokazywała datę 15 września, Elder 28 września, a Beavium brak ofert. Zielone „API online” opisuje dostępność endpointu, nie aktualność skanu. API skraca `observed_at` do samej daty, więc użytkownik nie dostaje dokładnej godziny.

**Działanie:** pokazać moment ostatniego opublikowanego skanu dla każdego serwera, wiek danych, informację o braku pierwszego skanu i status skanera. Ustalić oczekiwaną częstotliwość aktualizacji i alerty zależne od niej. Ostrzeżenie, że oferta może już nie istnieć, jest już obecne i jest właściwe.

## Co już działa dobrze

- Import wymaga osobnych, niepustych i różnych tokenów dla Pandory, Elder i Beavium; porównanie używa `MessageDigest.isEqual`.
- Zapytania SQL używają parametrów; nazwy serwerów/schematów są wybrane z enum, a nie bezpośrednio z treści JSON.
- Import jest transakcyjny, deduplikuje batch i obserwacje oraz odrzuca konflikt treści.
- API ma walidację paginacji i liczby VNUM; publiczne sprawdzenia potwierdziły jej działanie.
- CORS jest ograniczony do skonfigurowanych originów i GET dla `/api/**`. CORS nie zastępuje uwierzytelnienia i nie powstrzymuje klienta pozaprzeglądarkowego.
- Według Compose porty API i bazy wiążą się z `127.0.0.1`; proces Java w obrazie działa jako użytkownik non-root.
- Kod nie używa `dangerouslySetInnerHTML` do wyświetlania ofert; znalezione treści API trafiają do JSX jako tekst.
- Frontend ma obsługę błędów, timeout całego pobierania JSON, anulowanie poprzedniego wyszukiwania i ponowienie zapytania.
- Są etykiety kontrolek, semantyczne nagłówki, teksty alternatywne oraz obsługa preferencji ograniczenia animacji.

## Weryfikacja wykonana podczas audytu

| Weryfikacja | Wynik |
| --- | --- |
| `npm test` | 8/8 testów przeszło. |
| `npm run build` | Sukces; JS około 170 kB / 55 kB gzip, CSS około 20 kB / 5 kB gzip. |
| Testy backendu bez bazy | 19/19 przeszło na Java 26. |
| Testy integracyjne backendu | 5/5 przeszło na osobnej bazie PostgreSQL uruchomionej przez Testcontainers. |
| npm audit | 3 podatne pakiety narzędziowe; 0 przy `--omit=dev`. |
| OSV dla zależności Maven runtime | 59 pakietów sprawdzonych; 8 advisory dla 2 pakietów. |
| Lokalny frontend | Działa i pobiera produkcyjne dane przez proxy; sprawdzono szczegóły, fokus i układ 390 px. |
| Publiczny frontend Metin2Market | HTTPS i odpowiedź 200; przeglądarka wyrenderowała oferty; CORS dla właściwej domeny działa. Potwierdzono błędny fallback robots/sitemap/nieistniejącej ścieżki i brak wskazanych nagłówków. |

Pomyślne testy nie rozstrzygają bezpieczeństwa infrastruktury. Brak potwierdzonego SQL injection lub obejścia tokenu w tym zakresie nie jest gwarancją braku wszystkich podatności.

## Kolejność doprowadzenia serwisu do gotowości

1. Uwierzytelniać import przed body; ograniczyć wielkość żądań, częstotliwość, czas zapytań i zasoby.
2. Zaktualizować zależności i sprawdzić produkcyjne role bazy.
3. Dodać nagłówki bezpieczeństwa oraz poprawić odpowiedzi dla nieistniejących URL.
4. Uzupełnić informacje o operatorze, prywatności, analityce i pochodzeniu zasobów.
5. Naprawić zakres filtrów/statystyk oraz obsługę klawiatury i świeżości danych.
6. Uzupełnić SEO, prawdziwe robots i sitemap; sprawdzić renderowanie w Search Console.
7. Potwierdzić backup z odtworzeniem, restart, monitoring i bezpieczne wdrożenia.

**Odpowiedź na trzy pytania:** strona już działa publicznie, ale nie ma jeszcze wszystkich potrzebnych elementów; może być indeksowana już teraz, choć SEO i obsługa URL wymagają poprawy; zabezpieczenia podstawowe istnieją, lecz audyt wskazuje konkretne ryzyka wymagające usunięcia i nie pozwala uznać całej infrastruktury za zweryfikowaną bezpieczną produkcyjnie.
