#!/usr/bin/env python3
"""首次课程接入：本部教师、分公司学生，合格RBAC管理员兼任课程管理员。"""
import argparse
import configparser
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def manifest(snapshot, configuration, catalog):
    """回执绑定不可变来源与接入规则，不绑定后续角色、启停或显示名调整。"""
    payload = {
        'configuration': configuration, 'catalog': catalog,
        'users': sorted(user['id'] for user in snapshot['users']),
        'sources': sorted(snapshot['sources'], key=lambda item: (item['kind'], item['key'])),
    }
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def branch_name(department_id, departments, root_id):
    """沿实际组织父链寻找分公司；没有分公司祖先且连到可信根才是本部。"""
    visited = set()
    company = None
    while department_id != root_id:
        if department_id in visited or department_id not in departments:
            raise ValueError('SOURCE_ORGANIZATION_MISMATCH')
        visited.add(department_id)
        node = departments[department_id]
        if node.get('deleted') or node['parent_id'] is None:
            raise ValueError('SOURCE_ORGANIZATION_MISMATCH')
        if node['type'] == 'COMPANY':
            company = node['name']
        department_id = node['parent_id']
    return company


def plan_initialization(snapshot, configuration, catalog):
    """逐个核来源与组织，课程身份不从职位、领导称谓或RBAC级别猜测。"""
    roles = configuration['roles']
    if (set(roles) != {'teacher', 'student', 'administrator'}
            or len(set(roles.values())) != 3
            or any(not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,99}', code)
                   for code in roles.values())):
        raise ValueError('ROLE_CONFIGURATION_INVALID')
    roots = [row for row in snapshot['sources']
             if row['kind'] == 'ORGANIZATION' and row['key'] == configuration['headquartersSourceKey']]
    if len(roots) != 1:
        raise ValueError('TRUSTED_ROOT_REQUIRED')
    root_id = roots[0]['department_id']
    departments = {row['id']: row for row in snapshot['departments']}
    root = departments.get(root_id)
    if (root is None or root['parent_id'] is not None or root['type'] != 'HEADQUARTERS'
            or root.get('deleted')):
        raise ValueError('TRUSTED_ROOT_REQUIRED')
    users = {row['id']: row for row in snapshot['users']}
    personnel = [row for row in snapshot['sources'] if row['kind'] in {'EMPLOYEE', 'BOOTSTRAP'}]
    indexed = {row['user_id']: row for row in personnel}
    if len(indexed) != len(personnel) or set(indexed) != set(users):
        raise ValueError('SOURCE_MAPPING_INCOMPLETE')
    bootstrap = [row for row in personnel if row['kind'] == 'BOOTSTRAP']
    if len(bootstrap) != 1 or not snapshot['admin_role_ids']:
        raise ValueError('INITIAL_ADMIN_INVALID')
    administrators = sorted(user['id'] for user in users.values()
                            if user['enabled'] and user['level'] == 5 and user['department_id'] == root_id)
    if bootstrap[0]['user_id'] not in administrators:
        raise ValueError('INITIAL_ADMIN_INVALID')
    teachers, students, people = [], [], []
    for ident, user in sorted(users.items()):
        source = indexed[ident]
        if source['key'] != user['username']:
            raise ValueError('SOURCE_MAPPING_INCOMPLETE')
        company = branch_name(user['department_id'], departments, root_id)
        if source['kind'] == 'BOOTSTRAP':
            teacher = True
        else:
            cells = source['cells']
            expected = company or configuration['headquartersSourceKey']
            if len(cells) != 6 or cells[1].strip() != user['username'] or cells[2].strip() != expected:
                raise ValueError('SOURCE_ORGANIZATION_MISMATCH')
            teacher = company is None
        (teachers if teacher else students).append(ident)
        department = departments[user['department_id']]['name']
        if len(user['username']) > 64 or len(user['name']) > 128 or len(department) > 128:
            raise ValueError('COURSE_BINDING_FIELD_TOO_LONG')
        people.append({'id': ident, 'username': user['username'], 'name': user['name'],
                       'department': department, 'teacher': teacher})
    return {'system': catalog['system'], 'role_codes': dict(roles), 'root_id': root_id,
            'teachers': teachers, 'students': students, 'administrators': administrators,
            'people': people, 'admin_role_ids': sorted(snapshot['admin_role_ids']),
            'manifest': manifest(snapshot, configuration, catalog)}


