"""R29/R33验证当前接入配置可生成完整、真实的空库夹具，不连接数据库。"""
import os
import re
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from course_integration_fixture import course_catalog, rbac_sql


class R29CourseIntegrationFixtureTest(unittest.TestCase):
    """覆盖多行目录提取、多路由短码和最终SQL的真实路由。"""

    def test_R33_current_fixture_includes_both_directory_permissions(self):
        """新增目录API须进入当前空库夹具，普通教师与学生不获得目录管理权。"""
        _, routes = course_catalog()
        self.assertEqual([('GET', '/auth/departments')], routes['IDR'])
        self.assertEqual([('GET', '/auth/departments/{departmentId}/users')], routes['IDU'])
        with patch.dict(os.environ, {'UI_FIXTURE_PASSWORD': 'R33-synthetic-fixture-only'}):
            sql = rbac_sql()
        self.assertIn("'/auth/departments'", sql)
        self.assertIn("'/auth/departments/{departmentId}/users'", sql)
        for code in ('IDR', 'IDU'):
            permission = re.search(r"INSERT INTO permissions VALUES\((\d+),2,NULL,'" + code + "'", sql).group(1)
            for role in (1, 51):
                self.assertIn(f'INSERT INTO role_permissions VALUES({role},{permission});', sql)
            for role in (50, 52):
                self.assertNotIn(f'INSERT INTO role_permissions VALUES({role},{permission});', sql)

    def test_R29_formatted_source_preserves_catalog(self):
        """直接读取当前登记目录，保留多路由短码并包含新增组织查询。"""
        display, routes = course_catalog()
        self.assertEqual(17, len(display))
        self.assertEqual(18, len(routes))
        self.assertEqual(24, sum(map(len, routes.values())))
        self.assertEqual(22, len({route for entries in routes.values() for route in entries}))
        for code in ('TCM', 'CV', 'CTM', 'TEC'):
            self.assertEqual(3 if code in {'CV','CTM'} else 2, len(routes[code]), code)

    def test_R29_user_lookup_uses_teacher_real_route(self):
        """身份查询登记必须对应老师现有Controller，拒绝先前错误模板。"""
        _, routes = course_catalog()
        self.assertEqual([('GET', '/auth/users/{userId}')], routes['UL'])
        self.assertNotIn('CRS', routes)

    def test_R29_generated_sql_keeps_two_systems_and_explicit_admission(self):
        """实际生成的SQL包含课程API和显式教师角色，不扩散至全部员工。"""
        with patch.dict(os.environ, {'UI_FIXTURE_PASSWORD': 'R29-synthetic-fixture-only'}):
            sql = rbac_sql()
        self.assertIn("'course-project-management'", sql)
        self.assertIn("'GET','/auth/users/{userId}'", sql)
        self.assertNotIn('/identity/users/', sql)
        self.assertIn('INSERT INTO user_roles VALUES(5,50),(6,50),(8,50),(8,51),(9,52),(10,52);', sql)
        apis = re.findall(r"INSERT INTO api_resources VALUES\(\d+,2,'([^']+)','([^']+)'", sql)
        self.assertEqual(22, len(apis))
        self.assertEqual(22, len(set(apis)))


if __name__ == '__main__':
    unittest.main()
