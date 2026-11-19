#!/usr/bin/env python3
"""真实双系统联调探针；仅使用显式虚构夹具，口令由环境传入，报告不含令牌。"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import time
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen
import uuid


class Verification:
    """通过真实HTTP验证授权和老师既有教学功能，生成可核查的状态证据。"""
    def __init__(self, rbac, course, password):
        self.rbac, self.course, self.password = rbac, course, password
        self.evidence = []

    def request(self, label, base, path, expected, method='GET', token=None, body=None, files=None):
        """支持JSON、原始下载和真实multipart上传，不打印口令与令牌。"""
        headers = {'Content-Type': 'application/json'}
        if token: headers['Authorization'] = 'Bearer '+token
        data = json.dumps(body).encode('utf-8') if body is not None else None
        if files:
            boundary = 'local-'+uuid.uuid4().hex
            headers['Content-Type'] = 'multipart/form-data; boundary='+boundary
            chunks = []
            for key,name,content,kind in files:
                chunks.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"; filename="{name}"\r\nContent-Type: {kind}\r\n\r\n'.encode(),content,b'\r\n'])
            chunks.append(f'--{boundary}--\r\n'.encode());data=b''.join(chunks)
        request = Request(base+path, headers=headers, method=method, data=data)
        try: response=urlopen(request,timeout=30)
        except HTTPError as error: response=error
        with response:
            status,raw,kind=response.status,response.read(),response.headers.get('Content-Type','')
        passed=status in expected if isinstance(expected,(tuple,list)) else status==expected
        self.evidence.append({'step':label,'method':method,'path':path,'status':status,'expected':expected,'pass':passed})
        if not passed:
            code=''
            try: code=json.loads(raw).get('code','')
            except (ValueError,AttributeError): pass
            raise AssertionError(f'{label}: HTTP {status}, expected {expected}, code={code}')
        return json.loads(raw) if raw and 'json' in kind else raw if raw else None

    @contextmanager
    def temporary_permissions(self, admin):
        """临时角色变更无论中途成功或失败均恢复完整原集合。"""
        q=self.request;r=self.rbac
        original=q('保存角色初值',r,'/api/roles/51/permissions',200,token=admin)['data']['ids']
        try: yield original
        finally: q('恢复角色完整集合',r,'/api/roles/51/permissions',200,'PUT',admin,{'ids':original})

    def run(self):
        """覆盖三类身份、角色授撤权、目录与映射隔离及主要教学完整链。"""
        q,r,c=self.request,self.rbac,self.course
        admin=q('RBAC管理员登录',r,'/api/auth/login',200,'POST',body={'account':'ui-admin','password':self.password})['data']['token']
        def login(account,role):
            return q(role+'真实登录 '+account,c,'/login',200,'POST',body={'role':role,'account':account,'password':self.password})['accessToken']
        teacher=login('ui-user-05','teacher');manager=login('ui-user-08','teacher')
        student=login('ui-user-09','student');second=login('ui-user-10','student')
        q('普通员工不能登录RBAC',r,'/api/auth/login',403,'POST',body={'account':'ui-user-09','password':self.password})
        q('课程本地教师无RBAC资格拒绝',c,'/login',403,'POST',body={'role':'teacher','account':'ui-user-07','password':self.password})
        q('课程本地学生无RBAC资格拒绝',c,'/login',403,'POST',body={'role':'student','account':'ui-user-11','password':self.password})
        q('学生不能使用教师入口',c,'/login',404,'POST',body={'role':'teacher','account':'ui-user-09','password':self.password})
        q('匿名请求拒绝',c,'/me',401)
        q('课程令牌不能访问管理身份',r,'/api/me',401,token=student)
        q('管理令牌不能访问课程身份',c,'/me',401,token=admin)
        for token,role in ((teacher,'teacher'),(manager,'teacher'),(student,'student')):
            me=q('真实'+role+'当前身份',c,'/me',200,token=token)
            assert role in me['roles']
        courses=q('学生读取实际课程',c,'/me/courses',200,token=student)
        assert len(courses.get('items',[]))>=2
        q('教师读取任课课程',c,'/teacher/courses',200,token=teacher)
        q('教师没有全局课程权限',c,'/admin/courses',403,token=teacher)
        q('课程管理员查看全部课程',c,'/admin/courses',200,token=manager)
        q('课程管理员团队页面读取教学班',c,'/courses/2/classes',200,token=manager)
        q('学生不能访问教师名册',c,'/classes/1/students',403,token=student)
        for path in ('/terms','/teachers','/courses/1','/courses/2','/courses/1/classes','/courses/2/classes'):
            q('管理目录 '+path,c,path,200,token=manager if path in ('/teachers',) else teacher)
        q('课程管理员日志可用',c,'/audit-logs?page=1&pageSize=20',200,token=manager)
        q('普通教师不能直读全局外部日志','http://127.0.0.1:8085','/auth/audit-logs',403,token=teacher)
        tree=q('RBAC读取课程登记权限',r,'/api/permissions/tree?systemId=2',200,token=admin)['data']
        ids={}
        def collect(items):
            for item in items:ids[item['code']]=item['id'];collect(item.get('children',[]))
        collect(tree)
        assert q('学生资格来自角色',r,'/auth/me',200,token=student)['roles']==['student']
        with self.temporary_permissions(admin) as original:
            q('撤回课程管理员菜单',r,'/api/roles/51/permissions',200,'PUT',admin,{'ids':[x for x in original if x!=ids['MTR']]})
            assert not q('原令牌菜单即刻失效',r,'/auth/permissions/MTR',200,token=manager)['allowed']
            q('恢复菜单',r,'/api/roles/51/permissions',200,'PUT',admin,{'ids':original})
            q('撤回创建学期API能力',r,'/api/roles/51/permissions',200,'PUT',admin,{'ids':[x for x in original if x!=ids['TC']]})
            q('原令牌后端立刻拒绝',c,'/terms',403,'POST',manager,{'name':'拒绝测试','startDate':'2027-02-01','endDate':'2027-07-01'})
        saved=q('保存学生当前角色',r,'/api/users/9/roles',200,token=admin)['data']['user']['roleIds']
        try:
            q('撤回学生角色',r,'/api/users/9/roles',200,'PUT',admin,{'ids':[4]})
            q('原学生令牌立即失去准入',c,'/me/courses',403,token=student)
        finally:q('恢复学生角色',r,'/api/users/9/roles',200,'PUT',admin,{'ids':saved})
        q('原学生令牌恢复准入',c,'/me/courses',200,token=student)
        try:
            q('为未选课虚构学生授登录资格',r,'/api/users/11/roles',200,'PUT',admin,{'ids':[4,52]})
            unenrolled=login('ui-user-11','student')
            q('有学生角色但未选课不能读里程碑',c,'/courses/1/milestones',403,token=unenrolled)
        finally:q('恢复未选课虚构学生无资格状态',r,'/api/users/11/roles',200,'PUT',admin,{'ids':[4]})

        mapping=q('读取TC映射',r,f"/api/permissions/{ids['TC']}/apis",200,token=admin)['data']['ids']
        try:
            q('跨系统API映射拒绝',r,f"/api/permissions/{ids['TC']}/apis",400,'PUT',admin,{'ids':[1]})
            q('清空真实API映射',r,f"/api/permissions/{ids['TC']}/apis",200,'PUT',admin,{'ids':[]})
            q('API类型缺映射失败关闭',c,'/terms',403,'POST',manager,{'name':'拒绝测试','startDate':'2027-02-01','endDate':'2027-07-01'})
        finally:q('恢复TC映射',r,f"/api/permissions/{ids['TC']}/apis",200,'PUT',admin,{'ids':mapping})
        dept=q('部门经理登录',r,'/api/auth/login',200,'POST',body={'account':'ui-manager','password':self.password})['data']['token']
        locked=q('局部管理员读取锁定角色',r,'/api/users/9/roles',200,token=dept)['data']['lockedRoleIds']
        assert 52 in locked
        try:
            q('保留自定义角色时调整普通角色',r,'/api/users/9/roles',200,'PUT',dept,{'ids':[52]})
            q('局部管理员不能删除自定义关联',r,'/api/users/9/roles',403,'PUT',dept,{'ids':[]})
            q('业务角色不冻结范围内调动',r,'/api/users/9/department',200,'PUT',dept,{'departmentId':3})
        finally:
            q('恢复虚构学生原部门',r,'/api/users/9/department',200,'PUT',dept,{'departmentId':2})
            q('恢复普通角色并保留锁定项',r,'/api/users/9/roles',200,'PUT',dept,{'ids':[4,52]})
        tcm=q('读取多路由权限映射',r,f"/api/permissions/{ids['TCM']}/apis",200,token=admin)['data']
        try:
            keep=[item['id'] for item in tcm['items'] if item['method']=='PUT']
            q('删除DELETE映射但保留PUT',r,f"/api/permissions/{ids['TCM']}/apis",200,'PUT',admin,{'ids':keep})
            q('部分映射不能放行已删除路由',c,'/current-term',403,'DELETE',manager)
            q('老师粗粒度码按必要路由整组关闭',c,'/current-term',403,'PUT',manager,{'termId':1})
        finally:
            q('恢复多路由映射全集',r,f"/api/permissions/{ids['TCM']}/apis",200,'PUT',admin,{'ids':tcm['ids']})
        q('恢复后原令牌继续使用',c,'/current-term',204,'PUT',manager,{'termId':1})
        # 学期维护与当前学期切换使用真实库，结束后恢复基准。
        suffix=str(time.time_ns())
        term=q('新建学期',c,'/terms',201,'POST',manager,{'name':'联调临时-'+suffix,'startDate':'2027-02-01','endDate':'2027-07-01'})['id']
        q('修改学期',c,f'/terms/{term}',200,'PATCH',manager,{'name':'联调修改-'+suffix,'startDate':'2027-02-02','endDate':'2027-07-02'})
        q('切换当前学期',c,'/current-term',204,'PUT',manager,{'termId':term})
        q('取消当前学期',c,'/current-term',204,'DELETE',manager)
        q('恢复基准当前学期',c,'/current-term',204,'PUT',manager,{'termId':1})
        q('删除空学期',c,f'/terms/{term}',204,'DELETE',manager)
        lookup=q('教师前置身份查询',c,'/auth/users/12',200,token=manager);assert lookup['no']=='ui-user-12'
        newteacher=None
        for identifier in range(12,33):
            created=q('新增未使用的虚构教师',c,'/teachers',(201,409),'POST',manager,{'userId':str(identifier),'title':'讲师','dept':'联调测试'})
            if created.get('code')=='TEACHER_ALREADY_EXISTS': continue
            newteacher=created['id'];break
        assert newteacher is not None, '虚构教师候选已用完，请使用新的空库夹具'

        q('修改教师资料',c,f'/teachers/{newteacher}',200,'PATCH',manager,{'title':'副教授','dept':'联调修改'})
        q('删除未任课教师',c,f'/teachers/{newteacher}',204,'DELETE',manager)
        # 教师申请、管理员审核、课程和教学班维护。
        course=q('教师申请课程',c,'/terms/1/courses',201,'POST',teacher,{'code':'VERIFY-'+suffix,'name':'联调申请课程','description':'真实HTTP','workMode':'individual'})['id']
        q('课程提交审核',c,f'/courses/{course}/submit-review',200,'POST',teacher)
        q('管理员审核通过',c,f'/courses/{course}/review',200,'POST',manager,{'decision':'approved','comment':'联调通过'})
        q('编辑课程',c,f'/courses/{course}',200,'PATCH',manager,{'name':'联调编辑课程','code':'VERIFY-'+suffix,'description':'验证编辑'})
        q('配置课程组长并保留原成员',c,f'/courses/{course}/leaders',200,'PUT',manager,{'teacherIds':[5,6]})
        q('移除另一组长',c,f'/courses/{course}/leaders',200,'PUT',manager,{'teacherIds':[5]})
        q('重复保存同一组长名单',c,f'/courses/{course}/leaders',200,'PUT',manager,{'teacherIds':[5]})
        classes=q('批量新建教学班',c,f'/courses/{course}/classes',201,'POST',teacher,{'classes':[{'name':'联调临时班'}]})
        class_id=classes['items'][0]['id']
        q('修改教学班',c,f'/classes/{class_id}',200,'PATCH',teacher,{'name':'联调修改班'})
        q('指派任课教师',c,f'/classes/{class_id}/teachers',200,'PUT',manager,{'teacherIds':[5]})
        newstudent=q('手工添加名册与真实审计写入',c,f'/classes/{class_id}/students',201,'POST',teacher,{'no':'LOCAL-'+suffix,'name':'联调临时学生'})['id']
        q('搜索学生候选',c,f'/classes/{class_id}/student-candidates?keyword=LOCAL',200,token=teacher)
        q('读取班级名册',c,f'/classes/{class_id}/students',200,token=teacher)
        q('移出学生并写审计',c,f'/classes/{class_id}/students/{newstudent}',204,'DELETE',teacher)
        q('删除空教学班',c,f'/classes/{class_id}',204,'DELETE',teacher)
        q('开放课程按老师规则不可删除',c,f'/courses/{course}',409,'DELETE',manager)
        draft=q('新建可删除申请课程',c,'/terms/1/courses',201,'POST',teacher,{'code':'DELETE-'+suffix,'name':'待删除课程','description':'临时','workMode':'individual'})['id']
        q('删除申请课程',c,f'/courses/{draft}',204,'DELETE',manager)
        # 保留虚构课程的学生小组、提交和成绩，供人工体验，不清除真实历史日志。
        q('课程分组规则',c,'/courses/2/group-rule',200,token=teacher)
        q('班级有效分组规则',c,'/classes/2/group-rule',200,token=teacher)
        groups=q('学生读取课程小组',c,'/courses/2/groups',200,token=student)
        items=groups.get('items',[]) if isinstance(groups,dict) else groups
        group=items[0]['id'] if items else q('学生创建小组及审计',c,'/courses/2/groups',201,'POST',student,{'name':'联调学生项目组'})['id']
        detail=next(item for item in q('学生读取带成员的小组列表',c,'/courses/2/groups',200,token=student)['items'] if item['id']==group)
        if not any(item.get('id')==10 or item.get('studentId')==10 for item in detail.get('members',[])):
            join=q('另一学生申请入组',c,f'/groups/{group}/join-requests',202,'POST',second,{'note':'加入联调小组'})['id']
            q('组长读取申请',c,f'/groups/{group}/join-requests',200,token=student)
            q('组长批准入组',c,f'/join-requests/{join}',200,'PATCH',student,{'decision':'approved'})
        q('教师读取班级小组',c,'/classes/2/groups',200,token=teacher)
        q('教师编辑小组',c,f'/groups/{group}',200,'PATCH',teacher,{'name':'联调学生项目组','memberIds':[9,10]})
        q('教师解散虚构测试组',c,f'/groups/{group}',204,'DELETE',teacher)
        group=q('教师按负责教学班建组',c,'/classes/2/groups',201,'POST',teacher,{'name':'联调学生项目组','memberIds':[9,10]})['id']
        for course_id in (1,2):q('学生查看课程里程碑',c,f'/courses/{course_id}/milestones',200,token=student)
        individual=q('学生上传个人作业文件',c,'/me/courses/1/milestones/1/submissions',201,'POST',student,files=[('files','individual.txt',b'local individual assignment','text/plain')])
        q('学生读取本人提交',c,'/me/courses/1/milestones/1/submission',200,token=student)
        q('老师原实现尚不支持个人制批改列表',c,'/classes/1/milestones/1/submissions',409,token=teacher)
        assert q('学生下载本人真实文件',c,individual['files'][0]['url'],200,token=student)==b'local individual assignment'
        grouped=q('学生上传小组作业',c,f'/groups/{group}/milestones/3/submissions',201,'POST',student,files=[('files','group.txt',b'local group assignment','text/plain')])
        q('小组读取当前提交',c,f'/groups/{group}/milestones/3/submission',200,token=student)
        q('教师小组作业批改',c,f"/submissions/{grouped['id']}/review",200,'POST',teacher,{'verdict':'passed','score':90,'comment':'联调小组作业通过'})
        # 重复联调保留预约历史；使用老师现有编辑接口显式设置虚构场景上限，不清空历史。
        for course_id in (1,2):
            milestones=q('读取待编辑里程碑',c,f'/courses/{course_id}/milestones',200,token=teacher)['items']
            oral=next(item for item in milestones if item['mode']=='oral')
            change={key:oral[key] for key in ('name','description','due','mode','weight','scale','groupRatio','bookLimit','cancelBefore')}
            change['bookLimit']=100
            q('编辑虚构口试预约上限以供重复体验',c,f"/milestones/{oral['id']}",200,'PATCH',teacher,change)
        for class_id,milestone in ((1,2),(2,4)):

            q('查看口试时段',c,f'/classes/{class_id}/milestones/{milestone}/oral-slots',200,token=student)
            slot=q('教师发布口试时段',c,f'/classes/{class_id}/milestones/{milestone}/oral-slots',201,'POST',teacher,{'startAt':(datetime.now(timezone.utc)+timedelta(days=2)).isoformat(),'durationMinutes':30,'place':'联调教室','capacity':2})['id']
            booking=q('学生预约口试',c,f'/oral-slots/{slot}/bookings',201,'POST',student,{'confirmReschedule':True})['id']
            if class_id==1:
                q('学生取消预约',c,f'/bookings/{booking}',204,'DELETE',student)
                q('教师删除空口试时段',c,f'/oral-slots/{slot}',200,'DELETE',teacher)
            q('教师查看口试状态',c,f'/classes/{class_id}/milestones/{milestone}/oral-status',200,token=teacher)
        q('教师登记小组口试成绩',c,f'/milestones/4/groups/{group}/oral-records',201,'POST',teacher,{'verdict':'passed','memo':'联调口试完成','groupScore':92,'personalScores':[{'studentId':9,'score':93},{'studentId':10,'score':91}]})
        for course_id in (1,2):q('学生读取实际成绩',c,f'/me/courses/{course_id}/grades',200,token=student)
        for class_id in (1,2):
            q('教师成绩汇总',c,f'/classes/{class_id}/grades',200,token=teacher)
            export=q('教师导出真实Excel',c,f'/classes/{class_id}/grades/export',200,'POST',teacher)
            assert export[:2]==b'PK'
        logs=q('课程管理员查询真实业务日志',c,'/audit-logs?page=1&pageSize=100',200,token=manager)
        assert logs['total']>0
        Path(__file__).resolve().parents[1].joinpath('.local/course-integration/full/scenario-ids.json').write_text(json.dumps({'group':group,'individualSubmission':individual['id'],'groupSubmission':grouped['id']})+'\n',encoding='utf-8')


def main():
    """限loopback与忽略目录，不自动触及真实万人数据库。"""
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture',action='store_true',required=True)
    parser.add_argument('--rbac',default='http://127.0.0.1:8081');parser.add_argument('--course',default='http://127.0.0.1:8080')
    parser.add_argument('--report',required=True,type=Path);args=parser.parse_args()
    if any(urlparse(value).hostname not in {'127.0.0.1','localhost'} for value in (args.rbac,args.course)):raise ValueError('只允许本机隔离联调')
    if not args.report.resolve().is_relative_to((Path(__file__).resolve().parents[1]/'.local').resolve()):raise ValueError('报告只允许.local目录')
    verification=Verification(args.rbac,args.course,os.environ['UI_FIXTURE_PASSWORD'])
    try:verification.run()
    finally:args.report.write_text(json.dumps(verification.evidence,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('真实HTTP检查通过：',len(verification.evidence),'项；报告不含口令和令牌。')

if __name__=='__main__':main()
