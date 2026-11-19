"""R29验证联调探针失败后不会把临时课程授权留给夹具账号。"""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_course_integration import Verification


class R29CourseIntegrationCleanupTest(unittest.TestCase):
    """通过内存服务状态注入UL故障，不连接真实后端或数据库。"""

    def test_R29_lookup_failure_restores_original_role(self):
        """查询链失败也必须恢复角色完整集合，避免多留一个UL权限。"""
        state = {'ids': [2]}
        verification = Verification('http://127.0.0.1:8081', 'http://127.0.0.1:8080', 'synthetic')

        def request(label, base, path, expected, method='GET', token=None, body=None):
            """用最小HTTP契约模型记录授权结果，在UL查询处注入行为失败。"""
            if path == '/api/auth/login':
                return {'data': {'token': 'synthetic'}}
            if path in {'/auth/login', '/login'}:
                return {'accessToken': 'synthetic'}
            if path == '/admin/courses' and expected == 200:
                return {'total': 1}
            if path == '/api/roles/51/permissions':
                if method == 'PUT':
                    state['ids'] = list(body['ids'])
                return {'data': {'ids': list(state['ids'])}}
            if path.startswith('/api/permissions/tree'):
                return {'data': [{'code': 'UL', 'id': 1}, {'code': 'TC', 'id': 2}, {'code': 'TEC', 'id': 3}]}
            if path == '/auth/users/7' and expected == 200:
                raise AssertionError('模拟UL查询失败')
            return {}

        with patch.object(verification, 'request', side_effect=request):
            with self.assertRaisesRegex(AssertionError, '模拟UL查询失败'):
                verification.run()
        self.assertEqual([2], state['ids'])


if __name__ == '__main__':
    unittest.main()
