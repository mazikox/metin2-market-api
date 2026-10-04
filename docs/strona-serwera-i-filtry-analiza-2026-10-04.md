# Strona serwera i zaawansowane filtry — analiza

Data: 4 października 2026. Zakres: frontend i API Metin2 Bazar, istniejący importer oraz odczyt lokalnej bazy skanera Beavium. Dokument opisuje propozycję, nie wdrożoną funkcjonalność. Nie zmieniono kodu aplikacji, schematu bazy ani danych produkcyjnych.

## 1. Wniosek

Można zbudować ciekawszą stronę serwera i wydajne filtrowanie w obecnej architekturze React + Spring + PostgreSQL. Filtry po cenie, mapie, VNUM i bonusach korzystają z istniejących pól. Kategorie, wymagany poziom i klasa postaci potrzebują osobnego, zweryfikowanego katalogu przedmiotów dla każdego serwera.

Dla Beavium są dwie zależności: potwierdzenie kompletności bonusów w nowym skanie oraz sprawdzenie znaczenia ich numerów. API obecnie opisuje wszystkie bonusy Beavium jako nieznane. Katalog nazw Beavium istnieje w projekcie skanera; dedykowanego pliku `item_proto_beavium.tsv` nie znaleziono w sprawdzonym katalogu zasobów.

Rekomendowany pierwszy zakres: karty najczęściej spotykanych przedmiotów na stronie serwera, globalne sortowanie i filtrowanie po cenie/mapie/bonusach. Kategorie i poziomy włączyć po przygotowaniu wiarygodnych metadanych. Ta sama implementacja powinna obsługiwać trzy serwery, a dane i dostępne filtry pozostawać osobne.

## 2. Jak działa obecny projekt

| Obszar | Potwierdzone zachowanie |
| --- | --- |
| Wejście na Beavium i Elder | Puste zapytanie i brak wybranych VNUM. Pobranie pierwszych 8 ofert. |
| Wejście na Pandorę | Wstępne wyszukanie Zatrutego miecza i VNUM 180–189. |
| Kolejność API | `unit_price ASC, observed_at DESC, listing_id DESC`. To nie losowanie. |
| Wybór skanu | Jeden najnowszy skan `state = 3 AND publishable = true` w schemacie wybranego serwera. |
| Obserwacje sklepów | Ostatnia obserwacja danego `shop_vid` w wybranym skanie; przy braku VID identyfikatorem jest obserwacja. |
| Łączenie ofert | Identyczne pozycje w tej samej obserwacji są łączone z uwzględnieniem bonusów i socketów. |
| Sortowanie frontendowe | Cena malejąco i ilość działają tylko na pobranej stronie. |
| Filtr mapy | Działa tylko na pobranej stronie. Lista map też pochodzi tylko z tych wyników. |
| Filtry API | Nazwa, lista VNUM, strona, rozmiar. Brak filtrów bonusów, ceny, mapy i metadanych przedmiotów. |
| Statystyki ceny | Osobny endpoint, dane z tego samego rodzaju aktualnego skanu; najniższa cena każdego sklepu stanowi podstawę rozkładu. |
| Podpowiedzi | Katalog budowany z `DISTINCT item_vnum, item_name` z całej historii ofert. Rodziny ulepszeń są rozpoznawane po nazwach z końcówką +0…+9. |

Źródła kodowe:

- `E:/metin-market-web/src/App.tsx`: inicjalizacja ok. linii 17–28, pobieranie ok. 120, sortowanie i mapy ok. 256, aktualny interfejs ok. 310.
- `src/main/java/com/mazikox/metin_market_api/market/infrastructure/jdbc/JdbcMarketRepository.java`: wybór skanu, deduplikacja, agregacja i paginacja.
- `src/main/java/com/mazikox/metin_market_api/market/api/ItemSearchController.java`: publiczny kontrakt wyszukiwania.
- `src/main/resources/db/migration/V1__market_schema.sql` i `V2__publishable_scan_run.sql`: przechowywane pola i indeksy.

