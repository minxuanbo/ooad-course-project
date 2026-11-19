"""R29验证提交后格式化源码仍可产生完整、真实的联调目录，不连接数据库。"""
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from course_integration_fixture import course_catalog, rbac_sql


class R29CourseIntegrationFixtureTest(unittest.TestCase):
    """覆盖多行目录提取、多路由短码和最终SQL的真实路由。"""

    def test_R29_formatted_source_preserves_catalog(self):
        """必须直接解析当前提交的格式化Java，不能只测试未格式化副本。"""
        display, routes = course_catalog()
        self.assertEqual(17, len(display))
        self.assertEqual(16, len(routes))
        self.assertEqual(19, sum(map(len, routes.values())))
        for code in ('TCM', 'CV', 'CTM'):
            self.assertEqual(2, len(routes[code]), code)

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
        self.assertIn('INSERT INTO user_roles VALUES(5,50),(5,51),(6,50);', sql)


if __name__ == '__main__':
    unittest.main()
