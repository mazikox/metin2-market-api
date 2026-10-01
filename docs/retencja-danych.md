# Retencja logów i statystyk — Metin2 Bazar

Wdrożone na VPS 1 października 2026. Nie zmienia danych sklepów, ofert, cen, skanów ani ulubionych w przeglądarce.

## Ustalone okresy i cel

- Logi VPS: do 90 dni, na potrzeby diagnostyki awarii i analizy nadużyć. Dotyczy wspólnego journald, a więc także pozostałych usług VPS. Limit miejsca na dysku może skrócić ten okres.
- Odsłony i zdarzenia Umami dla witryny `6754c5f8-9c5d-4b21-babb-5fa96b970c5f`: 12 miesięcy, aby porównywać ruch i wykorzystanie katalogu w ciągu roku. Czyszczenie codzienne, więc fizyczne usunięcie następuje w kolejnym przebiegu po przekroczeniu okresu. Dane sesji pozostają tylko, dopóki odwołuje się do nich aktywność w przechowywanym okresie.
- Inne witryny w Umami: nieobjęte tym zadaniem.
- Oferty i dane sklepów: nieobjęte tym zadaniem i nie usuwane przez nie.
- Ulubione: pozostają na urządzeniu użytkownika do ich usunięcia lub wyczyszczenia danych witryny.

Okresy stanowią decyzję operacyjną po prośbie użytkownika o dłuższe, ograniczone przechowywanie. Nie są terminami narzuconymi przez prawo i nie zastępują oceny podstaw prawnych poszczególnych operacji.

## Mechanizm

Źródła w `ops/retention/`:

- `90-metin2bazar-retention.conf` → `/etc/systemd/journald.conf.d/90-metin2bazar-retention.conf`: MaxRetentionSec=89day, MaxFileSec=1day. Dziennik jest archiwizowany co najwyżej po dniu.
- `metin2bazar-retention.sh` → `/usr/local/sbin/metin2bazar-retention`: rotacja dziennika, usunięcie archiwów starszych niż 89 dni, a następnie SQL Umami. Pozostaje co najwyżej około 90 dni wpisów, uwzględniając dzienny interwał.
- `umami-prune.sql` → `/etc/metin2bazar/umami-prune.sql`: transakcja, blokada doradcza, limity czasu i sprawdzenie identyfikatora witryny. Usuwa przeterminowane zdarzenia i powiązane dane, zachowując sesje używane przez bieżące zdarzenia.
- Jednostka `metin2bazar-retention.service` i timer `metin2bazar-retention.timer`: codziennie o 03:00 UTC, z opóźnieniem do 5 minut, nadrabianie pominiętego uruchomienia po restarcie. W Polsce to 05:00 latem i 04:00 zimą.

Nie zmieniono Caddyfile, routingu, certyfikatów ani ustawienia noindex. Nowa konfiguracja domeny będzie korzystać z tego samego dziennika systemowego.

## Weryfikacja

- Test na tymczasowych kopiach tabel: usuwa stare dane tej witryny, także dane powiązane ze starymi zdarzeniami; zachowuje bieżące dane, sesję z bieżącą aktywnością i wszystkie rekordy innej witryny. Wszystkie kopie znikały przy zakończeniu połączenia.
- `systemd-analyze verify`: poprawne jednostki.
- Pierwsze rzeczywiste uruchomienie: Result=success, ExecMainStatus=0. Wszystkie dziewięć DELETE zwróciło 0; nie usunięto obecnych statystyk. Czyszczenie journald uwolniło 0 bajtów, ponieważ dziennik nie zawierał danych starszych od limitu.
- Timer włączony; konfiguracja journald odczytuje nowe limity.

## Kontrola i wyłączenie

```sh
sudo systemctl status metin2bazar-retention.timer
sudo systemctl show metin2bazar-retention.service -p Result -p ExecMainStatus
sudo journalctl -u metin2bazar-retention.service --since '7 days ago'
```

Aby zatrzymać przyszłe czyszczenie, wyłączyć timer oraz przywrócić poprzednią konfigurację journald. Nie przywraca to już usuniętych danych. Nie tworzyliśmy dodatkowych kopii bazy na potrzeby tej operacji, ponieważ nie było przeterminowanych rekordów. Przed zmianą okresów ponownie ocenić cel i konsekwencje.

## Granice kontroli

Retencja dotyczy aktywnej bazy Umami i lokalnych dzienników VPS. Panel OVH potwierdził codzienny automatyczny backup o 01:30 UTC i jeden dostępny punkt przywracania. Usunięte dane mogą pozostać w ostatniej kopii do zastąpienia kolejną; po przywróceniu należy ponownie wykonać czyszczenie. Osobne snapshoty i retencja własnych logów dostawcy nie zostały potwierdzone. Nie deklarować, że lokalny mechanizm obejmuje wszystkie kopie danych poza VPS.
