const test = require('node:test');
const assert = require('node:assert/strict');
const { createDemoModel, DEMO_PASSWORD, PEOPLE } = require('../model.js');
const login = (model, name = 'admin') => model.login(name, DEMO_PASSWORD);
const code = expected => error => error.code === expected;
/** 此组隔离授予级别，业务矩阵完整子集由initial-business-roles单独验证。 */
function rankOnlyFixture(model) {
  login(model);
  for (const id of [2, 3, 4, 5]) {
    model.saveRolePermissions(id, model.rolePermissions(id).permissions.filter(item => item.systemId === 1).map(item => item.id));
  }
}

test('R23 项目经理只能授予项目经理和普通员工，直接提交越级集合也拒绝', () => {
  const model = createDemoModel();
  rankOnlyFixture(model);
  login(model, 'SG000003');
  assert.deepEqual(model.candidateRoles(5, { size: 100 }).items.map(role => role.id), [4, 5]);
  for (const ids of [[2], [3], [10]]) assert.throws(() => model.assign(5, ids), code('MANAGEMENT_SCOPE_DENIED'));
  assert.deepEqual(model.currentRoles(5).roles.map(role => role.id), [5]);
});
test('R23 部门经理与公司领导分别受到三级/四级限制，多个角色按有效最高级别', () => {
  const model = createDemoModel();
  rankOnlyFixture(model);
  login(model, 'SG000002');
  assert.deepEqual(model.candidateRoles(5, { size: 100 }).items.map(role => role.id), [3, 4, 5]);
  login(model, 'SG000001');
  assert.deepEqual(model.candidateRoles(8, { size: 100 }).items.map(role => role.id), [2, 3, 4, 5]);
  login(model);
  model.assign(5, [2, 4]);
  login(model, 'SG000004');
  const other = model.list({ size: 100 }).items.find(person => person.id !== 5);
  assert.ok(model.candidateRoles(other.id, { size: 100 }).items.some(role => role.id === 2));
});
test('R23 改角色名称不改变授权级别，自定义角色默认仅管理员可授且不提升持有者等级', () => {
  const model = createDemoModel();
  login(model);
  model.patchRole(2, { name: '项目经理同名演示' });
  const custom = model.createRole({ name: '新协同角色', code: 'NEW_HELPER' });
  assert.equal(custom.requiredGrantLevel, 5);
  assert.equal(custom.authorityLevel, 0);
  model.saveRolePermissions(custom.id, [1, 2, 3, 4, 5, 18, 19]);
  model.assign(5, [custom.id]);
  login(model, 'SG000004');
  const other = model.list({ size: 100 }).items.find(person => person.id !== 5);
  assert.equal(model.candidateRoles(other.id, { size: 100 }).total, 0);
});
test('R11/R23 即使实际API相同也须比较完整Permission集合', () => {
  const model = createDemoModel();
  login(model);
  const current = model.rolePermissions(3);
  model.saveRolePermissions(3, [...current.permissions.map(permission => permission.id), 1000]);
  login(model, 'SG000001');
  assert.ok(!model.candidateRoles(8, { size: 100 }).items.some(role => role.id === 3));
});
test('R24 部门改名保留归属、职位或角色，根直属领导可维护下级', () => {
  const model = createDemoModel();
  login(model);
  const before = model.currentRoles(5);
  model.renameDepartment(5, '滨江一期项目部');
  assert.equal(model.tree().find(item => item.id === 5).name, '滨江一期项目部');
  assert.equal(model.currentRoles(5).departmentId, before.departmentId);
  assert.equal(model.currentRoles(5).position, before.position);
  assert.deepEqual(model.currentRoles(5).roles, before.roles);
  login(model, 'SG000005');
  assert.equal(model.renameDepartment(3, '新名称').name, '新名称');
  assert.throws(() => model.renameDepartment(1, '越权名称'), code('MANAGEMENT_SCOPE_DENIED'));
});
test('R24 部门改名校验空值、同级重名；根改名不改变可信根资格', () => {
  const model = createDemoModel();
  login(model);
  assert.throws(() => model.renameDepartment(3, ' '), code('INVALID_ARGUMENT'));
  assert.throws(() => model.renameDepartment(3, '财务管理部'), code('DATA_CONFLICT'));
  model.renameDepartment(1, '市政总公司');
  assert.equal(model.canGlobal('roles.list'), true);
});
test('R24 局部部门经理可改严格下级，不可修改本人、上级或平级', () => {
  const model = createDemoModel();
  login(model);
  login(model, 'SG000002');
  assert.equal(model.can('departments.rename'), true);
  assert.equal(model.canGlobal('departments.rename'), false);
  for (const id of [1, 2, 3, 10, 12]) assert.throws(() => model.renameDepartment(id, '越权名称'), code('MANAGEMENT_SCOPE_DENIED'));
  assert.equal(model.renameDepartment(5, '新项目部').name, '新项目部');
});
test('R13 新建空权限角色、编码唯一、修改名称不允许改编码', () => {
  const model = createDemoModel();
  login(model);
  const role = model.createRole({ name: '  现场审核  ', code: '  SITE_AUDIT  ' });
  assert.equal(role.name, '现场审核');
  assert.deepEqual(model.rolePermissions(role.id).permissions, []);
  assert.throws(() => model.createRole({ name: '其他', code: 'SITE_AUDIT' }), code('DATA_CONFLICT'));
  assert.throws(() => model.patchRole(role.id, { code: 'changed' }), code('INVALID_ARGUMENT'));
});
test('R13/R15 父菜单独立选择不自动选子项，移除管理员关键权限回滚', () => {
  const model = createDemoModel();
  login(model);
  const role = model.createRole({ name: '导航试用', code: 'NAV_ONLY' });
  model.saveRolePermissions(role.id, [1000]);
  assert.deepEqual(model.rolePermissions(role.id).permissions.map(item => item.id), [1000]);
  const before = model.rolePermissions(1);
  assert.throws(() => model.saveRolePermissions(1, before.permissions.filter(item => item.id !== 5).map(item => item.id)), code('LAST_ADMIN_REQUIRED'));
  assert.deepEqual(model.rolePermissions(1).permissions, before.permissions);
});
test('R14/R15 关键API停用和映射清空不能破坏最后维护者，失败保持原值', () => {
  const model = createDemoModel();
  login(model);
  assert.throws(() => model.patchApi(5, false), code('LAST_ADMIN_REQUIRED'));
  assert.equal(model.apis({ size: 100 }).items.find(item => item.id === 5).enabled, true);
  assert.throws(() => model.savePermissionApis(5, []), code('LAST_ADMIN_REQUIRED'));
  assert.deepEqual(model.permissionApis(5).apis.map(item => item.id), [5]);
});
test('R14 权限树拒绝环、按钮挂子项；父菜单停用不级联撤销子权限', () => {
  const model = createDemoModel();
  login(model);
  assert.throws(() => model.patchPermission(1000, { type: 'BUTTON' }), code('INVALID_ARGUMENT'));
  model.patchPermission(1001, { parentId: 1000 });
  assert.throws(() => model.patchPermission(1000, { parentId: 1001 }), code('INVALID_ARGUMENT'));
  model.patchPermission(1000, { enabled: false });
  assert.equal(model.can('assign'), true);
});
test('R14 停用API可保留映射，但不贡献有效能力；同API多链按OR', () => {
  const model = createDemoModel();
  login(model);
  model.patchApi(20, false);
  assert.equal(model.can('logs'), false);
  model.savePermissionApis(20, [20]);
  assert.equal(model.permissionApis(20).apis[0].enabled, false);
  model.patchApi(20, true);
  assert.equal(model.can('logs'), true);
});
test('R16 日志快照总数不受查询产生的新日志影响，篡改筛选游标拒绝', () => {
  const model = createDemoModel();
  login(model);
  const first = model.logs({ limit: 6, allowed: '' });
  assert.ok(first.hasMore);
  model.list();
  const next = model.logs({ limit: 6, allowed: '', cursor: first.nextCursor });
  assert.equal(next.total, first.total);
  assert.equal(next.snapshotId, first.snapshotId);
  assert.ok(next.items.every(item => item.id < first.items.at(-1).id));
  assert.throws(() => model.logs({ limit: 6, allowed: 'false', cursor: first.nextCursor }), code('INVALID_CURSOR'));
});
test('R16 409业务失败仍为鉴权允许，未知登录主体不复制原始手机号', () => {
  const model = createDemoModel();
  assert.throws(() => login(model, '18812340000'), code('LOGIN_FAILED'));
  login(model);
  assert.throws(() => model.patchApi(5, false), code('LAST_ADMIN_REQUIRED'));
  const logs = model.logs({ limit: 50 }).items;
  const conflict = logs.find(item => item.reason === 'LAST_ADMIN_REQUIRED');
  assert.equal(conflict.allowed, true);
  assert.equal(conflict.httpStatus, 409);
  assert.equal(logs.find(item => item.eventType === 'LOGIN_FAILED').username, null);
  assert.ok(logs.every(item => !JSON.stringify(item).includes('18812340000')));
});
test('R16 非根误授日志API仍被拒绝，日志撤权后下一批拒绝', () => {
  const model = createDemoModel();
  login(model);
  const lead = model.rolePermissions(2);
  model.saveRolePermissions(2, [...lead.permissions.map(item => item.id), 20]);
  login(model, 'SG000001');
  assert.throws(() => model.logs({ limit: 6 }), code('MANAGEMENT_SCOPE_DENIED'));
});
test('R25 总览严格限制在当前组织范围，未授权全局日志不混入概况', () => {
  const model = createDemoModel();
  login(model, 'SG000003');
  const overview = model.overview();
  assert.equal(overview.scope.id, 5);
  assert.equal(overview.total, model.list({ size: 100 }).total);
  assert.equal(overview.latestLogs, null);
  assert.equal(overview.departments, 1);
});
test('R15 已授权的角色权限写入成功后不重新要求被撤回的读取权限', () => {
  const model = createDemoModel();
  login(model);
  const current = model.rolePermissions(2).permissions.map(item => item.id);
  model.saveRolePermissions(2, [...current, 9, 10]);
  login(model, 'SG000005');
  const result = model.saveRolePermissions(2, [...current, 10]);
  assert.ok(!result.permissions.some(item => item.id === 9));
  assert.throws(() => model.rolePermissions(2), code('PERMISSION_DENIED'));
  login(model);
  assert.ok(!model.rolePermissions(2).permissions.some(item => item.id === 9));
});
test('R15 映射写入撤掉本人读取能力时仍返回本次成功结果，不产生伪403', () => {
  const model = createDemoModel();
  login(model);
  model.savePermissionApis(1000, [16]);
  const admin = model.rolePermissions(1).permissions.map(item => item.id);
  model.saveRolePermissions(1, [...admin, 1000]);
  const current = model.rolePermissions(2).permissions.map(item => item.id);
  model.saveRolePermissions(2, [...current, 16, 17]);
  login(model, 'SG000005');
  const result = model.savePermissionApis(16, []);
  assert.deepEqual(result.apis, []);
  assert.throws(() => model.permissionApis(16), code('PERMISSION_DENIED'));
  login(model);
  assert.deepEqual(model.permissionApis(16).apis, []);
});
test('R16 登录及业务错误日志复用页面错误的requestId', () => {
  const model = createDemoModel();
  let loginFailure;
  try { login(model, 'unknown'); } catch (error) { loginFailure = error; }
  login(model);
  let conflict;
  try { model.renameDepartment(3, '财务管理部'); } catch (error) { conflict = error; }
  const entries = model.logs({ limit: 50 }).items;
  assert.equal(entries.find(item => item.reason === 'DATA_CONFLICT').requestId, conflict.requestId);
  assert.equal(entries.find(item => item.eventType === 'LOGIN_FAILED').requestId, loginFailure.requestId);
});

