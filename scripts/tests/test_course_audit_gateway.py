"""R29本地外部审计支撑必须持久、幂等、隔离，不能伪造写入成功。"""
import importlib.util
from pathlib import Path
import tempfile
import io
import unittest

path = Path(__file__).resolve().parents[2] / 'integrations/course/local_identity_gateway.py'
spec = importlib.util.spec_from_file_location('local_identity_gateway', path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class R29ExternalAuditStoreTest(unittest.TestCase):
    """核查跨重启读取、幂等键冲突和应用隔离。"""
    def test_R29_persistent_idempotent_and_isolated(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'audit.sqlite3'
            store = module.AuditStore(path)
            actor = {'id': 5, 'name': '虚构用户', 'roles': ['teacher'], 'deleted': False}
            body = {'requestId': 'one', 'type': 'roster_changed', 'objectType': 'class',
                    'objectId': 'cpm-class-1', 'target': '添加学生', 'before': {}, 'after': {'state': 'enrolled'}}
            first = store.write('one-app', actor, body, {'classId': 'cpm-class-1'})
            self.assertIsInstance(first, dict)
            self.assertEqual(first, store.write('one-app', actor, body, {'classId': 'cpm-class-1'}))
            restarted = module.AuditStore(path)
            result = restarted.query('one-app', {'page': ['1'], 'pageSize': ['20']})
            self.assertEqual(1, result['total'])
            self.assertEqual(first, result['items'][0])
            self.assertEqual(0, restarted.query('other-app', {})['total'])
            changed = dict(body, target='伪造重放')
            with self.assertRaises(ValueError):
                store.write('one-app', actor, changed)

    def test_R29_filter_and_invalid_page(self):
        with tempfile.TemporaryDirectory() as folder:
            store = module.AuditStore(Path(folder)/'audit.sqlite3')
            with self.assertRaises(ValueError):
                store.query('one-app', {'page': ['0']})
            with self.assertRaises(ValueError):
                store.query('one-app', {'pageSize': ['101']})

class R29ChunkedProtocolTest(unittest.TestCase):
    """老师真实Spring客户端使用分块发送JSON，不能把合法请求误当成空体。"""
    def test_R29_chunked_body_matches_content_length(self):
        body = b'{"systems":["course-project-management"]}'
        chunk = format(len(body), 'x').encode()+b'\r\n'+body+b'\r\n0\r\n\r\n'
        self.assertEqual(body, module.request_bytes({'Transfer-Encoding': 'chunked'}, io.BytesIO(chunk)))
        self.assertEqual(body, module.request_bytes({'Content-Length': str(len(body))}, io.BytesIO(body)))
        with self.assertRaises(ValueError):
            module.request_bytes({'Transfer-Encoding': 'chunked'}, io.BytesIO(b'100001\r\n'))

class R29CoarsePermissionTest(unittest.TestCase):
    """删除多路由权限的部分映射后，不能继续用相同短码执行未映射操作。"""
    def test_R29_partial_mapping_fails_closed_without_backend_course_constants(self):
        catalog={'routes': {'TCM': [['PUT','/current-term'],['DELETE','/current-term']]}}
        identity={'roles':['teacher'],'permissions':['LOGIN','MTR','TCM'],
                  'permissionApis': {'TCM':[{'method':'PUT','path':'/current-term'}]}}
        result=module.project_identity(identity,catalog)
        self.assertEqual(['LOGIN','MTR'],result['permissions'])
        identity['permissionApis']['TCM'].append({'method':'DELETE','path':'/current-term'})
        self.assertIn('TCM',module.project_identity(identity,catalog)['permissions'])

if __name__ == '__main__':
    unittest.main()
