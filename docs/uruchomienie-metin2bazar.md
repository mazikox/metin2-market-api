# Uruchomienie metin2bazar.pl

Stan na 1 października 2026: DNS, HTTPS i konfiguracja Caddy są aktywne.
Frontend z podstronami i wyborem opcjonalnej analityki jest opublikowany.
Lustro pozostaje dostępne z noindex. Konfiguracja jest w `ops/Caddyfile`.

## DNS w OVH

| Typ | Nazwa | Wartość |
| --- | --- | --- |
| A | @ (puste pole subdomeny w OVH) | 146.59.63.158 |
| CNAME | www | metin2bazar.pl. |

Nie potrzeba osobnych rekordów API ani analityki: publiczne ścieżki to `/backend/api/...`
i `/metrics/...`. Nie dodawać AAAA bez potwierdzonego IPv6 VPS. Jeżeli OVH dodało parking,
usunąć konfliktujące A/AAAA dla domeny głównej i rekordy A/AAAA/CNAME dla www.
Nie usuwać rekordów MX/TXT poczty, jeśli istnieją. DNS nie blokuje indeksowania.

## Zachowanie obu domen

- metin2bazar.pl: indeksowanie włączone, canonical i sitemapa wskazują tę domenę.
- www.metin2bazar.pl: stałe przekierowanie do wersji bez www.
- metin2market.mazikox.pl: ten sam katalog i API, bez przekierowania do nowej domeny;
  odpowiedzi mają `X-Robots-Tag: noindex`, robots umożliwia odczyt noindex, sitemapę wyłączono.
- Stare API, panel analityczny, pandora redirect i game pozostają w konfiguracji VPS,
  aby nie przerwać już działających usług. Nie występują w nowym frontendzie ani jego buildzie.
- Ulubione są osobne dla każdego originu; zmiana domeny nie synchronizuje ich automatycznie.

## Kolejność aktywacji po DNS

1. Potwierdzić publiczne A i www oraz brak sprzecznego AAAA/parkingu.
2. Zachować aktualny Caddyfile i aktualne pliki frontendu poza katalogiem publikacji.
3. Utworzyć `/var/www/metin2bazar` z właścicielem użytkownika wdrożeniowego (na tym VPS debian)
   i uprawnieniami odczytu dla Caddy. Nie usuwać `/var/www/mazikox`; służy do powrotu do starej wersji.
4. Opublikować produkcyjny build z repo web w nowym katalogu. Do workflow dodano `npm test`;
   build używa `VITE_API_BASE_URL=/backend`. Workflow nie aktualizuje Caddy.
5. Wdrożyć backend ze zaktualizowanym CORS i compose. Ewentualne `CORS_ALLOWED_ORIGINS` w .env
   VPS ma pierwszeństwo — sprawdzić, czy zawiera metin2bazar.pl i www oraz stare wymagane adresy.
6. Odczytać jeszcze raz aktywny Caddyfile i upewnić się, że nie dodano innych usług po przygotowaniu
   tej wersji. Zweryfikować docelowy plik `sudo caddy validate --config ... --adapter caddyfile`,
   zainstalować go do `/etc/caddy/Caddyfile` i wykonać `sudo systemctl reload caddy`.
   Po poprawnym DNS Caddy uzyska certyfikaty HTTPS dla obu nowych hostów.
7. Przy aktywacji ustawić nazwę i domenę istniejącej witryny w Umami; przygotowano
   `ops/umami-metin2bazar.sql`. Nie resetować istniejących statystyk ani nagrywania sesji.
8. Sprawdzić publicznie HTTPS, przekierowanie www, API wszystkich trzech serwerów, podstrony,
   robots, sitemapę, 404 i skrypt analityki. Na starej domenie potwierdzić noindex, działający katalog,
   API i wyłączenie sitemapy. Dopiero potem zgłosić nową domenę/sitemapę w Search Console.

Jeśli aktywacja się nie powiedzie: przywrócić zachowany Caddyfile i wykonać reload.
Stary katalog `/var/www/mazikox` nie jest zmieniany przez przygotowany nowy workflow.
Przywrócenie konfiguracji wraca więc do wcześniejszej działającej strony.

## Sprawdzone

- Produkcyjny build oraz osiem testów klienta API.
- Pięć testów CORS, w tym nowa domena i www; stara kompatybilność zachowana.
- Caddyfile zwalidowany na rzeczywistej wersji Caddy na VPS.
- Izolowany test na dwóch portach bind 127.0.0.1: nowa strona, lustro z noindex,
  robots, sitemapa tylko na nowej stronie, podstrony i 404, proxy publicznego API,
  proxy skryptu Umami. Nieistniejące i nieudostępniane ścieżki zwracają 404.
- Nowy frontend i artefakty builda nie zawierają mazikox.pl.

## Pozostałe sprawdzenia organizacyjne

Treść prywatności, administrator i kontakt oraz wybór opcjonalnej analityki są przygotowane
w nowym frontendzie. Retencja logów do 90 dni i statystyk przez 12 miesięcy działa na VPS.
Panel OVH potwierdził codzienny automatyczny backup o 01:30 UTC i jeden punkt
przywracania. Lokalizacja, warunki powierzenia i osobne snapshoty wymagają sprawdzenia.
Treść i mechanizm zgody wdrożono razem; w przeglądarce produkcyjnej sprawdzono
brak skryptu Umami przed zgodą i po cofnięciu. Potwierdzono HTTPS, przekierowanie www,
API trzech rynków oraz noindex lustra. Dokończyć audyt bezpieczeństwa osobno.
