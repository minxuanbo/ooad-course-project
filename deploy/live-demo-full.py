"""Assert live authorization and departure scenarios using only dedicated demo identities."""
import argparse
import datetime
import getpass
import json
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

if not __debug__:
    raise RuntimeError('Run without -O: business assertions must remain enabled')

root = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--config', type=Path, default=root / '.local/live-demo/latest.json')
p.add_argument('--admin-password-stdin', action='store_true', help='Read from a secure pipe; never put passwords on the command line')
a = p.parse_args()
c = json.loads(a.config.read_text(encoding='utf-8'))
run, ids = c['run'], c['ids']
worker = run + '_worker'
uid, rid = ids[worker], ids[run + '_role']
base = 'http://120.46.86.147:8081/api'
count = 0
def req(method, path, body=None, token=None, expected=200, raw_auth=None):
    global count
    headers = {'Content-Type': 'application/json'}
    if token is not None:
        headers['Authorization'] = 'Bearer ' + token
    if raw_auth is not None:
        headers['Authorization'] = raw_auth
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=20) as r:
            status, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    if status != expected:
        raise RuntimeError(f'{method} {path}: expected {expected}, got {status}')
    count += 1
    print(f'{count:03} {method} {path} -> {status}')
    return json.loads(raw) if raw else None

