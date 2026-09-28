# Plan migracji API na trzy serwery gry

## Uzgodniony zakres

- Jedna instancja API i jedna istniejąca baza PostgreSQL `metin_market`.
- Trzy schematy w tej bazie: `pandora`, `elder`, `beavium`. Każdy zawiera taki sam zestaw tabel rynku i własną tabelę `flyway_schema_history`.
- Jeden kod obsługi importu, wyszukiwania, podpowiedzi i statystyk. Serwer jest jawnie wybierany w adresie żądania.
- Dotychczasowe dane Pandory pozostają w bazie i są przenoszone z `public` do `pandora`.
- Podpowiedzi nadal powstają wyłącznie z zaimportowanych ofert. Na tym etapie nie tworzymy `item_template` ani nie importujemy całego `item_proto`.
- Widoki frontendu mogą być wspólne. Budowa frontendu, ikony i interpretacja socketów są poza pierwszym etapem migracji API.

## Stan wyjściowy i zgodność

Obecny `compose.yaml` uruchamia jeden PostgreSQL 18 i jedno API. Aplikacja używa pojedynczego `JdbcClient`, domyślnego schematu `public`, automatycznego Flyway oraz jednego `SCANNER_TOKEN`. W `public` znajdują się tabele z migracji V1 i V2. Publiczne trasy to `/api/v1/items`, `/api/v1/items/suggestions`, `/api/v1/items/statistics`, a import korzysta z `/internal/v1/imports`. Skrypt `update_market.py` wysyła dane Pandory do `https://api.mazikox.pl`. Workflow `.github/workflows/deploy.yml` automatycznie wdraża `main` i sprawdza tylko stare publiczne API.

Zachować stare trasy jako **jawne aliasy Pandory** na czas nieograniczony lub do osobnej decyzji o ich wycofaniu. Stary token `SCANNER_TOKEN` pozostaje tokenem Pandory; dla Eldera i Beavium dodać osobne sekrety. Dzięki temu działający skrypt importu Pandory oraz obecny frontend nie wymagają zmiany w chwili migracji.

Docelowe trasy:

| Operacja | Trasa |
| --- | --- |
| Oferty | `GET /api/v1/servers/{server}/items` |
| Podpowiedzi | `GET /api/v1/servers/{server}/items/suggestions` |
| Statystyki | `GET /api/v1/servers/{server}/items/statistics` |
| Import | `POST /internal/v1/servers/{server}/imports` |

`{server}` przyjmuje wyłącznie `pandora`, `elder`, `beavium`; inne wartości kończą się 404. Żądanie bez serwera może działać tylko pod starymi trasami Pandory, nigdy przez domyślny wybór w warstwie bazy.

## Implementacja aplikacji

1. Wprowadzić zamknięty katalog serwerów (`pandora`, `elder`, `beavium`) i centralne rozpoznawanie serwera z trasy, zanim zostanie otwarta transakcja lub połączenie JDBC. Zweryfikować identyfikator przed dostępem do bazy. Nie wstawiać wartości podanej przez klienta do SQL, `search_path` ani JDBC URL.
2. Utworzyć trzy pule Hikari do **tej samej bazy**, każdą z ustalonym przy starcie `currentSchema`: odpowiednio `pandora`, `elder`, `beavium`. Nie dodawać `public` do ścieżki wyszukiwania. Ograniczyć rozmiary pul, ponieważ suma połączeń z trzech pul obciąża jeden PostgreSQL.
3. Podłączyć pule do jednego `AbstractRoutingDataSource` używanego przez `JdbcClient` i menedżer transakcji. Kontekst wyboru schematu ustawić na początku żądania i zawsze usunąć po nim, także przy błędzie. Nie konfigurować domyślnej puli. Import `@Transactional` musi od początku do końca korzystać z puli jednego serwera. Jeśli w przyszłości pojawi się przetwarzanie asynchroniczne, wybór serwera należy przekazać do tego zadania jawnie.
4. Wyłączyć automatyczną migrację Flyway pojedynczego schematu. Podczas startu uruchomić Flyway oddzielnie dla trzech schematów, z tą samą listą migracji, własną historią w każdym schemacie i błędem startu przy niepowodzeniu którejkolwiek migracji. Istniejących V1 i V2 nie zmieniać po wdrożeniu.
5. Dodać trasy z nazwą serwera i aliasy Pandory. Przy imporcie sprawdzić token przypisany do serwera z adresu **przed** wykonaniem zapisu. Token Eldera nie może autoryzować importu do Pandory ani Beavium. Identyfikatory `sourceId`, `batchId` i `runId` pozostają lokalne dla schematu; format JSON importu pozostaje bez zmian.
6. Wydzielić opisy bonusów z obecnego statycznego katalogu Pandory. W pierwszym wdrożeniu Pandora zachowuje obecne mapowanie. Dla Eldera i Beavium nie wolno stosować mapowania Pandory: do czasu dostarczenia ich definicji nieznane typy zwracają wartość surową i oznaczenie „nieznany”. Później ładować osobne, walidowane pliki definicji przy starcie aplikacji; restart przy aktualizacji jest akceptowalny.
7. Zmienić importer tak, aby dla nowych serwerów przyjmował jawne `--server`, adres bazy SQLite, `sourceId` i właściwy token. Zachować dotychczasowy sposób uruchamiania Pandory. Nie wyznaczać serwera na podstawie `sourceId` ani nazwy pliku SQLite.