def sql_text(value):
    """使用UTF8十六进制文本，不受引号、反斜杠或SQL模式影响。"""
    return "''" if value == '' else 'CONVERT(0x' + str(value).encode('utf-8').hex() + ' USING utf8mb4)'


class Mysql:
    """使用显式本机客户端配置，秘密不进入命令参数或报告。"""

    def __init__(self, executable, configuration, schema):
        """数据源固定为当前工程的明确数据库，不接受额外SQL参数。"""
        if schema not in {'rbac_core', 'course_project'}:
            raise ValueError('DATABASE_NAME_INVALID')
        settings = configparser.ConfigParser(interpolation=None, allow_no_value=True)
        try:
            settings.read(configuration, encoding='utf-8-sig')
        except configparser.Error:
            raise ValueError('LOCAL_CLIENT_CONFIGURATION_INVALID') from None
        port = settings.get('client', 'port', fallback='') or ''
        if (settings.get('client', 'host', fallback='') not in {'127.0.0.1', 'localhost'}
                or not port.isdigit() or not 1 <= int(port) <= 65535):
            raise ValueError('LOCAL_DATABASE_REQUIRED')
        self.command = [str(executable), '--defaults-file=' + str(configuration.resolve()),
                        '--no-login-paths', '--host=127.0.0.1', '--port=' + port,
                        '--protocol=TCP', '--skip-force',
                        '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names', schema]

    def execute(self, statement):
        """任何SQL失败整体报告失败，不回显可能含员工信息的数据库诊断。"""
        completed = subprocess.run(self.command, input=statement.encode('utf-8'), capture_output=True)
        if completed.returncode:
            raise ValueError('MYSQL_OPERATION_FAILED')
        return completed.stdout.decode('utf-8').strip()

    def rows(self, statement):
        """每行一个JSON对象，避免分隔符损坏姓名和来源字段。"""
        output = self.execute(statement)
        return [json.loads(line) for line in output.splitlines()] if output else []


def read_snapshot(database):
    """只读取组织、身份与来源；不读取密码哈希、令牌或历史业务日志。"""
    users = database.rows("""SELECT JSON_OBJECT('id',u.id,'username',u.username,'name',u.name,
        'department_id',u.department_id,'enabled',u.enabled+0,
        'level',COALESCE(MAX(CASE WHEN r.enabled=1 THEN r.management_level END),1))
        FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id
        LEFT JOIN roles r ON r.id=ur.role_id GROUP BY u.id ORDER BY u.id;""")
    departments = database.rows("""SELECT JSON_OBJECT('id',id,'name',name,'parent_id',parent_id,
        'type',type,'deleted',deleted+0) FROM departments ORDER BY id;""")
    sources = database.rows("""SELECT JSON_OBJECT('kind',source_type,'key',source_key,
        'department_id',department_id,'user_id',user_id,'hash',file_sha256,
        'row',source_row,'cells',CAST(source_json AS JSON)) FROM import_sources ORDER BY id;""")
    role_ids = database.rows("SELECT JSON_OBJECT('id',id) FROM roles WHERE enabled=1 AND management_level=5;")
    return {'users': users, 'departments': departments, 'sources': sources,
            'admin_role_ids': [row['id'] for row in role_ids]}


def course_bindings_sql(plan, teachers, students):
    """允许中断后重试相同绑定；冲突或额外身份拒绝，不覆盖老师已有对象。"""
    expected = {str(person['id']): person for person in plan['people']}
    existing = set()
    for rows, kind in ((teachers, True), (students, False)):
        for row in rows:
            uid = row['user_id']
            person = expected.get(uid)
            if (person is None or person['teacher'] != kind or row['no'] != person['username']
                    or uid in existing or row.get('deleted')):
                raise ValueError('COURSE_BINDING_CONFLICT')
            existing.add(uid)
    statements = ['START TRANSACTION;']
    audit = sql_text('course-initializer') + ',UTC_TIMESTAMP(6),'
    audit += sql_text('course-initializer') + ',UTC_TIMESTAMP(6)'
    for person in plan['people']:
        if str(person['id']) in existing:
            continue
        values = ','.join(sql_text(person[key]) for key in ('id', 'username', 'name'))
        if person['teacher']:
            statements.append('INSERT INTO teacher(user_id,employee_no,name,title,department,'
                              'creator,created_at,modifier,modi_at) VALUES('
                              + values + ',' + sql_text('教师') + ',' + sql_text(person['department']) + ',' + audit + ');')
        else:
            statements.append('INSERT INTO student(user_id,student_no,name,status,'
                              'creator,created_at,modifier,modi_at) VALUES('
                              + values + ",'active'," + audit + ');')
    return '\n'.join(statements + ['COMMIT;'])


