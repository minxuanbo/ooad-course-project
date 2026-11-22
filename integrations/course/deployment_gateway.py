"""Swarm独立接入应用：实时身份转接、配置短码核查及外部业务审计。"""
import json
import logging
import os
from pathlib import Path
import re
import time
from urllib.error import URLError
from urllib.parse import parse_qs, unquote

import pymysql

from integrations.course.deployment_store import AuditConflict, MysqlAuditStore
from integrations.course.local_identity_gateway import exchange, project_identity

MAX_BODY = 1048576
LOGGER = logging.getLogger('course.integration')


class Gateway:
    """WSGI处理器由Waitress提供有界HTTP运行器，不启动本地实验HTTPServer。"""

    def __init__(self, upstream, store, catalog, origins=()):
        self.upstream = upstream
        self.store = store
        self.catalog = catalog
        self.system = catalog['system']
        self.origins = set(origins)

    def dispatch(self, method, path, query, token, body):
        """逐次读取真实RBAC授权，拒绝跨应用及未声明路由；不使用mock或授权缓存。"""
        if method == 'GET' and path == '/healthz':
            self.store.ping()
            status, _ = self.upstream('/auth/me')
            return (200, {'status': 'UP'}) if status == 401 else (503, {'status': 'DOWN'})
        audit = path == '/auth/audit-logs'
        allowed = ((method == 'POST' and path in ('/auth/login', '/auth/logout'))
                   or (method == 'GET' and (path in ('/auth/me', '/auth/departments')
                       or re.fullmatch(r'/auth/(permissions/[^/]+|api-permissions/[A-Za-z0-9_.-]+|users/\d+|departments/\d+/users)', path)))
                   or (audit and method in ('GET', 'POST')))
        if not allowed:
            return 404, {'code': 'ENDPOINT_NOT_FOUND'}
        if path == '/auth/login':
            if not isinstance(body, dict) or body.get('systems') != [self.system]:
                return 403, {'code': 'APPLICATION_ACCESS_DENIED'}
            return self.upstream(path, method, token, body)
        if path == '/auth/logout':
            return self.upstream(path, method, token, body)
        status, identity = self.upstream('/auth/me', token=token)
        if status != 200:
            return status, identity
        if identity.get('system') != self.system:
            return 403, {'code': 'APPLICATION_ACCESS_DENIED'}
        identity = project_identity(identity, self.catalog)
        if path == '/auth/me':
            return 200, identity
        if path.startswith('/auth/permissions/'):
            return 200, {'allowed': unquote(path.rsplit('/', 1)[1]) in identity['permissions']}
        if not audit:
            return self.upstream(path + ('?' + query if query else ''), method, token, body)
        required = 'AV' if method == 'GET' else 'LOGIN'
        if required not in identity.get('permissions', []):
            return 403, {'code': 'PERMISSION_DENIED'}
        if method == 'GET':
            return 200, self.store.query(self.system, parse_qs(query, max_num_fields=30))
        if not isinstance(body, dict):
            raise ValueError('OBJECT_REQUIRED')
        actor = {'id': int(identity['subject']), 'name': '用户 ' + identity['subject'],
                 'roles': identity['roles'], 'deleted': False}
        return 200, self.store.write(self.system, actor, body, self.store.object_context(body))

    def __call__(self, environ, start_response):
        """限制请求体和跨域来源，日志仅记请求类别、结果及耗时，不含令牌和数据。"""
        started = time.monotonic()
        method, path = environ['REQUEST_METHOD'], environ.get('PATH_INFO', '')
        origin = environ.get('HTTP_ORIGIN')
        headers = [('Content-Type', 'application/json; charset=utf-8'), ('Cache-Control', 'no-store')]
        if origin in self.origins:
            headers += [('Access-Control-Allow-Origin', origin), ('Vary', 'Origin'),
                        ('Access-Control-Allow-Headers', 'Authorization,Content-Type'),
                        ('Access-Control-Allow-Methods', 'GET,POST,OPTIONS')]
        status, result = 500, {'code': 'INTERNAL_ERROR'}
        try:
            if origin and origin not in self.origins:
                status, result = 403, {'code': 'ORIGIN_DENIED'}
            elif method == 'OPTIONS':
                status, result = (204, None) if origin in self.origins else (403, {'code': 'ORIGIN_DENIED'})
            else:
                size = environ.get('CONTENT_LENGTH', '')
                if size and (not size.isdigit() or int(size) > MAX_BODY):
                    status, result = 413, {'code': 'REQUEST_TOO_LARGE'}
                else:
                    stream = environ['wsgi.input']
                    raw = stream.read(int(size)) if size else stream.read(MAX_BODY + 1) if environ.get('wsgi.input_terminated') else b''
                    if len(raw) > MAX_BODY:
                        status, result = 413, {'code': 'REQUEST_TOO_LARGE'}
                    else:
                        body = json.loads(raw) if raw else None
                        status, result = self.dispatch(method, path, environ.get('QUERY_STRING', ''),
                                                       environ.get('HTTP_AUTHORIZATION'), body)
        except AuditConflict:
            status, result = 409, {'code': 'AUDIT_IDEMPOTENCY_CONFLICT'}
        except (ValueError, KeyError, TypeError, OverflowError):
            status, result = 400, {'code': 'INVALID_ARGUMENT'}
        except (URLError, OSError, pymysql.Error):
            status, result = 503, {'code': 'EXTERNAL_SERVICE_UNAVAILABLE'}
        raw = b'' if status == 204 else json.dumps(result, ensure_ascii=False).encode('utf-8')
        from http import HTTPStatus
        headers.append(('Content-Length', str(len(raw))))
        start_response(str(status) + ' ' + HTTPStatus(status).phrase, headers)
        LOGGER.info('method=%s operation=%s status=%s elapsedMs=%d', method,
                    'health' if path == '/healthz' else 'business-audit' if path == '/auth/audit-logs' else 'identity',
                    status, (time.monotonic() - started) * 1000)
        return [raw]


