"""RBAC运行验收在全新工作区私有MySQL中验证正式初始化和默认JAR；不连接业务实例。"""

from pathlib import Path
from contextlib import contextmanager
import hashlib
import json
import os
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request


ROOT = Path(__file__).resolve().parents[2]
COMMON_GIT = Path(subprocess.check_output(
    ['git', 'rev-parse', '--git-common-dir'], cwd=ROOT, text=True).strip())
WORKSPACE = (ROOT / COMMON_GIT).resolve().parent
LOCAL = WORKSPACE / '.local'
REPORT = LOCAL / 'rbac-issues' / ('structured-runtime-' + str(time.time_ns()))
SOURCES = {
    '市政公司员工信息表.xlsx': '66657800a5e2e252c9d1dedfee2dbd54b694e3a5912166c2bac79c8703b20516',
    '市政公司部门信息表.xlsx': '555ae8cb1a1569dda43cf53c47ee37f4ba4e8964f334ec8f2e1ef73699686c40',
    '市政公司应用软件功能权限清单.xlsx': '13c18de30710cc2867588aa99843c6c388c0a8ee85029f3420f7b4c4c9fad655',
}
ENV = dict(os.environ)
JAVA_ENV = {}
WINDOW_FLAGS = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
MYSQL = []
JAVA = ''
PORT = 0
HTTP_COUNT = 0
ADMIN_ID = 0


def require(condition, message):
    """使用运行时检查，避免python -O跳过数据库安全边界或业务断言。"""
    if not condition:
        raise RuntimeError(message)


def sql(statement):
    """仅向已校验的本机私有实例发SQL，口令放子进程环境且不回显查询或返回值。"""
    environment = dict(ENV, MYSQL_PWD=ENV['RBAC_CORE_DB_PASSWORD'])
    result = subprocess.run(MYSQL, input=statement, encoding='utf-8',
                            capture_output=True, env=environment, timeout=40)
    require(result.returncode == 0, 'RBAC运行验收数据库步骤失败；禁止把SQL/个人数据输出到公共报告')
    return result.stdout.strip()


def literal(value):
    """测试夹具字符串以UTF-8十六进制字面量传入，避免姓名或工号参与SQL拼接。"""
    return 'CONVERT(0x' + str(value).encode('utf-8').hex() + ' USING utf8mb4)'