## Jednorazowe przygotowanie bazy produkcyjnej

Przed migracją sprawdzić **rzeczywisty stan VPS**, ponieważ zawartość produkcyjnej bazy i wersja kodu mogą różnić się od lokalnego repozytorium. Potwierdzić nazwę bazy, listę obiektów w `public`, historię Flyway, liczbę rekordów w każdej tabeli, aktualny pełny skan oraz wolne miejsce na kopię. Nie zmieniać nazwy bazy ani istniejącego wolumenu Docker.

Przygotować jednorazowy, wersjonowany skrypt SQL z kontrolą warunków początkowych. W oknie wdrożeniowym, po zatrzymaniu importów i starego API:

1. Zrobić kopię bazy narzędziem `pg_dump` i sprawdzić, że plik powstał oraz można odczytać jego spis treści. Zapisać też identyfikator działającego obrazu/kodu API i konfigurację niezbędną do powrotu.
2. Utworzyć schemat `pandora` i w transakcji przenieść do niego sześć tabel rynku: `synchronization_batch`, `scan_run`, `shop_observation`, `shop_listing`, `shop_listing_attribute`, `shop_listing_socket` oraz `flyway_schema_history`. Użyć `ALTER TABLE ... SET SCHEMA`; **nie** kopiować danych wiersz po wierszu ani nie uruchamiać ponownie V1/V2 na Pandorze.
3. Sprawdzić po przeniesieniu liczby rekordów, klucze obce, indeksy, sekwencje kolumn identity, właścicieli/uprawnienia i wersje Flyway. Szczególnie sprawdzić, czy następny import potrafi nadać nowe identyfikatory bez konfliktu.
4. Nowa aplikacja przez Flyway tworzy schematy `elder` i `beavium` oraz zakłada w nich tabele przez V1 i V2. Aplikacja nie może przyjąć ruchu, dopóki wszystkie trzy migracje się nie zakończą.

Nie dodawać tego przeniesienia jako zwykłej V3 wykonywanej automatycznie na każdym schemacie: jest to jednorazowa operacja na istniejących obiektach `public` i historii Flyway.

## Próba i kryteria odbioru

Przed produkcją odtworzyć kopię bazy w środowisku próbnym i wykonać na niej dokładnie tę samą procedurę. Testy integracyjne z PostgreSQL powinny potwierdzić:

- start na pustej bazie tworzy trzy kompletne schematy, a start na bazie po migracji zachowuje dane i historię Pandory;
- ten sam `vnum`, `sourceId`, `runId` i `batchId` mogą wystąpić na różnych serwerach bez kolizji;
- oferty, podpowiedzi, statystyki i ostatni pełny skan nie przenikają między schematami;
- import do jednego serwera nie zmienia pozostałych, również przy równoległych żądaniach i błędzie transakcji;
- nieznany serwer oraz token innego serwera nie dają dostępu do danych ani zapisu;
- stare adresy i token Pandory nadal działają, a odpowiedzi Pandory przed i po migracji są zgodne.

