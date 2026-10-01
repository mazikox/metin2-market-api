"""Private, on-demand report from existing Caddy logs; no visitor-side tracking."""
import collections
import datetime as dt
import ipaddress
import json
import re
import sys
from urllib.parse import parse_qs, urlsplit
from zoneinfo import ZoneInfo

HOSTS = {'metin2bazar.pl', 'www.metin2bazar.pl', 'metin2market.mazikox.pl'}
BOT = re.compile(r'bot|spider|crawl|headless|curl|wget|python|monitor|uptime|httpclient', re.I)
ITEMS = re.compile(r'^/backend/api/v1/servers/(pandora|elder|beavium)/items$')


def summarize(lines):
    visitors = {}
    daily = collections.defaultdict(lambda: {'ips': set(), 'pages': 0, 'results': 0, 'searches': 0})
    ignored = 0
    parsed = []
    for line in lines:
        try:
            entry = json.loads(line)
            request = entry.get('request', {})
            if not str(entry.get('logger', '')).startswith('http.log.access'):
                continue
            host = request.get('host', '').lower().split(':')[0]
            if host not in HOSTS or request.get('method') != 'GET' or not 200 <= int(entry.get('status', 0)) < 300:
                continue
            headers = {k.lower(): v for k, v in request.get('headers', {}).items()}
            agent = headers.get('user-agent', [])
            agent = ' '.join(agent) if isinstance(agent, list) else str(agent)
            if not agent or BOT.search(agent):
                ignored += 1
                continue
            address = ipaddress.ip_address(request['remote_ip'])
            address = str(getattr(address, 'ipv4_mapped', None) or address)
            timestamp = float(entry['ts'])
            url = urlsplit(request.get('uri', ''))
            endpoint = ITEMS.fullmatch(url.path)
            if url.path != '/' and not endpoint:
                continue
            parsed.append((timestamp, address, url, endpoint.group(1) if endpoint else None))
        except (ValueError, TypeError, KeyError, OverflowError):
            continue
    for timestamp, address, url, server in sorted(parsed, key=lambda item: item[0]):
        day = dt.datetime.fromtimestamp(timestamp, ZoneInfo('Europe/Warsaw')).date().isoformat()
        row = visitors.setdefault(address, {'pages': 0, 'results': 0, 'searches': 0, 'last': timestamp, 'seen_servers': set(), 'recent': {}})
        row['last'] = max(row['last'], timestamp)
        daily[day]['ips'].add(address)
        if server is None:
            row['pages'] += 1
            daily[day]['pages'] += 1
            continue
        row['results'] += 1
        daily[day]['results'] += 1
        params = parse_qs(url.query)
        first_server_request = server not in row['seen_servers']
        row['seen_servers'].add(server)
        if params.get('page', ['0']) != ['0']:
            continue
        query = params.get('query', [''])[0].strip()
        vnums = tuple(sorted(set(params.get('vnum', []))))
        if not query and not vnums:
            continue
        # Pandora loads this search automatically on opening the application.
        initial = query.lower() == 'zatruty miecz' and set(vnums) == {str(n) for n in range(180, 190)}
        if first_server_request and server == 'pandora' and initial:
            continue
        signature = (server, query.lower(), vnums)
        previous = row['recent'].get(signature, float('-inf'))
        row['recent'][signature] = timestamp
        # Retries and duplicate requests within two seconds are one estimate.
        if timestamp - previous < 2:
            continue
        row['searches'] += 1
        daily[day]['searches'] += 1
    # Addresses only group data in memory; neither the report nor output exposes them.
    rows = sorted(visitors.values(), key=lambda row: (-row['searches'], -row['results'], -row['last']))
    return {
        'unique_ips': len(rows),
        'active_ips': sum(row['results'] > 0 for row in rows),
        'ignored_bot_requests': ignored,
        'visitors': [{k: row[k] for k in ('pages', 'results', 'searches', 'last')} for row in rows],
        'daily': [{'day': day, 'unique_ips': len(row['ips']), **{k: row[k] for k in ('pages', 'results', 'searches')}} for day, row in sorted(daily.items(), reverse=True)],
    }


