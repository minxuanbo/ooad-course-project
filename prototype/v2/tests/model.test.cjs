const test = require('node:test');
const assert = require('node:assert/strict');
const { createDemoModel, DEMO_PASSWORD, MAINTENANCE } = require('../model.js');

const login = (model, account = 'admin') => model.login(account, DEMO_PASSWORD);
const errorCode = code => error => error.code === code;

test('R04/R07 工号与手机号定位同一账号，保留工号前导零', () => {
  const model = createDemoModel();
  const first = login(model, 'SG000001');
  const second = login(model, '13800000001');
  assert.equal(first.id, second.id);
  assert.equal(second.username, 'SG000001');
  assert.equal(login(model, '  SG000001  ').id, first.id);
});
test('R07 口令不trim，未知和错口令统一失败', () => {
  const model = createDemoModel();
  assert.throws(() => model.login('admin', ' ' + DEMO_PASSWORD), errorCode('LOGIN_FAILED'));
  assert.throws(() => login(model, 'not-found'), errorCode('LOGIN_FAILED'));
});
test('R08 普通员工被拒绝进入管理端', () => {
  assert.throws(() => login(createDemoModel(), 'SG000004'), errorCode('MANAGEMENT_ACCESS_DENIED'));
});
test('R02/R10 可见树投影根不增加范围，部门操作者不可查询兄弟组织', () => {
  const model = createDemoModel();
  login(model, 'SG000002');
  const tree = model.tree();
  assert.equal(tree.find(item => item.id === 3).parentId, null);
  assert.deepEqual(tree.map(item => item.id), [3, 5, 6]);
  assert.throws(() => model.list({ departmentId: 10 }), errorCode('MANAGEMENT_SCOPE_DENIED'));
});
test('R10 组合筛选total与分页保持一致，越末页保留total', () => {
  const model = createDemoModel();
  login(model);
  const all = model.list({ departmentId: 3, enabled: 'true', q: 'SG', size: 100 });
  const first = model.list({ departmentId: 3, enabled: 'true', q: 'SG', size: 6 });
  const beyond = model.list({ departmentId: 3, enabled: 'true', q: 'SG', page: 100, size: 6 });
  assert.equal(first.total, all.items.length);
  assert.ok(all.items.every(person => [3, 5, 6].includes(person.departmentId) && person.enabled));
  assert.equal(beyond.items.length, 0);
  assert.equal(beyond.total, first.total);
  assert.ok(first.items.every(person => !('phone' in person)));
  assert.ok(first.items.every(person => !('roles' in person)));
});
test('R11 候选按完整Permission子集，不把外系统角色当空角色', () => {
  const model = createDemoModel();
  login(model, 'SG000002');
  const all = model.candidateRoles(5, { size: 100 }).items;
  assert.ok(all.some(role => role.id === 10));
  assert.ok(all.some(role => role.id === 5));
  assert.ok(!all.some(role => [1, 90, 91].includes(role.id)));
  assert.throws(() => model.assign(5, [91]), errorCode('MANAGEMENT_SCOPE_DENIED'));
});
test('R11 跨页选择按完整ids保存，模型不依赖当前候选页', () => {
  const model = createDemoModel();
  login(model);
  const first = model.candidateRoles(5, { page: 0 });
  const second = model.candidateRoles(5, { page: 1 });
  const ids = [first.items.find(role => role.id === 5).id, second.items[0].id];
  model.assign(5, ids);
  assert.deepEqual(model.currentRoles(5).roles.map(role => role.id), ids);
});
test('R11 已停用当前角色可见但不能再授，移除后保存成功', () => {
  const model = createDemoModel();
  login(model);
  assert.ok(model.currentRoles(7).roles.some(role => role.id === 90 && !role.enabled));
  assert.throws(() => model.assign(7, [5, 90]), errorCode('MANAGEMENT_SCOPE_DENIED'));
  assert.deepEqual(model.currentRoles(7).roles.map(role => role.id), [5, 90]);
  model.assign(7, [5]);
  assert.deepEqual(model.currentRoles(7).roles.map(role => role.id), [5]);
});
test('R11 部门账号不能调整本人授权或部门', () => {
  const model = createDemoModel();
  login(model, 'SG000002');
  assert.throws(() => model.assign(3, [5]), errorCode('MANAGEMENT_SCOPE_DENIED'));
  assert.throws(() => model.move(3, 5), errorCode('MANAGEMENT_SCOPE_DENIED'));
});
test('R12 调动校验双范围，保留用户身份与全部角色', () => {
  const model = createDemoModel();
  login(model, 'SG000002');
  const before = model.currentRoles(5);
  assert.throws(() => model.move(5, 10), errorCode('MANAGEMENT_SCOPE_DENIED'));
  assert.equal(model.currentRoles(5).departmentId, 5);
  const after = model.move(5, 6);
  assert.equal(after.id, before.id);
  assert.equal(after.departmentId, 6);
  assert.deepEqual(after.roles, before.roles);
});
test('R12 停用保留角色与部门，下一次登录统一失败', () => {
  const model = createDemoModel();
  login(model);
  const before = model.currentRoles(5);
  model.disable(5);
  const after = model.currentRoles(5);
  assert.equal(after.departmentId, before.departmentId);
  assert.deepEqual(after.roles, before.roles);
  assert.equal(after.enabled, false);
  assert.throws(() => login(model, 'SG000004'), errorCode('LOGIN_FAILED'));
});
test('R15 完整K含17项，最后维护者的停用/撤角色/调离根均全回滚', () => {
  assert.equal(MAINTENANCE.length, 17);
  const model = createDemoModel();
  login(model);
  assert.throws(() => model.assign(1, [5]), errorCode('LAST_ADMIN_REQUIRED'));
  assert.throws(() => model.disable(1), errorCode('LAST_ADMIN_REQUIRED'));
  assert.throws(() => model.move(1, 3), errorCode('LAST_ADMIN_REQUIRED'));
  assert.equal(model.me().enabled, true);
  assert.equal(model.me().departmentId, 1);
  assert.deepEqual(model.me().roles.map(role => role.id), [1]);
});
test('R15 同一根用户完整覆盖才是保底，保留第二维护者后可停用原维护者', () => {
  const model = createDemoModel();
  login(model);
  model.assign(6, [1]);
  model.disable(1);
  assert.throws(() => model.list(), errorCode('SESSION_INVALID'));
  assert.equal(login(model, 'SG000005').id, 6);
  assert.ok(model.list().items.length);
});
test('R08/R09 撤权后保留身份，下一次管理操作立即拒绝', () => {
  const model = createDemoModel();
  login(model, 'SG000001');
  assert.ok(model.list().total);
  model.simulateRevocation();
  assert.equal(model.me().id, 2);
  assert.deepEqual(model.me().roles, []);
  assert.throws(() => model.list(), errorCode('PERMISSION_DENIED'));
});
test('R09 移除一条授权链后，另一有效角色仍提供查询能力', () => {
  const model = createDemoModel();
  login(model);
  model.assign(6, [2, 10]);
  login(model, 'SG000005');
  model.assign(6, [10]);
  assert.ok(model.list().total);
  assert.equal(model.can('assign'), false);
  assert.equal(model.can('users'), true);
});
test('R22 重建内存模型即还原，读取副本不允许意外改写保存状态', () => {
  const model = createDemoModel();
  login(model);
  const copy = model.currentRoles(5);
  copy.roles[0].name = 'changed';
  assert.equal(model.currentRoles(5).roles[0].name, '普通员工');
  model.move(5, 6);
  const reset = createDemoModel();
  login(reset);
  assert.equal(reset.currentRoles(5).departmentId, 5);
});
