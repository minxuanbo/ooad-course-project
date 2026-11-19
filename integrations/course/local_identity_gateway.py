"""仅供本地联调的统一身份转接与持久审计支撑，不加载课程业务或RBAC实现。

身份/权限全部实时转发RBAC。审计写入按调用者记录，读取要求配置的全局读权限，
不模拟课程对象范围。此工具不是生产部署方案，也不替代RBAC鉴权日志。
"""
import argparse
from contextlib import closing
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import sqlite3
import subprocess
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlsplit, unquote
from urllib.request import Request, urlopen


class AuditStore:
    """按应用分区、服务端时间取号，requestId对同一操作者与请求内容保持幂等。"""
    def __init__(self, path):
        self.path = str(path)
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute('CREATE TABLE IF NOT EXISTS audit (id INTEGER PRIMARY KEY AUTOINCREMENT, '
                       'system TEXT NOT NULL, request_id TEXT NOT NULL, actor_id INTEGER NOT NULL, '
                       'payload TEXT NOT NULL, response TEXT NOT NULL, UNIQUE(system,request_id))')

    def write(self, system, actor, payload, context=None):
        """原样保存调用者提交的业务快照，主体和时间由服务端覆盖，冲突重放拒绝。"""
        required = ('requestId', 'type', 'objectType', 'objectId', 'target')
        if any(not isinstance(payload.get(key), str) or not 1 <= len(payload[key]) <= 1000 for key in required):
            raise ValueError('审计字段不完整或过长')
        if any(not isinstance(payload.get(key), dict) for key in ('before', 'after')):
            raise ValueError('审计快照必须为对象')
        canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True)
        with closing(sqlite3.connect(self.path)) as db, db:
            db.execute('BEGIN IMMEDIATE')
            existing = db.execute('SELECT actor_id,payload,response FROM audit WHERE system=? AND request_id=?',
                                  (system, payload['requestId'])).fetchone()
            if existing:
                if existing[0] != actor['id'] or existing[1] != canonical:
                    raise ValueError('审计幂等键已被其他内容占用')
                return json.loads(existing[2])
            response = {'at': datetime.now(timezone.utc).isoformat(), 'actor': actor,
                        **{key: payload[key] for key in ('type', 'target', 'before', 'after')},
                        'termId': None, 'courseId': None, 'classId': None, **(context or {})}
            cursor = db.execute('INSERT INTO audit(system,request_id,actor_id,payload,response) VALUES(?,?,?,?,?)',
                                (system, payload['requestId'], actor['id'], canonical, '{}'))
            response['id'] = cursor.lastrowid
            db.execute('UPDATE audit SET response=? WHERE id=?',
                       (json.dumps(response, ensure_ascii=False), response['id']))
            return response

    def query(self, system, params):
        """支持老师实际分页与过滤，固定应用分区在任何查询条件之前执行。"""
        page, size = int(params.get('page', ['1'])[0]), int(params.get('pageSize', ['100'])[0])
        if page < 1 or not 1 <= size <= 100:
            raise ValueError('分页参数无效')
        with closing(sqlite3.connect(self.path)) as db, db:
            records = [json.loads(row[0]) for row in db.execute(
                'SELECT response FROM audit WHERE system=? ORDER BY id DESC', (system,))]
        def matches(item):
            actor = item['actor']
            for key in ('actor', 'actorId', 'target', 'from', 'to'):
                value = params.get(key, [''])[0]
                if not value:
                    continue
                if key == 'actor' and value.casefold() not in actor['name'].casefold(): return False
                if key == 'actorId' and int(value) != actor['id']: return False
                if key == 'target' and value.casefold() not in item['target'].casefold(): return False
                if key in ('from', 'to'):
                    requested = datetime.fromisoformat(value.replace('Z', '+00:00'))
                    actual = datetime.fromisoformat(item['at'])
                    if requested.tzinfo is None: raise ValueError('时间必须含时区')
                    if key == 'from' and actual < requested: return False
                    if key == 'to' and actual > requested: return False
            return True
        records = [item for item in records if matches(item)]
        return {'page': page, 'pageSize': size, 'total': len(records),
                'items': records[(page-1)*size:page*size]}


def exchange(base, path, method='GET', token=None, body=None):
    """连接真实身份服务，不缓存授权、记录令牌或返回虚假成功。"""
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = token
    request = Request(base + path, method=method, headers=headers,
                      data=json.dumps(body).encode('utf-8') if body is not None else None)
    try:
        with urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read())
    except HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else {'code': 'UPSTREAM_REJECTED'}