admin = user = None
password = input() if a.admin_password_stdin else getpass.getpass('Admin password: ')
try:
    admin = req('POST', '/auth/login', {'account': 'admin', 'password': password})['token']
    password = None
    def manage(method, path, body=None, expected=200):
        return req(method, path, body, admin, expected)
    # Fail closed before any mutation if the manifest does not match server records.
    users = manage('GET', '/users?q=' + run)
    assert {u['username']: u['id'] for u in users} == {k: v for k, v in ids.items() if k != run + '_role'}
    assert all(u['status'] == 'ACTIVE' for u in users), 'Prepare a fresh run; do not reuse departed identities'
    roles = manage('GET', '/roles')
    role = next(r for r in roles if r['id'] == rid)
    assert role['code'] == run + '_role' and role['userCount'] == 0
    assert manage('GET', f'/users/{uid}/handover')['handover'] == {}
    perms = manage('GET', '/me/permissions')
    perm = perms[0]
    catalog = manage('GET', f'/roles/{rid}/permissions')
    pid = next(x['id'] for x in catalog if x['code'] == perm['code'])
    app = next(x for x in manage('GET', '/applications') if x['code'] == perm['appCode'])
    appid = app['id']
    for body, expected in [({},400), ({'account':worker},400), ({'account':worker,'password':'incorrect'},401), ({'account':run+'_missing','password':'incorrect'},401)]:
        req('POST','/auth/login',body,expected=expected)
    req('GET','/me',expected=401)
    req('GET','/me',token='invalid',expected=401)
    req('GET','/me',raw_auth='invalid',expected=401)
    req('POST','/auth/logout',raw_auth='invalid')
    user = req('POST','/auth/login',{'account':worker,'password':c['password']})['token']
    def employee(method,path,body=None,expected=200):
        return req(method,path,body,user,expected)
    assert employee('GET','/me')['canManage'] is False
    assert employee('GET','/me/permissions') == []
    assert employee('GET','/me/applications') == []
    employee('GET','/dashboard',expected=403)
    employee('PUT',f'/users/{uid}/roles',{'ids':[rid]},403)
    employee('POST',f'/me/applications/{appid}/enter',expected=403)
    employee('GET',f'/me/applications/{appid}/modules',expected=403)
    def check(allowed, **override):
        body={'appCode':perm['appCode'],'permissionCode':perm['code'],'requestId':run}
        body.update(override)
        assert employee('POST','/authorize',body)['allowed'] is allowed
    check(False)
    manage('PUT',f'/users/{uid}/roles',{},400)
    manage('PUT','/users/0/roles',{'ids':[]},404)
    manage('PUT',f'/roles/{rid}/permissions',{},400)
    manage('PUT',f'/roles/{rid}/permissions',{'ids':[pid,pid]})
    manage('PUT',f'/users/{uid}/roles',{'ids':[rid,rid]})
    assert any(x['id']==rid and x['assigned']==1 for x in manage('GET',f'/users/{uid}/roles'))
    check(True)
    check(True,requestId=None)
    check(False,appCode='missing')
    check(False,permissionCode=None)
    check(False,appCode=None)
    for field,length in [('appCode',192),('permissionCode',513),('requestId',192)]:
        employee('POST','/authorize',{field:'x'*length},400)
    assert len(employee('GET','/me/permissions?app='+urllib.parse.quote(perm['appCode']))) == 1
    assert employee('GET','/me/permissions?app=missing') == []
    assert len(employee('GET','/me/applications')) == 1
    assert employee('POST',f'/me/applications/{appid}/enter')['allowed'] is True
    modules=employee('GET',f'/me/applications/{appid}/modules')['modules']
    for module in modules:
        employee('GET',f'/me/applications/{appid}/modules/{module["id"]}/features')
    employee('GET',f'/me/applications/{appid}/modules/0/features',expected=404)
    employee('POST','/me/applications/0/enter',expected=404)
    employee('GET','/me/applications/0/modules',expected=404)
    manage('PUT',f'/roles/{rid}/permissions',{'ids':[]})
    check(False)
    manage('PUT',f'/roles/{rid}/permissions',{'ids':[pid]})
    manage('PUT',f'/users/{uid}/roles',{'ids':[]})
    check(False)
    manage('PUT',f'/users/{uid}/roles',{'ids':[rid]})
    check(True)
    for path in ['/dashboard','/users/page?q='+run+'&page=-1&size=0','/users?q='+run+'&company=demo&departmentId=0&size=101','/users/filters?company=demo',f'/roles/{rid}/permissions?q=missing','/authorization-logs?limit=1']:
        manage('GET',path)
    for path in ['/authorization-logs?limit=0','/authorization-logs?limit=501','/users/page?size=bad']:
        manage('GET',path,expected=400)
    manage('GET','/users/0/handover',expected=404)
    manage('POST',f'/users/{uid}/handover/complete',{},400)
    body={'reason':'Dedicated oral exam demonstration','expectedDate':datetime.date.today().isoformat(),'receiverNo':run+'_receiver','ownerNo':run+'_owner','oaReference':None}
    for overrides in [{'reason':None},{'reason':'x'*1001},{'reason':''},{'expectedDate':'not-a-date'},{'receiverNo':worker},{'receiverNo':run+'_missing'},{'oaReference':'x'*201}]:
        manage('POST',f'/users/{uid}/handover',dict(body,**overrides),400)
    manage('POST','/users/0/handover',body,404)
    manage('POST',f'/users/{uid}/handover',body)
    manage('POST',f'/users/{uid}/handover',body,400)
    assert manage('GET',f'/users/{uid}/handover')['handover']['state']=='HANDOVER'
    check(True)
    manage('POST',f'/users/{uid}/handover/complete',{'note':'demo','confirmed':'false'},400)
    done={'note':'Demo handover complete','confirmed':'true'}
    manage('POST',f'/users/{uid}/handover/complete',done)
    manage('POST',f'/users/{uid}/handover/complete',done)
    state=manage('GET',f'/users/{uid}/handover')
    assert state['handover']['state']=='DEPARTED' and state['user']['status']=='DISABLED' and state['roles']==[]
    employee('GET','/me',expected=401)
    req('POST','/auth/login',{'account':worker,'password':c['password']},expected=401)
    manage('PUT',f'/users/{uid}/roles',{'ids':[rid]},400)
    manage('POST',f'/users/{uid}/handover',body,400)
    manage('PUT',f'/roles/{rid}/permissions',{'ids':[]})
    req('POST','/auth/logout')
    print(f'PASS: {count} checked requests; grant/revoke/handover/session invalidation verified. Run={run}')
finally:
    for token in [user,admin]:
        if token:
            req('POST','/auth/logout',token=token)
