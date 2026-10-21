"""Exercise the deployed backend without changing roles or personnel records."""
import argparse
import getpass
import json
import urllib.request
import urllib.error
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://120.46.86.147:8081/api')
parser.add_argument('--account', default='admin')
args = parser.parse_args()
password = getpass.getpass('Password (not printed): ')
token = None

def request(method, path, body=None, expected=200):
    data = None if body is None else json.dumps(body).encode('utf-8')
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(args.base_url.rstrip('/') + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    if status != expected:
        raise RuntimeError('%s %s: expected %s, got %s' % (method, path, expected, status))
    print('%s %s -> %s' % (method, path, status))
    return json.loads(raw) if raw else None

try:
    request('GET', '/me', expected=401)
    login = request('POST', '/auth/login', {'account': args.account, 'password': password})
    password = None
    token = login['token']
    me = request('GET', '/me')
    permissions = request('GET', '/me/permissions')
    apps = request('GET', '/me/applications')
    if apps:
        app_id = apps[0]['id']
        request('POST', '/me/applications/%s/enter' % app_id)
        modules = request('GET', '/me/applications/%s/modules' % app_id)['modules']
        if modules:
            request('GET', '/me/applications/%s/modules/%s/features' % (app_id, modules[0]['id']))
    if permissions:
        p = permissions[0]
        result = request('POST', '/authorize', {'appCode': p['appCode'], 'permissionCode': p['code'], 'requestId': 'live-demo-' + uuid.uuid4().hex})
        if result['allowed'] is not True:
            raise RuntimeError('Expected chosen active demo permission to be allowed')
    denied = request('POST', '/authorize', {'appCode': 'nonexistent-live-demo', 'permissionCode': 'nonexistent-live-demo', 'requestId': 'live-demo-' + uuid.uuid4().hex})
    if denied['allowed'] is not False:
        raise RuntimeError('Unknown permission unexpectedly allowed')
    if me['canManage']:
        for path in ['/dashboard', '/users/page?size=1', '/users/filters', '/roles', '/applications', '/authorization-logs?limit=1']:
            request('GET', path)
    else:
        request('GET', '/dashboard', expected=403)
    print('Live API demonstration passed; response bodies and tokens were not printed.')
finally:
    if token:
        request('POST', '/auth/logout')