def object_context(options, payload):
    """只读老师课程库补充对象归属，课程标识规则留在联调工具，不进入RBAC。"""
    match = re.fullmatch(r'cpm-(term|course|class|group|milestone|submission|oral_record)-(\d+)', payload['objectId'])
    if not match or not options.mysql_config: return {}
    kind, ident = match.groups()
    queries = {
        'term': 'SELECT id,NULL,NULL FROM term WHERE id={id}',
        'course': 'SELECT term_id,id,NULL FROM course WHERE id={id}',
        'class': 'SELECT c.term_id,c.id,t.id FROM teaching_class t JOIN course c ON c.id=t.course_id WHERE t.id={id}',
        'group': 'SELECT c.term_id,c.id,g.responsible_class_id FROM project_group g JOIN course c ON c.id=g.course_id WHERE g.id={id}',
        'milestone': 'SELECT c.term_id,c.id,NULL FROM milestone m JOIN course c ON c.id=m.course_id WHERE m.id={id}',
        'submission': 'SELECT c.term_id,c.id,NULL FROM submission s JOIN milestone m ON m.id=s.milestone_id JOIN course c ON c.id=m.course_id WHERE s.id={id}',
        'oral_record': 'SELECT c.term_id,c.id,NULL FROM oral_assessment a JOIN milestone m ON m.id=a.milestone_id JOIN course c ON c.id=m.course_id WHERE a.id={id}',
    }
    # ident仅由全数字正则产生；程序参数数组不会经过shell拼装。
    result = subprocess.run([options.mysql, '--defaults-extra-file='+str(options.mysql_config),
                             '--batch', '--skip-column-names', 'course_project', '-e',
                             queries[kind].format(id=ident)], capture_output=True, check=True, text=True, timeout=10)
    if not result.stdout.strip(): return {}
    values = result.stdout.strip().split('\t')
    return {key: 'cpm-'+prefix+'-'+value for key,prefix,value in
            zip(('termId','courseId','classId'), ('term','course','class'), values) if value != 'NULL'}


def request_bytes(headers, stream):
    """读取有界HTTP请求体，支持老师Spring客户端使用的chunked编码。"""
    if headers.get('Transfer-Encoding', '').lower() == 'chunked':
        chunks, total = [], 0
        while True:
            line = stream.readline(128)
            if not line.endswith(b'\r\n'): raise ValueError('分块长度无效')
            size = int(line.strip().split(b';',1)[0],16)
            if size < 0 or total+size > 1048576: raise ValueError('请求体过大')
            if size == 0:
                if stream.readline(8192) != b'\r\n': raise ValueError('不支持分块尾部字段')
                return b''.join(chunks)
            chunk = stream.read(size)
            if len(chunk) != size or stream.read(2) != b'\r\n': raise ValueError('分块内容不完整')
            chunks.append(chunk);total += size
    if headers.get('Transfer-Encoding'): raise ValueError('传输编码不受支持')
    length = int(headers.get('Content-Length', '0'))
    if not 0 <= length <= 1048576: raise ValueError('请求体过大')
    result = stream.read(length)
    if len(result) != length: raise ValueError('请求体不完整')
    return result


def project_identity(identity, catalog):
    """依据课程协议配置核实粗粒度权限必须覆盖的全部路由。"""
    expected = catalog['routes']
    actual = identity.get('permissionApis', {})
    permissions = []
    for code in identity.get('permissions', []):
        if code in expected:
            routes = {(route['method'], route['path']) for route in actual.get(code, [])}
            if not set(map(tuple, expected[code])).issubset(routes):
                continue
        permissions.append(code)
    return {**identity, 'permissions': permissions}


