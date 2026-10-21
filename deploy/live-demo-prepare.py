"""Create a new, isolated set of demo identities in the deployed database via SSH."""
import json
import secrets
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.local/demo-deps'))
import bcrypt

run = 'demo_' + secrets.token_hex(6)
password = secrets.token_urlsafe(18)
hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
home = Path.home()
ssh = ['ssh', '-F', 'NUL' if sys.platform == 'win32' else '/dev/null', '-i', str(home / '.ssh/course_deploy_20261020'),
       '-o', 'UserKnownHostsFile=' + str(home / '.ssh/course_deploy_known_hosts'),
       '-o', 'StrictHostKeyChecking=yes', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes', 'root@120.46.31.246']
def remote(command, data=None):
    return subprocess.run(ssh + [command], input=data, text=True, encoding='utf-8', capture_output=True, check=True).stdout
cid = remote("docker ps -q --filter label=com.docker.swarm.service.name=rbac_mysql").strip()
if not cid or any(c not in '0123456789abcdef' for c in cid):
    raise RuntimeError('Expected exactly one project MySQL container')
# No existing identity is updated. A collision aborts the transaction.
sql = ['START TRANSACTION;']
for suffix in ['worker', 'receiver', 'owner']:
    account = run + '_' + suffix
    sql.append(f"INSERT INTO users(employee_no,username,name,position,password_hash) VALUES('{account}','{account}','{account}','Live demo only','{hashed}');")
sql.append(f"INSERT INTO roles(code,name,description) VALUES('{run}_role','{run}_role','Dedicated live demo role');")
sql.append('COMMIT;')
sql.append(f"SELECT username,id FROM users WHERE username IN ('{run}_worker','{run}_receiver','{run}_owner');")
sql.append(f"SELECT code,id FROM roles WHERE code='{run}_role';")
command = "docker exec -i " + cid + " sh -c 'MYSQL_PWD=$(cat /run/secrets/rbac_mysql_root_password) exec mysql --default-character-set=utf8mb4 -uroot -N -B rbac'"
rows = remote(command, '\n'.join(sql))
ids = dict(line.split('\t') for line in rows.strip().splitlines())
config = {'run': run, 'password': password, 'ids': {k: int(v) for k, v in ids.items()}}
folder = ROOT / '.local/live-demo'
folder.mkdir(parents=True, exist_ok=True)
path = folder / (run + '.json')
path.write_text(json.dumps(config, indent=2), encoding='utf-8')
(folder / 'latest.json').write_text(json.dumps(config, indent=2), encoding='utf-8')
print('Created worker, receiver, owner and one empty role:', run)
print('Private credentials (do not commit/share):', path)
print('Ready: python deploy/live-demo-full.py')