def html_report(data):
    created = dt.datetime.now(ZoneInfo('Europe/Warsaw')).strftime('%d.%m.%Y %H:%M')
    total = lambda key: sum(row[key] for row in data['visitors'])
    daily = ''.join(f"<tr><td>{r['day']}</td><td>{r['unique_ips']}</td><td>{r['pages']}</td><td>{r['results']}</td><td>{r['searches']}</td></tr>" for r in data['daily'])
    visitors = ''.join(f"<tr><td>Odwiedzający {i}</td><td>{r['pages']}</td><td>{r['results']}</td><td>{r['searches']}</td><td>{dt.datetime.fromtimestamp(r['last'], ZoneInfo('Europe/Warsaw')).strftime('%d.%m %H:%M')}</td></tr>" for i, r in enumerate(data['visitors'], 1))
    return f'''<!doctype html><html lang="pl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Metin2 Bazar — raport użycia</title>
<style>body{{background:#101111;color:#eee;font:16px/1.6 system-ui;margin:0}}main{{max-width:1100px;margin:auto;padding:30px}}h1{{font-weight:500}}p{{color:#bbb}}.cards{{display:flex;flex-wrap:wrap;gap:16px}}.card{{border:1px solid #555;padding:20px;flex:1}}strong{{font-size:32px;color:#d1aa71;display:block}}.table{{overflow:auto}}table{{border-collapse:collapse;width:100%;white-space:nowrap}}th,td{{text-align:left;padding:10px;border-bottom:1px solid #444}}small{{color:#bbb}}</style>
<main><h1>Metin2 Bazar — raport użycia</h1><p>Prywatny raport z ostatnich 7 dni dostępnych logów. Aktualizacja: {created}.</p>
<div class="cards"><div class="card"><strong>{data['active_ips']}</strong>Różne IP pobierające wyniki katalogu</div><div class="card"><strong>{total('searches')}</strong>Szacowane wyszukiwania</div><div class="card"><strong>{total('results')}</strong>Pobrania wyników</div></div>
<p>Wszystkie IP z wejściem lub pobraniem wyników: {data['unique_ips']}. Główny licznik uwzględnia tylko IP, które pobrały wyniki katalogu — samo otwarcie strony nie oznacza aktywnego użycia. Wcześniejszy frontend odpytywał osobną domenę API bez tych logów: przed uruchomieniem proxy /backend dane o wyszukiwaniach są niekompletne; zero nie dowodzi braku użycia.</p>
<p>Jedno IP to jedno oznaczenie w tym raporcie. Kilka osób może mieć wspólne IP; zmiana IP powoduje nowy wpis. Oznaczenia mogą zmienić się po odświeżeniu raportu. Nie oznaczają nowych ani zidentyfikowanych osób. Wykluczono rozpoznane boty i narzędzia techniczne ({data['ignored_bot_requests']} żądań); niewykryte boty i testy w przeglądarce mogą pozostać.</p>
<h2>Dziennie</h2><div class="table"><table><thead><tr><th>Dzień</th><th>Różne IP</th><th>Wejścia na katalog</th><th>Pobrania wyników</th><th>Szacowane wyszukiwania</th></tr></thead><tbody>{daily}</tbody></table></div>
<h2>Użycie według IP</h2><div class="table"><table><thead><tr><th>Oznaczenie</th><th>Wejścia</th><th>Pobrania wyników</th><th>Wyszukiwania</th><th>Ostatnia aktywność</th></tr></thead><tbody>{visitors}</tbody></table></div>
<p>Wyszukiwania to udane pobrania pierwszej strony z zapytaniem lub identyfikatorem przedmiotu, po odjęciu pierwszego automatycznego wyszukania Pandory i duplikatów do 2 sekund. Podpowiedzi, statystyki cen i kolejne strony wyników nie zwiększają tego licznika. Odświeżenie strony, automatyczny ponowny odczyt i niewykryte boty mogą zawyżać wynik — to szacunek, nie dokładna liczba kliknięć „Szukaj”.</p>
<small>Raport nie pokazuje IP, treści wyszukiwań ani pełnych logów. Nie zawiera skryptów analitycznych i nie jest publikowany w internecie.</small></main></html>'''


if __name__ == '__main__':
    print(html_report(summarize(sys.stdin)))
