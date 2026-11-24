#!/usr/bin/env python3
"""仅供停止产品服务的全新部署库执行：老师名单绑定校正及配置角色首次接入。"""
import argparse
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from initialize_course_integration import (course_bindings_sql, manifest, plan_initialization,
                                           read_snapshot, rbac_initialization_sql, sql_text)


def reconcile_bindings(plan, teachers, students):
    """核对导入的旧工号绑定，保留业务主键和不参与授权的额外演示对象。"""
    people = {person['username']: person for person in plan['people']}
    seen, statements, normalized = set(), [], [[], []]
    extras = [0, 0]
    for index, rows in enumerate((teachers, students)):
        for row in rows:
            person = people.get(row['no'])
            if person is None:
                if str(row.get('user_id') or '').isdigit():
                    raise ValueError('UNMATCHED_NUMERIC_BINDING')
                extras[index] += 1
                continue
            if (person['teacher'] != (index == 0) or row['no'] in seen or row.get('deleted')
                    or (index == 1 and row.get('status') != 'active')):
                raise ValueError('COURSE_BINDING_CONFLICT')
            seen.add(row['no'])
            expected = str(person['id'])
            if row['user_id'] not in (None, '', row['no'], expected):
                raise ValueError('COURSE_BINDING_CONFLICT')
            if row['user_id'] != expected:
                table = 'teacher' if index == 0 else 'student'
                statements.append('UPDATE ' + table + ' SET user_id=' + sql_text(expected)
                                  + ',modifier=' + sql_text('deployment-initializer')
                                  + ',modi_at=UTC_TIMESTAMP(6) WHERE id=' + str(int(row['id'])) + ';')
            normalized[index].append({**row, 'user_id': expected})
    insert_sql = course_bindings_sql(plan, *normalized).splitlines()[1:-1]
    report = {'rebound': len(statements), 'created': len(people) - len(seen),
              'preservedExtraTeachers': extras[0], 'preservedExtraStudents': extras[1]}
    return '\n'.join(['START TRANSACTION;'] + statements + insert_sql + ['COMMIT;']), report


class Database:
    """部署初始化专用SQL执行器；只接受程序生成的首次SQL，不开放HTTP入口。"""

    def __init__(self, schema, user, secret):
        import pymysql
        self.settings = dict(host=os.environ['DB_HOST'], port=int(os.environ.get('DB_PORT', '3306')),
                             user=user, password=Path(secret).read_text(encoding='utf-8').rstrip('\r\n'),
                             database=schema, charset='utf8mb4', connect_timeout=5, read_timeout=120,
                             client_flag=pymysql.constants.CLIENT.MULTI_STATEMENTS)

    def rows(self, sql):
        """读取每行JSON对象，避免姓名和工号中的分隔符影响核对。"""
        import pymysql
        with pymysql.connect(**self.settings) as connection, connection.cursor() as cursor:
            cursor.execute(sql)
            return [json.loads(row[0]) for row in cursor.fetchall()]

    def execute(self, sql):
        """完整消费生成的事务SQL，错误回滚且不回显SQL或数据库秘密。"""
        import pymysql
        with pymysql.connect(**self.settings) as connection, connection.cursor() as cursor:
            cursor.execute(sql)
            while cursor.nextset():
                pass
            connection.commit()


def initialize(rbac, course, configuration, catalog, apply=False, operations=None):
    """先核对所有来源及冲突，再提交课程绑定，最后发布RBAC角色；中断可重试。"""
    snapshot = read_snapshot(rbac)
    fingerprint = manifest(snapshot, configuration, catalog if operations is None else {**catalog, 'operations': operations})
    receipts = rbac.rows("SELECT JSON_OBJECT('hash',manifest_sha256) FROM bootstrap_receipts WHERE seed_key="
                         + sql_text(configuration['seedKey']) + ';')
    if receipts:
        if receipts[0]['hash'] != fingerprint:
            raise ValueError('INITIALIZATION_MANIFEST_CHANGED')
        return {'skipped': True, 'reason': 'matching-receipt-preserves-runtime-changes'}
    plan = plan_initialization(snapshot, configuration, catalog)
    plan['manifest'] = fingerprint
    conflicts = rbac.rows("SELECT JSON_OBJECT('count',COUNT(*)) FROM systems WHERE code="
                         + sql_text(plan['system']) + ';')[0]['count']
    conflicts += rbac.rows("SELECT JSON_OBJECT('count',COUNT(*)) FROM roles WHERE code IN ("
                          + ','.join(sql_text(code) for code in plan['role_codes'].values()) + ');')[0]['count']
    if conflicts:
        raise ValueError('COURSE_CATALOG_ALREADY_PRESENT')
    teachers = course.rows("SELECT JSON_OBJECT('id',id,'user_id',user_id,'no',employee_no,'deleted',deleted_at IS NOT NULL) FROM teacher;")
    students = course.rows("SELECT JSON_OBJECT('id',id,'user_id',user_id,'no',student_no,'status',status) FROM student;")
    course_sql, report = reconcile_bindings(plan, teachers, students)
    rbac_sql = rbac_initialization_sql(plan, configuration, catalog, operations)
    if apply:
        course.execute(course_sql)
        rbac.execute(rbac_sql)
    return {'skipped': False, 'applied': apply, 'users': len(plan['people']),
            'teachers': len(plan['teachers']), 'students': len(plan['students']),
            'courseAdministrators': len(plan['administrators']), 'bindings': report,
            'manifest': fingerprint}


def main():
    """必须显式声明离线首次部署阶段；默认仅输出无个人明细的预览。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if os.environ.get('INITIALIZATION_PHASE') != 'offline-first-install':
        raise ValueError('OFFLINE_FIRST_INSTALL_REQUIRED')
    configuration = json.loads((ROOT / 'integrations/course/initialization.json').read_text(encoding='utf-8'))
    catalog = json.loads((ROOT / 'integrations/course/catalog.json').read_text(encoding='utf-8'))
    rbac = Database('rbac_core', 'rbac', '/run/secrets/rbac_db_password')
    course = Database('course_project', 'course_app', '/run/secrets/course_db_password')
    course_report = initialize(rbac, course, configuration, catalog, args.apply, json.loads((ROOT / 'integrations/course/operations.json').read_text(encoding='utf-8'))['operations'])
    print(json.dumps({'course': course_report}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # SQL错误可能含姓名、数据和密码；命令失败只报告类型和稳定业务错误。
        raise SystemExit(str(error) if isinstance(error, ValueError) else type(error).__name__) from None
