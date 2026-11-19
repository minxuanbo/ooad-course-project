#!/usr/bin/env python3
"""仅为两个独立空库生成虚构联调夹具，不连接数据库、不覆盖现有资料。"""
import argparse
import os
from pathlib import Path
import re

from ui_acceptance_fixture import fixture_sql

ROOT = Path(__file__).resolve().parents[1]


def course_catalog():
    """从共享协议取值读取权限与真实API，断言数量以防静默漏登记。"""
    source = (ROOT / 'backend/src/main/java/com/municipal/rbaccore/service/port/course/CourseContract.java').read_text(encoding='utf-8')
    match = re.search(r'DISPLAY_CODES\s*=\s*Set\.of\s*\((.*?)\);', source, re.S)
    if match is None:
        raise ValueError('无法读取CourseContract显示码目录，请核对协议定义')
    display = match.group(1)
    codes = ['COURSE_TEACHER'] + re.findall(r'"([A-Z]+)"', display)
    # 按相邻Map.entry切块，保留一个短码对应的全部路由。
    routes = {}
    for entry in re.split(r'Map\.entry\s*\(', source)[1:]:
        code = re.match(r'\s*"([A-Z]+)"', entry).group(1)
        routes[code] = re.findall(r'new RbacCatalog\.Route\s*\(\s*"([A-Z]+)"\s*,\s*"([^"]+)"\s*\)', entry)
    assert len(codes) == 17 and len(routes) == 16 and sum(map(len, routes.values())) == 20
    assert len({route for entries in routes.values() for route in entries}) == 19
    assert 'CRS' not in routes, '老师没有reset实现，不得编造API'
    return codes, routes


def rbac_sql():
    """复用RBAC最小可信根，追加课程目录及明确教师准入；所有员工均为虚构。"""
    base = fixture_sql().removesuffix('COMMIT;\n')
    statements = [base, "INSERT INTO systems VALUES(2,'course-project-management','课程项目管理系统',1);",
                  "INSERT INTO roles VALUES(50,'course-teacher','课程教师身份',1,NULL),(51,'course-operator','课程学期维护',1,NULL);",
                  'INSERT INTO user_roles VALUES(5,50),(5,51),(6,50);']
    codes, routes = course_catalog()
    all_codes = codes + sorted(routes)
    ids = {code: 1000 + index for index, code in enumerate(all_codes)}
    titles = {'COURSE_TEACHER': '课程教师准入', 'MCO': '课程管理菜单', 'MTR': '学期管理菜单',
              'MTE': '教师管理菜单', 'MLG': '审计菜单（本轮未接入）', 'TC': '新建学期', 'TE': '编辑学期',
              'TD': '删除学期', 'TCM': '设置和取消当前学期', 'CV': '查看课程', 'UL': '查询员工身份'}
    operator = {'MCO', 'MTR', 'MTRC', 'MTRE', 'MTRD', 'MTRM', 'CV', 'TC', 'TE', 'TD', 'TCM'}
    for code in all_codes:
        ident = ids[code]
        enabled = 0 if code in {'MLG', 'AV'} else 1
        kind = 'MENU' if code in {'MCO', 'MTR', 'MTE', 'MLG'} else 'BUTTON'
        statements.append(f"INSERT INTO permissions VALUES({ident},2,NULL,'{code}','{titles.get(code, '课程权限 ' + code)}','{kind}',{enabled});")
        statements.append(f'INSERT INTO role_permissions VALUES(1,{ident});')
        if code == 'COURSE_TEACHER':
            statements.append(f'INSERT INTO role_permissions VALUES(50,{ident});')
        if code in operator:
            statements.append(f'INSERT INTO role_permissions VALUES(51,{ident});')
    next_api = 1000
    api_ids = {}
    for code, entries in sorted(routes.items()):
        for method, path in entries:
            route = (method, path)
            if route not in api_ids:
                api_ids[route] = next_api
                enabled = 0 if code == 'AV' else 1
                statements.append(f"INSERT INTO api_resources VALUES({next_api},2,'{method}','{path}',{enabled});")
                next_api += 1
            statements.append(f'INSERT INTO permission_apis VALUES({ids[code]},{api_ids[route]});')
    statements.append('COMMIT;')
    return '\n'.join(statements) + '\n'


def teacher_sql():
    """为老师现有表写入3个虚构教师、1个学期及课程，绝不修改老师迁移。"""
    statements = ['START TRANSACTION;']
    for ident in (5, 6, 7):
        statements.append(f"INSERT INTO teacher(id,user_id,employee_no,name,title,department,creator,created_at,modifier,modi_at) VALUES({ident},'{ident}','ui-user-{ident:02}','联调虚构教师{ident}','联调教师','工程部','local-fixture',NOW(6),'local-fixture',NOW(6));")
    statements += ["INSERT INTO term(id,name,starts_on,ends_on,is_current,creator,created_at,modifier,modi_at) VALUES(1,'联调基准学期','2026-09-01','2027-01-31',1,'local-fixture',NOW(6),'local-fixture',NOW(6));",
                   "INSERT INTO course(id,term_id,code,name,description,work_mode,status,creator,created_at,modifier,modi_at) VALUES(1,1,'LOCAL-RBAC-01','RBAC真实联调课程','仅隔离库虚构数据','individual',3,'5',NOW(6),'5',NOW(6));",
                   "INSERT INTO course_leader_assignment(course_id,teacher_id,creator,created_at,modifier,modi_at) VALUES(1,5,'local-fixture',NOW(6),'local-fixture',NOW(6));", 'COMMIT;']
    return '\n'.join(statements) + '\n'


def main():
    """只接受忽略目录下的新文件，生成SQL需要显式提供虚构账号口令。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', required=True, type=Path)
    args = parser.parse_args()
    target = args.output_dir.resolve()
    if not target.is_relative_to((ROOT / '.local').resolve()):
        raise ValueError('输出必须位于项目.local目录')
    if not os.environ.get('UI_FIXTURE_PASSWORD'):
        raise ValueError('请通过UI_FIXTURE_PASSWORD提供联调口令')
    target.mkdir(parents=True, exist_ok=True)
    for name, content in [('rbac-fixture.sql', rbac_sql()), ('course-fixture.sql', teacher_sql())]:
        with (target / name).open('x', encoding='utf-8') as stream:
            stream.write(content)
    print('已生成两库夹具；没有连接数据库，没有修改老师源码。')


if __name__ == '__main__':
    main()