Po wdrożeniu sprawdzić wszystkie trzy nowe trasy oraz stary adres Pandory. Dla pustych jeszcze serwerów poprawnym wynikiem jest pusta lista, nie błąd SQL. Porównać liczby rekordów i wybrane wyniki Pandory z zapisanymi wartościami sprzed migracji. Wysłać niewielki testowy import do każdego nowego schematu, następnie sprawdzić jego widoczność tylko na właściwej trasie.

## Kolejność wydania na VPS

1. Przygotować i przetestować kod API, konfigurację Compose, skrypt migracji, importer i scenariusz powrotu. Uzupełnić `.env` o tokeny nowych serwerów bez usuwania obecnego `SCANNER_TOKEN`. Pozostawić jeden kontener PostgreSQL i jeden kontener API. Obecny `api.mazikox.pl` może nadal wskazywać ten sam port API; nowe subdomeny nie są wymagane.
2. Zmienić `.github/workflows/deploy.yml`, aby **pierwsze** wydanie migracyjne wymagało kontrolowanego uruchomienia po wykonaniu kroku SQL. Sam push na `main` nie może uruchomić nowego API przed przeniesieniem Pandory. Rozszerzyć kontrolę po wdrożeniu o trasę z nazwą serwera oraz sprawdzenie każdego schematu.
3. W ustalonym oknie zatrzymać ręczne importy, zatrzymać stare API, wykonać kopię i migrację SQL, uruchomić nową wersję API, sprawdzić Flyway i testy HTTP, następnie wznowić importy.
4. Dopiero po potwierdzeniu działania Pandory zaimportować pierwsze skany Eldera i Beavium. Frontend można dołączyć później, używając nowych tras.

Jeżeli start lub weryfikacja nie powiedzie się, zatrzymać nowe API, odwrócić przeniesienie obiektów Pandory do `public`, uruchomić poprzednią wersję API i sprawdzić starą trasę oraz import. Jest to możliwe także po importach, o ile pierwsze wydanie nie zmienia struktury istniejących tabel: dane Eldera i Beavium pozostaną wtedy w swoich schematach, choć stare API ich nie pokaże. Odtworzenie kopii jest ostatnią opcją, bo usuwa importy wykonane po jej utworzeniu.

## Warunki zakończenia pierwszego etapu

Pandora zachowuje historię i dotychczasowy sposób aktualizacji. Elder i Beavium mają puste, gotowe do importu schematy, osobne tokeny i działające adresy API. Wszystkie trzy serwery korzystają z jednego procesu API i jednej bazy, a żadne żądanie nie może przypadkowo trafić do innego schematu. Ikony, katalog całej gry i szczegółowe tłumaczenie socketów nie blokują tego etapu.

---

# Specyfikacja wykonawcza dla implementującego

Poniższe punkty precyzują zakres pierwszego wydania. Wykonuj je w kolejności. Nie wdrażaj na VPS podczas pisania kodu ani nie traktuj lokalnej bazy jako wiernej kopii produkcji. Zatrzymaj się przed krokiem produkcyjnym, jeżeli preflight wykryje inną historię migracji, dodatkowe tabele aplikacji lub inny sposób uruchamiania niż opisany tutaj.

## 1. Kontrakt, którego nie należy zmieniać

1. JSON żądania importu `ImportRequest` i JSON odpowiedzi `ImportResponse` pozostają takie same. `sourceId` nadal identyfikuje instalację skanera, **nie** serwer gry.
2. JSON odpowiedzi z ofert, podpowiedzi i statystyk pozostaje taki sam. Zmienia się tylko adres dla Eldera i Beavium.
3. `GET /api/v1/items`, `/suggestions`, `/statistics` i `POST /internal/v1/imports` zawsze używają Pandory. Istniejący frontend, `update_market.py` i `pandora_market_update.bat` nie mogą przestać działać.
4. Nowe adresy mają postać z tabeli powyżej. Slug ma być zapisany małymi literami dokładnie jako `pandora`, `elder`, `beavium`. Nie dodawać automatycznego fallbacku na Pandorę dla błędnego lub brakującego sluga.
5. Błąd serwera w adresie: 404. Brak tokenu lub token przypisany do innego serwera: 401. Poprawny, ponowiony import tego samego batcha: dotychczasowe `alreadyProcessed=true`. Konflikt danych: dotychczasowe 409.
6. W pierwszym wydaniu nie dodawać migracji zmieniającej istniejące tabele rynku. Potrzebne są tylko przeniesienie schematu i uruchomienie tych samych V1/V2 w dwóch pustych schematach. Ułatwia to powrót do starego API.

