"""Install catalog statistics secrets on the VPS, reading credentials only from stdin.
Run as root. Requires the already-installed Python bcrypt module.
Does not restart services, deploy code, rotate valid analytics keys or print secrets.
"""
import json
import os
import pathlib
import pwd
import re
import secrets
import subprocess
import sys
import tempfile
import bcrypt


def read_env(path):
    if path.is_symlink() or (path.exists() and not path.is_file()):
        raise RuntimeError('Expected a regular environment file')
    text = path.read_text() if path.exists() else ''
    values = {}
    for line in text.splitlines():
        match = re.match(r'^([A-Z_]+)=(.*)$', line.strip())
        if match: values[match[1]] = match[2].strip().strip("\"'")
    return text, values


def atomic_write(path, text, uid=0, gid=0):
    if path.is_symlink(): raise RuntimeError('Refusing a symlink destination')
    descriptor, temporary = tempfile.mkstemp(prefix='.stats-', dir=path.parent)
    try:
        os.fchmod(descriptor, 0o600)
        os.fchown(descriptor, uid, gid)
        with os.fdopen(descriptor, 'w') as output:
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)


def update_env(text, replacements):
    lines = [line for line in text.splitlines() if not any(re.match(r'^' + key + r'\s*=', line.strip()) for key in replacements)]
    return '\n'.join(lines).rstrip() + '\n' + ''.join(key + '=' + value + '\n' for key, value in replacements.items())


def main():
    if os.geteuid() != 0: raise RuntimeError('Run as root')
    payload = json.load(sys.stdin)
    repo = pathlib.Path(payload['repo']).resolve(strict=True)
    if repo != pathlib.Path('/home/debian/metin2-market-api'): raise RuntimeError('Unexpected VPS checkout')
    username = payload['username']
    password = payload['password']
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,32}', username) or len(password) < 32:
        raise RuntimeError('Invalid admin credentials')
    env_path = repo / '.env'
    if not env_path.is_file(): raise RuntimeError('Existing API environment is required')
    text, values = read_env(env_path)
    secret = values.get('ANALYTICS_SECRET') or secrets.token_hex(32)
    proxy = values.get('ANALYTICS_PROXY_TOKEN') or secrets.token_hex(32)
    if not re.fullmatch(r'[a-fA-F0-9]{64}', secret) or not re.fullmatch(r'[a-fA-F0-9]{64}', proxy):
        raise RuntimeError('Existing analytics secrets must be reviewed before replacement')
    secret_dir = pathlib.Path('/etc/metin2bazar')
    if secret_dir.is_symlink(): raise RuntimeError('Refusing a symlink secrets directory')
    secret_dir.mkdir(mode=0o700, exist_ok=True)
    os.chmod(secret_dir, 0o700)
    os.chown(secret_dir, 0, 0)
    caddy_env = secret_dir / 'stats.env'
    _, current = read_env(caddy_env)
    if current.get('STATS_ADMIN_PASSWORD_HASH'):
        if current.get('STATS_ADMIN_USER') != username or not bcrypt.checkpw(password.encode(), current['STATS_ADMIN_PASSWORD_HASH'].encode()):
            raise RuntimeError('Existing admin password differs; refusing an implicit rotation')
        hashed = current['STATS_ADMIN_PASSWORD_HASH']
    else:
        hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=14)).decode()
    dropin_dir = pathlib.Path('/etc/systemd/system/caddy.service.d')
    if dropin_dir.is_symlink(): raise RuntimeError('Refusing a symlink service directory')
    dropin_dir.mkdir(mode=0o755, exist_ok=True)
    dropin = dropin_dir / '90-stats-environment.conf'
    desired = '[Service]\nEnvironmentFile=/etc/metin2bazar/stats.env\n'
    if dropin.exists() and dropin.read_text() != desired:
        raise RuntimeError('Existing service drop-in differs; review it first')
    account = pwd.getpwnam('debian')
    # Keep a private original environment backup only on first installation.
    backup = secret_dir / 'api.env.before-stats'
    if not backup.exists(): atomic_write(backup, text)
    atomic_write(env_path, update_env(text, {'ANALYTICS_SECRET': secret, 'ANALYTICS_PROXY_TOKEN': proxy}), account.pw_uid, account.pw_gid)
    atomic_write(caddy_env, 'ANALYTICS_PROXY_TOKEN=' + proxy + '\nSTATS_ADMIN_USER=' + username + "\nSTATS_ADMIN_PASSWORD_HASH='" + hashed + "'\n")
    atomic_write(dropin, desired)
    os.chmod(dropin, 0o644)
    subprocess.run(['systemctl', 'daemon-reload'], check=True)
    print('OK: analytics secrets installed; matching Caddy proxy token; bcrypt admin hash; private permissions.')
    print('OK: existing API variables preserved; no services restarted and no code deployed.')


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('Configuration failed: ' + type(error).__name__ + ' (no secret values printed)', file=sys.stderr)
        sys.exit(1)
