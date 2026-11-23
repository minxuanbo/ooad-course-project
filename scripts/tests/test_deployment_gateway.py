"""R34独立部署接入：协议透明、即时失权、隔离及真实MySQL持久审计。"""
from concurrent.futures import ThreadPoolExecutor
import io
import json
import os
from pathlib import Path
import sys
import unittest
from urllib.error import URLError
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from integrations.course.deployment_gateway import Gateway
from integrations.course.deployment_gateway import _CLIENT_IP
from integrations.course.deployment_store import AuditConflict, MysqlAuditStore
import pymysql


class GatewayTest(unittest.TestCase):
    """HTTP契约桩仅用于测试依赖，不替代真实双系统验收。"""

    def setUp(self):
        self.identity = {'subject': '8', 'system': 'course-project-management', 'roles': ['teacher'],
                         'permissions': ['LOGIN', 'TCM'],
                         'permissionApis': {'TCM': [{'method': 'PUT', 'path': '/current-term'}]}}
        self.calls = []
        self.failure = False
        def upstream(path, method='GET', token=None, body=None):
            self.calls.append((method, path))
            if self.failure:
                raise URLError('dependency down')
            if path == '/auth/logout':
                return 204, None
            if not token:
                return 401, {'code': 'AUTH_REQUIRED'}
            if path == '/auth/me':
                return 200, self.identity
            return 200, {'items': [], 'page': 1, 'pageSize': 20, 'total': 0}
        catalog = {'system': 'course-project-management',
                   'routes': {'TCM': [['PUT', '/current-term'], ['DELETE', '/current-term']]}}
        self.app = Gateway(upstream, None, catalog, ['http://course.test'])

    def test_R16_source_ip_trust_and_request_context_are_isolated(self):
        """WSGI边界验证来源头仅在可信代理后生效，且并发与异常请求不串来源。"""
        from threading import Barrier
        from ipaddress import ip_network
        self.app.trusted_proxies = (ip_network('10.0.0.0/24'),)
        def request(peer, forwarded=None):
            env = {'REQUEST_METHOD': 'POST', 'PATH_INFO': '/auth/logout', 'QUERY_STRING': '',
                   'wsgi.input': io.BytesIO(b''), 'CONTENT_LENGTH': '0'}
            if peer is not None:
                env['REMOTE_ADDR'] = peer
            if forwarded is not None:
                env['HTTP_X_FORWARDED_FOR'] = forwarded
            raw = b''.join(self.app(env, lambda *_: None))
            return json.loads(raw)
        def upstream(*args, **kwargs):
            return 200, {'source': _CLIENT_IP.get()}
        self.app.upstream = upstream
        for peer, header, expected in [
                ('10.0.0.2', '203.0.113.4', '203.0.113.4'),
                ('10.1.0.2', '203.0.113.4', '10.1.0.2'),
                ('10.0.0.2', '203.0.113.4, 10.0.0.1', '10.0.0.2'),
                ('10.0.0.2', 'example.test', '10.0.0.2'),
                ('10.0.0.2', 'fe80::1%zone', '10.0.0.2'),
                ('10.0.0.2', '::1', '::1'),
                (None, '203.0.113.4', None)]:
            self.assertEqual(expected, request(peer, header)['source'])
            self.assertIsNone(_CLIENT_IP.get())
        barrier = Barrier(2)
        def simultaneous(*args, **kwargs):
            barrier.wait(timeout=5)
            return 200, {'source': _CLIENT_IP.get()}
        self.app.upstream = simultaneous
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda ip: request('10.0.0.2', ip)['source'],
                                    ['203.0.113.5', '203.0.113.6']))
        self.assertEqual(['203.0.113.5', '203.0.113.6'], results)
        self.app.upstream = lambda *args, **kwargs: (_ for _ in ()).throw(OSError('offline'))
        self.assertEqual('EXTERNAL_SERVICE_UNAVAILABLE', request('10.0.0.2')['code'])
        self.assertIsNone(_CLIENT_IP.get())

    def test_R16_waitress_preserves_headers_for_gateway_validation(self):
        """采用正式Docker启动参数构造实际中间件，原始连接方必须保留给网关验证。"""
        from ipaddress import ip_network
        from waitress.adjustments import Adjustments
        from waitress.proxy_headers import proxy_headers_middleware
        dockerfile = (Path(__file__).resolve().parents[2] / 'deploy/integration.Dockerfile').read_text(encoding='utf-8')
        command = json.loads(next(line[4:] for line in dockerfile.splitlines() if line.startswith('CMD ')))
        options, arguments = Adjustments.parse_args(command[1:])
        del options['call'], options['help']
        settings = Adjustments(**options)
        self.assertIsNone(settings.trusted_proxy)
        self.assertFalse(settings.clear_untrusted_proxy_headers)
        self.app.trusted_proxies = (ip_network('10.0.0.0/24'),)
        self.app.upstream = lambda *args, **kwargs: (200, {'source': _CLIENT_IP.get()})
        application = proxy_headers_middleware(self.app, trusted_proxy=settings.trusted_proxy,
            trusted_proxy_count=settings.trusted_proxy_count, trusted_proxy_headers=settings.trusted_proxy_headers,
            clear_untrusted=settings.clear_untrusted_proxy_headers)
        for peer, forwarded, expected in [('10.0.0.2', '203.0.113.4', '203.0.113.4'),
                ('192.0.2.2', '203.0.113.4', '192.0.2.2'),
                ('10.0.0.2', '203.0.113.4, 10.0.0.3', '10.0.0.2')]:
            environment = {'REQUEST_METHOD': 'POST', 'PATH_INFO': '/auth/logout', 'QUERY_STRING': '',
                'CONTENT_LENGTH': '0', 'wsgi.input': io.BytesIO(b''), 'REMOTE_ADDR': peer,
                'HTTP_X_FORWARDED_FOR': forwarded}
            result = json.loads(b''.join(application(environment, lambda status, headers: None)))
            self.assertEqual(expected, result['source'])
            self.assertEqual(peer, environment['REMOTE_ADDR'])
            self.assertIsNone(_CLIENT_IP.get())

    def request(self, path, method='GET', token='Bearer private-token', origin=None, raw=b''):
        """通过WSGI边界读取实际状态、头和字节，不直接调用授权内部方法。"""
        result = {}
        def start(status, headers):
            result.update(status=int(status.split()[0]), headers=dict(headers))
        env = {'REQUEST_METHOD': method, 'PATH_INFO': path, 'QUERY_STRING': '',
               'wsgi.input': io.BytesIO(raw), 'CONTENT_LENGTH': str(len(raw))}
        if token is not None:
            env['HTTP_AUTHORIZATION'] = token
        if origin:
            env['HTTP_ORIGIN'] = origin
        result['raw'] = b''.join(self.app(env, start))
        result['body'] = json.loads(result['raw']) if result['raw'] else None
        return result

    def test_R34_logout_preserves_upstream_204_even_after_session_expires(self):
        """退出保持原协议的幂等及空体，不能被网关改成401。"""
        self.identity = {'code': 'TOKEN_EXPIRED'}
        def upstream(path, method='GET', token=None, body=None):
            return (204, None) if path == '/auth/logout' else (401, self.identity)
        self.app.upstream = upstream
        result = self.request('/auth/logout', 'POST')
        self.assertEqual(204, result['status'])
        self.assertEqual(b'', result['raw'])

    def test_R34_partial_mapping_closes_whole_shortcode_then_recovers_without_cache(self):
        """同一令牌每次都重新检查配置所需路由全集。"""
        self.assertFalse(self.request('/auth/permissions/TCM')['body']['allowed'])
        self.identity['permissionApis']['TCM'].append({'method': 'DELETE', 'path': '/current-term'})
        self.assertTrue(self.request('/auth/permissions/TCM')['body']['allowed'])
        self.identity['permissions'].remove('TCM')
        self.assertFalse(self.request('/auth/permissions/TCM')['body']['allowed'])

    def test_R34_other_application_cannot_use_course_gateway(self):
        self.identity['system'] = 'another-system'
        self.assertEqual(403, self.request('/auth/me')['status'])
        self.assertEqual(403, self.request('/auth/login', 'POST', raw=b'{"systems":["another-system"]}')['status'])

    def test_R34_audit_requires_global_read_permission_and_fails_closed(self):
        self.assertEqual(403, self.request('/auth/audit-logs')['status'])
        self.failure = True
        self.assertEqual(503, self.request('/auth/me')['status'])

    def test_R34_cors_is_exact_and_preflight_does_not_call_identity(self):
        self.assertEqual(403, self.request('/auth/me', origin='http://evil.test')['status'])
        self.assertEqual([], self.calls)
        result = self.request('/auth/me', 'OPTIONS', origin='http://course.test')
        self.assertEqual(204, result['status'])
        self.assertEqual('http://course.test', result['headers']['Access-Control-Allow-Origin'])
        self.assertEqual([], self.calls)

    def test_R34_request_limits_and_unknown_route(self):
        self.assertEqual(413, self.request('/auth/login', 'POST', raw=b'x' * 1048577)['status'])
        self.assertEqual(400, self.request('/auth/login', 'POST', raw=b'{broken')['status'])
        self.assertEqual(404, self.request('/api/users')['status'])

    def test_R16_health_uses_operational_probe_without_anonymous_identity_requests(self):
        """存活探测只读运维健康状态，不制造未识别主体的鉴权失败。"""
        class Store:
            """只记录健康调用，业务审计接口不参与本测试。"""
            def ping(self):
                return None
        self.app.store = Store()
        probes = []
        def health():
            probes.append(True)
            return 200, {'status': 'UP'}
        self.app.health_probe = health
        for _ in range(3):
            self.assertEqual(200, self.request('/healthz', token=None)['status'])
        self.assertEqual(3, len(probes))
        self.assertEqual([], self.calls)
        self.app.health_probe = lambda: (503, {'status': 'DOWN'})
        self.assertEqual(503, self.request('/healthz', token=None)['status'])
        self.app.health_probe = lambda: (200, {'status': 'DOWN'})
        self.assertEqual(503, self.request('/healthz', token=None)['status'])