## 2. Lista zmian w repozytorium

| Plik lub nowy moduł | Dokładna zmiana |
| --- | --- |
| `src/main/java/com/mazikox/metin_market_api/server/GameServer.java` | Nowy enum z trzema slugami. Metoda parsowania sluga; żadnych dowolnych nazw schematów od klienta. |
| `server/ServerContext.java` | Kontekst serwera dla bieżącego żądania; `getRequired()` rzuca błąd bez ustawionego serwera, `clear()` usuwa wartość. |
| `server/ServerRoutingFilter.java` | Filtr `OncePerRequestFilter`. Rozpoznaje tylko cztery stare trasy Pandory i nowe trasy `/api/v1/servers/{server}/items...` oraz `/internal/v1/servers/{server}/imports`. Ustawia kontekst przed wywołaniem kontrolera; `finally` zawsze czyści kontekst. Błędny slug kończy żądanie 404. |
| `server/ServerRoutingDataSource.java` i `server/ServerDatabaseConfiguration.java` | Trzy pule Hikari do jednego URL bazy, jedna pula na schemat; jeden `AbstractRoutingDataSource` jako bean `DataSource`. `determineCurrentLookupKey()` pobiera `GameServer` z kontekstu. Bez domyślnego targetu; `setLenientFallback(false)`. Zamknąć wszystkie trzy pule przy zamknięciu aplikacji. |
| `server/ServerFlywayConfiguration.java` albo część konfiguracji bazy | Uruchamia trzy **oddzielne** instancje Flyway na docelowych pulach przed udostępnieniem API. Dla każdej ustawia `schemas(schema)`, `defaultSchema(schema)`, `locations("classpath:db/migration")`. Każda ma własne `flyway_schema_history`; nie używać jednego Flyway z trzema schematami w `schemas(...)`. |
| `src/main/resources/application.properties` | Zachować `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`. Ustawić `spring.flyway.enabled=false`; dodać konfigurację trzech tokenów: Pandora z obecnego `SCANNER_TOKEN`, Elder z `SCANNER_TOKEN_ELDER`, Beavium z `SCANNER_TOKEN_BEAVIUM`. Brak tokenu ma zatrzymać start. |
| `src/main/java/com/mazikox/metin_market_api/market/ItemSearchController.java` | Ustawić bazowe mapowania `@RequestMapping({"/api/v1/items", "/api/v1/servers/{server}/items"})`. Zachować te same parametry, ograniczenia i metody serwisu. Wybrany serwer pochodzi z filtra, nie z niesprawdzonego tekstu w SQL. |
| `src/main/java/com/mazikox/metin_market_api/ingestion/ImportController.java` | Ustawić bazowe mapowania `@RequestMapping({"/internal/v1/imports", "/internal/v1/servers/{server}/imports"})`; wybrać oczekiwany token według serwera z kontekstu. Walidować token przed wywołaniem `ImportService`; pozostawić porównanie odporne na timing. |
| `src/main/java/com/mazikox/metin_market_api/market/ItemSearchService.java` i `MarketCatalogService.java` | SQL może pozostać bez kwalifikacji schematu, bo fizyczna pula połączeń ma na stałe ustawiony schemat. Zmienić wywołanie `ItemBonusCatalog.describe(...)` w `ItemSearchService` na wersję zależną od serwera. Upewnić się, że żadna ścieżka nie otwiera połączenia bez kontekstu. Nie dopisywać `server_id` do tabel. |
| `src/main/java/com/mazikox/metin_market_api/market/ItemBonusCatalog.java` | Zachować mapę Pandory; udostępnić ją wyłącznie dla Pandory. Nowy serwer z brakiem definicji zwraca `UNKNOWN`, oryginalny typ i wartość, bez etykiet Pandory. Dodać test, że ten sam `attr_type` nie używa opisu Pandory dla Eldera/Beavium. |
| `tools/import_sqlite.py`, `update_market.py` | Dodać jawne `--server` (domyślnie `pandora` tylko dla zgodności), zbudować adres nowego importu dla innych serwerów. Dla `elder` i `beavium` wymagać jawnego `--database` i `--source-id`, żeby domyślna baza Pandory nie trafiła na inny serwer. Token brać z właściwej zmiennej środowiskowej lub argumentu. |
| `compose.yaml` | Nadal po jednej usłudze `postgres` i `api`, ten sam wolumen i port. Dodać do `api.environment` dwa wymagane tokeny. Nie dodawać kontenerów API ani baz PostgreSQL. |
| `.github/workflows/security-verification.yml` | Dodać testowe wartości dwóch nowych tokenów do kroku `docker compose config`. |
| `.github/workflows/deploy.yml` | Przed pierwszym wydaniem wstrzymać automatyczny deploy przy `push main`, zostawić testy. Dodać ręczne uruchomienie deployu po wykonaniu migracji SQL. Po stabilizacji można przywrócić automat. Zmienić smoke test na starą trasę Pandory i trzy nowe trasy. |
| `README.md` | Opisać trasy, wybór serwera, tokeny, uruchomienie lokalne, import oraz procedurę migracji Pandory. Istniejące `CorsConfiguration` obejmuje nowe publiczne trasy przez `/api/**`; potwierdzić to testem. |
| `ops/migrate-public-to-pandora.sql`, `ops/rollback-pandora-to-public.sql` | Dwa jawne, jednorazowe skrypty SQL; każdy z warunkami wstępnymi. Nie wpisywać ich do katalogu zwykłych migracji Flyway. |

