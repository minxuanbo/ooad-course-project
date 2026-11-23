"""独立首次业务目录配置；原表仅表达计划权限，不生成虚构业务API。"""
import argparse
import importlib.util
import hashlib
import json
from pathlib import Path
from initialize_course_integration import Mysql, sql_text

ROOT = Path(__file__).resolve().parents[1]
ROLE_CODES = ('system-admin', 'company-leader', 'department-manager', 'project-manager', 'employee')
SEED_KEY = 'business-catalog-v1'


def load_source(path):
    """复用已核实的原表只读提取器，保留原单元格及规范化记录。"""
    spec = importlib.util.spec_from_file_location('business_source', ROOT / 'scripts/build-prototype-applications.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.extract(path)


def build_plan(source):
    """全部只展开为同行显式动作；空白不授权，菜单仅显式授予给有动作的角色。"""
    if source['roleNames'] != ['系统管理员', '公司领导', '部门经理', '项目经理', '普通员工']:
        raise ValueError('BUSINESS_ROLE_COLUMNS_CHANGED')
    if len(source['systems']) != 18 or len(source['features']) != 359 or source['moduleCount'] != 115:
        raise ValueError('BUSINESS_SOURCE_COUNTS_CHANGED')
    permissions, modules, grants = [], {}, {code: set() for code in ROLE_CODES}
    for feature in source['features']:
        system = next(row for row in source['systems'] if row['id'] == feature['systemId'])
        key = (system['code'], feature['module'])
        if key not in modules:
            module_code = system['code'] + '.m' + str(sum(1 for item in modules if item[0] == key[0]) + 1).zfill(3)
            modules[key] = module_code
            permissions.append({'system': system['code'], 'code': module_code, 'name': feature['module'], 'type': 'MENU', 'parent': None, 'sourceRow': feature['row']})
        feature_code = system['code'] + '.f' + str(feature['row']).zfill(3)
        permissions.append({'system': system['code'], 'code': feature_code, 'name': feature['name'], 'type': 'MENU', 'parent': modules[key], 'sourceRow': feature['row']})
        for index, (action, role_indices) in enumerate(zip(feature['operations'], feature['operationRoles']), 1):
            code = feature_code + '.a' + str(index).zfill(2)
            permissions.append({'system': system['code'], 'code': code, 'name': action, 'type': 'BUTTON', 'parent': feature_code, 'sourceRow': feature['row']})
            for role_index in role_indices:
                grants[ROLE_CODES[role_index]].update((modules[key], feature_code, code))
    payload = {'version': 1, 'source': source, 'roleCodes': ROLE_CODES,
               'systems': [{**row, 'connectionStatus': 'PENDING'} for row in source['systems']],
               'permissions': permissions, 'grants': {code: sorted(ids) for code, ids in grants.items()}}
    actions = {(feature['row'], action): role_indices for feature in source['features']
               for action, role_indices in zip(feature['operations'], feature['operationRoles'])}
    for item in permissions:
        item['systemCode'] = item['system']
        item['row'] = item['sourceRow']
        item['kind'] = 'action' if item['type'] == 'BUTTON' else 'module' if item['parent'] is None else 'feature'
        if item['kind'] == 'action':
            item['roleIndexes'] = actions[item['row'], item['name']]
    payload['manifest'] = hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    return payload


def initialization_sql(plan, root_id, role_ids):
    """根锁内一次追加目录、五角色关系和来源回执，不替换任何已有授权。"""
    statements = ['START TRANSACTION;', f'SELECT id FROM departments WHERE id={int(root_id)} FOR UPDATE;']
    for system in plan['systems']:
        statements += ['INSERT INTO systems(code,name,enabled,connection_status) VALUES('
                       + sql_text(system['code']) + ',' + sql_text(system['name']) + ",1,'PENDING');",
                       'SET @business_system_' + system['code'] + '=LAST_INSERT_ID();']
    variables = {}
    for index, item in enumerate(plan['permissions']):
        variable = '@business_permission_' + str(index)
        parent = variables[item['parent']] if item['parent'] else 'NULL'
        statements += ['INSERT INTO permissions(system_id,parent_id,code,name,type,enabled) VALUES(@business_system_' + item['system'] + ',' + parent + ','
                       + ','.join(sql_text(item[key]) for key in ('code', 'name', 'type')) + ',1);', 'SET ' + variable + '=LAST_INSERT_ID();']
        variables[item['code']] = variable
    for code, ids in plan['grants'].items():
        for permission in ids:
            statements.append(f'INSERT INTO role_permissions(role_id,permission_id) VALUES({int(role_ids[code])},{variables[permission]});')
    statements.append('INSERT INTO bootstrap_receipts(seed_key,manifest_sha256,completed_at,source_json) VALUES('
                      + sql_text(SEED_KEY) + ',' + sql_text(plan['manifest']) + ',UTC_TIMESTAMP(3),'
                      + sql_text(json.dumps(plan, ensure_ascii=False, sort_keys=True)) + ');')
    return '\n'.join(statements + ['COMMIT;'])


def initialize(database, plan, apply=False):
    """相同回执保留后续人工维护；来源变化或目录冲突显式拒绝。"""
    receipt = database.rows("SELECT JSON_OBJECT('hash',manifest_sha256) FROM bootstrap_receipts WHERE seed_key=" + sql_text(SEED_KEY) + ';')
    if receipt:
        if receipt[0]['hash'] != plan['manifest']:
            raise ValueError('INITIALIZATION_MANIFEST_CHANGED')
        return {'skipped': True, 'applied': False, 'reason': 'matching-receipt-preserves-runtime-changes'}
    roots = database.rows("SELECT JSON_OBJECT('id',d.id) FROM import_sources s JOIN departments d ON d.id=s.department_id WHERE s.source_type='ORGANIZATION' AND s.source_key=" + sql_text('总公司') + " AND d.deleted=0 AND d.parent_id IS NULL;")
    role_rows = database.rows("SELECT JSON_OBJECT('id',id,'code',code,'level',management_level,'type',role_type) FROM roles WHERE code IN (" + ','.join(sql_text(code) for code in ROLE_CODES) + ');')
    roles = {row['code']: row for row in role_rows}
    if len(roots) != 1 or set(roles) != set(ROLE_CODES) or any(roles[code]['level'] != 5-index or roles[code]['type'] != 'SYSTEM_BUILTIN' for index, code in enumerate(ROLE_CODES)):
        raise ValueError('BUSINESS_BASE_ROLES_REQUIRED')
    conflicts = database.rows("SELECT JSON_OBJECT('count',COUNT(*)) FROM systems WHERE code IN (" + ','.join(sql_text(row['code']) for row in plan['systems']) + ');')[0]['count']
    if conflicts:
        raise ValueError('BUSINESS_CATALOG_ALREADY_PRESENT')
    if apply:
        database.execute(initialization_sql(plan, roots[0]['id'], {code: row['id'] for code, row in roles.items()}))
    return {'skipped': False, 'applied': apply, 'systems': len(plan['systems']),
            'modules': plan['source']['moduleCount'], 'features': len(plan['source']['features']),
            'permissions': len(plan['permissions']), 'grants': {code: len(ids) for code, ids in plan['grants'].items()}, 'manifest': plan['manifest']}


def main():
    """默认预览，显式apply才向指定本机数据库追加首次目录。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / '市政公司应用软件功能权限清单.xlsx')
    parser.add_argument('--mysql', required=True, type=Path)
    parser.add_argument('--rbac-client', required=True, type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    print(json.dumps(initialize(Mysql(args.mysql, args.rbac_client, 'rbac_core'), build_plan(load_source(args.source)), args.apply), ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        raise SystemExit(str(error) if isinstance(error, ValueError) else type(error).__name__) from None
