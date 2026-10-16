#!/usr/bin/env python3
"""Explicit V1 -> normalized V2 migration. Stop all application writers first.

Default is read-only preflight. --apply creates a private full SQL backup before
DDL. DDL is not transactional; on failure stop and restore the backup into a
separate database for inspection, never automatically delete the source DB.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from datetime import datetime, timezone
import local_mysql as local


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, int):
        return str(value)
    return "CONVERT(X'" + str(value).encode('utf-8').hex() + "' USING utf8mb4)"


def migrate(database, apply=False):
    credentials = json.loads((local.LOCAL / 'credentials.json').read_text())
    def sql(query):
        return local.client(query, database=database, user=database, password=credentials[database])
    def rows(table, columns):
        args=[]
        for column in columns:
            args.extend([literal(column), '`'+column+'`'])
        return [json.loads(line) for line in sql('SELECT JSON_OBJECT('+','.join(args)+') FROM `'+table+'`;').splitlines()]
    def fingerprint(table, columns):
        data=rows(table, columns)
        encoded=sorted(json.dumps(r,sort_keys=True,ensure_ascii=False) for r in data)
        return {'rows':len(data),'sha256':hashlib.sha256('\n'.join(encoded).encode()).hexdigest()}
    old=int(sql("SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='company'"))
    if not old:
        print(database+': already normalized; no changes.');return
    # Detect partial earlier DDL instead of guessing how to resume it.
    if int(sql("SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='departments' AND column_name='company_id') OR (table_name='permissions' AND column_name='feature_id'))")):
        raise RuntimeError('Partial migration detected. Inspect private backup/report; do not rerun blindly.')
    depts=rows('departments',['id','name','manager_name','parent_id','enabled'])
    users=rows('users',['id','company','department_id','name','status'])
    perms=rows('permissions',['id','app_id','module_name','feature_name','action','code','description','enabled'])
    companies=sorted({u['company'] for u in users if u['company']})
    by_dept={d['id']:d for d in depts}; warnings=[]
    for d in depts:
        names={u['company'] for u in users if u['department_id']==d['id'] and u['company']}
        if len(names)>1:
            raise RuntimeError('Department shared by multiple companies, resolve mapping before migration: '+str(d['id']))
        if names:
            d['company']=next(iter(names))
        elif d['name'] in companies:
            d['company']=d['name']
        else:
            raise RuntimeError('Unresolved department company: '+str(d['id']))
        matches=[u['id'] for u in users if u['department_id']==d['id'] and u['name']==d['manager_name'] and u['status']=='ACTIVE']
        if len(matches)>1:
            raise RuntimeError('Ambiguous department manager: '+str(d['id']))
        d['manager_id']=matches[0] if matches else None
        if d['manager_name'] and not matches:
            warnings.append('department '+str(d['id'])+': unresolved manager left NULL (legacy name retained in backup)')
    for d in depts:
        if d['parent_id'] and by_dept[d['parent_id']]['company']!=d['company']:
            warnings.append('department '+str(d['id'])+': cross-company parent removed; independent company tree')
            d['parent_id']=None
        seen={d['id']}; parent=d['parent_id']
        while parent:
            if parent in seen: raise RuntimeError('Department cycle')
            seen.add(parent);parent=by_dept[parent]['parent_id']
    for u in users:
        if u['department_id'] is not None and u['company']!=by_dept[u['department_id']]['company']:
            raise RuntimeError('User company mismatch: '+str(u['id']))
        if u['department_id'] is None and u['id']!=1:
            raise RuntimeError('Unassigned employee requires confirmation: '+str(u['id']))
    descriptions={}
    action_codes={'查看':'read','新增':'create','编辑':'update','删除':'delete','审批':'approve','导出':'export','配置':'configure',
                  '编辑本人':'编辑本人','查看本人':'查看本人','view':'view'}
    for p in perms:
        key=(p['app_id'],p['module_name'],p['feature_name'])
        if key in descriptions and descriptions[key]!=p['description']:
            raise RuntimeError('Conflicting feature descriptions: '+str(p['id']))
        descriptions[key]=p['description']
        if p['action'] not in action_codes: raise RuntimeError('Unmapped action: '+p['action'])
    print(database+': preflight passed; '+str(len(depts))+' departments, '+str(len(perms))+' permissions; warnings='+str(len(warnings)))
    for warning in warnings: print(warning)
    if not apply: return
    if int(sql("SELECT count(*) FROM information_schema.processlist WHERE DB=DATABASE() AND ID<>CONNECTION_ID()")):
        raise RuntimeError('Other database connections exist. Stop backend and other writers before --apply.')
    # Includes hashes, sessions and private audit data; never write this under docs/.
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    folder=local.LOCAL/'backups';folder.mkdir(mode=0o700,parents=True,exist_ok=True)
    backup=folder/(database+'-before-normalize-'+stamp+'.sql')
    env=dict(os.environ,MYSQL_PWD=credentials[database])
    with backup.open('xb') as out:
        backup.chmod(0o600)
        subprocess.run([shutil.which('mysqldump'),'--no-defaults','--protocol=SOCKET','--socket='+str(local.SOCKET),
                        '--user='+database,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF',database],
                       stdout=out,env=env,check=True)
    preserved={
        'users':['id','employee_no','username','name','phone','department_id','position','password_hash','status','created_at'],
        'roles':['id','code','name','description','enabled'],
        'applications':['id','code','name','enabled'],
        'permissions':['id','code','enabled'],
        'user_roles':['user_id','role_id','granted_by','granted_at'],
        'role_permissions':['role_id','permission_id'],
        'sessions':['token_hash','user_id','expires_at','revoked','created_at'],
        'authorization_logs':['id','occurred_at','user_id','username','app_code','permission_code','allowed','request_id','reason'],
        'authorization_changes':['id','occurred_at','operator_id','target_type','target_id','action','detail'],
        'handovers':['user_id','receiver_id','owner_id','expected_date','reason','oa_reference','state','started_by','started_at','completed_by','completed_at','completion_note']}
    before={t:fingerprint(t,cols) for t,cols in preserved.items()}
    grant_prefix="SELECT DISTINCT ur.user_id,p.code,a.code FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id "
    old_grants=sql(grant_prefix+"JOIN applications a ON a.id=p.app_id WHERE u.status='ACTIVE' AND r.enabled=1 AND p.enabled=1 AND a.enabled=1 ORDER BY ur.user_id,p.code,a.code")
    report=folder/(database+'-normalize-'+stamp+'.json')
    report.write_text(json.dumps({'backup':str(backup),'status':'started','before':before,'warnings':warnings},indent=2));report.chmod(0o600)
    # New tables only; CREATE IF NOT EXISTS leaves legacy tables intact until backfill.
    sql((local.ROOT/'src/main/resources/schema.sql').read_text())
    sql('ALTER TABLE departments ADD company_id BIGINT NULL, ADD code VARCHAR(100) NULL, ADD manager_user_id BIGINT NULL; ALTER TABLE permissions ADD feature_id BIGINT NULL, ADD action_code VARCHAR(32) NULL;')
    statements=['START TRANSACTION;']
    for i,name in enumerate(companies,1):
        statements.append('INSERT INTO companies(id,code,name) VALUES('+','.join(map(literal,[i,'company-'+str(i),name]))+');')
    for d in depts:
        statements.append('UPDATE departments SET company_id='+str(companies.index(d['company'])+1)+',code='+literal('dept-'+str(d['id']))+',manager_user_id='+literal(d['manager_id'])+',parent_id='+literal(d['parent_id'])+' WHERE id='+str(d['id'])+';')
    for i,(name,code) in enumerate(action_codes.items()):
        statements.append('INSERT INTO actions(code,name,sort_order) VALUES('+','.join(map(literal,[code,name,i]))+');')
    modules={};features={}
    for p in sorted(perms,key=lambda p:p['id']):
        mk=(p['app_id'],p['module_name']);fk=mk+(p['feature_name'],)
        if mk not in modules:
            mid=len(modules)+1;modules[mk]=mid
            statements.append('INSERT INTO modules(id,app_id,code,name,sort_order) VALUES('+','.join(map(literal,[mid,p['app_id'],'module-'+str(mid),p['module_name'],mid]))+');')
        if fk not in features:
            fid=len(features)+1;features[fk]=fid
            statements.append('INSERT INTO features(id,module_id,code,name,description,sort_order) VALUES('+','.join(map(literal,[fid,modules[mk],'feature-'+str(fid),p['feature_name'],p['description'],fid]))+');')
        statements.append('UPDATE permissions SET feature_id='+str(features[fk])+',action_code='+literal(action_codes[p['action']])+' WHERE id='+str(p['id'])+';')
    statements.append('COMMIT;');sql('\n'.join(statements))
    new_grants=sql(grant_prefix+"JOIN features f ON f.id=p.feature_id JOIN modules m ON m.id=f.module_id JOIN applications a ON a.id=m.app_id JOIN actions ac ON ac.code=p.action_code WHERE u.status='ACTIVE' AND r.enabled=1 AND p.enabled=1 AND a.enabled=1 AND m.enabled=1 AND f.enabled=1 AND ac.enabled=1 ORDER BY ur.user_id,p.code,a.code")
    if new_grants!=old_grants: raise RuntimeError('Effective permissions differ; do not resume service. See backup '+str(backup))
    # Resolve existing FK names instead of assuming MySQL generated names.
    for table,column in [('permissions','app_id'),('departments','parent_id')]:
        names=sql("SELECT constraint_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name="+literal(table)+" AND column_name="+literal(column)+" AND referenced_table_name IS NOT NULL").splitlines()
        for name in names: sql('ALTER TABLE `'+table+'` DROP FOREIGN KEY `'+name+'`;')
    sql("ALTER TABLE permissions DROP INDEX uk_permission_function, DROP COLUMN app_id, DROP COLUMN module_name, DROP COLUMN feature_name, DROP COLUMN action, DROP COLUMN description, MODIFY feature_id BIGINT NOT NULL, MODIFY action_code VARCHAR(32) NOT NULL, ADD UNIQUE KEY uk_permission_function(feature_id,action_code), ADD FOREIGN KEY(feature_id) REFERENCES features(id), ADD FOREIGN KEY(action_code) REFERENCES actions(code);")
    sql("ALTER TABLE departments DROP INDEX name, DROP COLUMN manager_name, MODIFY company_id BIGINT NOT NULL, MODIFY code VARCHAR(100) NOT NULL, ADD UNIQUE KEY uk_department_code(code), ADD UNIQUE KEY uk_department_name(company_id,name), ADD UNIQUE KEY uk_department_company_id(company_id,id), ADD FOREIGN KEY(company_id) REFERENCES companies(id), ADD CONSTRAINT fk_department_manager FOREIGN KEY(manager_user_id) REFERENCES users(id);")
    sql('ALTER TABLE departments ADD FOREIGN KEY(company_id,parent_id) REFERENCES departments(company_id,id);')
    sql('ALTER TABLE users DROP INDEX idx_users_company_dept, DROP COLUMN company;')
    after={t:fingerprint(t,cols) for t,cols in preserved.items()}
    if before!=after: raise RuntimeError('Preserved data differs. Stop and inspect backup/report.')
    report.write_text(json.dumps({'status':'verified','backup':str(backup),'before':before,'after':after,'effectiveGrantsPreserved':True,'warnings':warnings},indent=2))
    print('Verified: all retained columns and effective grant sets unchanged. Private report: '+str(report))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database',choices=['rbac','rbac_test'],required=True)
    parser.add_argument('--apply',action='store_true',help='backup and migrate; requires stopped writers')
    args=parser.parse_args()
    migrate(args.database,args.apply)
