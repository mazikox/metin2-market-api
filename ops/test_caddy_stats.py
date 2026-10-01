"""Isolated real-Caddy tests for production routing, auth and proxy metadata.
Run after the web build: python ops/test_caddy_stats.py --web-dist ../metin-market-web/dist
No production services, credentials or databases are used.
"""
import argparse
import base64
import http.server
import json
import pathlib
import secrets
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request


def docker(*args, input=None):
    result = subprocess.run(['docker', *args], text=True, capture_output=True, input=input)
    if result.returncode: raise RuntimeError(result.stderr)
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--web-dist', required=True, type=pathlib.Path)
    args = parser.parse_args()
    web = args.web_dist.resolve()
    assert (web / 'index.html').is_file(), 'Build the frontend first'
    name = 'metin-stats-caddy-test-' + secrets.token_hex(5)
    password = secrets.token_urlsafe(20)
    proxy = secrets.token_hex(32)
    hashed = docker('run', '--rm', 'caddy:2-alpine', 'caddy', 'hash-password', '--plaintext', password)
    env = ['-e', 'STATS_ADMIN_USER=testadmin', '-e', 'STATS_ADMIN_PASSWORD_HASH=' + hashed,
           '-e', 'ANALYTICS_PROXY_TOKEN=' + proxy]
    authorization = 'Basic ' + base64.b64encode(('testadmin:' + password).encode()).decode()

    class Backend(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({key: self.headers.get(key) for key in
                ['X-Analytics-Proxy-Token', 'X-Analytics-Client-IP', 'X-Analytics-Admin']}).encode())
        def log_message(self, *_):
            pass
    backend = http.server.ThreadingHTTPServer(('0.0.0.0', 0), Backend)
    thread = threading.Thread(target=backend.serve_forever, daemon=True)
    thread.start()
    try:
        with tempfile.TemporaryDirectory(prefix='metin-stats-caddy-', dir=pathlib.Path(__file__).resolve().parent.parent / 'target') as directory:
            config = pathlib.Path(directory) / 'Caddyfile'
            original = pathlib.Path(__file__).with_name('Caddyfile').read_text(encoding='utf-8')
            config.write_text(original, encoding='utf-8')
            mount = ['-v', str(web) + ':/var/www/metin2bazar:ro']
            docker('run', '--rm', '-i', *env, 'caddy:2-alpine', 'caddy', 'validate', '--config', '/dev/stdin', '--adapter', 'caddyfile', input=original)
            print('PASS: production Caddyfile validates')
            fixture = original.replace('127.0.0.1:8080', 'host.docker.internal:' + str(backend.server_port))
            for host in ['metin2bazar.pl', 'www.metin2bazar.pl', 'metin2market.mazikox.pl', 'api.mazikox.pl', 'pandora.mazikox.pl', 'game.mazikox.pl']:
                fixture = fixture.replace('\n' + host + ' {', '\nhttp://' + host + ':80 {')
            config.write_text('{\n    auto_https off\n}\n' + fixture, encoding='utf-8')
            docker('create', '--name', name, '-p', '127.0.0.1::80', *env, *mount, 'caddy:2-alpine')
            docker('cp', str(config), name + ':/etc/caddy/Caddyfile')
            docker('start', name)
            port = json.loads(docker('inspect', name))[0]['NetworkSettings']['Ports']['80/tcp'][0]['HostPort']
            def request(host, path, auth=False, forged=False):
                headers = {'Host': host}
                if auth: headers['Authorization'] = authorization
                if forged:
                    headers.update({'X-Analytics-Proxy-Token': 'forged', 'X-Analytics-Client-IP': '198.51.100.200',
                                    'X-Analytics-Admin': 'attacker', 'X-Forwarded-For': '198.51.100.200'})
                try:
                    response = urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:' + port + path, headers=headers), timeout=10)
                except urllib.error.HTTPError as error:
                    response = error
                with response:
                    return response.status, response.headers, response.read()
            for _ in range(30):
                try:
                    if request('metin2bazar.pl', '/')[0] == 200: break
                except (OSError, urllib.error.URLError):
                    time.sleep(0.2)
            for host in ['metin2bazar.pl', 'metin2market.mazikox.pl']:
                for path in ['/admin/stats', '/admin/stats/', '/backend/api/v1/admin/stats', '/api/v1/admin/stats']:
                    status, headers, _ = request(host, path, forged=True)
                    assert status == 401, (host, path, status)
                    assert headers.get('WWW-Authenticate', '').startswith('Basic')
                status, headers, body = request(host, '/admin/stats', auth=True)
                assert status == 200 and b'id="root"' in body
                assert headers.get('Cache-Control') == 'no-store'
                status, headers, body = request(host, '/backend/api/v1/admin/stats', auth=True, forged=True)
                data = json.loads(body)
                assert status == 200 and data['X-Analytics-Admin'] == 'testadmin'
                assert data['X-Analytics-Proxy-Token'] == proxy
                assert data['X-Analytics-Client-IP'] != '198.51.100.200'
                assert headers.get('Cache-Control') == 'no-store'
            assert request('api.mazikox.pl', '/api/v1/admin/stats', forged=True)[0] == 401
            status, _, body = request('api.mazikox.pl', '/api/v1/admin/stats', auth=True, forged=True)
            assert status == 200 and json.loads(body)['X-Analytics-Admin'] == 'testadmin'
            for host, path in [('metin2bazar.pl', '/backend/api/v1/items'), ('api.mazikox.pl', '/api/v1/items')]:
                status, _, body = request(host, path, forged=True)
                data = json.loads(body)
                assert status == 200 and data['X-Analytics-Admin'] is None
                assert data['X-Analytics-Proxy-Token'] == proxy
                assert data['X-Analytics-Client-IP'] != '198.51.100.200'
            for path in ['/metrics/script.js', '/metrics/api/send', '/privacy-preferences.js']:
                assert request('metin2bazar.pl', path)[0] == 404, path
            print('PASS: panel/API require auth on catalog, mirror and API domain')
            print('PASS: authenticated panel/API work and disable caching')
            print('PASS: spoofed IP/proxy/admin headers are overwritten or removed')
            print('PASS: retired Umami endpoints return 404')
    finally:
        subprocess.run(['docker', 'rm', '-f', name], capture_output=True)
        backend.shutdown()
        backend.server_close()


if __name__ == '__main__':
    main()
