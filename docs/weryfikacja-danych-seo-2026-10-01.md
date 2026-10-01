# Weryfikacja danych i SEO — 1 października 2026

Zakres: frontend `E:\metin-market-web`, backend `E:\metin-market-api`, odczyt działającego VPS przez SSH, agregaty bazy Umami, konfiguracja Caddy i odpowiedzi publiczne HTTPS. Bez zmian usług i bez odczytu danych pojedynczych odwiedzających.

## Fakty dotyczące danych

| Warstwa | Potwierdzone przetwarzanie | Zapis i retencja |
| --- | --- | --- |
| Ulubione frontendu | Fraza wyszukiwania i VNUM, osobno dla serwerów | localStorage w przeglądarce; do usunięcia pozycji lub danych witryny |
| API wyszukiwania | Fraza/VNUM, serwer, paginacja | Brak własnego rejestru historii użytkownika, adresów e-mail lub kont odwiedzających w przejrzanym kodzie |
| Baza rynku | Oferty, bonusy, sklepy, `owner_name`, lokalizacja w grze i czas obserwacji | Kod importu zapisuje te pola; nick może stanowić dane osobowe, gdy umożliwia identyfikację osoby |
| Caddy | Rzeczywisty adres IP klienta, URI, nagłówki (w tym User-Agent), czas, status, rozmiar i czas odpowiedzi | Dla metin2market włączona dyrektywa `log`; wyjście do journald. Potwierdzono pola w logu domeny. Brak jawnego stałego okresu retencji w sprawdzonej konfiguracji |
| Umami 3.3.1 | Sesje, odsłony, adresy i query URL, referrer, przeglądarka, system, urządzenie, ekran, język, kraj/region/miasto | Oddzielny PostgreSQL na VPS; 42 sesje i 260 zdarzeń dla identyfikatora używanego przez stronę. Najstarsze rekordy: 13 września 2026. Nie znaleziono ustawienia retencji ani zadania czyszczącego w sprawdzonych cronach/timerach |

W sprawdzonych tabelach odsłon/sesji Umami nie ma kolumny surowego IP. Nie oznacza to braku przetwarzania IP w infrastrukturze: Caddy zapisuje je bez anonimizacji.

Nagrywanie sesji Umami: `recorder_enabled=false`; zero rekordów `session_replay`, `heatmap_event`, `event_data` i `session_data` dla tej witryny. Nie znaleziono własnych `distinct_id` dla sesji. Są to wyniki tej kontroli, a nie gwarancja przyszłej konfiguracji.

`DISABLE_TELEMETRY=1` w Umami nie wyłącza analityki odwiedzin witryny. W bazie jest 101 zdarzeń z query URL i 48 odsłon z localhost/127.0.0.1. Zapisana domena konfiguracji witryny nadal brzmi `pandora.mazikox.pl`, mimo używania identyfikatora na metin2market.

Brak wykrytego automatycznego czyszczenia nie dowodzi, że dane nigdy nie są usuwane ręcznie. Limit przestrzeni logów także nie jest ustalonym okresem retencji. Nie sprawdzano umowy z dostawcą VPS, jego ewentualnych logów i kopii poza systemem ani konfiguracji zewnętrznych kont.

Wniosek: nie można deklarować, że serwis nie przetwarza żadnych danych. Surowe IP w logach wymagają uwzględnienia w ocenie ochrony danych. Brak kont, cookies lub surowego IP w bazie analityki nie stanowi samodzielnego dowodu anonimowości całego serwisu. Informację o administratorze i kontakcie należy ustalić zgodnie z rzeczywistymi obowiązkami; nie wpisano fikcyjnych danych właściciela ani kontaktu.

## SEO: pliki lokalne a produkcja

Lokalny generator tworzy UTF-8 `robots.txt` i XML `sitemap.xml` w głównym katalogu. Robots nie blokuje zasobów JS/CSS ani treści i wskazuje pełny adres sitemapy. `Allow: /` jest dopuszczalne, choć przy braku blokad zbędne.

Sitemapa zawiera obecnie sześć unikalnych, pełnych adresów HTTPS: rynek Pandora, Elder, Beavium oraz trzy podstrony informacyjne. Nie zawiera fragmentów, 404, plików technicznych ani API. XML został sparsowany; zweryfikowano domenę, unikalność i odsyłacz z robots.

Nie dodano `priority` ani `changefreq`, które Google ignoruje. Nie wpisano sztucznego `lastmod` z datą każdego builda: wartość powinna odpowiadać istotnej zmianie treści. Dla aktualizowanych ofert ewentualny lastmod należy powiązać z publikacją skanu, nie zegarem wdrożenia.