Wniosek produktowy: zmiana kolejności ośmiu najtańszych ofert na froncie nie daje prawdziwego rankingu rynku. Sortowanie i filtrowanie trzeba przenieść do API, przed paginację.

## 3. Co rzeczywiście jest w danych Beavium

Baza źródłowa: `%LOCALAPPDATA%/ElderSuite/eldersuite-history-beavium.db`. Odczyt przez SQLite `mode=ro` z `PRAGMA query_only=ON`. Nie wykonywano importu ani operacji na procesie gry.

Zbadany ostatni formalnie zakończony skan:

- Run ID: `ba118a74-03b9-4bcd-88be-e91045328c35`.
- Koniec: 4 października 2026, 18:48:57 czasu Warszawy.
- 479 kanonicznych obserwacji sklepów, 6505 pozycji, 636 różnych VNUM, 7868 sztuk.
- Zero zapisanych rekordów bonusów i zero rekordów socketów w tym skanie.
- Poprzedni większy zakończony skan z tego samego dnia także nie zawierał tych danych.
- Nowszy skan `dec767d1-a446-41da-a63c-70478db0a055`, będący w stanie 1 podczas sprawdzania, zawierał już bonusy i sockety. Liczby rosną podczas skanowania, więc nie są podstawą finalnego rankingu.

Brak rekordów bonusów w starszym skanie nie dowodzi, że wszystkie te przedmioty nie miały bonusów. Należy rozróżniać „odczytane i brak bonusów” od „dane bonusów nie zostały pobrane”. Po zakończeniu nowego skanu sprawdzić konkretne oferty z gry lub zweryfikowanego zapisanego snapshotu, importer i odpowiedź API. Nie przenosić historycznych bonusów do nowych ofert na podstawie samego VNUM.

Lokalny SQLite nie zawiera flagi `publishable`. Nie potwierdzono, że ten skan jest aktualnym skanem produkcyjnego PostgreSQL. Podane liczby opisują dane lokalne, nie audyt produkcji.

Przykładowy ranking dostępności ze zbadanego skanu:

| Przedmiot | Liczba sklepów |
| --- | ---: |
| Futro Wilka+ | 101 |
| Ornament | 87 |
| Futro Wilka | 83 |
| Shuriken | 60 |
| Miedziany Naszyjnik+0 | 17 |
| Kupon SM (100)* | 10 |
| Zwój Błogosławieństwa | 8 |
| Miecz Pełni Księżyca+0 | 5 |

To pokazuje, że ogólny ranking według liczby sklepów zdominują ulepszacze. Podział na kategorie lub ręcznie dobrane, jawnie opisane skróty wyszukiwania pozwoli pokazać również wyposażenie i wartościowe materiały.

## 4. Proponowana strona po wybraniu serwera

Na wejściu wyświetlać przegląd rynku, a po wyszukaniu lub ustawieniu filtrów — listę konkretnych ofert.

Przegląd rynku:

1. Wyszukiwarka, przycisk „Filtry”, czas zakończenia skanu i liczba obserwowanych sklepów.
2. Sekcja „Najczęściej spotykane w sklepach”: 8–12 kart różnych przedmiotów lub zweryfikowanych rodzin. Karta: ikona, nazwa, liczba sklepów, cena od i liczba sztuk. Kliknięcie otwiera oferty.
3. Przełącznik kategorii, gdy metadane są gotowe: wszystkie, broń, zbroje, biżuteria, ulepszacze itd. Można pokazać osobne rzędy kategorii, aby materiały nie zajmowały całej strony.
4. Sekcja „Najczęściej wyszukiwane — ostatnie 7 dni”, gdy próbka wyszukiwań jest wystarczająca. Przy braku danych pozostaje ranking dostępności.
5. Zachować szybkie wyszukiwania oraz ulubione użytkownika. Dostosować skróty do faktycznych nazw/VNUM danego serwera.