def serve(options):
    """只监听loopback；课程客户端沿原HTTP协议访问，老师源码保持不变。"""
    store = AuditStore(options.database)
    catalog = json.loads(options.catalog.read_text(encoding="utf-8"))
    class Handler(BaseHTTPRequestHandler):
        """转接既有身份请求并对外部审计执行即时权限门禁。"""
        def log_message(self, *_):
            """禁用包含路径、身份和请求参数的默认访问日志。"""

        def respond(self, status, body):
            """来源只允许显式配置项，不发送任意跨域凭证。"""
            raw = json.dumps(body, ensure_ascii=False).encode('utf-8')
            self.send_response(status)
            origin = self.headers.get('Origin')
            if origin in options.origins:
                self.send_header('Access-Control-Allow-Origin', origin)
                self.send_header('Vary', 'Origin')
                self.send_header('Access-Control-Allow-Headers', 'Authorization,Content-Type')
                self.send_header('Access-Control-Allow-Methods', 'GET,POST,OPTIONS')
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def do_OPTIONS(self):
            """仅为批准来源处理预检。"""
            self.respond(200 if self.headers.get('Origin') in options.origins else 403, {})

        def do_GET(self):
            """处理通用身份读取及外部审计查询。"""
            self.handle_request()

        def do_POST(self):
            """处理通用登录及真实审计追加。"""
            self.handle_request()

        def handle_request(self):
            """所有授权取当前RBAC关系，任何连接故障均拒绝。"""
            try:
                raw = request_bytes(self.headers, self.rfile)
                body = json.loads(raw) if raw else None
                token = self.headers.get('Authorization')
                path = urlsplit(self.path).path
                if path != '/auth/audit-logs':
                    if self.command == 'POST' and path == '/auth/login':
                        if not isinstance(body, dict) or body.get('systems') != [options.system]:
                            return self.respond(403, {'code': 'APPLICATION_ACCESS_DENIED'})
                    elif not (self.command == 'GET' and (path == '/auth/me' or re.fullmatch(r'/auth/(permissions/[^/]+|users/\d+)', path))):
                        return self.respond(404, {'code': 'ENDPOINT_NOT_FOUND'})
                    if self.command == 'GET':
                        status, identity = exchange(options.rbac, '/auth/me', token=token)
                        if status != 200: return self.respond(status, identity)
                        if identity.get('system') != options.system:
                            return self.respond(403, {'code': 'APPLICATION_ACCESS_DENIED'})
                        identity = project_identity(identity, catalog)
                        if path == '/auth/me': return self.respond(200, identity)
                        if path.startswith('/auth/permissions/'):
                            code = unquote(path.rsplit('/',1)[1])
                            return self.respond(200, {'allowed': code in identity['permissions']})
                    status, result = exchange(options.rbac, self.path, self.command, token, body)
                    return self.respond(status, result)
                status, identity = exchange(options.rbac, '/auth/me', token=token)
                if status != 200: return self.respond(status, identity)
                if identity.get('system') != options.system:
                    return self.respond(403, {'code': 'APPLICATION_ACCESS_DENIED'})
                identity = project_identity(identity, catalog)
                required = options.read_permission if self.command == 'GET' else options.write_permission
                if required not in identity.get('permissions', []):
                    return self.respond(403, {'code': 'PERMISSION_DENIED'})
                if self.command == 'GET':
                    return self.respond(200, store.query(options.system, parse_qs(urlsplit(self.path).query)))
                if not isinstance(body, dict): raise ValueError('请求必须为对象')
                actor = {'id': int(identity['subject']), 'name': '用户 '+identity['subject'],
                         'roles': identity['roles'], 'deleted': False}
                # 不冒充课程提交证明；此服务只保存已认证调用者上报的事件。
                return self.respond(200, store.write(options.system, actor, body, object_context(options, body)))
            except (ValueError, KeyError, TypeError):
                self.respond(400, {'code': 'INVALID_ARGUMENT', 'message': '外部审计参数无效或幂等键冲突'})
            except (URLError, OSError, sqlite3.Error, subprocess.SubprocessError):
                self.respond(503, {'code': 'EXTERNAL_SERVICE_UNAVAILABLE', 'message': '本地联调依赖暂不可用'})
    ThreadingHTTPServer(('127.0.0.1', options.port), Handler).serve_forever()


def main():
    """凭据文件与持久数据只能由操作者放入Git忽略的本地目录。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8085)
    parser.add_argument('--catalog', type=Path, default=Path(__file__).with_name('catalog.json'))
    parser.add_argument('--rbac', default='http://127.0.0.1:8081')
    parser.add_argument('--system', default='course-project-management')
    parser.add_argument('--database', required=True, type=Path)
    parser.add_argument('--read-permission', default='AV')
    parser.add_argument('--write-permission', default='LOGIN')
    parser.add_argument('--origin', dest='origins', action='append', default=[])
    parser.add_argument('--mysql', default='C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe')
    parser.add_argument('--mysql-config', type=Path)
    options = parser.parse_args()
    if urlsplit(options.rbac).hostname not in ('127.0.0.1', 'localhost'):
        parser.error('本地联调只允许loopback身份服务')
    options.database.parent.mkdir(parents=True, exist_ok=True)
    serve(options)

if __name__ == '__main__':
    main()
