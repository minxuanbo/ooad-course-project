#!/usr/bin/env python3
"""仅为两个独立空库生成虚构联调夹具，不连接数据库、不覆盖现有资料。"""
import argparse
import json
import os
from pathlib import Path
import re

from ui_acceptance_fixture import fixture_sql

ROOT = Path(__file__).resolve().parents[1]


def course_catalog():
    """从当前接入配置读取权限与真实API，校验结构而不写死旧阶段数量。"""
    catalog = json.loads((ROOT / 'integrations/course/catalog.json').read_text(encoding='utf-8'))
    codes, routes = catalog['display'], {code: [tuple(route) for route in entries] for code,entries in catalog['routes'].items()}
    assert len(codes) == len(set(codes)) and not set(codes).intersection(routes)
    assert (set(codes) | set(routes)).issubset(catalog['names'])
    assert all(entries and len(entries) == len(set(entries)) for entries in routes.values())
    assert all(method in {'GET', 'POST', 'PUT', 'PATCH', 'DELETE'} and path.startswith('/')
               for entries in routes.values() for method, path in entries)
    assert 'CRS' not in routes, '老师没有reset实现，不得编造API'

    return codes, routes


def rbac_sql():
    """复用RBAC最小可信根，追加课程目录及明确教师准入；所有员工均为虚构。"""
    base = fixture_sql().removesuffix('COMMIT;\n')
    statements = [base, "INSERT INTO systems(id,code,name,enabled) VALUES(2,'course-project-management','课程项目管理系统',1);",
                  "INSERT INTO roles(id,code,name,enabled,management_level,role_type,application_id) VALUES(50,'teacher','课程教师资格',1,NULL,'APPLICATION_PRESET',2),(51,'course-admin','课程管理员',1,NULL,'APPLICATION_PRESET',2),(52,'student','课程学生资格',1,NULL,'APPLICATION_PRESET',2);",
                  'INSERT INTO user_roles VALUES(5,50),(6,50),(8,50),(8,51),(9,52),(10,52);']
    codes, routes = course_catalog()
    all_codes = codes + sorted(routes)
    ids = {code: 1000 + index for index, code in enumerate(all_codes)}
    titles = json.loads((ROOT / 'integrations/course/catalog.json').read_text(encoding='utf-8'))['names']
    operator = set(all_codes) - {'LOGIN'}
    for code in all_codes:
        ident = ids[code]
        enabled = 1
        kind = 'API' if code in routes else 'MENU' if code in {'MCO', 'MTR', 'MTE', 'MLG'} else 'BUTTON'
        statements.append(f"INSERT INTO permissions VALUES({ident},2,NULL,'{code}','{titles.get(code, '课程权限 ' + code)}','{kind}',{enabled});")
        statements.append(f'INSERT INTO role_permissions VALUES(1,{ident});')
        if code == 'LOGIN':
            statements.append(f'INSERT INTO role_permissions VALUES(50,{ident}),(52,{ident});')
        if code in operator:
            statements.append(f'INSERT INTO role_permissions VALUES(51,{ident});')
    next_api = 1000
    api_ids = {}
    for code, entries in sorted(routes.items()):
        for method, path in entries:
            route = (method, path)
            if route not in api_ids:
                api_ids[route] = next_api
                enabled = 1
                statements.append(f"INSERT INTO api_resources VALUES({next_api},2,'{method}','{path}',{enabled});")
                next_api += 1
            statements.append(f'INSERT INTO permission_apis VALUES({ids[code]},{api_ids[route]});')
    statements.append('COMMIT;')
    return '\n'.join(statements) + '\n'


def teacher_sql():
    """初始化老师自有表的虚构教学场景：教师、学生、个人/分组课程及两类里程碑。"""
    statements = ['START TRANSACTION;']
    audit = "'local-fixture',NOW(6),'local-fixture',NOW(6)"
    for ident in (5, 6, 7, 8):
        statements.append(f"INSERT INTO teacher(id,user_id,employee_no,name,title,department,creator,created_at,modifier,modi_at) VALUES({ident},'{ident}','ui-user-{ident:02}','联调虚构教师{ident}','教师','工程部',{audit});")
    for ident in (9, 10, 11):
        statements.append(f"INSERT INTO student(id,user_id,student_no,name,status,creator,created_at,modifier,modi_at) VALUES({ident},'{ident}','ui-user-{ident:02}','联调虚构学生{ident}','active',{audit});")
    statements.append(f"INSERT INTO term(id,name,starts_on,ends_on,is_current,creator,created_at,modifier,modi_at) VALUES(1,'联调基准学期','2026-09-01','2027-01-31',1,{audit});")
    for ident,mode in ((1,'individual'),(2,'group')):
        statements.append(f"INSERT INTO course(id,term_id,code,name,description,work_mode,status,creator,created_at,modifier,modi_at) VALUES({ident},1,'LOCAL-RBAC-{ident:02}','联调{mode}课程','隔离库虚构教学场景','{mode}',3,{audit});")
        statements.append(f"INSERT INTO course_leader_assignment(course_id,teacher_id,creator,created_at,modifier,modi_at) VALUES({ident},5,{audit});")
        statements.append(f"INSERT INTO teaching_class(id,course_id,no,name,creator,created_at,modifier,modi_at) VALUES({ident},{ident},1,'联调教学班{ident}',{audit});")
        statements.append(f"INSERT INTO class_teacher_assignment(class_id,teacher_id,creator,created_at,modifier,modi_at) VALUES({ident},5,{audit});")
        for student in (9,10):
            statements.append(f"INSERT INTO enrollment(class_id,student_id,course_id,source,creator,created_at,modifier,modi_at) VALUES({ident},{student},{ident},'manual',{audit});")
        for index,check in enumerate(('grading','oral'),1):
            milestone=(ident-1)*2+index
            statements.append(f"INSERT INTO milestone(id,course_id,name,description,due_at,mode,weight,scale,group_ratio,book_limit,cancel_before,done,creator,created_at,modifier,modi_at) VALUES({milestone},{ident},'联调{check}里程碑','可上传文本文件与预约口试','2027-01-01 00:00:00','{check}',50,100,{0 if mode=='individual' else 50},{'NULL' if check=='grading' else 100},{'NULL' if check=='grading' else 0},0,{audit});")
    statements.append(f"INSERT INTO course_grouping_rule(course_id,min_size,max_size,allow_cross_class,starts_at,formation_closes_at,creator,created_at,modifier,modi_at) VALUES(2,1,4,1,'2026-01-01','2027-01-01',{audit});")
    statements.append('COMMIT;')
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
