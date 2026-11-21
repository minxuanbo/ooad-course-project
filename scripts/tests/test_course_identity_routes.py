"""R32以真实HTTP上游stub验证本地转接保留完整八项身份协议。"""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class IdentityForwardingTest(unittest.TestCase):
    """只替代上游HTTP协议，不mock数据库或宣称完成真实RBAC鉴权。"""

    def test_R32_directory_api_permission_and_logout_reach_identity_provider(self):
        """四项新增协议及查询串必须转到提供方，退出保持204。"""
        class Upstream(BaseHTTPRequestHandler):
            """返回稳定最小协议响应，避免断言内部调用次数。"""

            def log_message(self, *args):
                """测试不输出Authorization或请求信息。"""

            def do_GET(self):
                """模拟当前身份及目录/全局门禁的实际HTTP响应。"""
                body = {'subject': '1', 'roles': ['configured-role'],
                        'system': 'course-project-management', 'permissions': ['LOGIN'], 'permissionApis': {}}
                if self.path.startswith('/auth/departments'):
                    body = {'page': 2 if 'page=2' in self.path else 1, 'pageSize': 20, 'total': 0, 'items': []}
                elif self.path.startswith('/auth/api-permissions/'):
                    body = {'operationId': 'createTerm', 'allowed': False}
                raw = json.dumps(body).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)

            def do_POST(self):
                """退出响应没有JSON体。"""
                self.send_response(204)
                self.end_headers()

        upstream = ThreadingHTTPServer(('127.0.0.1', 0), Upstream)
        thread = threading.Thread(target=upstream.serve_forever, daemon=True)
        thread.start()
        with tempfile.TemporaryDirectory() as temporary:
            with socket.socket() as reservation:
                reservation.bind(('127.0.0.1', 0))
                port = reservation.getsockname()[1]
            root = Path(__file__).resolve().parents[2]
            process = subprocess.Popen([
                sys.executable, str(root / 'integrations/course/local_identity_gateway.py'),
                '--port', str(port), '--rbac', 'http://127.0.0.1:' + str(upstream.server_port),
                '--database', str(Path(temporary) / 'audit.sqlite3')],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                for _ in range(100):
                    try:
                        with urlopen('http://127.0.0.1:' + str(port) + '/auth/me', timeout=1):
                            break
                    except URLError:
                        time.sleep(0.02)
                for path in ['/auth/departments?page=2', '/auth/departments/1/users?page=2',
                             '/auth/api-permissions/createTerm']:
                    request = Request('http://127.0.0.1:' + str(port) + path,
                                      headers={'Authorization': 'Bearer test-identity'})
                    with urlopen(request, timeout=3) as response:
                        body = json.load(response)
                        if 'departments' in path:
                            self.assertEqual(2, body['page'])
                        else:
                            self.assertFalse(body['allowed'])
                request = Request('http://127.0.0.1:' + str(port) + '/auth/logout', method='POST',
                                  headers={'Authorization': 'Bearer test-identity'})
                with urlopen(request, timeout=3) as response:
                    self.assertEqual(204, response.status)
                    self.assertEqual(b'', response.read())
            finally:
                process.terminate()
                process.wait(timeout=5)
                upstream.shutdown()
                upstream.server_close()


if __name__ == '__main__':
    unittest.main()
