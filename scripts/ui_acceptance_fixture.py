#!/usr/bin/env python3
"""仅生成浏览器验收SQL；不连接数据库，不创建、删除或重置现有库。"""
from pathlib import Path
import argparse
import os
import re
import bcrypt

ROOT = Path(__file__).resolve().parents[1]


def fixture_sql():
    """生成固定ID虚构夹具，主键冲突使误用非空库失败，不清理已有数据。"""
    source = (ROOT/'backend/src/main/java/com/municipal/rbaccore/service/port/security/RbacCatalog.java').read_text(encoding='utf-8')
    entries = re.findall(r'definitions\.put\(\s*"([^"]+)",(.*?)\);', source, re.S)
    statements = ['START TRANSACTION;']
    statements += ["INSERT INTO departments(id,code,name,parent_id,type) VALUES (1,'ui-root','验收总公司',NULL,'HEADQUARTERS'),(2,'ui-engineering','工程部',1,'DEPARTMENT'),(3,'ui-project','道路项目',2,'PROJECT'),(4,'ui-finance','财务部',1,'DEPARTMENT');", "INSERT INTO import_sources(source_type,source_key,source_json,department_id) VALUES('ORGANIZATION','总公司','[]',1);", "INSERT INTO systems VALUES(1,'rbac','权限管理系统',1);", "INSERT INTO roles VALUES(1,'system-admin','系统管理员',1,5),(2,'department-manager','部门经理',1,3),(3,'project-manager','项目经理',1,2),(4,'employee','普通员工',1,1);"]
    api_id = 0
    for permission_id,(code,definition) in enumerate(entries,1):
        statements += [f"INSERT INTO permissions VALUES({permission_id},1,NULL,'{code}','{code}','BUTTON',1);", f"INSERT INTO role_permissions VALUES(1,{permission_id});"]
        if code.startswith(('rbac.users.','rbac.departments.','rbac.overview.')):
            statements += [f"INSERT INTO role_permissions VALUES(2,{permission_id}),(3,{permission_id});"]
        for method,path in re.findall(r'route\("([^"]+)", "([^"]+)"\)',definition):
            api_id += 1
            statements += [f"INSERT INTO api_resources VALUES({api_id},1,'{method}','{path}',1);",f"INSERT INTO permission_apis VALUES({permission_id},{api_id});"]
    assert len(entries)==13 and api_id==25
    password = os.environ['UI_FIXTURE_PASSWORD'].encode('utf-8')
    if not 1 <= len(password) <= 72:
        raise ValueError('验收口令须为1至72字节')
    for ident in range(1,33):
        password_hash = bcrypt.hashpw(password, bcrypt.gensalt(rounds=10)).decode()
        username = {1:'ui-admin',2:'ui-manager',3:'ui-peer',4:'ui-project'}.get(ident,f'ui-user-{ident:02}')
        name = {1:'验收管理员',2:'验收部门经理',3:'验收同级经理',4:'验收项目经理'}.get(ident,f'验收员工{ident:02}')
        department = 1 if ident==1 else 3 if ident==4 else 2
        role = 1 if ident==1 else 2 if ident in (2,3) else 3 if ident==4 else 4
        statements += [f"INSERT INTO users VALUES({ident},'{username}','{password_hash}','{name}',NULL,'虚构验收岗位',{department},1);",f"INSERT INTO user_roles VALUES({ident},{role});"]
    for ident in range(5,36):
        statements += [f"INSERT INTO roles VALUES({ident},'ui-extra-{ident:02}','验收空角色{ident:02}',1,NULL);"]
    statements += ["INSERT INTO permissions VALUES(100,1,NULL,'ui.fixture.permission','验收独立权限','BUTTON',1);", "INSERT INTO api_resources VALUES(100,1,'GET','/api/ui-fixture',1);", "INSERT INTO permissions VALUES(101,1,NULL,'ui.fixture.parent','验收父菜单','MENU',1);", 'COMMIT;']
    return '\n'.join(statements) + '\n'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    # 固定在忽略目录，生成的口令哈希与原始夹具不进入版本库。
    output = args.output.resolve()
    if not output.is_relative_to((ROOT / '.local').resolve()):
        raise ValueError('输出必须位于项目.local目录')
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8') as target:
        target.write(fixture_sql())
    print('已生成独立验收夹具SQL；未连接或修改数据库。')