@unittest.skipUnless(os.environ.get('DEPLOY_TEST_DB_FILE'), '需要本轮隔离MySQL配置')
class MysqlAuditTest(unittest.TestCase):
    """使用真实MySQL核查并发幂等、重建服务后回读、应用分区与数据库分页。"""

    def setUp(self):
        config = json.loads(Path(os.environ['DEPLOY_TEST_DB_FILE']).read_text(encoding='utf-8-sig'))
        def connect():
            return pymysql.connect(host='127.0.0.1', port=config['port'], user=config['user'],
                                   password=config['password'], database='course_audit',
                                   charset='utf8mb4', cursorclass=pymysql.cursors.DictCursor)
        self.connect = connect
        self.store = MysqlAuditStore(connect, connect)
        self.system = 'r34-' + uuid.uuid4().hex
        self.actor = {'id': 8, 'name': '隔离测试操作者', 'roles': ['teacher'], 'deleted': False}
        self.body = {'requestId': 'same-request', 'type': 'course_changed', 'objectType': 'course',
                     'objectId': 'cpm-course-1', 'target': '修改课程', 'before': {}, 'after': {'name': '测试'}}

    def tearDown(self):
        """仅删除本测试生成的随机应用分区，不清理其他记录。"""
        connection = self.connect()
        try:
            with connection.cursor() as cursor:
                cursor.execute('DELETE FROM audit_events WHERE app_code=%s', (self.system,))
            connection.commit()
        finally:
            connection.close()

    def test_R34_concurrent_idempotency_and_restart(self):
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(lambda _: self.store.write(self.system, self.actor, self.body), range(4)))
        self.assertTrue(all(result == results[0] for result in results))
        restarted = MysqlAuditStore(self.connect, self.connect)
        result = restarted.query(self.system, {})
        self.assertEqual(1, result['total'])
        self.assertEqual(results[0], result['items'][0])
        self.assertEqual(0, restarted.query('other-' + self.system, {})['total'])
        with self.assertRaises(AuditConflict):
            restarted.write(self.system, self.actor, dict(self.body, target='不同重放'))
        with self.assertRaises(AuditConflict):
            restarted.write(self.system, dict(self.actor, id=9), self.body)

    def test_R34_database_filter_and_pagination(self):
        self.store.write(self.system, self.actor, self.body)
        self.store.write(self.system, self.actor, dict(self.body, requestId='second', target='删除课程'))
        result = self.store.query(self.system, {'target': ['修改'], 'actorId': ['8'], 'pageSize': ['1']})
        self.assertEqual(1, result['total'])
        self.assertEqual('修改课程', result['items'][0]['target'])
        self.assertEqual([], self.store.query(self.system, {'page': ['3'], 'pageSize': ['1']})['items'])
        with self.assertRaises(ValueError):
            self.store.query(self.system, {'from': ['2026-01-01']})


if __name__ == '__main__':
    unittest.main()