test('R16 全部预置日志的登录事件、鉴权结果与HTTP状态一致', () => {
  const model = createDemoModel();
  login(model);
  const first = model.logs({ limit: 50 });
  const entries = [...first.items];
  let page = first;
  while (page.hasMore) {
    page = model.logs({ limit: 50, cursor: page.nextCursor });
    entries.push(...page.items);
  }
  assert.ok(entries.length > 50);
  assert.equal(entries.length, first.total);
  for (const item of entries) {
    if (item.eventType === 'LOGIN_SUCCESS') {
      assert.equal(item.allowed, true, '登录成功事件不得标记拒绝，日志ID ' + item.id);
      assert.equal(item.reason, 'OK');
      assert.equal(item.httpStatus, 200);
      assert.equal(item.httpMethod, 'POST');
      assert.equal(item.requestPath, '/api/auth/login');
    } else if (item.eventType === 'LOGIN_FAILED') {
      assert.equal(item.allowed, false);
      assert.ok([401, 403].includes(item.httpStatus));
      assert.equal(item.reason, item.httpStatus === 401 ? 'LOGIN_FAILED' : 'MANAGEMENT_ACCESS_DENIED');
      assert.equal(item.requestPath, '/api/auth/login');
    } else if (item.reason === 'PERMISSION_DENIED') {
      assert.equal(item.eventType, 'AUTHORIZATION');
      assert.equal(item.allowed, false);
      assert.equal(item.httpStatus, 403);
      assert.equal(item.requestPath, '/api/users');
    }
  }
});
