"""R31覆盖本部教师、分公司学生及双系统同一管理员的首次分配规则。"""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from initialize_course_integration import Mysql, course_bindings_sql, manifest, plan_initialization


class CourseInitializationTest(unittest.TestCase):
    """用本部普通员工与分公司领导对照，避免把RBAC级别当课程身份。"""

    def setUp(self):
        """建立来源完整的最小组织，含本部部门与分公司子树。"""
        self.configuration = {
            'seedKey': 'course-initial-identity-v1', 'headquartersSourceKey': '总公司',
            'roles': {'teacher': 'teacher', 'student': 'student', 'administrator': 'course-admin'},
        }
        self.catalog = {'system': 'course-project-management', 'display': ['LOGIN', 'MTR'],
                        'routes': {'TC': [['POST', '/terms']]},
                        'names': {'LOGIN': '课程登录资格', 'MTR': '学期管理', 'TC': '新建学期'}}
        self.snapshot = {
            'departments': [
                {'id': 1, 'name': '总公司', 'parent_id': None, 'type': 'HEADQUARTERS'},
                {'id': 2, 'name': '总部办公室', 'parent_id': 1, 'type': 'DEPARTMENT'},
                {'id': 3, 'name': '桥梁分公司', 'parent_id': 1, 'type': 'COMPANY'},
                {'id': 4, 'name': '桥梁项目', 'parent_id': 3, 'type': 'PROJECT'},
            ],
            'users': [
                {'id': 1, 'username': 'admin', 'name': '维护员', 'department_id': 1, 'level': 5, 'enabled': True},
                {'id': 2, 'username': '0002', 'name': '本部领导', 'department_id': 1, 'level': 4, 'enabled': True},
                {'id': 3, 'username': '0003', 'name': '本部员工', 'department_id': 2, 'level': 1, 'enabled': True},
                {'id': 4, 'username': '0004', 'name': '分公司领导', 'department_id': 3, 'level': 4, 'enabled': True},
                {'id': 5, 'username': '0005', 'name': '项目员工', 'department_id': 4, 'level': 1, 'enabled': True},
            ],
            'sources': [
                {'kind': 'ORGANIZATION', 'key': '总公司', 'department_id': 1, 'user_id': None, 'cells': ['总公司', '', '']},
                {'kind': 'BOOTSTRAP', 'key': 'admin', 'user_id': 1, 'department_id': None, 'cells': []},
            ],
            'admin_role_ids': [10],
        }
        for user in self.snapshot['users'][1:]:
            company = '总公司' if user['id'] in (2, 3) else '桥梁分公司'
            self.snapshot['sources'].append({
                'kind': 'EMPLOYEE', 'key': user['username'], 'user_id': user['id'],
                'department_id': None, 'cells': [user['name'], user['username'], company, '', '', ''],
            })

    def test_R31_headquarters_leader_and_ordinary_employee_are_teachers(self):
        """本部教师覆盖部门普通员工，不能只取根直属领导。"""
        plan = plan_initialization(self.snapshot, self.configuration, self.catalog)
        self.assertEqual([1, 2, 3], plan['teachers'])
        self.assertEqual([4, 5], plan['students'])

    def test_R31_branch_leader_is_student_and_same_admin_manages_both(self):
        """分公司领导仍是学生，初始admin兼具教师与课程管理员。"""
        plan = plan_initialization(self.snapshot, self.configuration, self.catalog)
        self.assertEqual([1], plan['administrators'])
        self.assertIn(4, plan['students'])
        self.assertEqual(5, len(plan['teachers']) + len(plan['students']))
        self.assertFalse(set(plan['teachers']) & set(plan['students']))

    def test_R31_unknown_account_source_is_rejected(self):
        """账号来源不完整不能猜测其课程身份。"""
        self.snapshot['sources'].pop()
        with self.assertRaisesRegex(ValueError, 'SOURCE_MAPPING_INCOMPLETE'):
            plan_initialization(self.snapshot, self.configuration, self.catalog)

    def test_R31_source_company_must_match_organization_branch(self):
        """分公司子树不能因错误的本部标签被授教师。"""
        self.snapshot['sources'][-1]['cells'][2] = '总公司'
        with self.assertRaisesRegex(ValueError, 'SOURCE_ORGANIZATION_MISMATCH'):
            plan_initialization(self.snapshot, self.configuration, self.catalog)

    def test_R31_initial_administrator_must_retain_real_rbac_qualification(self):
        """独立维护来源仍须启用、根直属且具有有效五级角色。"""
        self.snapshot['users'][0]['level'] = 4
        with self.assertRaisesRegex(ValueError, 'INITIAL_ADMIN_INVALID'):
            plan_initialization(self.snapshot, self.configuration, self.catalog)

    def test_R31_every_eligible_rbac_administrator_gets_course_admin(self):
        """未来合法取得根直属五级的用户同样由初次计划识别。"""
        self.snapshot['users'][1]['level'] = 5
        plan = plan_initialization(self.snapshot, self.configuration, self.catalog)
        self.assertEqual([1, 2], plan['administrators'])

    def test_R31_catalog_and_role_configuration_are_explicit(self):
        """接入角色取配置，通用RBAC本体不决定teacher或student编码。"""
        self.configuration['roles']['teacher'] = 'configured-teacher'
        plan = plan_initialization(self.snapshot, self.configuration, self.catalog)
        self.assertEqual('configured-teacher', plan['role_codes']['teacher'])
        self.assertEqual('course-project-management', plan['system'])

    def test_R31_source_order_does_not_change_plan(self):
        """计划按稳定用户ID排序，不因查询返回顺序改变。"""
        expected = plan_initialization(self.snapshot, self.configuration, self.catalog)
        reordered = copy.deepcopy(self.snapshot)
        reordered['users'].reverse()
        reordered['sources'].reverse()
        self.assertEqual(expected, plan_initialization(reordered, self.configuration, self.catalog))

    def test_R31_receipt_does_not_reset_runtime_role_or_display_changes(self):
        """回执不因后续角色撤回、启停或显示名变化而驱动重新初始化。"""
        before = manifest(self.snapshot, self.configuration, self.catalog)
        changed = copy.deepcopy(self.snapshot)
        changed['users'][0].update(level=1, enabled=False, name='运行期新名称')
        self.assertEqual(before, manifest(changed, self.configuration, self.catalog))

    def test_R31_source_change_invalidates_receipt(self):
        """原件来源变化不能冒用旧回执重新授予。"""
        before = manifest(self.snapshot, self.configuration, self.catalog)
        self.snapshot['sources'][-1]['cells'][0] = '来源已变化'
        self.assertNotEqual(before, manifest(self.snapshot, self.configuration, self.catalog))

    def test_R31_existing_matching_course_binding_is_preserved(self):
        """中断后重试保留已建身份，不覆盖姓名、职称或创建时间。"""
        plan = plan_initialization(self.snapshot, self.configuration, self.catalog)
        teachers = [{'user_id': str(i), 'no': plan['people'][i - 1]['username']} for i in (1, 2, 3)]
        students = [{'user_id': str(i), 'no': plan['people'][i - 1]['username']} for i in (4, 5)]
        sql = course_bindings_sql(plan, teachers, students)
        self.assertNotIn('INSERT INTO teacher', sql)
        self.assertNotIn('INSERT INTO student', sql)
        self.assertNotIn('UPDATE', sql)

    def test_R31_conflicting_course_identity_is_rejected(self):
        """同一user_id不能从学生表被静默改造成教师身份。"""
        plan = plan_initialization(self.snapshot, self.configuration, self.catalog)
        with self.assertRaisesRegex(ValueError, 'COURSE_BINDING_CONFLICT'):
            course_bindings_sql(plan, [], [{'user_id': '3', 'no': '0003'}])

    def test_R31_deleted_teacher_is_not_silently_restored(self):
        """已有删除标记的教师记录不能由首次接入脚本擅自恢复。"""
        plan = plan_initialization(self.snapshot, self.configuration, self.catalog)
        with self.assertRaisesRegex(ValueError, 'COURSE_BINDING_CONFLICT'):
            course_bindings_sql(plan, [{'user_id': '3', 'no': '0003', 'deleted': True}], [])

    def test_R31_local_initializer_rejects_remote_database(self):
        """本地初始化不能因误传连接文件写入远程业务库。"""
        with tempfile.TemporaryDirectory() as temporary:
            configuration = Path(temporary) / 'client.ini'
            configuration.write_text('[client]\nhost=remote.example\nport=3306\n', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'LOCAL_DATABASE_REQUIRED'):
                Mysql(Path('mysql'), configuration, 'rbac_core')

    def test_R31_mysql_options_cannot_override_validated_connection_or_continue_errors(self):
        """客户端其他组和登录文件不得改变已验证地址或启用错误后继续提交。"""
        with tempfile.TemporaryDirectory() as temporary:
            configuration = Path(temporary) / 'client.ini'
            configuration.write_text('[client]\nhost=127.0.0.1\nport=3351\n'
                                     '[mysql]\nhost=remote.example\nport=3306\nforce=1\n', encoding='utf-8')
            database = Mysql(Path('mysql'), configuration, 'rbac_core')
            self.assertEqual('--defaults-file=' + str(configuration.resolve()), database.command[1])
            for required in ['--no-login-paths', '--host=127.0.0.1', '--port=3351',
                             '--protocol=TCP', '--skip-force']:
                self.assertIn(required, database.command)

    def test_R31_invalid_ini_does_not_expose_configuration_contents(self):
        """配置解析错误只返回稳定错误码，不回显可能含口令的原始行。"""
        with tempfile.TemporaryDirectory() as temporary:
            configuration = Path(temporary) / 'client.ini'
            configuration.write_text('sensitive-invalid-line\n', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, '^LOCAL_CLIENT_CONFIGURATION_INVALID$'):
                Mysql(Path('mysql'), configuration, 'rbac_core')


if __name__ == '__main__':
    unittest.main()
