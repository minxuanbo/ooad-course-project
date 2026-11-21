#!/usr/bin/env python3
"""R31真实万人库与双系统探针；在显式本机新库保留可供人工体验的教学场景。"""
import argparse
import json
from pathlib import Path
from urllib.parse import urlparse

from initialize_course_integration import Mysql, initialize, plan_initialization, read_snapshot, sql_text
from verify_course_integration import Verification

ROOT = Path(__file__).resolve().parents[1]


def choose_accounts(snapshot, plan):
    """选择本部领导、普通员工、分公司领导及员工，身份不依据姓名猜测。"""
    users = {user['id']: user for user in snapshot['users']}
    def choose(ids, level):
        """按稳定ID选取指定基础级别的代表，不将员工明细打印到报告。"""
        return next(users[ident] for ident in ids if users[ident]['level'] == level)
    return {'admin': users[plan['administrators'][0]],
            'headquartersLeader': choose(plan['teachers'], 4),
            'headquartersEmployee': choose(plan['teachers'], 1),
            'branchLeader': choose(plan['students'], 4),
            'branchEmployee': choose(plan['students'], 1)}


def verify_database(rbac, course, plan, configuration):
    """逐个核对全部用户的课程角色和本地绑定，不只抽查少数登录账号。"""
    role_codes = plan['role_codes']
    rows = rbac.rows("SELECT JSON_OBJECT('user_id',ur.user_id,'code',r.code) FROM user_roles ur "
                     'JOIN roles r ON r.id=ur.role_id WHERE r.code IN ('
                     + ','.join(sql_text(value) for value in role_codes.values()) + ');')
    actual = {}
    for row in rows:
        actual.setdefault(row['user_id'], set()).add(row['code'])
    for person in plan['people']:
        expected = {role_codes['teacher' if person['teacher'] else 'student']}
        if person['id'] in plan['administrators']:
            expected.add(role_codes['administrator'])
        assert actual.get(person['id']) == expected, 'R31_ROLE_ASSIGNMENT_MISMATCH'
    bound = {}
    for kind, table, number in [('teacher', 'teacher', 'employee_no'), ('student', 'student', 'student_no')]:
        for row in course.rows("SELECT JSON_OBJECT('uid',user_id,'no'," + number + ') FROM ' + table + ';'):
            assert row['uid'] not in bound, 'R31_DUPLICATE_COURSE_IDENTITY'
            bound[row['uid']] = (kind, row['no'])
    assert len(bound) == len(plan['people'])
    for person in plan['people']:
        assert bound[str(person['id'])] == ('teacher' if person['teacher'] else 'student', person['username'])
    salts = rbac.rows("SELECT JSON_OBJECT('accounts',COUNT(*),'distinctHashes',COUNT(DISTINCT password_hash)) FROM users;")[0]
    assert salts['accounts'] == salts['distinctHashes'] == len(plan['people'])
    return {'allUserRoleAssignmentsChecked': len(actual), 'allCourseBindingsChecked': len(bound),
            'teachers': len(plan['teachers']), 'students': len(plan['students']),
            'courseAdministrators': len(plan['administrators']), 'independentPasswordHashes': salts['distinctHashes']}