Obecny `ItemSearchService`, test integracyjny, `tools/import_sqlite.py` oraz lokalne skrypty mają niezapisane zmiany. Przed edycją sprawdź `git status` i zachowaj te zmiany; nie wykonuj `git reset` w lokalnym repozytorium.

## 3. Szczegóły wyboru serwera i połączenia

Przyjmij jeden konkretny mechanizm: trzy fizyczne pule połączeń, każda skonfigurowana podczas startu przez parametr pgJDBC `currentSchema` ustawiony na **jeden** schemat. Wartość pochodzi wyłącznie z `GameServer`. Nie wykonuj `SET search_path` na współdzielonym połączeniu po każdym żądaniu. Unikniesz przeniesienia ustawienia z poprzedniego żądania w puli.

Zachowaj istniejące nazwy zmiennych bazy i ustaw w aplikacji poniższy kontrakt konfiguracyjny. Dokładny sposób bindowania do klasy Java może być inny, ale nazwy zmiennych środowiskowych i ich znaczenie mają pozostać takie:

```properties
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/metin_market}
spring.datasource.username=${DATABASE_USERNAME:metin_market}
spring.datasource.password=${DATABASE_PASSWORD:metin_market}
spring.flyway.enabled=false
app.scanner-token.pandora=${SCANNER_TOKEN}
app.scanner-token.elder=${SCANNER_TOKEN_ELDER}
app.scanner-token.beavium=${SCANNER_TOKEN_BEAVIUM}
```

W `compose.yaml` do środowiska pojedynczej usługi `api` dodaj `SCANNER_TOKEN_ELDER: ${SCANNER_TOKEN_ELDER:?required}` i `SCANNER_TOKEN_BEAVIUM: ${SCANNER_TOKEN_BEAVIUM:?required}`. Nie zmieniaj istniejącego `SCANNER_TOKEN` Pandory. Przy budowie pul użyj tego samego URL, nazwy użytkownika i hasła, lecz dla każdej puli przekaż inną wartość `currentSchema` z enuma. Przykładowo `HikariConfig.addDataSourceProperty("currentSchema", server.schema())`. Zmierz liczbę dostępnych połączeń PostgreSQL; na początek ustaw niewielki `maximumPoolSize`, np. 4 na pulę, i sprawdź działanie pod równoległymi żądaniami.

