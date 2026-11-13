const test = require('node:test');
const assert = require('node:assert/strict');
const source = require('../applications.js');
const { createDemoModel, DEMO_PASSWORD, PEOPLE } = require('../model.js');
const login = (model, account = 'admin') => model.login(account, DEMO_PASSWORD);

/** 从原始单元格推导期望值，独立核对运行目录的初始化结果。 */
function expectedOperations(column) {
  return new Set(source.features.flatMap(feature => {
    const raw = feature.roleRights[column];
    const values = raw === '全部' ? feature.roleRights.filter(value => value && !['无', '全部'].includes(value)).flatMap(value => value.split('/'))
      : raw && raw !== '无' ? raw.split('/') : [];
    return values.map(value => feature.row + ':' + (value === 'view' ? '查看' : value));
  }));
}

test('R28 四个基础角色首次业务授权逐行匹配原Excel，不漏授或补授', () => {
  const model = createDemoModel();
  login(model);
  const catalog = model.permissionCatalog().permissions;
  for (const [roleId, column] of [[2, 1], [3, 2], [4, 3], [5, 4]]) {
    const actual = model.rolePermissions(roleId).permissions;
    const buttons = actual.filter(item => item.sourceKind === 'operation');
    assert.deepEqual(new Set(buttons.map(item => item.sourceRow + ':' + item.name)), expectedOperations(column));
    const requiredMenus = new Set();
    for (const button of buttons) {
      for (let parent = catalog.find(item => item.id === button.parentId); parent; parent = catalog.find(item => item.id === parent.parentId)) requiredMenus.add(parent.id);
    }
    assert.deepEqual(new Set(actual.filter(item => item.systemId !== 1 && item.type === 'MENU').map(item => item.id)), requiredMenus);
    assert.ok(actual.filter(item => item.systemId !== 1).every(item => item.apiIds.length === 0));
  }
  assert.equal(model.rolePermissions(5).permissions.some(item => item.sourceRow === 340), false);
});

test('R08/R28 业务初始权限不增加RBAC管理权，三管理角色保留各自范围', () => {
  const model = createDemoModel();
  login(model);
  assert.deepEqual(model.rolePermissions(5).permissions.filter(item => item.systemId === 1), []);
  for (const id of [2, 3, 4]) assert.deepEqual(model.rolePermissions(id).permissions.filter(item => item.systemId === 1).map(item => item.key), PEOPLE);
  assert.throws(() => login(model, 'SG000004'), error => error.code === 'MANAGEMENT_ACCESS_DENIED');
  for (const [account, rootId] of [['SG000001', 10], ['SG000002', 3], ['SG000003', 5]]) {
    login(model, account);
    assert.equal(model.me().departmentId, rootId);
    assert.equal(model.canGlobal('roles.permissions.write'), false);
    assert.equal(model.can('logs'), false);
  }
});

test('R11/R23/R28 原表权限不逐级包含，候选和保存仍检查全部系统子集', () => {
  const model = createDemoModel();
  login(model, 'SG000001');
  assert.deepEqual(model.candidateRoles(8, { size: 100 }).items.map(item => item.id), [2]);
  assert.throws(() => model.assign(8, [3]), error => error.code === 'MANAGEMENT_SCOPE_DENIED');
  login(model, 'SG000003');
  assert.deepEqual(model.candidateRoles(5, { size: 100 }).items.map(item => item.id), [4]);
  assert.throws(() => model.assign(5, [5]), error => error.code === 'MANAGEMENT_SCOPE_DENIED');
});

test('R22/R28 首次业务授权不反复覆盖显式修改，新建自定义角色保持空权限', () => {
  const model = createDemoModel();
  login(model);
  const initial = model.rolePermissions(5).permissions.map(item => item.id);
  assert.ok(initial.length > 0);
  model.saveRolePermissions(5, []);
  assert.deepEqual(model.rolePermissions(5).permissions, []);
  model.resources({ systemId: 2 });
  assert.deepEqual(model.rolePermissions(5).permissions, []);
  const custom = model.createRole({ name: '新空角色', code: 'INITIAL_EMPTY' });
  assert.deepEqual(model.rolePermissions(custom.id).permissions, []);
  const reset = createDemoModel();
  login(reset);
  assert.deepEqual(reset.rolePermissions(5).permissions.map(item => item.id), initial);
});
