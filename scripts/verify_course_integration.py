#!/usr/bin/env python3
"""对虚构夹具执行真实双系统HTTP授撤权；口令仅从环境读取，报告不保存令牌。"""
import argparse
import json
import os
from pathlib import Path
import time
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


class Verification:
    """记录真实响应状态，所有管理写入只针对专用虚构角色和用户。"""

    def __init__(self, rbac, course, password):
        self.rbac, self.course, self.password = rbac, course, password
        self.evidence = []

    def request(self, label, base, path, expected, method='GET', token=None, body=None):
        """错误只输出步骤和状态，不将身份凭据带入报告或断言。"""
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = Request(base + path, data=json.dumps(body).encode() if body is not None else None,
                          headers=headers, method=method)
        try:
            response = urlopen(request, timeout=20)
        except HTTPError as error:
            response = error
        with response:
            status = response.status
            raw = response.read()
        self.evidence.append({'step': label, 'method': method, 'path': path, 'status': status,
                              'expected': expected, 'pass': status == expected})
        if status != expected:
            raise AssertionError(f'{label}: HTTP {status}, expected {expected}')
        return json.loads(raw) if raw else None

    def run(self):
        """保留原课程令牌，逐项修改RBAC关系并观察课程下一次真实请求。"""
        r, c, q = self.rbac, self.course, self.request
        admin = q('RBAC管理员登录', r, '/api/auth/login', 200, 'POST', body={
            'account': 'ui-admin', 'password': self.password})['data']['token']
        unbound = q('身份服务可认证但课程本地未绑定', r, '/auth/login', 200, 'POST', body={
            'username': 'ui-admin', 'password': self.password,
            'systems': ['course-project-management']})['accessToken']
        q('直取身份令牌不能绕过课程本地Teacher', c, '/admin/courses', 403, token=unbound)
        q('匿名课程请求拒绝', c, '/admin/courses', 401)
        q('普通员工无管理准入', r, '/api/auth/login', 403, 'POST', body={
            'account': 'ui-user-05', 'password': self.password})
        q('课程本地有教师但RBAC未授准入', c, '/login', 403, 'POST', body={
            'role': 'teacher', 'account': 'ui-user-07', 'password': self.password})
        course = q('老师原始登录链', c, '/login', 200, 'POST', body={
            'role': 'teacher', 'account': 'ui-user-05', 'password': self.password})['accessToken']
        reader = q('仅有教师身份也可登录', c, '/login', 200, 'POST', body={
            'role': 'teacher', 'account': 'ui-user-06', 'password': self.password})['accessToken']
        q('课程令牌不能进入管理端', r, '/api/me', 401, token=course)
        q('管理令牌不能进入课程端', c, '/me', 401, token=admin)
        courses = q('有权查看真实课程', c, '/admin/courses', 200, token=course)
        assert courses['total'] >= 1, '必须读取真实课程数据'
        q('仅教师身份不能查看管理课程', c, '/admin/courses', 403, token=reader)
        all_ids = q('读取专用课程角色完整授权', r, '/api/roles/51/permissions', 200, token=admin)['data']['ids']
        tree = q('读取课程实际权限目录', r, '/api/permissions/tree?systemId=2', 200, token=admin)['data']
        ids = {}
        def visit(items):
            """目录可能包含祖先，按真实响应而非硬编码主键查找。"""
            for item in items:
                ids[item['code']] = item['id']
                visit(item.get('children', []))
        visit(tree)
        q('课程真实身份查询路由要求UL', c, '/auth/users/7', 403, token=course)
        q('临时授予UL以验证真实调用链', r, '/api/roles/51/permissions', 200, 'PUT', admin,
          {'ids': all_ids + [ids['UL']]})
        lookup = q('课程UL经原客户端读取RBAC身份', c, '/auth/users/7', 200, token=course)
        assert lookup['no'] == 'ui-user-07'
        q('恢复不含UL的原课程角色', r, '/api/roles/51/permissions', 200, 'PUT', admin, {'ids': all_ids})
        mappings = q('读取TC真实映射', r, f"/api/permissions/{ids['TC']}/apis", 200, token=admin)['data']['ids']
        term_ids = []
        def create(label, expected):
            """唯一名称使授权结果不被重复学期约束掩盖。"""
            result = q(label, c, '/terms', expected, 'POST', course, {
                'name': 'RBAC联调-' + str(time.time_ns()), 'startDate': '2027-02-01', 'endDate': '2027-07-01'})
            if expected == 201:
                term_ids.append(result['id'])
        try:
            create('有权新建真实学期', 201)
            q('有权编辑真实学期', c, f'/terms/{term_ids[0]}', 200, 'PATCH', course, {
                'name': 'RBAC联调编辑-' + str(time.time_ns()), 'startDate': '2027-02-02', 'endDate': '2027-07-02'})
            q('有权设置当前学期', c, '/current-term', 204, 'PUT', course, {'termId': term_ids[0]})
            q('有权取消当前学期', c, '/current-term', 204, 'DELETE', course)
            q('恢复原始当前学期', c, '/current-term', 204, 'PUT', course, {'termId': 1})
            q('角色撤回后端TC', r, '/api/roles/51/permissions', 200, 'PUT', admin,
              {'ids': [ident for ident in all_ids if ident != ids['TC']]})
            create('原令牌下一请求拒绝新建', 403)
            q('角色恢复TC', r, '/api/roles/51/permissions', 200, 'PUT', admin, {'ids': all_ids})
            create('原令牌恢复新建', 201)
            q('单独撤回前端菜单', r, '/api/roles/51/permissions', 200, 'PUT', admin,
              {'ids': [ident for ident in all_ids if ident != ids['MTR']]})
            assert q('菜单实时拒绝', r, '/auth/permissions/MTR', 200, token=course)['allowed'] is False
            create('菜单不代替API门禁', 201)
            q('恢复前端菜单', r, '/api/roles/51/permissions', 200, 'PUT', admin, {'ids': all_ids})
            q('人员撤回课程操作角色', r, '/api/users/5/roles', 200, 'PUT', admin, {'ids': [4, 50]})
            create('人员撤权后真实接口拒绝', 403)
            q('人员恢复课程操作角色', r, '/api/users/5/roles', 200, 'PUT', admin, {'ids': [4, 50, 51]})
            create('人员恢复后原令牌成功', 201)
            q('拒绝跨系统API映射', r, f"/api/permissions/{ids['TC']}/apis", 400, 'PUT', admin, {'ids': [1]})
            q('清空TC映射', r, f"/api/permissions/{ids['TC']}/apis", 200, 'PUT', admin, {'ids': []})
            create('映射撤回即时拒绝', 403)
            q('恢复TC映射', r, f"/api/permissions/{ids['TC']}/apis", 200, 'PUT', admin, {'ids': mappings})
            q('停用TC实际API', r, f'/api/api-resources/{mappings[0]}', 200, 'PATCH', admin, {'enabled': False})
            create('API停用即时拒绝', 403)
            q('恢复TC实际API', r, f'/api/api-resources/{mappings[0]}', 200, 'PATCH', admin, {'enabled': True})
            q('授予另一虚构员工RBAC管理角色', r, '/api/users/8/roles', 200, 'PUT', admin, {'ids': [4, 3]})
            manager = q('管理权限生效可登录', r, '/api/auth/login', 200, 'POST', body={
                'account': 'ui-user-08', 'password': self.password})['data']['token']
            q('管理端实际总览放行', r, '/api/overview', 200, token=manager)
            q('撤回管理角色', r, '/api/users/8/roles', 200, 'PUT', admin, {'ids': [4]})
            q('管理端旧令牌下一请求拒绝', r, '/api/overview', 403, token=manager)
            q('课程权限不受另一员工管理撤权影响', c, '/admin/courses', 200, token=course)
            q('停用独立虚构教师账号', r, '/api/users/6/status', 200, 'PATCH', admin, {'enabled': False})
            q('停用账号旧课程令牌失效', c, '/me', 401, token=reader)
        finally:
            # 即使中途失败也恢复夹具，失败本身仍由报告和进程状态保留。
            q('恢复课程角色完整集合', r, '/api/roles/51/permissions', 200, 'PUT', admin, {'ids': all_ids})
            q('恢复TC映射完整集合', r, f"/api/permissions/{ids['TC']}/apis", 200, 'PUT', admin, {'ids': mappings})
            q('恢复TC实际API启用', r, f'/api/api-resources/{mappings[0]}', 200, 'PATCH', admin, {'enabled': True})
            q('恢复虚构员工课程角色', r, '/api/users/5/roles', 200, 'PUT', admin, {'ids': [4, 50, 51]})
            q('恢复另一虚构员工普通角色', r, '/api/users/8/roles', 200, 'PUT', admin, {'ids': [4]})
            q('恢复夹具原始当前学期', c, '/current-term', 204, 'PUT', course, {'termId': 1})
            for ident in term_ids:
                q('清理本轮新建学期', c, f'/terms/{ident}', 204, 'DELETE', course)


def main():
    """必须显式确认虚构夹具，结果只写忽略目录且不包含身份令牌。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture', action='store_true', required=True)
    parser.add_argument('--rbac', default='http://127.0.0.1:8081')
    parser.add_argument('--course', default='http://127.0.0.1:8080')
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    for base in (args.rbac, args.course):
        if urlparse(base).hostname not in {'127.0.0.1', 'localhost'}:
            raise ValueError('只允许本机隔离联调')
    root = Path(__file__).resolve().parents[1]
    if not args.report.resolve().is_relative_to((root / '.local').resolve()):
        raise ValueError('报告只允许.local目录')
    verification = Verification(args.rbac, args.course, os.environ['UI_FIXTURE_PASSWORD'])
    try:
        verification.run()
    finally:
        args.report.write_text(json.dumps(verification.evidence, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'真实HTTP检查通过：{len(verification.evidence)}项；报告没有口令或令牌。')


if __name__ == '__main__':
    main()
