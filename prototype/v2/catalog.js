/** 原型目录数据；全部为演示条目，正式API及新增提案另在评审文档核对。 */
(function () {
'use strict';
function createCatalog() {
  const definitions = [
    ['tree', '查看部门树', 'GET', '/api/departments/tree', 1000],
    ['users', '查询人员', 'GET', '/api/users', 1000],
    ['currentRoles', '查看人员角色', 'GET', '/api/users/{id}/roles', 1000],
    ['candidates', '查询可授予角色', 'GET', '/api/users/{id}/assignable-roles', 1000],
    ['assign', '分配人员角色', 'PUT', '/api/users/{id}/roles', 1000],
    ['roles.list', '查询角色', 'GET', '/api/roles', 1001],
    ['roles.create', '新建角色', 'POST', '/api/roles', 1001],
    ['roles.patch', '修改角色', 'PATCH', '/api/roles/{id}', 1001],
    ['roles.permissions.read', '查看角色权限', 'GET', '/api/roles/{id}/permissions', 1001],
    ['roles.permissions.write', '配置角色权限', 'PUT', '/api/roles/{id}/permissions', 1001],
    ['systems', '查看系统目录', 'GET', '/api/systems', 1002],
    ['permissions.tree', '查看权限树', 'GET', '/api/permissions/tree', 1002],
    ['apis.list', '查询API资源', 'GET', '/api/api-resources', 1002],
    ['permissions.patch', '维护权限属性', 'PATCH', '/api/permissions/{id}', 1002],
    ['apis.patch', '启停API资源', 'PATCH', '/api/api-resources/{id}', 1002],
    ['permissions.apis.read', '查看API映射', 'GET', '/api/permissions/{id}/apis', 1002],
    ['permissions.apis.write', '配置API映射', 'PUT', '/api/permissions/{id}/apis', 1002],
    ['disable', '停用人员', 'PATCH', '/api/users/{id}/status', 1000],
    ['move', '调动部门归属', 'PUT', '/api/users/{id}/department', 1000],
    ['logs', '查询鉴权日志', 'GET', '/api/authorization-logs', 1003],
    ['departments.rename', '修改部门名称', 'PATCH', '/api/departments/{id}', 1004],
  ];
  const apis = definitions.map(([key, name, method, path], index) => ({
    id: index + 1, key, name, method, path, enabled: true, systemId: 1,
  }));
  const permissions = definitions.map(([key, name, , , parentId], index) => ({
    id: index + 1, key, code: 'RBAC_' + key.replaceAll('.', '_').toUpperCase(),
    name, parentId, type: 'BUTTON', enabled: true, systemId: 1, apiIds: [index + 1],
  }));
  [['人员管理', 'people'], ['角色与权限', 'roles'], ['资源与API', 'resources'], ['鉴权日志', 'audit'], ['部门管理', 'organization']].forEach(([name, key], index) => {
    permissions.push({ id: 1000 + index, key: 'menu.' + key, code: 'MENU_' + key.toUpperCase(), name, parentId: null, type: 'MENU', enabled: true, systemId: 1, apiIds: [] });
  });
  permissions.push({ id: 2000, key: 'external.review', code: 'EXTERNAL_REVIEW', name: '外系统业务审核', parentId: null, type: 'BUTTON', enabled: true, systemId: 2, apiIds: [2000] });
  apis.push({ id: 2000, key: 'external.review', name: '外系统业务审核', method: 'GET', path: '/external/reviews', enabled: true, systemId: 2 });
  return {
    permissions, apis,
    systems: [{ id: 1, code: 'RBAC', name: '统一权限管理系统', enabled: true }, { id: 2, code: 'EXTERNAL', name: '业务协同系统', enabled: true }],
  };
}
const api = { createCatalog };
if (typeof module !== 'undefined' && module.exports) module.exports = api;
else globalThis.MunicipalCatalog = api;
})();
