# Statystyki katalogu i prywatny panel

Panel `/admin/stats` oraz API `/backend/api/v1/admin/stats` wymagają Caddy basic_auth.
Chronione są także bezpośrednia ścieżka `/api/v1/admin/stats`, domena api.mazikox.pl
oraz lustro katalogu. Backend wymaga prywatnego tokena proxy i tożsamości admina,
które Caddy nadpisuje po uwierzytelnieniu. Publiczne proxy usuwa tożsamość admina.
Port API pozostaje na 127.0.0.1. Nie publikuj 8080 ani nie umieszczaj sekretów w Vite.
Konfiguracja używa `basicauth`, zgodnego z Caddy 2.6.2 obecnym na VPS oraz nowszymi wersjami.
W nowszej dokumentacji ta dyrektywa nazywa się `basic_auth` (https://caddyserver.com/docs/caddyfile/directives/basic_auth).

## Co liczymy

- Użytkownicy: przybliżona dzienna liczba różnych IP, które skutecznie pobrały oferty.
  Jeden adres może reprezentować wiele osób. Zmiana adresu tworzy kolejny unique.
  Globalny unique deduplikuje również aktywność na różnych serwerach.
- Pobrania: udane zapytania o oferty, w tym start katalogu i strony paginacji.
  Licznik opisuje odpowiedzi backendu; nie potwierdza odczytania całego body przez klienta.
- Wyszukiwania: submit, wybór podpowiedzi lub wyszukiwania z ulubionych,
  pierwsza strona z niepustą nazwą/VNUM. Startowe zapytanie nie jest wyszukiwaniem.
- Token żądania i osobny token akcji powstają tylko w pamięci komponentu React.
  Retry i StrictMode używają tych samych tokenów. Paginacja nie zwiększa wyszukiwań,
  także po powrocie na pierwszą stronę. Deduplikacja tokenów działa również przez północ.
- Suggestions, statystyki cen, HEAD, importy, healthchecki i zwykłe curl wdrożeniowe
  nie tworzą zdarzeń. Rozpoznane boty i prefetch są wykluczane przez User-Agent/Purpose;
  User-Agent nie jest zapisywany ani używany do identyfikacji. Bot podszywający się pod
  przeglądarkę i wysyłający znaczniki frontendowe może zawyżać przybliżone statystyki.
- Popularność: wyłącznie kanoniczne nazwy obecne w katalogu, VNUM lub dokładne
  dopasowanie nazwy. Warianty +0…+9 są grupowane. Fragmenty i nierozpoznane zapytania
  zwiększają wyszukiwania, ale nie ranking. Nie zapisujemy dowolnego tekstu użytkownika.

## Baza i prywatność

Osobny schemat `analytics`, z własną historią Flyway `db/analytics/V1__daily_analytics.sql`,
jest migrowany raz podczas startu API. Migrations rynków pozostają bez zmian.
`daily_totals` zawiera dzień, serwer, unique, searches, results.
`daily_items` zawiera dzień, serwer, nazwę katalogową i licznik, bez identyfikatora klienta.
`daily_visitors` zawiera tylko dzień, serwer i HMAC-SHA256(data + IP, ANALYTICS_SECRET).
Nie ma surowego IP ani relacji visitor → item. Daty są według Europe/Warsaw.
Pseudonimy są usuwane po dniu, w najbliższym godzinnym sprzątaniu (do około 26h z DST).
Osobne `request_dedup` zawiera tylko losowy token operacji, typ i czas przyjęcia,
bez klienta, serwera lub przedmiotu. Godzinne sprzątanie po 47h daje maksimum 48h.
API oraz niezależny timer VPS sprzątają te rekordy. Długoterminowo zostają agregaty.
W przypadku niedziałającej bazy/timera usuwanie nastąpi po przywróceniu działania.
Admin API zwraca wyłącznie agregaty, zakres 1–366 dni oraz Top 10/20.

Raw IP nadal może występować w technicznych logach Caddy (dotychczasowa retencja 90 dni);
nie importujemy logów do analytics. Nie używamy cookies, localStorage ani visitor_id.
Pełne snapshoty VPS mogą obejmować dzienne pseudonimy; obecna pojedyncza codzienna
kopia jest zastępowana następną. Dla ręcznych, długoterminowych pg_dump wyklucz dane
`analytics.daily_visitors` i `analytics.request_dedup`, np. `--exclude-table-data=analytics.daily_visitors`.
Nie rotuj sekretu HMAC w środku dnia, bo może to zawyżyć unique tego dnia.

## Logowanie bez ręcznego kopiowania tokenów

Tokeny są przeznaczone wyłącznie dla backendu i Caddy — nie wpisujesz ich w przeglądarce.
Hasło panelu jest lokalnie zapisane w `.private/stats-admin.credential.xml`, zaszyfrowane
Windows DPAPI dla konta, które je utworzyło; katalog ma ograniczone ACL i jest ignorowany przez Git.
Na VPS znajduje się tylko hash bcrypt. Aby skopiować hasło, z katalogu repo API uruchom:

```powershell
.\ops\get-stats-login.ps1 -CopyPassword
```

Najprościej kliknij dwukrotnie skrót na pulpicie **Haslo panelu Metin2 Bazar**.
Skopiuje hasło bez otwierania terminala i pokaże potwierdzenie. Skrót nie zawiera hasła.
Jeśli potrzebujesz utworzyć go ponownie, uruchom `ops/create-stats-login-shortcut.ps1`.
Skrypty mają UTF-8 z BOM i działają również w Windows PowerShell 5.1.

Otwórz `https://metin2bazar.pl/admin/stats`, podaj login `admin` i wklej hasło (Ctrl+V).
Zapisz je w menedżerze haseł: plik DPAPI nie jest przenośną kopią na inny komputer/profil Windows.
Po logowaniu wyczyść schowek przez `Set-Clipboard -Value ""`.
Sam skrypt bez `-CopyPassword` pokazuje wyłącznie adres panelu i login.
Sekrety mogą być przygotowane przed wdrożeniem; panel zaczyna działać dopiero po publikacji web/API i konfiguracji Caddy.

`ops/configure-stats-secrets.py` jest narzędziem VPS uruchamianym jako root.
Czyta poświadczenia ze stdin, generuje 256-bitowe sekrety, zapisuje pliki atomowo z
uprawnieniami 600, zachowuje pozostałe zmienne API oraz poprawne istniejące klucze.
Istniejącego hasła admina nie zmienia bez zgodnych poświadczeń. Instalacja drop-in Caddy
wykonuje tylko daemon-reload; nie restartuje usług ani nie wdraża kodu.
Python bcrypt jest już dostępny na tym VPS. Nigdy nie podawaj poświadczeń w argumentach procesu.

## Sekrety

W `.env` backendu, poza Git:

```dotenv
ANALYTICS_SECRET=<openssl rand -hex 32>
ANALYTICS_PROXY_TOKEN=<drugie openssl rand -hex 32>
```

Compose włącza analytics i wymaga obu sekretów. Bez Compose domyślnie wyłączone;
ustaw `ANALYTICS_ENABLED=true`, jeśli uruchamiasz JAR bez Compose.
Istniejące POSTGRES_PASSWORD i trzy SCANNER_TOKEN pozostają wymagane.
Caddy otrzymuje ten sam ANALYTICS_PROXY_TOKEN oraz STATS_ADMIN_USER i
STATS_ADMIN_PASSWORD_HASH. Nie używamy X-Forwarded-For od klienta:
Caddy ustawia X-Analytics-Client-IP na `{remote_host}` i nadpisuje token proxy.
Backend ignoruje metadane bez prawidłowego tokena oraz wszystkie zwykłe forwarded headers.
To zakłada Caddy bez dodatkowego CDN przed nim, zgodnie z obecną konfiguracją VPS.

## VPS — kolejność wdrożenia

Repo na VPS: `~/metin2-market-api` i `~/metin2-market-web`. Dopasuj ścieżkę web,
jeżeli checkout na tym VPS ma inną nazwę. Nie uruchamiaj ponownie migracji public→Pandora.

Jeśli sekrety zostały już przygotowane przez narzędzie, pomiń ręczne generowanie w krokach 1–2.
Nie nadpisuj istniejących `.env`, `/etc/metin2bazar/stats.env` ani drop-in Caddy.
Przejdź do wdrożenia z kroku 3; restart z kroku 5 wczyta przygotowane środowisko.

1. Przy pierwszej ręcznej instalacji, przed push do main ustaw sekrety: obecny workflow backendu automatycznie wdraża main,
   ale nie instaluje Caddy ani jego sekretów. Na VPS `cd ~/metin2-market-api`,
   `umask 077`, wygeneruj dwukrotnie `openssl rand -hex 32` i dopisz do `.env`.
   Zachowaj pozostałe zmienne i uprawnienia `chmod 600 .env`.
2. Wygeneruj hash interaktywnie `caddy hash-password` (hasło nie trafia do repo ani historii).
   Utwórz `sudo install -d -m 700 /etc/metin2bazar` i przez
   `sudoedit /etc/metin2bazar/stats.env` zapisz:

```dotenv
ANALYTICS_PROXY_TOKEN=<ten sam token co w .env backendu>
STATS_ADMIN_USER=<wybrany login>
STATS_ADMIN_PASSWORD_HASH='<hash zwrócony przez caddy hash-password>'
```

   `sudo chmod 600 /etc/metin2bazar/stats.env`. W `sudo systemctl edit caddy` dodaj:

```ini
[Service]
EnvironmentFile=/etc/metin2bazar/stats.env
```

3. Zachowaj kopię bieżącego Caddyfile i publikowanego frontendu. Zrób backup bazy:

```sh
cd ~/metin2-market-api
umask 077
sudo docker compose exec -T postgres pg_dump -U metin_market -d metin_market -Fc \
  --exclude-table-data=analytics.daily_visitors \
  --exclude-table-data=analytics.request_dedup > "$HOME/metin_market_before_stats.dump"
git pull --ff-only
sudo docker compose config --quiet
sudo docker compose up -d --build api
sudo docker compose logs --tail=80 api
sudo docker compose exec -T postgres psql -U metin_market -d metin_market \
  -c 'SELECT version,success FROM analytics.flyway_schema_history;'
```

   Flyway tworzy schemat automatycznie; nie wykonuj SQL V1 ręcznie.
4. Zbuduj web `npm ci && npm test && npm run build`; opublikuj dist do
   `/var/www/metin2bazar/`. Usuń z katalogu publikacji tylko stary
   `/var/www/metin2bazar/privacy-preferences.js`, bo zwykłe kopiowanie dist go nie usuwa.
   Przykład z checkoutu web:

```sh
npm ci
npm test
npm run build
sudo cp -a dist/. /var/www/metin2bazar/
sudo rm -f /var/www/metin2bazar/privacy-preferences.js
```

5. Sprawdź różnice z aktywnym Caddyfile, zachowując inne lokalne usługi. Walidacja
   musi dostać zmienne z rootowego EnvironmentFile; nie publikuj `caddy adapt` z sekretami:

```sh
cd ~/metin2-market-api
sudo sh -c 'set -a; . /etc/metin2bazar/stats.env; set +a; caddy validate --config /home/debian/metin2-market-api/ops/Caddyfile --adapter caddyfile'
sudo cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.before-stats
sudo install -m 644 ops/Caddyfile /etc/caddy/Caddyfile
sudo systemctl daemon-reload
sudo systemctl restart caddy
```

   EnvironmentFile jest wczytywany przy starcie procesu; pierwszy raz potrzebny restart.
   Przy późniejszych zmianach samej konfiguracji wystarcza reload. W pliku env zapisz
   hash w pojedynczych cudzysłowach, żeby powyższe źródłowanie przez sh nie rozwinęło `$`.
6. Zainstaluj niezależną retencję (po pomyślnej migracji bazy):

```sh
sudo install -m 644 ops/retention/analytics-prune.sql /etc/metin2bazar/analytics-prune.sql
sudo install -m 755 ops/retention/metin2bazar-analytics-retention.sh /usr/local/sbin/metin2bazar-analytics-retention
sudo install -m 644 ops/retention/metin2bazar-analytics-retention.service ops/retention/metin2bazar-analytics-retention.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now metin2bazar-analytics-retention.timer
sudo systemctl start metin2bazar-analytics-retention.service
```

   Skrypt domyślnie używa `/home/debian/metin2-market-api`; inną ścieżkę ustaw przez
   `Environment=METIN_MARKET_API_DIR=...` w drop-in nowej usługi.
7. Zatrzymaj samą aplikację Umami po sprawdzeniu nazw kontenerów (`docker ps`).
   Zachowaj bazę, wolumeny i jej dotychczasowy timer retencji 12 miesięcy. Usunięto
   aktywne `/metrics` i domenę analytics z Caddy. Nie resetuj i nie usuwaj historycznych danych.

## Sprawdzenie działania i ochrony

```sh
curl -I https://metin2bazar.pl/admin/stats
curl -i https://metin2bazar.pl/backend/api/v1/admin/stats
curl -i https://metin2bazar.pl/api/v1/admin/stats
curl -i https://api.mazikox.pl/api/v1/admin/stats
curl -i https://metin2market.mazikox.pl/backend/api/v1/admin/stats
# Wszystkie powyższe: 401 bez hasła, WWW-Authenticate: Basic.
curl -u '<login>' -i https://metin2bazar.pl/backend/api/v1/admin/stats
# curl zapyta o hasło; 200, Cache-Control: no-store, wyłącznie agregaty.
curl -i http://127.0.0.1:8080/api/v1/admin/stats
# Bez nagłówków proxy i admina: 401 także na bezpośrednim backendzie.
```

W przeglądarce otwórz `/admin/stats`, zaloguj się, potem otwórz katalog:
start zwiększa pobrania i unique, ale nie wyszukiwania. Ręczne wyszukiwanie zwiększa
wyszukiwania raz, następna strona tylko pobrania, suggestions/statystyki cen nic.
Powtórzenie żądania z tymi samymi tokenami nie zwiększa liczników. Kolejny adres IP
zwiększa unique; ten sam IP na innym serwerze zwiększa jego unique, bez globalnego duplikatu.
`/metrics/script.js` i `/metrics/api/send` powinny zwracać 404.

Testy lokalne: `./mvnw.cmd test package`, `npm test`, `npm run build`,
`python ops/test_caddy_stats.py --web-dist ../metin-market-web/dist` (wymaga Dockera).