def prepare():
    """确认非默认本机端口、工作区私有datadir及空库后，才允许创建验收数据。"""
    global MYSQL, JAVA, JAVA_ENV
    required = ('RBAC_CORE_DB_URL', 'RBAC_CORE_DB_USER', 'RBAC_CORE_DB_PASSWORD',
                'RBAC_CORE_EMPLOYEE_PASSWORD', 'RBAC_CORE_ADMIN_PASSWORD', 'RBAC_ACCEPTANCE_DATADIR')
    require(all(ENV.get(key) for key in required), '缺少RBAC运行验收必填环境配置，见验收说明')
    # Spring环境可覆盖YAML中的RBAC占位符；JVM环境还可注入-D/agent，必须先拒绝。
    overrides = []
    for key in ENV:
        normalized = key.upper().replace('.', '_').replace('-', '_')
        if normalized.startswith(('SPRING_', 'LOADER_')) or normalized in (
                'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
            overrides.append(key)
    require(not overrides, 'RBAC运行验收不允许额外Spring/JVM配置；请使用干净环境再运行')
    operating_system = {'PATH', 'SYSTEMROOT', 'WINDIR', 'COMSPEC', 'PATHEXT', 'TEMP', 'TMP',
                        'USERPROFILE', 'APPDATA', 'LOCALAPPDATA', 'PROGRAMDATA', 'HOME',
                        'LANG', 'LC_ALL', 'TZ'}
    JAVA_ENV = {key: value for key, value in ENV.items() if key.upper() in operating_system}
    for key in ('JAVA_HOME', 'RBAC_CORE_DB_URL', 'RBAC_CORE_DB_USER', 'RBAC_CORE_DB_PASSWORD',
                'RBAC_CORE_EMPLOYEE_PASSWORD', 'RBAC_CORE_ADMIN_PASSWORD', 'RBAC_CORE_ADMIN_USERNAME'):
        if key in ENV:
            JAVA_ENV[key] = ENV[key]
    url = urllib.parse.urlsplit(ENV['RBAC_CORE_DB_URL'].removeprefix('jdbc:'))
    require(url.scheme == 'mysql' and url.hostname in ('127.0.0.1', 'localhost')
            and url.port is not None and 1024 <= url.port <= 65535 and url.port != 3306
            and url.path == '/rbac_core' and not url.username and not url.password,
            'RBAC运行验收只允许非默认本机端口的rbac_core；不得连接业务数据库')
    expected = Path(ENV['RBAC_ACCEPTANCE_DATADIR']).resolve()
    require(expected.is_relative_to(LOCAL.resolve()), 'RBAC运行验收 datadir必须在当前仓库共同工作区.local内')
    MYSQL = [ENV.get('MYSQL_CLIENT') or shutil.which('mysql') or 'mysql', '--no-defaults',
             '--protocol=TCP', '--host=' + url.hostname, '--port=' + str(url.port),
             '--user=' + ENV['RBAC_CORE_DB_USER'], '--default-character-set=utf8mb4', '-N', '-B', 'rbac_core']
    actual = Path(sql('SELECT @@datadir;')).resolve()
    require(actual == expected, '连接实例datadir与指定私有实例不符，拒绝写入')
    require(sql('SHOW TABLES;') == '', 'RBAC运行验收要求全新空库，拒绝覆盖或清空已有数据；请另建私有实例')
    for filename, digest in SOURCES.items():
        require(hashlib.sha256((ROOT / filename).read_bytes()).hexdigest() == digest, '原始Excel摘要不符')
    java_home = ENV.get('JAVA_HOME')
    JAVA = str(Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if java_home else shutil.which('java')
    require(JAVA is not None, '未找到Java')
    version = subprocess.run([JAVA, '-version'], capture_output=True, text=True, check=True, env=JAVA_ENV)
    require('version "21.' in version.stderr, 'RBAC运行验收正式复现要求实际JDK21，不以release编译参数代替运行环境')
    require((ROOT / 'backend/target/rbac0-0.1.0.jar').is_file(), '请先构建实际Spring Boot jar')
    REPORT.mkdir(parents=True)
    (REPORT / 'environment.json').write_text(json.dumps({
        'base': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'java': version.stderr.splitlines()[0], 'mysql': sql('SELECT VERSION();'),
        'sourceSha256': SOURCES,
    }, ensure_ascii=False, indent=2), encoding='utf-8')
    ENV['RBAC_CORE_SOURCE_DIR'] = str(ROOT)
    JAVA_ENV['RBAC_CORE_SOURCE_DIR'] = str(ROOT)
    for filename in ('rbac-core-schema.sql', 'rbac-core-runtime.sql'):
        sql((ROOT / 'deploy' / filename).read_text(encoding='utf-8'))


def offline(main, name, extra=()):
    """执行正式离线命令并保存本地日志；不增加测试profile或降低生产口令强度。"""
    with (REPORT / name).open('wb') as log:
        result = subprocess.run([JAVA, '-Xmx512m', '-Dloader.main=' + main, '-cp',
                                 'backend/target/rbac0-0.1.0.jar', 'org.springframework.boot.loader.launch.PropertiesLauncher',
                                 *extra], cwd=ROOT, env=JAVA_ENV, stdout=log, stderr=subprocess.STDOUT,
                                timeout=1500, creationflags=WINDOW_FLAGS)
    require(result.returncode == 0, '离线步骤失败，检查被忽略的本地日志：' + name)


def request(method, path, payload=None, token=None):
    """通过真实HTTP验证外壳，不把请求体、账号、令牌或完整响应写到报告。"""
    global HTTP_COUNT
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request('http://127.0.0.1:' + str(PORT) + path,
                                 data=None if payload is None else json.dumps(payload).encode(),
                                 headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            status, body = response.status, json.load(response)
    except urllib.error.HTTPError as error:
        status, body = error.code, json.load(error)
    HTTP_COUNT += 1
    require(body.get('requestId') and 'data' in body and 'message' in body and 'code' in body,
            '真实HTTP响应缺少统一外壳/requestId：' + method + ' ' + path.split('?')[0])
    return status, body


def expect(method, path, status=200, payload=None, token=None, code=None):
    """只输出场景路径及预期状态，不输出含个人数据的响应内容。"""
    actual, body = request(method, path, payload, token)
    require(actual == status and (code is None or body['code'] == code),
            'HTTP契约失败：' + method + ' ' + path.split('?')[0] + '，预期' + str(status))
    require(body['code'] == 'OK' if status < 400 else body['data'] is None, '成功/错误外壳不符')
    return body['data']


def login(account, password, status=200, code=None):
    """双入口沿同一真实登录端点，不依据输入类型或角色名模拟鉴权。"""
    return expect('POST', '/api/auth/login', status, {'account': account, 'password': password}, code=code)


@contextmanager
def runtime(round_number):
    """启动默认java -jar且不加测试配置，结束时始终停止本次进程。"""
    global PORT
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        PORT = probe.getsockname()[1]
    environment = dict(JAVA_ENV, PORT=str(PORT))
    with (REPORT / ('default-' + str(round_number) + '.log')).open('wb') as log:
        process = subprocess.Popen([JAVA, '-Xmx512m', '-jar', 'backend/target/rbac0-0.1.0.jar'],
                                   cwd=ROOT, env=environment, stdout=log,
                                   stderr=subprocess.STDOUT, creationflags=WINDOW_FLAGS)
        try:
            for _ in range(120):
                require(process.poll() is None, '默认jar退出，检查本地启动日志')
                try:
                    expect('GET', '/api/me', 401, code='AUTHENTICATION_REQUIRED')
                    break
                except (ConnectionError, urllib.error.URLError, TimeoutError):
                    time.sleep(.5)
            else:
                raise RuntimeError('默认jar启动超时')
            yield
        finally:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


def account_for(role_name):
    """从已核对的首次授权选择一名夹具，不将姓名或岗位当成运行时授权判断。"""
    row = sql('SELECT u.id,u.username,u.phone,u.department_id FROM users u JOIN user_roles ur ON u.id=ur.user_id '
              'JOIN roles r ON r.id=ur.role_id WHERE r.name=' + literal(role_name) + ' ORDER BY u.id LIMIT 1;')
    parts = row.split('\t')
    require(len(parts) == 4, '找不到初始化验收账号')
    return {'id': int(parts[0]), 'username': parts[1], 'phone': parts[2], 'department': int(parts[3])}


def verify_login_and_queries(manager, admin_token):
    """核对双入口同身份/授权/范围、歧义、前导零及真实分页，不依赖内存原型。"""
    employee_password = ENV['RBAC_CORE_EMPLOYEE_PASSWORD']
    first = login(manager['username'], employee_password)
    second = login(manager['phone'], employee_password)
    require(first['user'] == second['user'] and first['user']['id'] == manager['id'], '双入口身份不一致')
    mt = second['token']
    require(expect('GET', '/api/me/permissions', token=first['token']) == expect('GET', '/api/me/permissions', token=mt), '双入口权限不一致')
    tree = expect('GET', '/api/departments/tree', token=mt)
    require(tree and all(item['parentId'] is None for item in tree if item['id'] == manager['department']), '范围树根未投影')
    page = expect('GET', '/api/users?size=2', token=mt)
    require(page['size'] == 2 and page['page'] == 0 and page['total'] >= len(page['items']), '分页外形错误')
    end = expect('GET', '/api/users?size=2&page=2147483647', token=mt)
    require(end['items'] == [] and end['total'] == page['total'], '越末页total变化')
    q = urllib.parse.quote(manager['username'])
    found = expect('GET', '/api/users?q=' + q + '&enabled=true&departmentId=' + str(manager['department']), token=mt)
    require(any(item['id'] == manager['id'] for item in found['items']), '姓名/工号状态部门组合筛选不符')
    expect('GET', '/api/roles', 403, token=mt, code='PERMISSION_DENIED')
    expect('GET', '/api/authorization-logs', 403, token=mt)
    root = int(sql('SELECT department_id FROM users WHERE id=' + str(ADMIN_ID)))
    expect('GET', '/api/users?departmentId=' + str(root), 403, token=mt, code='MANAGEMENT_SCOPE_DENIED')
    expect('GET', '/api/users/9007199254740991/roles', 404, token=admin_token, code='RESOURCE_NOT_FOUND')
    expect('GET', '/api/users/9007199254740991/roles', 403, token=mt, code='MANAGEMENT_SCOPE_DENIED')
    ordinary = account_for('普通员工')
    for value in (ordinary['username'], ordinary['phone']):
        login(value, employee_password, 403, 'MANAGEMENT_ACCESS_DENIED')
    for value in (manager['username'], manager['phone'], 't13-unknown-account'):
        login(value, employee_password + '-wrong', 401, 'LOGIN_FAILED')
    # 以下账号字段只由隔离验收SQL布置边界，产品没有账号资料编辑接口；每项恢复原始字段。
    uid = manager['id']
    try:
        sql('UPDATE users SET phone=NULL WHERE id=' + str(uid))
        login(manager['username'], employee_password)
        login(manager['phone'], employee_password, 401, 'LOGIN_FAILED')
        sql('UPDATE users SET phone=username WHERE id=' + str(uid))
        login(manager['username'], employee_password)
    finally:
        sql('UPDATE users SET phone=' + literal(manager['phone']) + ' WHERE id=' + str(uid))
    try:
        sql('UPDATE users SET username=' + literal(manager['phone']) + ' WHERE id=' + str(ordinary['id']))
        login(manager['phone'], employee_password, 401, 'LOGIN_FAILED')
    finally:
        sql('UPDATE users SET username=' + literal(ordinary['username']) + ' WHERE id=' + str(ordinary['id']))
    leading = '000' + str(time.time_ns())
    try:
        sql('UPDATE users SET username=' + literal(leading) + ' WHERE id=' + str(uid))
        require(login(leading, employee_password)['user']['username'] == leading, '前导零丢失')
        login(leading.lstrip('0'), employee_password, 401, 'LOGIN_FAILED')
    finally:
        sql('UPDATE users SET username=' + literal(manager['username']) + ' WHERE id=' + str(uid))
    expect('POST', '/api/auth/logout', token=mt)
    expect('POST', '/api/auth/logout', token=mt)
    expect('POST', '/api/auth/logout')
    expect('GET', '/api/me', 401, token=mt, code='SESSION_INVALID')
    print('RBAC运行验收 dual login, admission, identifier boundaries, scope/search/pages and idempotent logout passed.', flush=True)


def verify_management(manager, root_token):
    """以真实API走角色/资源/人员链路，并留下一项调整验证重复初始化不覆盖。"""
    role = expect('POST', '/api/roles', 201, {'code': 't13-' + str(time.time_ns()), 'name': 'RBAC运行验收隔离空权限角色'}, root_token)
    require(expect('GET', '/api/roles/' + str(role['id']) + '/permissions', token=root_token)['ids'] == [], '新角色非空')
    expect('PATCH', '/api/roles/' + str(role['id']), payload={'name': 'RBAC运行验收隔离角色改名'}, token=root_token)
    expect('PUT', '/api/roles/' + str(role['id']) + '/permissions', payload={'ids': []}, token=root_token)
    system = expect('GET', '/api/systems', token=root_token)[0]
    tree = expect('GET', '/api/permissions/tree?systemId=' + str(system['id']), token=root_token)
    require(tree, '资源树为空')
    apis = expect('GET', '/api/api-resources?size=2', token=root_token)
    require(apis['total'] == 20, '正式API目录不是20项')
    permission_id = int(sql("SELECT id FROM permissions WHERE code='rbac.logs.read';"))
    mapping = expect('GET', '/api/permissions/' + str(permission_id) + '/apis', token=root_token)
    expect('PUT', '/api/permissions/' + str(permission_id) + '/apis', payload={'ids': mapping['ids']}, token=root_token)
    expect('PATCH', '/api/permissions/' + str(permission_id), payload={'enabled': True}, token=root_token)
    expect('PATCH', '/api/api-resources/' + str(mapping['ids'][0]), payload={'enabled': True}, token=root_token)
    expect('PUT', '/api/users/' + str(ADMIN_ID) + '/roles', 409, {'ids': []}, root_token, 'LAST_ADMIN_REQUIRED')
    # 选择同部门普通员工，使部门操作者的合法授权范围可直接核验。
    row = sql('SELECT u.id,u.username,u.phone FROM users u JOIN user_roles ur ON u.id=ur.user_id JOIN roles r ON r.id=ur.role_id '
              'WHERE r.name=' + literal('普通员工') + ' AND u.department_id=' + str(manager['department']) + ' ORDER BY u.id LIMIT 1;')
    parts = row.split('\t')
    require(len(parts) == 3, '部门无直属普通员工验收夹具')
    target, username, phone = int(parts[0]), parts[1], parts[2]
    mt = login(manager['username'], ENV['RBAC_CORE_EMPLOYEE_PASSWORD'])['token']
    candidate = expect('GET', '/api/users/' + str(target) + '/assignable-roles?size=100', token=mt)
    role_id = int(sql('SELECT role_id FROM user_roles WHERE user_id=' + str(manager['id'])))
    require(any(item['id'] == role_id for item in candidate['items']), '候选未含自身可授予角色')
    expect('GET', '/api/users/' + str(target) + '/roles', token=mt)
    expect('PUT', '/api/users/' + str(target) + '/roles', payload={'ids': [role_id]}, token=mt)
    worker = login(phone, ENV['RBAC_CORE_EMPLOYEE_PASSWORD'])
    expect('GET', '/api/users', token=worker['token'])
    expect('PUT', '/api/users/' + str(target) + '/roles', payload={'ids': []}, token=mt)
    expect('GET', '/api/users', 403, token=worker['token'], code='PERMISSION_DENIED')
    login(username, ENV['RBAC_CORE_EMPLOYEE_PASSWORD'], 403, 'MANAGEMENT_ACCESS_DENIED')
    expect('PATCH', '/api/users/' + str(target) + '/status', payload={'enabled': False}, token=mt)
    expect('GET', '/api/me', 401, token=worker['token'], code='SESSION_INVALID')
    for value in (username, phone):
        login(value, ENV['RBAC_CORE_EMPLOYEE_PASSWORD'], 401, 'LOGIN_FAILED')
    other = int(sql('SELECT department_id FROM users WHERE id=' + str(ADMIN_ID)))
    # 根操作者具有双范围，停用用户仍可维护资料；调动不改变id或剩余角色。
    changed = expect('PUT', '/api/users/' + str(target) + '/department', payload={'departmentId': other}, token=root_token)
    require(changed['id'] == target and changed['departmentId'] == other and changed['roleIds'] == [], '调动改变身份或授权')
    expect('PUT', '/api/users/' + str(target) + '/department', 403, {'departmentId': manager['department']}, mt, 'MANAGEMENT_SCOPE_DENIED')
    sql('UPDATE users SET password_hash=(SELECT hash FROM (SELECT password_hash hash FROM users WHERE id=' + str(ADMIN_ID) + ') p) WHERE id=' + str(target))
    print('RBAC运行验收 packaged role/resource writes, personnel grant/revoke/disable/transfer and last-maintainer rollback passed.', flush=True)
    return target, other


def verify_logs_and_removed(root_token):
    """验证快照日志、拒绝脱敏及旧入口不能继续执行业务。"""
    page = expect('GET', '/api/authorization-logs?limit=1', token=root_token)
    require(page['hasMore'] and len(page['items']) == 1, '日志快照夹具不足')
    following = expect('GET', '/api/authorization-logs?limit=1&cursor=' + urllib.parse.quote(page['nextCursor']), token=root_token)
    require(following['snapshotId'] == page['snapshotId'] and following['total'] == page['total']
            and following['items'][0]['id'] != page['items'][0]['id'], '日志快照不稳定或重复')
    expect('GET', '/api/authorization-logs?limit=2&cursor=' + urllib.parse.quote(page['nextCursor']), 400, token=root_token, code='INVALID_CURSOR')
    require(sql('SELECT COUNT(*) FROM authorization_logs l JOIN users u ON l.username=u.phone WHERE u.phone<>u.username;') == '0', '日志复制了手机号登录输入')
    require(sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name IN ('employee_no','token','password','archived_at');") == '0', '旧模型/敏感日志字段复活')
    for method, path, payload in [('GET', '/api/users/page', None), ('GET', '/api/users/filters', None),
                                  ('GET', '/api/applications', None), ('POST', '/api/authorize', {}),
                                  ('POST', '/api/authorization-logs/archive', {}), ('GET', '/api/management/permissions', None)]:
        status, _ = request(method, path, payload, root_token)
        require(status in (403, 404, 405), '已移除入口仍能执行业务：' + path)
    print('RBAC运行验收 log snapshot/filter cursor, login-input minimization and removed endpoints passed.', flush=True)


def main():
    """完整验收采用一次全新数据准备和两次默认启动，结束不启动后续任务。"""
    global ADMIN_ID
    prepare()
    print('RBAC运行验收 fresh private schema verified; importing production BCrypt10 population.', flush=True)
    offline('com.municipal.rbaccore.bootstrap.ImportCommand', 'import.log')
    require(sql('SELECT COUNT(*) FROM users;') == '10001' and sql('SELECT COUNT(*) FROM users WHERE enabled=1;') == '10001', '新账号数量或启用状态不符')
    require(sql('SELECT COUNT(*) FROM departments;') == '110' and sql("SELECT COUNT(*) FROM import_sources WHERE source_type='ORGANIZATION';") == '111', '组织来源与目标数量不符')
    require(sql('SELECT COUNT(*) FROM user_roles;') == '0', '导入阶段自动授予角色')
    offline('com.municipal.rbaccore.bootstrap.BootstrapAuthorizationCommand', 'preview.log', ['--rbac.core.preview-file=' + str(REPORT / 'preview.csv')])
    require(sql('SELECT COUNT(*) FROM user_roles;') == '0', '预览写入授权')
    offline('com.municipal.rbaccore.bootstrap.BootstrapAuthorizationCommand', 'bootstrap.log')
    ADMIN_ID = int(sql("SELECT user_id FROM import_sources WHERE source_type='BOOTSTRAP';"))
    require(sql('SELECT GROUP_CONCAT(n ORDER BY n) FROM (SELECT COUNT(*) n FROM user_roles GROUP BY role_id) x;') == '1,64,72,106,9758', '初始五角色人数不符')
    require(sql('SELECT COUNT(*) FROM users WHERE department_id=(SELECT department_id FROM users WHERE id=' + str(ADMIN_ID) + ');') == '11', '根直属人员不符')
    print('RBAC运行验收 111 source/110 target organizations, 10001 enabled accounts, read-only preview and five-role counts passed.', flush=True)
    manager = account_for('部门经理')
    with runtime(1):
        root_token = login(ENV.get('RBAC_CORE_ADMIN_USERNAME', 'admin'), ENV['RBAC_CORE_ADMIN_PASSWORD'])['token']
        verify_login_and_queries(manager, root_token)
        target, department = verify_management(manager, root_token)
        verify_logs_and_removed(root_token)
    before = sql('SELECT password_hash FROM users WHERE id=' + str(target))
    offline('com.municipal.rbaccore.bootstrap.ImportCommand', 'repeat-import.log')
    offline('com.municipal.rbaccore.bootstrap.BootstrapAuthorizationCommand', 'repeat-bootstrap.log')
    with runtime(2):
        token = login(ENV.get('RBAC_CORE_ADMIN_USERNAME', 'admin'), ENV['RBAC_CORE_ADMIN_PASSWORD'])['token']
        changed = expect('GET', '/api/users/' + str(target) + '/roles', token=token)
        require(changed['roles'] == [], '二次初始化恢复已撤角色')
        require(sql('SELECT CAST(enabled AS UNSIGNED),department_id FROM users WHERE id=' + str(target)) == '0\t' + str(department), '二次初始化覆盖停用/调动')
        require(sql('SELECT password_hash FROM users WHERE id=' + str(target)) == before, '二次初始化覆盖口令')
        expect('GET', '/api/authorization-logs?limit=1', token=token)
    for filename, digest in SOURCES.items():
        require(hashlib.sha256((ROOT / filename).read_bytes()).hexdigest() == digest, '验收修改了原始Excel')
    (REPORT / 'summary.json').write_text(json.dumps({'result': 'PASS', 'httpRequests': HTTP_COUNT,
                                                   'scope': 'current structured backend only'}, indent=2), encoding='utf-8')
    print('RBAC运行验收 default JAR restart and repeat initialization preserved changes. Real HTTP requests=' + str(HTTP_COUNT), flush=True)
    print('Local evidence directory: ' + str(REPORT), flush=True)


if __name__ == '__main__':
    main()