Nie wystawiaj trzech pul jako niezależnych beanów `DataSource` używanych przez autokonfigurację. Wystaw **jeden** routing `DataSource`; pule docelowe przechowuj w konfiguracji i zamknij je przy shutdownie. `JdbcClient` i menedżer transakcji muszą używać routing `DataSource`. Flyway dostaje bezpośrednio konkretną pulę. Podczas startu najpierw utwórz pule i wykonaj Flyway, potem udostępnij routing `DataSource`. Nie dopuść do stanu, w którym HTTP działa, choć migracja jednego schematu nie powiodła się.

Filtr HTTP ma zostać wykonany **przed** otwarciem połączenia. Istniejące `@Transactional` na `ImportService.importBatch` pozostaje na całym imporcie; nie przenoś wyboru serwera do środka tej metody. W odczytach, które wykonują wiele zapytań JDBC, kontekst nie może się zmieniać w trakcie żądania. Weryfikację wykonaj także na dwóch równoczesnych żądaniach do różnych serwerów.

Nie używaj surowego `{server}` w nazwach tabel lub schematów SQL. Dotychczasowe niekwalifikowane zapytania są poprawne, jeśli połączenie ma właściwy, stały `currentSchema`. W testach możesz sprawdzić `SELECT current_schema()` dla każdej trasy lub bezpośredniej puli.

