"""R34老师首次导入名单与RBAC数字subject绑定，业务对象主键不得重建。"""
import importlib.util
from pathlib import Path
import unittest
import os
import sys
from unittest.mock import patch

source = Path(__file__).resolve().parents[2] / 'deploy/initialize_deployment.py'
spec = importlib.util.spec_from_file_location('deployment_initializer', source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class DeploymentBindingsTest(unittest.TestCase):
    """只校正首发隔离库，不能把旧工号当成新的JWT数字主键。"""

    def setUp(self):
        self.plan = {'people': [
            {'id': 10001, 'username': 'admin', 'name': '管理员', 'department': '总公司', 'teacher': True},
            {'id': 3, 'username': '0003', 'name': '本部教师', 'department': '办公室', 'teacher': True},
            {'id': 6, 'username': '0006', 'name': '分公司学生', 'department': '分公司', 'teacher': False}]}
        self.teachers = [{'id': 77, 'user_id': '0003', 'no': '0003', 'deleted': False}]
        self.students = [{'id': 88, 'user_id': '0006', 'no': '0006', 'status': 'active'}]

    def test_R28_first_install_only_adds_explicit_course_application(self):
        """核心首次授权已有RBAC，部署入口只显式配置真实课程，不导入Excel模拟系统。"""
        for apply in (False, True):
            with self.subTest(apply=apply), patch.dict(os.environ, {'INITIALIZATION_PHASE': 'offline-first-install'}), \
                    patch.object(sys, 'argv', ['initializer'] + (['--apply'] if apply else [])), \
                    patch.object(module, 'Database') as database, patch.object(module, 'initialize', return_value={'applied': apply}) as initialize, patch('builtins.print'):
                module.main()
                self.assertEqual(2, database.call_count)
                args = initialize.call_args.args
                self.assertEqual('course-project-management', args[3]['system'])
                self.assertEqual(apply, args[4])
                self.assertTrue(args[5])
                self.assertEqual({'course-project-management'}, {operation['system'] for operation in args[5]})

    def test_R28_startup_cannot_run_offline_first_install(self):
        """普通运行阶段拒绝初始化，不能因重启再次恢复删除的应用或授权。"""
        with patch.dict(os.environ, {'INITIALIZATION_PHASE': 'runtime'}), patch.object(sys, 'argv', ['initializer']), patch.object(module, 'Database') as database:
            with self.assertRaisesRegex(ValueError, 'OFFLINE_FIRST_INSTALL_REQUIRED'):
                module.main()
            database.assert_not_called()

    def test_R34_legacy_numbers_rebind_in_place_and_admin_is_added(self):
        sql, report = module.reconcile_bindings(self.plan, self.teachers, self.students)
        self.assertIn("UPDATE teacher SET user_id=", sql)
        self.assertIn('WHERE id=77', sql)
        self.assertIn('WHERE id=88', sql)
        self.assertNotIn('DELETE', sql)
        self.assertEqual(2, report['rebound'])
        self.assertEqual(1, report['created'])
        self.assertIn('INSERT INTO teacher', sql)

    def test_R34_extra_demo_records_are_preserved_without_role_assignment(self):
        self.teachers.append({'id': 90, 'user_id': 'sso-demo', 'no': 'DEMO', 'deleted': False})
        sql, report = module.reconcile_bindings(self.plan, self.teachers, self.students)
        self.assertEqual(1, report['preservedExtraTeachers'])
        self.assertNotIn('WHERE id=90', sql)

    def test_R34_conflicting_existing_bindings_and_identity_kind_are_rejected(self):
        for changed in ('different-sso-user', '6'):
            self.teachers[0]['user_id'] = changed
            with self.assertRaises(ValueError):
                module.reconcile_bindings(self.plan, self.teachers, self.students)
        self.teachers[0]['user_id'] = '0003'
        self.students[0]['no'] = '0003'
        with self.assertRaises(ValueError):
            module.reconcile_bindings(self.plan, self.teachers, self.students)

    def test_R34_already_matching_binding_is_not_rewritten(self):
        self.teachers[0]['user_id'] = '3'
        self.students[0]['user_id'] = '6'
        sql, report = module.reconcile_bindings(self.plan, self.teachers, self.students)
        self.assertEqual(0, report['rebound'])
        self.assertNotIn('UPDATE', sql)


if __name__ == '__main__':
    unittest.main()
