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
            """只模拟角色持久状态；使用真实临时授权上下文验证失败恢复。"""
            if method == 'PUT':
                state['ids'] = list(body['ids'])
            return {'data': {'ids': list(state['ids'])}}

        with patch.object(verification, 'request', side_effect=request):
            with self.assertRaisesRegex(AssertionError, '模拟UL查询失败'):
                with verification.temporary_permissions('synthetic'):
                    verification.request('变更', verification.rbac, '/api/roles/51/permissions',
                                         200, 'PUT', 'synthetic', {'ids': [1,2]})
                    raise AssertionError('模拟UL查询失败')
        self.assertEqual([2], state['ids'])


if __name__ == '__main__':
    unittest.main()