Poprawiono wcześniejszą niespójność canonical: wspólny HTML nie deklaruje już głównego adresu jako canonical wszystkich widoków. React wstawia jeden canonical odpowiadający serwerowi. Podstrony statyczne mają canonical w źródłowym HTML. Google dopuszcza wstawianie canonical przez JS przy braku canonical w pierwotnym HTML; docelowo prerender/SSR stron rynku byłby mocniejszym rozwiązaniem dla robotów bez JS. Nie tworzono stron SEO dla każdego przedmiotu; wyszukiwanie nie ma jeszcze trwałych adresów takich stron.

Publiczna produkcja podczas kontroli:

| URL | Status | Content-Type | Wynik |
| --- | --- | --- | --- |
| /robots.txt | 200 | text/html; charset=utf-8 | HTML katalogu, 884 bajty |
| /sitemap.xml | 200 | text/html; charset=utf-8 | HTML katalogu, 884 bajty |
| /audyt-nieistniejacy-adres-20261001 | 200 | text/html; charset=utf-8 | HTML katalogu, 884 bajty |

Przyczyna: lokalne zmiany nie zostały opublikowane, a Caddy ma `try_files {path} /index.html`. Samo istnienie pliku 404.html nie powoduje zwracania statusu 404. Konfiguracja produkcyjna musi poprawnie serwować indeksy podstron i odróżniać brakujące adresy od istniejącej strony głównej. Robots i sitemapa powinny być zwracane jako właściwe pliki, nie fallback HTML.

Sitemapa jest wskazówką dla robotów, nie gwarancją indeksowania. Nie potwierdzono zgłoszenia sitemapy w Search Console ani rzeczywistego stanu indeksu Google. Nie używać robots jako zabezpieczenia API ani sposobu ukrywania danych.

## Weryfikacja zmian

- Produkcyjny build frontendu przeszedł.
- XML sitemapy: poprawne parsowanie, sześć unikalnych adresów HTTPS, zgodna domena i brak fragmentów.
- Robots wskazuje właściwą sitemapę.
- `git diff --check` bez błędów.
- Próba dodatkowej kontroli metadanych w przeglądarce zakończyła się timeoutem połączenia przeglądarki; brak potwierdzenia nowego canonical w działającym DOM. Kod i build zweryfikowano.
- Nie opublikowano plików ani nie zmieniono konfiguracji VPS, analityki lub logowania.

## Źródła

- [Google: tworzenie sitemapy](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap)
- [Google: robots.txt](https://developers.google.com/search/docs/crawling-indexing/robots/intro)
- [Google: JavaScript SEO, canonical i soft 404](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics)
- [Caddy: logowanie żądań](https://caddyserver.com/docs/caddyfile/directives/log)
- [RODO](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32016R0679), w szczególności art. 4, 5 i 13–14; ocena stosowania wymaga uwzględnienia faktycznego przetwarzania i identyfikowalności.

## Aktualizacja: decyzja o nieindeksowaniu obecnej domeny

Użytkownik 1 października 2026 wskazał, że obecny serwis na metin2market.mazikox.pl
ma nie być indeksowany. Indeksowanie będzie dotyczyć przyszłej osobnej domeny.
Poprzednie ustalenia o sitemapie opisują wcześniejszy wariant konfiguracji.

- Na VPS dodano wyłącznie w bloku metin2market.mazikox.pl nagłówek
  `X-Robots-Tag: noindex`. Wykonano kopię `/etc/caddy/Caddyfile`, walidację Caddy
  i reload. Odpowiedzi dla `/`, `/?server=elder` i `/jak-korzystac/` potwierdzają nagłówek.
- Kopia: `/etc/caddy/Caddyfile.before-market-noindex-20261001T102409Z`.
- Lokalnie `src/site.json` ma `indexable: false`. Build dodaje meta robots noindex
  do katalogu oraz wszystkich podstron; generator nie tworzy sitemapy dla tej domeny.
- Robots pozostawia crawling dozwolony, żeby robot mógł odczytać noindex.
- Build i kontrola artefaktów przeszły. Zmieniono konfigurację nagłówka na VPS;
  nowych plików frontendu nadal nie opublikowano.
- Noindex nie jest ograniczeniem dostępu. Usunięcie już indeksowanych adresów wymaga
  ponownego odczytu przez wyszukiwarkę. Nie potwierdzono obecnego stanu indeksu Google.

## Doprecyzowanie danych właścicieli sklepów

Po uwadze użytkownika sprawdzono agregaty produkcyjnych tabel shop_observation w schematach pandora, elder i beavium. Pandora: 4460 obserwacji, 0 niepustych owner_name, 85 niepustych shop_title. Elder: 1407 obserwacji, 0 niepustych owner_name, 1407 niepustych shop_title. Beavium: 0 obserwacji. Kod przewiduje pole owner_name, ale aktualna baza nie przechowuje w nim nicków. Wcześniejsze stwierdzenia o przechowywaniu nicków nie były potwierdzone stanem danych. Sama nazwa sklepu nie jest automatycznie daną osobową; znaczenie ma rzeczywista identyfikowalność osoby. Nie zmieniano danych ani usług VPS.