def teaching_scenario(verification, tokens, accounts, path):
    """全部通过老师现有生产API创建课程、教学班、选课和里程碑，不用SQL伪造教学成功。"""
    q, base = verification.request, verification.course
    state = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
    def save():
        """每次真实创建后保存本次私有ID，失败重试不覆盖其他教学资料。"""
        path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
    if 'term' not in state:
        state['term'] = q('课程管理员新建演示学期', base, '/terms', 201, 'POST', tokens['admin'],
                          {'name': '2026至2027本部教师联调学期', 'startDate': '2026-09-01', 'endDate': '2027-07-31'})['id']
        save()
    q('课程管理员设置当前学期', base, '/current-term', 204, 'PUT', tokens['admin'], {'termId': state['term']})
    if 'course' not in state:
        state['course'] = q('本部普通员工以教师身份申请课程', base, f"/terms/{state['term']}/courses", 201,
                            'POST', tokens['headquartersEmployee'], {'code': 'R31-DEMO-GROUP',
                            'name': '本部教师与分公司学生演示课程', 'description': 'R31真实账号授权与教学场景', 'workMode': 'group'})['id']
        save()
    course_id = state['course']
    if not state.get('submitted'):
        q('教师提交审核', base, f'/courses/{course_id}/submit-review', 200, 'POST', tokens['headquartersEmployee'])
        state['submitted'] = True; save()
    if not state.get('approved'):
        q('同一RBAC管理员审批课程', base, f'/courses/{course_id}/review', 200, 'POST', tokens['admin'],
          {'decision': 'approved', 'comment': 'R31本地联调通过'})
        state['approved'] = True; save()
    teachers = [accounts[key]['teacherId'] for key in ('headquartersEmployee', 'headquartersLeader')]
    q('管理员分配本部领导与普通员工为课程组长', base, f'/courses/{course_id}/leaders', 200,
      'PUT', tokens['admin'], {'teacherIds': teachers})
    q('重复保存组长保持关联', base, f'/courses/{course_id}/leaders', 200,
      'PUT', tokens['admin'], {'teacherIds': teachers})
    if 'class' not in state:
        result = q('教师建立演示教学班', base, f'/courses/{course_id}/classes', 201, 'POST',
                   tokens['headquartersEmployee'], {'classes': [{'name': '分公司学生体验班'}]})
        state['class'] = result['items'][0]['id']; save()
    class_id = state['class']
    q('管理员设置任课教师', base, f'/classes/{class_id}/teachers', 200, 'PUT', tokens['admin'], {'teacherIds': teachers})
    for key in ('branchLeader', 'branchEmployee'):
        if not state.get(key + 'Enrolled'):
            q(key + '以学生身份选入教学班', base, f'/classes/{class_id}/students', 201,
              'POST', tokens['headquartersEmployee'], {'no': accounts[key]['username'], 'name': accounts[key]['name']})
            state[key + 'Enrolled'] = True; save()
    if 'milestone' not in state:
        state['milestone'] = q('教师创建真实作业里程碑', base, f'/courses/{course_id}/milestones', 201,
                               'POST', tokens['headquartersEmployee'], {'name': '本地联调项目作业',
                               'description': '由本部教师布置，分公司学生在所属教学班查看', 'due': '2027-06-30T12:00:00+08:00',
                               'mode': 'grading', 'weight': 100, 'scale': 100, 'groupRatio': 50,
                               'bookLimit': None, 'cancelBefore': None})['id']
        save()
    for key in ('branchLeader', 'branchEmployee'):
        listing = q(key + '读取真实选课', base, '/me/courses', 200, token=tokens[key])
        assert any(row['id'] == course_id for row in listing['items'])
        q(key + '读取开放课程里程碑', base, f'/courses/{course_id}/milestones', 200, token=tokens[key])
        q(key + '不能读取班级教师名册', base, f'/classes/{class_id}/students', 403, token=tokens[key])
    for key in ('headquartersEmployee', 'headquartersLeader'):
        q(key + '读取任课教学班', base, f'/courses/{course_id}/classes', 200, token=tokens[key])
        q(key + '读取实际学生名册', base, f'/classes/{class_id}/students', 200, token=tokens[key])
    q('课程管理员团队工作区读取教学班', base, f'/courses/{course_id}/classes', 200, token=tokens['admin'])
    q('课程管理员读取外部持久业务日志', base, '/audit-logs?page=1&pageSize=20', 200, token=tokens['admin'])
    return state


