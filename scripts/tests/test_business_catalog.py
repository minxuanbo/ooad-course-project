"""核对正式首次业务目录与原矩阵逐项一致，不连接业务库。"""
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))


class BusinessCatalogTest(unittest.TestCase):
    """原表权限、稳定身份、重入保护必须分别验证。"""

    def test_full_matrix_and_no_fake_apis(self):
        """逐行逐动作比较五角色，包含全部、本人限定、空白与view规范化。"""
        from initialize_business_catalog import build_plan
        spec = importlib.util.spec_from_file_location('source', ROOT / 'scripts/build-prototype-applications.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        source = module.extract(ROOT / '市政公司应用软件功能权限清单.xlsx')
        plan = build_plan(source)
        self.assertEqual(18, len(plan['systems']))
        self.assertEqual(115, len([p for p in plan['permissions'] if p['kind'] == 'module']))
        self.assertEqual(359, len([p for p in plan['permissions'] if p['kind'] == 'feature']))
        self.assertTrue(all(s['connectionStatus'] == 'PENDING' for s in plan['systems']))
        actions = {(p['row'], p['name']): p for p in plan['permissions'] if p['kind'] == 'action'}
        for feature in source['features']:
            for index, action in enumerate(feature['operations']):
                self.assertEqual(feature['operationRoles'][index], actions[feature['row'], action]['roleIndexes'])
                for role_index, code in enumerate(plan['roleCodes']):
                    raw = feature['roleRights'][role_index]
                    expected = raw == '全部' or action in (raw or '').replace('view', '查看').split('/')
                    self.assertEqual(expected, actions[feature['row'], action]['code'] in plan['grants'][code],
                                     (feature['row'], role_index, action))
        self.assertTrue(any(p['name'] == '查看本人' for p in actions.values()))
        self.assertTrue(all(p['type'] in {'MENU', 'BUTTON'} for p in plan['permissions']))
        self.assertEqual(len(plan['permissions']), len({(p['systemCode'], p['code']) for p in plan['permissions']}))

    def test_receipt_preserves_manual_changes(self):
        """匹配回执后不重建角色、覆盖目录或重置人工授撤权。"""
        from initialize_business_catalog import initialize, build_plan, load_source
        plan = build_plan(load_source(ROOT / '市政公司应用软件功能权限清单.xlsx'))
        class Database:
            def rows(self, sql):
                return [{'hash': plan['manifest']}]
            def execute(self, sql):
                raise AssertionError('matching receipt must not write')
        self.assertTrue(initialize(Database(), plan, True)['skipped'])


if __name__ == '__main__':
    unittest.main()
