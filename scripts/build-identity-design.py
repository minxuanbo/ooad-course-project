#!/usr/bin/env python3
"""从已核对的身份用例生成两套类图；差异顺序图统一由主注册表生成。"""
import argparse
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/design'
HEADER = '''@startuml
!pragma layout smetana
skinparam defaultFontName Microsoft YaHei
skinparam defaultFontSize 16
skinparam classAttributeFontSize 15
skinparam classAttributeIconSize 0
skinparam shadowing false
skinparam backgroundColor white
skinparam linetype polyline
skinparam nodesep 65
skinparam ranksep 55
hide circle
'''


def source(name):
    """每次生成先确认真实类型存在，不为文档虚构实现。"""
    matches = list((ROOT / 'backend/src/main/java').rglob(name + '.java'))
    if len(matches) != 1:
        raise ValueError('图件类型定位不唯一：' + name)
    return matches[0].read_text(encoding='utf-8')


def class_diagram(ddd):
    """Service居中展示业务差异及新增读模型/元数据端口。"""
    service = 'DddApplicationIdentityService' if ddd else 'StructuredApplicationIdentityService'
    implementation = source(service)
    for method in ['login', 'current', 'allowed', 'user', 'logout', 'departments', 'departmentUsers', 'apiAllowed']:
        if method + '(' not in implementation:
            raise ValueError(service + '缺少方法' + method)
    left = 'AuthorizationRepository' if ddd else 'PermissionJpaRepository'
    scope = 'OrganizationRepository' if ddd else 'StructuredDepartmentScopeService'
    for name in [left, scope, 'IdentityDirectoryGateway', 'ApiOperationCatalog']:
        source(name)
    left_method = '+ facts(key: User.Key): Optional<AuthorizationFacts>' if ddd else '+ directPermissions(userId, systems): List<PermissionEntity>'
    scope_method = '+ load(): OrganizationFacts' if ddd else '+ allowedIds(actor): List<Long>\n+ isGlobal(actor): boolean'
    text = HEADER + '''class "SERVICE" as S {
- authentication: AuthenticationUseCase
- tokens: IdentityTokenStore
- directory: IdentityDirectoryGateway
- operations: ApiOperationCatalog
--
+ login(command, system): ApplicationLoginResult
+ current(header): ApplicationIdentityResult
+ allowed(header, code): boolean
+ user(header, id): ApplicationUserResult
+ logout(header): void
+ departments(header, parentId, keyword, page, pageSize): ApplicationDirectoryPage
+ departmentUsers(header, departmentId, keyword, page, pageSize): ApplicationDirectoryPage
+ apiAllowed(header, operationId): boolean
}
interface "LEFT" as A <<interface>> {
LEFT_METHOD
}
interface "IdentityDirectoryGateway" as D <<interface>> {
+ department(id): Optional<ApplicationDepartmentResult>
+ departments(rootId, parentId, scope, keyword, page, pageSize): ApplicationDirectoryPage
+ users(departmentId, keyword, page, pageSize): ApplicationDirectoryPage
}
interface "ApiOperationCatalog" as O <<interface>> {
+ find(system, operationId): Optional<Operation>
}
S "1" -left-> "1" A : LEFT_ROLE
S "1" -down-> "directory\\n1" D
S "1" -up-> "operations\\n1" O
'''.replace('SERVICE', service).replace('LEFT_METHOD', left_method).replace('LEFT_ROLE', 'authorization' if ddd else 'permissions').replace('LEFT', left)
    if not ddd:
        text += 'A -[hidden]down-> D\n'
    text += ('interface' if ddd else 'class') + ' "' + scope + '" as R' + (' <<interface>>' if ddd else '') + ' {\n' + scope_method + '\n}\n'
    text += 'S "1" -right-> "1" R : ' + ('organizations' if ddd else 'scope') + '\n'
    if ddd:
        source('ApplicationAuthorizationPolicy')
        text += '''class "ApplicationAuthorizationPolicy" as P {
{static} + exported(facts, system): Set<Permission>
}
S .down.> P
'''
    return text + '@enduml\n'


def main():
    """工具路径由调用者显式提供，不把绘图运行时加入产品依赖。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java')
    parser.add_argument('--plantuml', type=Path, required=True)
    args = parser.parse_args()
    diagrams = {
        'class-ddd-application-identity': class_diagram(True),
        'class-structured-application-identity': class_diagram(False),
    }
    for name, content in diagrams.items():
        path = OUTPUT / (name + '.puml')
        path.write_text(content, encoding='utf-8')
        for kind in ['-tsvg', '-tpng']:
            subprocess.run([args.java, '-jar', str(args.plantuml.resolve()), '-charset', 'UTF-8', kind,
                            str(path)], check=True, capture_output=True)
    print('2组身份类图及同源SVG/PNG已生成；需打开PNG视觉复核。')


if __name__ == '__main__':
    main()