def connection_factory(schema, user, secret_file):
    """从只读Secrets读取数据库密码，不接受拼接SQL或命令行密码。"""
    password = Path(secret_file).read_text(encoding='utf-8').rstrip('\r\n')
    if not password:
        raise ValueError('DATABASE_SECRET_REQUIRED')
    def connect():
        """每次请求使用独立连接，关闭时未提交事务自动回滚。"""
        return pymysql.connect(host=os.environ.get('DB_HOST', 'mysql'),
                               port=int(os.environ.get('DB_PORT', '3306')), user=user, password=password,
                               database=schema, charset='utf8mb4', cursorclass=pymysql.cursors.DictCursor,
                               connect_timeout=3, read_timeout=10, write_timeout=10, autocommit=False)
    return connect


def create_app():
    """工厂由正式WSGI运行器加载；应用、上游和允许来源全部显式配置。"""
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(name)s %(message)s')
    base = os.environ['RBAC_BASE_URL'].rstrip('/')
    catalog = json.loads(Path(os.environ.get('CATALOG_FILE', '/app/integrations/course/catalog.json')).read_text(encoding='utf-8'))
    store = MysqlAuditStore(
        connection_factory('course_audit', 'course_audit', os.environ.get('AUDIT_DB_PASSWORD_FILE', '/run/secrets/audit_db_password')),
        connection_factory('course_project', 'course_context', os.environ.get('COURSE_READ_PASSWORD_FILE', '/run/secrets/course_read_password')))
    def upstream(path, method='GET', token=None, body=None):
        """请求真实RBAC，不记录请求或响应内容。"""
        return exchange(base, path, method, token, body)
    return Gateway(upstream, store, catalog, os.environ['ALLOWED_ORIGINS'].split(','))