def run(args):
    """核全量分配并验证真实登录、受众隔离、即时撤权和幂等不重授。"""
    configuration = json.loads((ROOT / 'integrations/course/initialization.json').read_text(encoding='utf-8'))
    catalog = json.loads((ROOT / 'integrations/course/catalog.json').read_text(encoding='utf-8'))
    rbac = Mysql(args.mysql, args.client, 'rbac_core')
    course = Mysql(args.mysql, args.client, 'course_project')
    snapshot = read_snapshot(rbac)
    plan = plan_initialization(snapshot, configuration, catalog)
    accounts = choose_accounts(snapshot, plan)
    passwords = json.loads(args.credentials.read_text(encoding='utf-8-sig'))
    verification = Verification(args.rbac, args.course, passwords['employeePassword'])
    q = verification.request
    result = {'database': verify_database(rbac, course, plan, configuration), 'http': verification.evidence}
    tokens = {}
    try:
        admin = q('同一管理员登录RBAC', args.rbac, '/api/auth/login', 200, 'POST',
                  body={'account': accounts['admin']['username'], 'password': passwords['adminPassword']})['data']['token']
        for key, person in accounts.items():
            role = 'teacher' if key in {'admin', 'headquartersEmployee', 'headquartersLeader'} else 'student'
            password = passwords['adminPassword'] if key == 'admin' else passwords['employeePassword']
            login = q(key + '按初始化身份登录课程', args.course, '/login', 200, 'POST',
                      body={'role': role, 'account': person['username'], 'password': password})
            tokens[key] = login['accessToken']
            current = q(key + '查询课程当前身份', args.course, '/me', 200, token=tokens[key])
            assert role in current['roles']
            assert str(current['userId']) == str(person['id'])
            person['teacherId'] = current.get('teacherId')
            person['studentId'] = current.get('studentId')
            if key == 'admin':
                assert configuration['roles']['administrator'] in current['roles']
        q('本部普通教师仍不能进入RBAC管理端', args.rbac, '/api/auth/login', 403, 'POST',
          body={'account': accounts['headquartersEmployee']['username'], 'password': passwords['employeePassword']})
        q('分公司领导仍可按RBAC管理权进入本系统', args.rbac, '/api/auth/login', 200, 'POST',
          body={'account': accounts['branchLeader']['username'], 'password': passwords['employeePassword']})
        q('分公司学生不能用教师入口', args.course, '/login', 404, 'POST',
          body={'role': 'teacher', 'account': accounts['branchEmployee']['username'], 'password': passwords['employeePassword']})
        q('本部教师不能用学生入口', args.course, '/login', 404, 'POST',
          body={'role': 'student', 'account': accounts['headquartersEmployee']['username'], 'password': passwords['employeePassword']})
        q('应用受众令牌不能进入RBAC', args.rbac, '/api/me', 401, token=tokens['admin'])
        q('RBAC受众令牌不能冒充课程身份', args.course, '/me', 401, token=admin)
        q('普通教师不能管理全部课程', args.course, '/admin/courses', 403, token=tokens['headquartersEmployee'])
        q('同一管理员可以查看全部课程', args.course, '/admin/courses', 200, token=tokens['admin'])
        q('普通教师不能读取全局教师目录', args.course, '/teachers', 403, token=tokens['headquartersEmployee'])
        q('同一管理员可以读取191名教师目录', args.course, '/teachers?page=1&pageSize=20', 200, token=tokens['admin'])
        result['scenario'] = teaching_scenario(verification, tokens, accounts, args.scenario)
        role = rbac.rows('SELECT JSON_OBJECT(\'id\',id) FROM roles WHERE code=' + sql_text(configuration['roles']['student']) + ';')[0]['id']
        original = q('读取学生角色完整权限', args.rbac, f'/api/roles/{role}/permissions', 200, token=admin)['data']['ids']
        try:
            q('真实RBAC撤回学生应用资格', args.rbac, f'/api/roles/{role}/permissions', 200, 'PUT', admin, {'ids': []})
            q('学生原令牌下一请求立即拒绝', args.course, '/me', 403, token=tokens['branchEmployee'])
            repeated = initialize(rbac, course, configuration, catalog, apply=True)
            assert repeated['skipped'] is True
            q('重复初始化不能偷偷恢复已撤资格', args.course, '/me', 403, token=tokens['branchEmployee'])
        finally:
            q('恢复学生原有完整权限集合', args.rbac, f'/api/roles/{role}/permissions', 200, 'PUT', admin, {'ids': original})
        q('恢复授权后原学生令牌可继续使用', args.course, '/me', 200, token=tokens['branchEmployee'])
        result['repeatedInitializationPreservesRevocation'] = True
        args.accounts.write_text(json.dumps(accounts, ensure_ascii=False, indent=2), encoding='utf-8')
        result['passed'] = True
    finally:
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'database': result['database'], 'httpChecks': len(verification.evidence), 'passed': True}, ensure_ascii=False))


def main():
    """仅接受loopback和.local新报告，凭据与选定员工名单留本地。"""
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('mysql', 'client', 'credentials', 'accounts', 'scenario', 'report'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--rbac', default='http://127.0.0.1:8081')
    parser.add_argument('--course', default='http://127.0.0.1:8080')
    args = parser.parse_args()
    if any(urlparse(value).hostname not in {'127.0.0.1', 'localhost'} for value in (args.rbac, args.course)):
        raise ValueError('LOCAL_HTTP_REQUIRED')
    for target in (args.accounts, args.scenario, args.report):
        if not target.resolve().is_relative_to((ROOT / '.local').resolve()):
            raise ValueError('PRIVATE_OUTPUT_REQUIRED')
        target.parent.mkdir(parents=True, exist_ok=True)
    if args.report.exists():
        raise ValueError('PRESERVE_PREVIOUS_REPORT')
    run(args)


if __name__ == '__main__':
    main()
