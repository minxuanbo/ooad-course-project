"""T13验收工具的错库配置前置保护；不连接或模拟业务数据库行为。"""

import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location('t13_probe', Path(__file__).resolve().parents[1] / 'accept-core-runtime.py')
PROBE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PROBE)


class AcceptanceEnvironmentT13Test(unittest.TestCase):
    """部署遗留环境不能使Java绕过已经核实的私有MySQL实例。"""

    def environment(self):
        """提供无真实秘密的必要配置，在任何数据库接触前测试拒绝行为。"""
        return {'RBAC_CORE_DB_URL': 'jdbc:mysql://127.0.0.1:3321/rbac_core',
                'RBAC_CORE_DB_USER': 'unused', 'RBAC_CORE_DB_PASSWORD': 'unused',
                'RBAC_CORE_EMPLOYEE_PASSWORD': 'unused', 'RBAC_CORE_ADMIN_PASSWORD': 'unused',
                'RBAC_ACCEPTANCE_DATADIR': str(PROBE.LOCAL / 'unit-only-no-database')}

    def test_t13_rejects_spring_and_jvm_overrides_before_database_contact(self):
        """覆盖数据源、JSON、配置位置、profile及JVM/loader注入，全部在接库前失败。"""
        for key in ('SPRING_DATASOURCE_URL', 'spring.datasource.url', 'SPRING_APPLICATION_JSON',
                    'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_PROFILES_ACTIVE',
                    'spring.config.import', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'LOADER_MAIN', 'LOADER_PATH'):
            with self.subTest(key=key), patch.object(PROBE, 'ENV', dict(self.environment(), **{key: 'override'})), \
                    patch.object(PROBE, 'sql', side_effect=RuntimeError('已经接触数据库')) as database:
                with self.assertRaisesRegex(RuntimeError, '额外Spring/JVM配置'):
                    PROBE.prepare()
                database.assert_not_called()

    def test_t13_clean_environment_reaches_private_database_verification(self):
        """正常输入应进入私有实例核实，不能为通过拒绝测试而一律报错。"""
        with patch.object(PROBE, 'ENV', self.environment()), \
                patch.object(PROBE, 'sql', side_effect=RuntimeError('私有实例核实入口')):
            with self.assertRaisesRegex(RuntimeError, '私有实例核实入口'):
                PROBE.prepare()

    def test_t13_only_verified_properties_enter_java_environment(self):
        """无关环境与非规范同名配置不会进入Java，保留已校验的精确数据源配置。"""
        environment = dict(self.environment(), SERVER_PORT='1', rbac_core_db_url='jdbc:mysql://other/rbac_core')
        with patch.object(PROBE, 'ENV', environment), \
                patch.object(PROBE, 'sql', side_effect=RuntimeError('私有实例核实入口')):
            with self.assertRaisesRegex(RuntimeError, '私有实例核实入口'):
                PROBE.prepare()
            self.assertEqual(PROBE.JAVA_ENV['RBAC_CORE_DB_URL'], environment['RBAC_CORE_DB_URL'])
            self.assertNotIn('SERVER_PORT', PROBE.JAVA_ENV)
            self.assertNotIn('rbac_core_db_url', PROBE.JAVA_ENV)


if __name__ == '__main__':
    unittest.main()