Źródła technicznego wyboru: [pgJDBC `currentSchema`](https://jdbc.postgresql.org/documentation/use/), [Flyway o lokalizacji historii](https://documentation.red-gate.com/fd/flyway-schema-history-table-273973417.html) oraz [Flyway o oddzielnych cyklach życia schematów](https://documentation.red-gate.com/fd/frequently-asked-questions-277579363.html).

## 4. Dokładny skrypt przeniesienia Pandory

Skrypt `ops/migrate-public-to-pandora.sql` ma działać przy zatrzymanym API. Przed nim sprawdź, że `public.flyway_schema_history` zawiera **dokładnie oczekiwane** udane V1 i V2 z tego repozytorium. Jeśli produkcja ma inną wersję, najpierw dostosuj plan do faktycznego stanu; nie dodawaj `repair` automatycznie. Sprawdź, że wszystkie siedem tabel istnieje w `public`, a schemat `pandora` i te same tabele w nim nie istnieją.

Rdzeń skryptu ma mieć taką kolejność (dodaj do pliku warunki wstępne rzucające błąd; żadnego `IF EXISTS` maskującego niezgodny stan):

```sql
BEGIN;
CREATE SCHEMA pandora;

ALTER TABLE public.synchronization_batch SET SCHEMA pandora;
ALTER TABLE public.scan_run SET SCHEMA pandora;
ALTER TABLE public.shop_observation SET SCHEMA pandora;
ALTER TABLE public.shop_listing SET SCHEMA pandora;
ALTER TABLE public.shop_listing_attribute SET SCHEMA pandora;
ALTER TABLE public.shop_listing_socket SET SCHEMA pandora;
ALTER TABLE public.flyway_schema_history SET SCHEMA pandora;
COMMIT;
```

Warunki wstępne wykonaj **wewnątrz tej samej transakcji**, przed `CREATE SCHEMA`. Mają zweryfikować po nazwie wszystkie siedem tabel, brak `pandora`, udane wersje V1/V2 i brak innych wersji w `public.flyway_schema_history`. Wykonuj przez `psql -v ON_ERROR_STOP=1`, by pierwszy błąd przerwał skrypt. `elder` i `beavium` pozostają poza tym skryptem: Flyway nowej aplikacji tworzy je na pierwszym starcie i waliduje na kolejnych.

Użyj konta właściciela obecnych obiektów. `ALTER TABLE ... SET SCHEMA` przenosi również powiązane indeksy, ograniczenia i sekwencje należące do kolumn, lecz nadal zweryfikuj je po operacji. Nie zmieniaj nazw tabel, ich kluczy, ani identyfikatorów rekordów. Nie uruchamiaj V1/V2 ponownie w Pandorze. Podstawa: [PostgreSQL `ALTER TABLE`](https://www.postgresql.org/docs/18/sql-altertable.html).

Skrypt odwrotny ma zatrzymać się, jeśli tabele Pandory nie są kompletne albo `public` zawiera już obiekty o tych nazwach. W transakcji wykonuje te same siedem `ALTER TABLE pandora.<nazwa> SET SCHEMA public`, po czym `DROP SCHEMA pandora RESTRICT`. Ostatni krok uda się tylko wtedy, gdy w schemacie nie zostały żadne inne obiekty. **Nie usuwa** schematów `elder` ani `beavium` i nie usuwa ich danych. Stary kod zobaczy wtedy tylko przywróconą Pandorę.

## 5. Procedura próby przed produkcją

1. Na lokalnym lub osobnym testowym PostgreSQL odtwórz strukturę starej aplikacji (`public`, V1/V2), wstaw dane odpowiadające przynajmniej jednemu pełnemu skanowi, jego obserwacjom, ofertom, atrybutom i socketom. Lepiej użyć kopii produkcyjnej, jeśli jest dostępna.
2. Zanotuj liczby rekordów każdej z sześciu tabel, wiersze `public.flyway_schema_history`, ID ostatniego pełnego skanu i odpowiedzi starego API dla co najmniej jednego zapytania o ofertę, podpowiedzi i statystyki.
3. Uruchom `ops/migrate-public-to-pandora.sql` z `psql -v ON_ERROR_STOP=1 -f ...`. Powtórne uruchomienie ma zakończyć się błędem warunków wstępnych, bez zmiany danych.
4. Uruchom nowe API. Zwróć uwagę, czy Flyway nie próbuje wykonać V1/V2 w `pandora`, a wykonuje je w pustych `elder` i `beavium`. Sprawdź po jednej `flyway_schema_history` w każdym schemacie i brak aplikacyjnych tabel rynku w `public`.
5. Porównaj liczby rekordów oraz odpowiedzi starego adresu Pandory z zapisem z punktu 2. Sprawdź, czy nowa trasa Pandory zwraca tę samą odpowiedź.
6. Zaimportuj do Eldera i Beavium przykładowe batch-e z tym samym `sourceId`, `batchId`, `runId` i `vnum`, ale inną nazwą przedmiotu. Potwierdź izolację wyników i idempotencję importu wewnątrz każdego schematu.
7. Przećwicz powrót: zatrzymaj nowe API, wykonaj skrypt odwrotny, uruchom starą wersję i sprawdź Pandorę. Ponownie uruchom migrację Pandory i nowe API; istniejące już schematy Eldera i Beavium powinny przejść walidację Flyway bez utraty danych. Potwierdź, że ponowna próba jest wykonalna przy spełnionych warunkach wstępnych.

## 6. Testy kodu, które mają powstać

- Uaktualnij `MarketApiIntegrationTest`: obecny `@Autowired JdbcClient` w `@BeforeEach` wykonuje `TRUNCATE` bez kontekstu serwera. Zastąp go testowym połączeniem administracyjnym i jawnie kwalifikowanym `TRUNCATE pandora...., elder...., beavium....` albo odrębnym czyszczeniem każdego schematu przez odpowiednią pulę. Zaktualizuj `@DynamicPropertySource` do nowych nazw tokenów/konfiguracji.
- Zachowaj dotychczasowe przypadki testowe Pandory. Dodaj import tych samych identyfikatorów na dwóch serwerach, wyszukiwanie z tym samym `vnum`, podpowiedzi i statystyki wyłącznie dla właściwego schematu, wybór ostatniego pełnego skanu osobno, pusty nowy schemat, zły slug, zły token, brak tokenu i stare aliasy.
- Test równoległości: dwa równoczesne importy lub odczyty Pandory i Eldera, a potem ponowny odczyt obu schematów. To ma wykryć przeciek kontekstu między wątkami i pulami.
- Uaktualnij `ImportControllerTest` i `ItemSearchControllerTest` dla nowych tras, zachowując weryfikację parametrów `vnum`, `page`, `size`. Dodaj test, że token Eldera na trasie Pandory nie wywołuje `ImportService`.
- Uaktualnij `ItemBonusCatalogTest`: mapowanie Pandory bez regresji, a ten sam numeryczny typ na Elderze i Beavium zwraca bezpieczne `UNKNOWN` do czasu wczytania ich definicji.
- Uruchom `./mvnw test` i `docker compose config` z testowymi tokenami. Test migracji na starej bazie jest osobnym warunkiem odbioru: test świeżej bazy nie zastępuje go.

## 7. Wydanie produkcyjne, dokładna kolejność

**Przygotowanie CI.** W osobnej zmianie przestaw `.github/workflows/deploy.yml` tak, aby testy nadal działały po pushu, lecz krok SSH z `docker compose up -d --build` nie uruchamiał się automatycznie. Dodaj `workflow_dispatch` dla kontrolowanego wdrożenia. Najpierw dostarcz i zweryfikuj tę zmianę; dopiero później połącz kod wieloserwerowy do `main`. Obecny workflow wykonuje `git reset --hard origin/main` na VPS, więc przed ręcznym wdrożeniem sprawdź, czy na VPS nie ma zmian lokalnych wymagających zachowania.

**Preflight VPS.** Sprawdź `docker compose ps`, wersję uruchomionego kodu, obecność bazy `metin_market`, listę obiektów w `public` i `public.flyway_schema_history`. Zapisz wyniki zapytań kontrolnych oraz liczniki tabel. Nie wykonuj SQL z planu, jeżeli historia albo schemat produkcyjny różni się od założeń. Przygotuj w `.env` nowe `SCANNER_TOKEN_ELDER` i `SCANNER_TOKEN_BEAVIUM`, pozostaw `SCANNER_TOKEN` Pandory. Uruchom `docker compose config` przed zmianą usług; nie umieszczaj tokenów w logach ani repozytorium.

**Okno migracyjne.** Wstrzymaj ręczne importy. Zatrzymaj tylko API, pozostaw PostgreSQL i wolumen. Wykonaj `pg_dump` bazy `metin_market` w formacie custom (`-Fc`), potwierdź poprawność pliku przez odczyt listy archiwum `pg_restore -l` i przechowaj go poza kontenerem. Zapisz stary obraz/commit. Uruchom skrypt SQL przez `psql -v ON_ERROR_STOP=1`. Zweryfikuj obiekty i liczby rekordów. Uruchom ręczny deploy nowego API. Nie wywołuj `docker compose down -v` ani nie usuwaj wolumenu.

**Smoke test.** Sprawdź 200 na starej trasie Pandory i trzech nowych trasach (puste wyniki Eldera i Beavium są poprawne). Porównaj liczbę wyników Pandory i wybrane oferty z preflightem. Sprawdź wszystkie trzy historie Flyway. Zweryfikuj błędny slug i zły token, nie wysyłając produkcyjnych danych testowych, których nie da się łatwo odróżnić od prawdziwego skanu. Dopiero wtedy wznów ręczny import Pandory. Pierwszy rzeczywisty import Eldera i Beavium wykonaj oddzielnie.

**Powrót.** Jeśli kontrola nie przejdzie: wstrzymaj importy, zatrzymaj nowe API, wykonaj odwrotny skrypt SQL, uruchom stary obraz/kod, sprawdź starą trasę i import Pandory. Zachowaj dane Eldera i Beavium w ich schematach na kolejną próbę. Pełne `pg_restore` stosuj dopiero, jeśli odwrócenie skryptu nie wystarczy; odtworzenie wcześniejszej kopii usuwa późniejsze importy.

## 8. Co odłożyć na osobny etap

Nie dodawać teraz `item_template`, katalogu wszystkich przedmiotów, tłumaczenia socketów, CDN ani frontendu. Gdy pojawią się pliki klienta, osobno określić format mapowania bonusów dla Eldera i Beavium oraz ikon. Nie zgadywać znaczenia `attr_type` ani `socket_value` na podstawie Pandory. Tabela `shop_listing.item_name` nadal przechowuje nazwę nadesłaną przez skaner i zasila podpowiedzi.
