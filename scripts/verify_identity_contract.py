#!/usr/bin/env python3
"""R32本地真实HTTP验收：八项身份协议、JWT、当前映射与转接，报告不保存秘密。"""
import argparse
import base64
import json
from pathlib import Path
from urllib.parse import urlparse

from verify_course_integration import Verification

ROOT = Path(__file__).resolve().parents[1]


def run(args):
    """仅对明确的本机验收库临时停用后恢复API，不改变人员和角色集合。"""
    passwords = json.loads(args.credentials.read_text(encoding='utf-8-sig'))
    accounts = json.loads(args.accounts.read_text(encoding='utf-8-sig'))
    verification = Verification(args.rbac, args.gateway, passwords['employeePassword'])
    q = verification.request
    result = {'http': verification.evidence, 'passed': False}
    try:
        management = q('管理员进入管理端', args.rbac, '/api/auth/login', 200, 'POST',
                       body={'account': accounts['admin']['username'], 'password': passwords['adminPassword']})['data']['token']
        me = q('读取真实管理身份', args.rbac, '/api/me', 200, token=management)['data']['user']
        root_id, admin_id = me['departmentId'], me['id']

        def login(key, base):
            """账号口令只进入本机HTTP请求，不输出到终端或报告。"""
            password = passwords['adminPassword' if key == 'admin' else 'employeePassword']
            return q('签发' + key + '应用JWT', base, '/auth/login', 200, 'POST',
                     body={'username': accounts[key]['username'], 'password': password,
                           'systems': ['course-project-management']})['accessToken']

        admin = login('admin', args.rbac)
        teacher = login('headquartersEmployee', args.rbac)
        parts = admin.split('.')
        assert len(parts) == 3, 'JWT_COMPACT_FORMAT_REQUIRED'
        decode = lambda value: json.loads(base64.urlsafe_b64decode(value + '=' * (-len(value) % 4)))
        header, payload = decode(parts[0]), decode(parts[1])
        assert header['alg'] == 'HS256'
        assert not {'roles', 'permissions', 'identity'} & payload.keys()
        assert payload['sub'] == str(admin_id)
        assert payload['aud'] in ('course-project-management', ['course-project-management'])
        assert payload['exp'] - payload['iat'] == 43200 and payload['iss'] and payload['jti']
        result['jwtIdentityOnly'] = True
        q('篡改JWT签名拒绝', args.rbac, '/auth/me', 401, token=parts[0] + '.' + parts[1] + '.invalid')
        q('外部JWT不能访问管理受众', args.rbac, '/api/me', 401, token=admin)
        q('管理受众不能冒充外部身份', args.rbac, '/auth/me', 401, token=management)

        for path in ['/auth/me', '/auth/permissions/IDR', '/auth/departments',
                     f'/auth/departments/{root_id}/users', '/auth/api-permissions/createTerm',
                     f'/auth/users/{admin_id}']:
            q('匿名身份接口拒绝', args.rbac, path, 401)
        q('匿名退出拒绝', args.rbac, '/auth/logout', 401, 'POST')
        q('管理令牌不能从外部退出', args.rbac, '/auth/logout', 401, 'POST', management)
        q('错误受众退出不影响管理会话', args.rbac, '/api/me', 200, token=management)

        for base in [args.rbac, args.gateway]:
            token = admin if base == args.rbac else login('admin', base)
            current = q('当前应用身份', base, '/auth/me', 200, token=token)
            assert current['subject'] == str(admin_id)
            assert q('目录权限允许', base, '/auth/permissions/IDR', 200, token=token)['allowed']
            roots = q('逐层根目录', base, '/auth/departments', 200, token=token)
            assert roots['page'] == 1 and roots['pageSize'] == 20 and roots['total'] == 1
            assert roots['items'][0]['id'] == root_id and roots['items'][0]['hasChildren']
            children = q('直接子级分页', base, f'/auth/departments?parentId={root_id}&pageSize=1', 200, token=token)
            assert len(children['items']) == 1 and children['total'] > 1
            people = q('直属用户最小目录', base, f'/auth/departments/{root_id}/users', 200, token=token)
            assert people['total'] == 11
            assert all(set(row) == {'userId', 'no', 'name', 'departmentId', 'departmentName'} for row in people['items'])
            person = q('字符串身份最小查询', base, f'/auth/users/{admin_id}', 200, token=token)
            assert set(person) == {'userId', 'no', 'name'}
            assert q('API全局权限允许', base, '/auth/api-permissions/createTerm', 200, token=token) == {
                'operationId': 'createTerm', 'allowed': True}
            assert q('身份目录operationId可查询', base, '/auth/api-permissions/listIdentityDepartments', 200,
                     token=token)['allowed']
            assert q('未知操作拒绝', base, '/auth/api-permissions/noSuchOperation', 404,
                     token=token)['code'] == 'API_OPERATION_NOT_FOUND'
            assert q('未知字符串身份404', base, '/auth/users/sso-not-present', 404,
                     token=token)['code'] == 'IDENTITY_USER_NOT_FOUND'
            q('零起页号拒绝', base, '/auth/departments?page=0', 400, token=token)
            q('过大页面拒绝', base, '/auth/departments?pageSize=101', 400, token=token)
            q('查询后销毁当前JWT', base, '/auth/logout', 204, 'POST', token)
            q('退出后的原JWT立即失效', base, '/auth/me', 401, token=token)
        q('其他会话仍然有效', args.rbac, '/auth/me', 200, token=teacher)
        for path in ['/auth/departments', f'/auth/departments/{root_id}/users']:
            q('普通教师无目录权限', args.rbac, path, 403, token=teacher)
        assert not q('普通教师无全局学期管理权', args.rbac, '/auth/api-permissions/createTerm', 200,
                     token=teacher)['allowed']
        assert q('对象业务仍交课程校验', args.rbac, '/auth/api-permissions/getCourse', 200,
                 token=teacher)['allowed']

        admin = login('admin', args.rbac)
        systems = q('定位真实应用目录', args.rbac, '/api/systems', 200, token=management)['data']
        system = next(item['id'] for item in systems if item['code'] == 'course-project-management')
        apis = q('读取实际API资源', args.rbac, f'/api/api-resources?systemId={system}&page=0&size=100', 200,
                 token=management)['data']['items']
        for path, method, target, expected in [
                ('/auth/departments', 'GET', '/auth/departments', 403),
                ('/terms', 'POST', '/auth/api-permissions/createTerm', 200)]:
            api = next(item for item in apis if item['path'] == path and item['method'] == method)
            assert api['enabled'] is True, 'PRESERVE_EXISTING_DISABLED_CONFIGURATION'
            endpoint = '/api/api-resources/' + str(api['id'])
            try:
                q('真实管理写停用API', args.rbac, endpoint, 200, 'PATCH', management, {'enabled': False})
                response = q('原JWT下一请求读取最新状态', args.rbac, target, expected, token=admin)
                if expected == 200:
                    assert response['allowed'] is False
            finally:
                q('恢复原启用状态', args.rbac, endpoint, 200, 'PATCH', management, {'enabled': True})
            restored = q('恢复授权后原JWT生效', args.rbac, target, 200, token=admin)
            if 'allowed' in restored:
                assert restored['allowed'] is True
        result.update(passed=True, allEightCoveredDirectlyAndThroughGateway=True,
                      sameTokenRevocationChecked=True)
    finally:
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'passed': True, 'httpChecks': len(verification.evidence), 'jwtIdentityOnly': True}))


def main():
    """仅接受明确loopback及.local新报告，实际目标和账号由操作者提供。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rbac', default='http://127.0.0.1:8081')
    parser.add_argument('--gateway', default='http://127.0.0.1:8085')
    for name in ('credentials', 'accounts', 'report'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    for url in (args.rbac, args.gateway):
        parsed = urlparse(url)
        if parsed.scheme != 'http' or parsed.hostname not in {'127.0.0.1', 'localhost'} or not parsed.port:
            raise ValueError('LOCAL_HTTP_REQUIRED')
    if args.report.exists() or not args.report.resolve().is_relative_to((ROOT / '.local').resolve()):
        raise ValueError('REPORT_MUST_BE_NEW_PRIVATE_FILE')
    args.report.parent.mkdir(parents=True, exist_ok=True)
    run(args)


if __name__ == '__main__':
    main()