Pierwszy ranking: liczba różnych kanonicznych sklepów malejąco, następnie stabilny klucz przedmiotu. Ilość sztuk i liczba slotów są informacjami dodatkowymi; jeden sklep z wieloma slotami nie powinien samodzielnie wygrać rankingu.

Nazwy rankingów muszą odpowiadać mierzonej rzeczy:

- „Najczęściej spotykane” mierzy dostępność w skanie.
- „Najczęściej wyszukiwane” mierzy wyszukiwania na stronie.
- „Najczęściej kupowane” wymaga danych o transakcjach, których obecny model nie dostarcza. Zniknięcie oferty nie jest dowodem sprzedaży.

`analytics.daily_items` już przechowuje dzienne liczby wyszukiwań według serwera i nazwy bez surowych zapytań. Obecna logika liczy dopasowania do konkretnych nazw/rodzin, nie dowolne fragmenty tekstu, i może przypisać jedną operację do kilku nazw. To dobry punkt wyjścia, ale docelowo warto używać stabilnego identyfikatora przedmiotu lub rodziny. Publiczny endpoint powinien zwracać wyłącznie zagregowany ranking. Automatyczne pobranie przeglądu nie może liczyć się jako wyszukiwanie.

Nie rekomenduję na początek „największych okazji” dla ekwipunku: obecne statystyki według VNUM mieszają egzemplarze z różnymi bonusami. Porównanie cen ma sens po określeniu porównywalnej grupy. Zmiany cen i nowości względem poprzedniego skanu można dodać później po uwzględnieniu pokrycia skanów i wariantów.

## 5. Interfejs i znaczenie filtrów

Filtry mają się składać niezależnie. Nazwa lub wybór konkretnego przedmiotu nie są wymagane do wyszukiwania bonusu.

Przykłady:

- Wszystkie przedmioty z Max PŻ co najmniej 1500.
- Same naszyjniki z Max PŻ co najmniej 1500 i odpornością na ogień co najmniej 10%.
- Powyższe naszyjniki wymagające maksymalnie 75 poziomu, z ceną do wybranej kwoty.
- Konkretny przedmiot lub rodzina +0…+9 z zadanym bonusem.
- Broń ze średnimi obrażeniami co najmniej 30%, sortowana po tym bonusie malejąco.

Proponowane kontrolki:

- Nazwa/wybrany przedmiot lub rodzina.
- Kategoria i podkategoria z katalogu serwera.
- Maksymalny wymagany poziom oraz opcjonalnie zakres poziomów.
- Zakres ceny za sztukę, mapa, kanał.
- Powtarzalne wiersze „Bonus / od / do”; pusta wartość oznacza samą obecność typu.
- Tryb „wszystkie bonusy” jako domyślny. Alternatywę „dowolny z bonusów” można dodać jako jawny przełącznik.
- „Zastosuj”, „Wyczyść”, widoczne aktywne filtry i liczba wyników.

Semantyka:

- Wszystkie warunki bonusów muszą dotyczyć tego samego egzemplarza oferty, nie różnych przedmiotów tego samego sklepu.
- Dolna i górna granica bonusu są sprawdzane na tym samym rekordzie atrybutu.
- Zakres obsługuje liczby ujemne. Dla bonusów procentowych API operuje na wartości surowej w zweryfikowanej skali serwera; użytkownik widzi procent.
- Bonusy logiczne, np. niewrażliwość, wymagają kontrolki obecności i potwierdzonej semantyki danych, a nie zakresu procentowego.
- „Do 75 poziomu” oznacza `required_level <= 75`, nie poziom ulepszenia +7 lub +9.
- Brak metadanych to `NULL`/stan nieznany. Nie zamieniać go na poziom 0. Gdy filtr poziomu jest aktywny, rekord z nieznanym poziomem nie spełnia warunku; UI powinien wyjaśniać pokrycie katalogu.
- Na początku „bonus” powinien oznaczać atrybut egzemplarza ze skanu. Bonus bazowy z `item_proto` i bonus kamienia to osobne źródła; sumowanie ich wymaga definicji zgodnej z serwerem.
- Zmiana filtra lub sortowania resetuje stronę do 0. Zapytania zachowują anulowanie i ochronę przed spóźnionymi odpowiedziami.
- Filtry można zapisywać w URL i rozbudowanych ulubionych z zachowaniem zgodności ze starymi zapisami.

