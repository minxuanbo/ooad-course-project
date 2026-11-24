"""R35首次接入元数据显式安装、边界拒绝与回执保留。"""
import copy
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from initialize_course_integration import operation_catalog_sql


class OnboardingInitializationTest(unittest.TestCase):
    """首次安装不应把协议条件变成默认放行。"""

    def setUp(self):
        self.operation = {'system': 'example', 'operationId': 'readExample', 'method': 'GET',
                          'path': '/examples/{id}', 'requiresPermission': True,
                          'permissionRoles': ['teacher', 'course-admin']}

    def test_R35_course_controlled_operations_have_real_api_mappings(self):
        """首次配置的每个全局受控操作均须具备同应用真实API及短码映射。"""
        root = Path(__file__).resolve().parents[2]
        catalog = json.loads((root / 'integrations/course/catalog.json').read_text(encoding='utf-8'))
        operations = json.loads((root / 'integrations/course/operations.json').read_text(encoding='utf-8'))['operations']
        mapped = {tuple(route) for routes in catalog['routes'].values() for route in routes}
        missing = [operation['operationId'] for operation in operations
                   if operation['requiresPermission'] and (operation['method'], operation['path']) not in mapped]
        self.assertEqual([], missing)

    def test_R35_explicit_metadata_retains_conditions(self):
        """真实配置以安全字面值写入支撑表，角色条件及门禁均保留。"""
        sql = '\n'.join(operation_catalog_sql('@system', 'example', [self.operation], 'LOGIN'))
        self.assertIn('INSERT INTO application_profiles', sql)
        self.assertIn('INSERT INTO api_operations', sql)
        self.assertIn("CONVERT(0x" + json.dumps(['course-admin', 'teacher']).encode().hex(), sql)
        self.assertNotIn('DELETE', sql)
        self.assertNotIn('UPDATE', sql)

    def test_R35_bad_metadata_has_stable_error(self):
        """畸形字段、重复及越界必须稳定拒绝，不能泄漏内部TypeError。"""
        for field, value in [('operationId', []), ('path', None), ('path', 4),
                             ('path', '/a?b'), ('path', '/' + 'a'*512), ('method', []),
                             ('requiresPermission', 'true'), ('permissionRoles', [{}]),
                             ('permissionRoles', ['teacher', 'teacher']), ('system', 'other')]:
            with self.subTest(field=field, value=value):
                op = copy.deepcopy(self.operation)
                op[field] = value
                with self.assertRaisesRegex(ValueError, 'OPERATION_CATALOG_INVALID'):
                    operation_catalog_sql('@system', 'example', [op], 'LOGIN')
        for operations in [[self.operation, self.operation], [None], {}]:
            with self.subTest(operations=operations):
                with self.assertRaisesRegex(ValueError, 'OPERATION_CATALOG_INVALID'):
                    operation_catalog_sql('@system', 'example', operations, 'LOGIN')