def operation_catalog_sql(system_variable, system, operations, login_code):
    """显式首次安装接入元数据，不在产品启动或回执重跑时覆盖运行配置。"""
    if not isinstance(operations, list) or any(not isinstance(op, dict) for op in operations):
        raise ValueError('OPERATION_CATALOG_INVALID')
    statements = ['INSERT INTO application_profiles(system_id,login_permission_code) VALUES('
                  + system_variable + ',' + sql_text(login_code) + ');']
    seen = set()
    for operation in operations:
        ident = operation.get('operationId', '')
        method, path = operation.get('method', ''), operation.get('path', '')
        roles = operation.get('permissionRoles', [])
        if (operation.get('system') != system or not isinstance(ident, str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,128}', ident)
                or ident in seen or not isinstance(method, str) or method not in {'GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'}
                or not isinstance(path, str) or not path.startswith('/') or len(path) > 512 or re.search(r'[?#\s]', path)
                or not isinstance(operation.get('requiresPermission'), bool)
                or not isinstance(roles, list)
                or any(not isinstance(role, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,99}', role) for role in roles)
                or len(set(roles)) != len(roles)):
            raise ValueError('OPERATION_CATALOG_INVALID')
        seen.add(ident)
        values = [sql_text(ident), sql_text(method), sql_text(path),
                  '1' if operation['requiresPermission'] else '0', sql_text(json.dumps(sorted(roles)))]
        statements.append('INSERT INTO api_operations(system_id,operation_id,http_method,path_template,requires_permission,permission_roles) VALUES('
                          + system_variable + ',' + ','.join(values) + ');')
    return statements


def rbac_initialization_sql(plan, configuration, catalog, operations=None):
    """根锁内一次发布接入目录、角色、权限、人员关系与回执，不改变五角色级别。"""
    roles = plan['role_codes']
    statements = ['START TRANSACTION;', f"SELECT id FROM departments WHERE id={plan['root_id']} FOR UPDATE;"]
    statements.append('INSERT INTO systems(code,name,enabled) VALUES('
                      + sql_text(plan['system']) + ',' + sql_text(configuration['systemName']) + ',1);')
    statements.append('SET @course_system=LAST_INSERT_ID();')
    for key, label in [('teacher', '课程教师资格'), ('student', '课程学生资格'), ('administrator', '课程管理员')]:
        statements.append('INSERT INTO roles(code,name,enabled,management_level,role_type,application_id) VALUES('
                          + sql_text(roles[key]) + ',' + sql_text(label) + ",1,NULL,'APPLICATION_PRESET',@course_system);")
        statements.append('SET @course_' + key + '=LAST_INSERT_ID();')
    routes = catalog['routes']
    codes = catalog['display'] + sorted(routes)
    if len(set(codes)) != len(codes) or configuration['admissionPermission'] not in codes:
        raise ValueError('CATALOG_INVALID')
    api_variables = {}
    for code in codes:
        entries = routes.get(code, [])
        kind = 'API' if code in routes else 'MENU' if code in configuration['menuCodes'] else 'BUTTON'
        statements.append('INSERT INTO permissions(system_id,parent_id,code,name,type,enabled) VALUES(@course_system,NULL,'
                          + ','.join(sql_text(value) for value in (code, catalog['names'][code], kind)) + ',1);')
        statements.append('SET @course_permission=LAST_INSERT_ID();')
        for ident in plan['admin_role_ids']:
            statements.append(f'INSERT INTO role_permissions VALUES({ident},@course_permission);')
        if code == configuration['admissionPermission']:
            statements.append('INSERT INTO role_permissions VALUES(@course_teacher,@course_permission),(@course_student,@course_permission);')
        else:
            statements.append('INSERT INTO role_permissions VALUES(@course_administrator,@course_permission);')
        for method, route in entries:
            pair = (method, route)
            if pair not in api_variables:
                variable = '@course_api_' + str(len(api_variables))
                api_variables[pair] = variable
                statements.append('INSERT INTO api_resources(system_id,http_method,path_template,enabled) VALUES(@course_system,'
                                  + sql_text(method) + ',' + sql_text(route) + ',1);')
                statements.append('SET ' + variable + '=LAST_INSERT_ID();')
            statements.append('INSERT INTO permission_apis VALUES(@course_permission,' + api_variables[pair] + ');')
    for key in ('teachers', 'students', 'administrators'):
        variable = {'teachers': '@course_teacher', 'students': '@course_student',
                    'administrators': '@course_administrator'}[key]
        for ident in plan[key]:
            statements.append(f'INSERT INTO user_roles VALUES({ident},{variable});')
    if operations is not None:
        statements.extend(operation_catalog_sql('@course_system', plan['system'], operations, configuration['admissionPermission']))
    statements.append('INSERT INTO bootstrap_receipts(seed_key,manifest_sha256,completed_at) VALUES('
                      + sql_text(configuration['seedKey']) + ',' + sql_text(plan['manifest']) + ',UTC_TIMESTAMP(3));')
    return '\n'.join(statements + ['COMMIT;'])


def initialize(rbac, course, configuration, catalog, apply=False, operations=None):
    """先建立课程本地绑定，再原子授RBAC角色；中断重跑不重复创建或重置。"""
    snapshot = read_snapshot(rbac)
    receipts = rbac.rows('SELECT JSON_OBJECT(\'hash\',manifest_sha256) FROM bootstrap_receipts WHERE seed_key='
                         + sql_text(configuration['seedKey']) + ';')
    fingerprint = manifest(snapshot, configuration, catalog if operations is None else {**catalog, 'operations': operations})
    if receipts:
        if receipts[0]['hash'] != fingerprint:
            raise ValueError('INITIALIZATION_MANIFEST_CHANGED')
        return {'skipped': True, 'applied': False, 'reason': 'matching-receipt-preserves-runtime-changes'}
    plan = plan_initialization(snapshot, configuration, catalog)
    plan['manifest'] = fingerprint
    existing_catalog = rbac.rows('SELECT JSON_OBJECT(\'count\',COUNT(*)) FROM systems WHERE code='
                                 + sql_text(plan['system']) + ';')[0]['count']
    existing_roles = rbac.rows('SELECT JSON_OBJECT(\'count\',COUNT(*)) FROM roles WHERE code IN ('
                              + ','.join(sql_text(code) for code in plan['role_codes'].values()) + ');')[0]['count']
    if existing_catalog or existing_roles:
        raise ValueError('COURSE_CATALOG_ALREADY_PRESENT')
    teachers = course.rows("SELECT JSON_OBJECT('user_id',user_id,'no',employee_no,'deleted',deleted_at IS NOT NULL) FROM teacher;")
    students = course.rows("SELECT JSON_OBJECT('user_id',user_id,'no',student_no) FROM student;")
    course_sql = course_bindings_sql(plan, teachers, students)
    rbac_sql = rbac_initialization_sql(plan, configuration, catalog, operations)
    if apply:
        course.execute(course_sql)
        rbac.execute(rbac_sql)
    return {'skipped': False, 'applied': apply, 'users': len(plan['people']),
            'teachers': len(plan['teachers']), 'students': len(plan['students']),
            'courseAdministrators': len(plan['administrators']), 'manifest': fingerprint}


def main():
    """默认只读预览，实际执行必须显式apply；输出报告不包含员工明细或秘密。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', required=True, type=Path)
    parser.add_argument('--rbac-client', required=True, type=Path)
    parser.add_argument('--course-client', required=True, type=Path)
    parser.add_argument('--configuration', type=Path, default=ROOT / 'integrations/course/initialization.json')
    parser.add_argument('--catalog', type=Path, default=ROOT / 'integrations/course/catalog.json')
    parser.add_argument('--operations', type=Path, default=ROOT / 'integrations/course/operations.json')
    parser.add_argument('--report', required=True, type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    report = args.report.resolve()
    if not report.is_relative_to((ROOT / '.local').resolve()) or report.exists():
        raise ValueError('REPORT_MUST_BE_NEW_PRIVATE_FILE')
    configuration = json.loads(args.configuration.read_text(encoding='utf-8-sig'))
    catalog = json.loads(args.catalog.read_text(encoding='utf-8-sig'))
    result = initialize(Mysql(args.mysql, args.rbac_client, 'rbac_core'),
                        Mysql(args.mysql, args.course_client, 'course_project'), configuration, catalog, args.apply, json.loads(args.operations.read_text(encoding='utf-8-sig'))['operations'])
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        raise SystemExit('课程首次接入失败：' + (str(error) if isinstance(error, ValueError) else type(error).__name__))