## 6. Jakie informacje trzeba dodać do bazy

Obecny PostgreSQL przechowuje VNUM, nazwę, ilość, ceny, atrybuty, sockety oraz dane sklepu i skanu. Nie przechowuje typu/podtypu, wymaganego poziomu ani ograniczeń klasowych. SQLite `item_catalog` zawiera tylko VNUM i nazwę.

W ElderSuite `ItemProtoStats` ma typ, podtyp, wymagany poziom, flagi klasowe i bazowe bonusy. Zasoby zawierają `item_proto.tsv` oraz `item_proto_elder.tsv`; `item_catalog_beavium.tsv` jest katalogiem nazw/ikon/opisów. `EmbeddedItemCatalog` próbuje wczytać plik właściwy dla serwera, a następnie korzysta z ogólnego `item_proto.tsv` jako fallbacku.

W badanym skanie 627 z 636 VNUM występowało w ogólnym proto. To wyłącznie zbieżność identyfikatorów, nie dowód poprawności kategorii, poziomów czy parametrów Beavium. Występowały też różnice nazw. Nie należy automatycznie uznawać fallbacku za potwierdzone dane serwera.

Proponowane tabele w każdym istniejącym schemacie serwera:

| Tabela | Istotne pola |
| --- | --- |
| `item_definition` | `item_vnum` PK, `canonical_name`, `search_name`, `item_type`, `item_subtype`, `category_code`, `required_level`, `refine_level`, `family_id`, opcjonalnie `anti_flags`, `metadata_status`, `source_version`, `updated_at` |
| `item_family` | stabilny identyfikator, nazwa, pochodzenie/wersja mapowania |
| `bonus_definition` | `attr_type` PK, stabilny kod, nazwa, jednostka, skala, rodzaj numeric/flag, informacja o weryfikacji |
| `item_base_attribute` (później) | VNUM, typ, wartość i indeks bazowego bonusu, gdy potrzebne będą filtry po statystykach bazowych |

Nie ma potrzeby powielać wymaganego poziomu w każdym historycznym rekordzie oferty. Katalog synchronizuje się oddzielnie, a oferty łączy z nim po VNUM w schemacie wybranego serwera. Dla historycznej analizy metadanych po aktualizacjach gry potrzebna będzie później wersjonizacja; pierwszy zakres dotyczy bieżącego rynku.

Importer katalogu powinien być oddzielny od importu obserwacji. Katalog zawiera pochodzenie i status weryfikacji. Brak wpisu nie może usuwać oferty z wyszukiwania bez filtrów katalogowych, więc stosować `LEFT JOIN` lub dołączać katalog tylko tam, gdzie jest potrzebny. Nie dodawać od razu wymagającego kompletności FK z ofert do katalogu.

Mapowanie `attr_type` Beavium potwierdzić na znanych egzemplarzach/snapshotach i źródle definicji serwera. `ItemBonusCatalog.describe(server, ...)` zwraca nazwy tylko dla Pandory. Numeracja podobna do ogólnej jest przesłanką do weryfikacji, a nie gotowym dowodem. Dostępne bonusy i jednostki dostarczać frontendowi z API serwera.

## 7. Kontrakt API i zapytania

Proponowane rozszerzenia:

- `GET /api/v1/servers/{server}/market/overview?sort=shopCountDesc&category=...&limit=12` — karty i informacja o skanie.
- `GET /api/v1/servers/{server}/items/filter-options` — kategorie, bonusy i jednostki, mapy/kanały oraz pokrycie metadanych.
- Rozbudowany istniejący `GET .../items`: obecne parametry plus kategoria, poziom, cena, mapa, kanał, bonusy, sortowanie i opcjonalny `snapshotId`.

Możliwe kodowanie powtarzalnych bonusów w GET: `bonus=TYPE:MIN:MAX`, z pustymi granicami, np. `bonus=TYPE:1500:`. `TYPE` oznacza zweryfikowany typ danego serwera, nie nazwę wpisaną przez użytkownika. DTO z jawnymi polami wewnątrz backendu; gdy filtr rozrośnie się o grupy logiczne, osobny endpoint POST z JSON jest czytelniejszy. Nie potrzeba go do pierwszej wersji.

Sortowanie ofert: cena rosnąco/malejąco, ilość rosnąco/malejąco, nazwa oraz wartość wybranego bonusu. Dla sortowania po bonusie ustalić obsługę wielokrotnych typów i braków, np. maksymalna pasująca wartość, braki na końcu. Czas obserwacji można dodać, lecz w obecnej odpowiedzi API jest tylko data (`LocalDate`), więc precyzyjne „najnowsze” wymaga także zwracania pełnego czasu.

Przykładowy fragment SQL dla dwóch warunków na bonusy:

```sql
WHERE EXISTS (
    SELECT 1 FROM shop_listing_attribute a
    WHERE a.listing_id = l.id
      AND a.attr_type = :bonusType1
      AND a.attr_value >= :minimum1
)
AND EXISTS (
    SELECT 1 FROM shop_listing_attribute a
    WHERE a.listing_id = l.id
      AND a.attr_type = :bonusType2
      AND a.attr_value >= :minimum2
      AND a.attr_value <= :maximum2
)
```

Do obecnego `matching_listings` dodać te warunki oraz filtry katalogu/ceny/mapy. Potem zachować agregowanie identycznych ofert, globalne `ORDER BY`, liczenie dopasowań i dopiero `LIMIT/OFFSET`. Nie łączyć wprost wielu bonusów przez JOIN w sposób mnożący oferty i ilości. `EXISTS` pozwala uniknąć takiej zmiany liczności.

Wartości zawsze bindować jako parametry. Sortowanie wybierać z enumu i zamkniętej listy fragmentów SQL. Walidować liczbę bonusów, typy, zakresy, długość nazwy i wielkość strony. Proponowany limit pierwszej wersji: 5 warunków bonusowych. Bez nazw schematów przekazywanych przez klienta; zachować obecny routing serwera.

Wynik powinien podawać `snapshotId`, czas skanu i liczbę wyników po wszystkich filtrach. Kolejne strony mogą korzystać z tego samego snapshotu, aby nowy skan nie zmieniał rynku w środku paginacji. Sortowanie musi mieć stabilny dodatkowy klucz.

Statystyki ceny trzeba jawnie opisać: obecny endpoint dotyczy całego VNUM. Po ustawieniu bonusów nadal może być pokazywany jako „cały rynek tego przedmiotu”, albo przyjmować te same filtry i obliczać statystyki dopasowanych ofert. Nie opisywać obecnych statystyk jako ceny ekwipunku z wybranymi bonusami.

## 8. Wydajność

Obecne indeksy obejmują VNUM, `lower(item_name)`, czas obserwacji, referencję do skanu i klucze główne tabel bonusów/socketów. Klucz `(listing_id, slot_index)` już pomaga w znajdowaniu bonusów konkretnej oferty. Brakuje indeksu umożliwiającego rozpoczynanie od typu i wartości bonusu.

Kandydaci do pomiaru, nie lista indeksów do automatycznego dodania:

```sql
CREATE INDEX ix_attribute_type_value_listing
    ON shop_listing_attribute (attr_type, attr_value, listing_id);

CREATE INDEX ix_definition_category_level
    ON item_definition (category_code, required_level, item_vnum);

CREATE INDEX ix_listing_observation_vnum_price
    ON shop_listing (observation_id, item_vnum, unit_price);
```

Pierwszy jest kandydatem dla wyszukiwania samego bonusu. Przy bardzo wąskim VNUM może wystarczać istniejący indeks po `listing_id`; dodatkowy `(listing_id, attr_type, attr_value)` ocenić dopiero po pomiarach. Kolejność kolumn ma znaczenie dla B-tree: równość typu/kategorii przed zakresem wartości/poziomu. [Dokumentacja PostgreSQL 18](https://www.postgresql.org/docs/18/indexes-multicolumn.html).

Obecne wyszukiwanie `unaccent(lower(item_name)) LIKE '%fragment%'` nie odpowiada istniejącemu zwykłemu indeksowi `lower(item_name)`. Proponuję przechowywany i aktualizowany przy imporcie znormalizowany `search_name` oraz GIN `gin_trgm_ops`; takie indeksy wspierają wyszukiwanie fragmentów przez LIKE/ILIKE. Bardzo krótkie frazy mogą nadal wymagać szerokiego odczytu. Nie tworzyć bez sprawdzenia indeksu funkcyjnego na `unaccent`, który ma wymagania dotyczące niezmienności funkcji. [Dokumentacja pg_trgm](https://www.postgresql.org/docs/18/pgtrgm.html).

Ranking strony głównej powinien być jednym zapytaniem agregującym aktualny rynek, z cache zależnym od serwera, snapshotu i wariantu rankingu. Nie pobierać ofert wszystkich VNUM osobnymi requestami i nie uruchamiać pełnych histogramów dla każdej karty. Na początek wystarczą liczba sklepów, ilość i minimum ceny.

Nie ma jeszcze pomiarów uzasadniających pełną dodatkową tabelę aktualnych ofert. Zacząć od istniejących tabel, filtrowania przed kosztowną agregacją i indeksów. Jeżeli pomiary pokażą koszt powtarzanej kanonizacji skanów lub rankingu, dodać preliczone podsumowania/tabelę bieżącego snapshotu. Widok materializowany jest alternatywą, ale wymaga odświeżania i świadomego zarządzania aktualnością. [Dokumentacja PostgreSQL](https://www.postgresql.org/docs/18/rules-materializedviews.html).

Istotna zależność importu: `update_market.py` i `tools/import_sqlite.py` wysyłają publiczny rekord skanu w pierwszej paczce, zanim nadejdą pozostałe obserwacje. Ranking/cache uruchamiany tylko przy pojawieniu się nowego run ID mógłby utrwalić częściowy skan. Docelowo: import niepubliczny, potwierdzenie wszystkich paczek i liczności, przygotowanie podsumowania, a następnie atomowe udostępnienie. Operacja finalizacji musi być idempotentna i odporna na przerwany import. Przy pozostawieniu obecnego importu cache wymaga uwzględniania kolejnych paczek i jawnej informacji o częściowych danych.

Pomiar na odizolowanej kopii reprezentatywnych danych PostgreSQL: `EXPLAIN (ANALYZE, BUFFERS)` dla samego bonusu, VNUM + bonusu, kategorii + poziomu + kilku bonusów, tekstu, sortowania i dalszej strony. Sprawdzić czasy i liczbę odczytanych wierszy przy rosnącej historii, koszty `count(*) OVER()` i agregacji podpisów bonusów/socketów. Nie podawać gwarancji czasów bez takich pomiarów. [Dokumentacja EXPLAIN](https://www.postgresql.org/docs/18/using-explain.html).

Przy głębokiej paginacji rozważyć cursor/keyset ze stabilnym porządkiem i przypiętym snapshotem. Nie jest to warunek pierwszej wersji przy stronie po 8–20 wyników.

## 9. Kolejność wdrożenia i kryteria odbioru

1. **Dane Beavium:** zakończony nowy skan z bonusami/socketami, potwierdzony import i definicje bonusów. Brak danych odczytu nie jest prezentowany jako pewny brak bonusów. Zachować niezmienność istniejących obserwacji; uzupełnione dane starej obserwacji mogą być odrzucone przez mechanizm fingerprint/hash, więc bezpieczną podstawą jest nowy zweryfikowany skan.
2. **Przegląd rynku:** endpoint kart, ranking po liczbie sklepów, czas skanu, obsługa braku danych, odświeżanie po pełnym imporcie. Ten zakres nie wymaga jeszcze metadanych poziomu.
3. **Wyszukiwanie w całej bazie aktualnego rynku:** cena, mapa/kanał, bonusy i globalne sortowanie. Parametryzowane zapytania i indeksy uzasadnione pomiarami. UI bez filtrów ograniczonych do jednej strony.
4. **Katalog Beavium:** zweryfikowane kategorie/podkategorie, poziomy i rodziny. Import odrębny od ofert i jawny stan braków. Włączenie tych filtrów.
5. **Pozostałe serwery:** osobne katalogi i słowniki bonusów, wspólny kontrakt i komponenty. Nie kopiować danych Beavium jako definicji innych serwerów.
6. **Rozwinięcia:** najczęściej wyszukiwane, filtry w ulubionych, statystyki filtrowanych ofert, ewentualnie porównania skanów i okazje w porównywalnych grupach.

Weryfikacja implementacji powinna obejmować:

- Jedna oferta spełniająca oba bonusy i dwie oferty tego samego sklepu spełniające po jednym: tylko pierwsza pasuje w trybie AND.
- Granice zakresów, wartości ujemne, obecność bez progu i powtórzony typ bonusu.
- Wynik istniejący dopiero poza pierwszą stroną musi zostać znaleziony po filtracji, a globalna cena malejąco musi zaczynać się od najdroższych dopasowań całego rynku.
- Brak podwajania ilości/ofert po filtrowaniu i zachowanie dotychczasowej agregacji identycznych pozycji.
- Różne poziomy wariantów +0/+9, nieznane metadane, kategoria z katalogu i rozdzielenie wymaganej klasy od samych flag.
- Izolacja trzech serwerów; ten sam numer bonusu/VNUM może mieć inne znaczenie.
- Ranking liczy sklep raz dla danego przedmiotu/rodziny; statystyki i ceny wariantów z socketów nie są bezwarunkowo mieszane.
- Import częściowy, retry, finalizacja i odświeżenie cache; brak przedwczesnego rankingu dla niepełnego skanu.
- Wydajność na PostgreSQL przy reprezentatywnym wolumenie, aktualne statystyki planera i brak N+1 requestów dla strony głównej.

## 10. Granice przeprowadzonej analizy

Przejrzano aktualny kod obu repozytoriów, migracje, importer i analitykę oraz zasoby katalogu i modele danych lokalnego projektu skanera. Wykonano odczyty SQLite: schemat, liczności, kompletność atrybutów/socketów według skanu, ranking dostępności i porównanie pokrycia VNUM z ogólnym proto. Sprawdzono dokumentację PostgreSQL 18 dotyczącą indeksów, pg_trgm, preliczanych wyników i planów zapytań.

Nie połączono się z produkcyjną bazą, nie sprawdzono jej rzeczywistych indeksów/planów ani wersji wdrożonego kodu. Nie mierzono wydajności proponowanych zapytań na PostgreSQL. Nie uruchamiano testów aplikacji, ponieważ nie zmieniano kodu wykonywalnego. Stan lokalnej bazy skanera jest zmienny i nie stanowi gwarancji stanu publicznego rynku.

Jedyny utworzony plik to ten raport. Istniejące lokalne zmiany importera i skryptów aktualizacji zostały zachowane.
