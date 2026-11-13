const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const { createDemoModel, DEMO_PASSWORD } = require('../model.js');
const source = require('../applications.js');
const login = (model, account = 'admin') => model.login(account, DEMO_PASSWORD);
const code = expected => error => error.code === expected;

test('R27 停用自定义角色仍保留管理员目标保护，局部不可撤回、停用或调动', () => {
  const model = createDemoModel();
  login(model, 'SG000002');
  assert.ok(model.currentRoles(7).roles.some(item => item.id === 90 && !item.enabled));
  assert.equal(model.canManageUser(7, 'assign'), false);
  assert.throws(() => model.assign(7, [5]), code('MANAGEMENT_SCOPE_DENIED'));
  assert.throws(() => model.disable(7), code('MANAGEMENT_SCOPE_DENIED'));
  assert.throws(() => model.move(7, 5), code('MANAGEMENT_SCOPE_DENIED'));
  login(model);
  model.assign(7, [5]);
  assert.deepEqual(model.currentRoles(7).roles.map(item => item.id), [5]);
});

test('R08/R24 单独持有任一新部门管理API也可按有效管理权限准入', () => {
  for (const key of ['departments.list', 'departments.create', 'departments.delete']) {
    const model = createDemoModel();
    login(model);
    const id = model.permissionCatalog().permissions.find(item => item.key === key).id;
    model.saveRolePermissions(3, [id]);
    assert.equal(login(model, 'SG000002').id, 3);
    assert.equal(model.can(key), true);
  }
});

test('R27 同公司领导、本人与上级三种写操作均拒绝，允许查看', () => {
  const model = createDemoModel();
  login(model, 'SG000001');
  assert.equal(model.currentRoles(9).roles[0].id, 2);
  for (const id of [2, 9]) {
    assert.equal(model.canManageUser(id, 'assign'), false);
    assert.throws(() => model.assign(id, [5]), code('MANAGEMENT_SCOPE_DENIED'));
    assert.throws(() => model.disable(id), code('MANAGEMENT_SCOPE_DENIED'));
    assert.throws(() => model.move(id, 11), code('MANAGEMENT_SCOPE_DENIED'));
  }
  login(model);
  model.move(2, 12);
  login(model, 'SG000007');
  assert.throws(() => model.assign(2, [5]), code('MANAGEMENT_SCOPE_DENIED'));
  assert.throws(() => model.disable(2), code('MANAGEMENT_SCOPE_DENIED'));
  assert.throws(() => model.move(2, 14), code('MANAGEMENT_SCOPE_DENIED'));
});

test('R23/R27 下级可被授予本级角色，下一次按同级保护；系统管理员例外', () => {
  const model = createDemoModel();
  login(model, 'SG000001');
  assert.equal(model.canManageUser(8, 'assign'), true);
  model.assign(8, [2]);
  assert.equal(model.canManageUser(8, 'assign'), false);
  assert.throws(() => model.assign(8, [5]), code('MANAGEMENT_SCOPE_DENIED'));
  login(model);
  model.assign(8, [3]);
  assert.equal(model.currentRoles(8).roles[0].id, 3);
});

test('R24 局部可在本人和下级下创建，仅严格下级可改删，ID不复用', () => {
  const model = createDemoModel();
  login(model, 'SG000001');
  const own = model.departments().find(item => item.id === 10);
  assert.equal(own.canCreate, true);
  assert.equal(own.canRename, false);
  assert.equal(own.canDelete, false);
  const first = model.createDepartment({ name: '临时部门', parentId: 10 });
  const child = model.createDepartment({ name: '临时项目', parentId: first.id, type: '项目' });
  assert.throws(() => model.deleteDepartment(first.id), code('DEPARTMENT_NOT_EMPTY'));
  assert.equal(model.renameDepartment(child.id, '项目新名').name, '项目新名');
  model.deleteDepartment(child.id);
  model.deleteDepartment(first.id);
  const next = model.createDepartment({ name: '再次新增', parentId: 10 });
  assert.ok(next.id > child.id);
  for (const parentId of [1, 2, 3, 20]) assert.throws(() => model.createDepartment({ name: '越界', parentId }), code('MANAGEMENT_SCOPE_DENIED'));
  for (const id of [1, 2, 3, 10]) assert.throws(() => model.deleteDepartment(id), code('MANAGEMENT_SCOPE_DENIED'));
});

test('R24 根不可删，有人员的叶子不可删，空名/重名/类型无效均不创建', () => {
  const model = createDemoModel();
  login(model);
  assert.throws(() => model.deleteDepartment(1), code('MANAGEMENT_SCOPE_DENIED'));
  assert.throws(() => model.deleteDepartment(5), code('DEPARTMENT_NOT_EMPTY'));
  const before = model.departments().length;
  assert.throws(() => model.createDepartment({ name: '  ', parentId: 3 }), code('INVALID_ARGUMENT'));
  assert.throws(() => model.createDepartment({ name: '滨江项目部', parentId: 3, type: '项目' }), code('DATA_CONFLICT'));
  assert.throws(() => model.createDepartment({ name: 'X', parentId: 3, type: '未知' }), code('INVALID_ARGUMENT'));
  assert.equal(model.departments().length, before);
  for (const operation of [() => model.deleteDepartment(99999), () => model.renameDepartment(99999, 'X'), () => model.createDepartment({ name: 'X', parentId: 99999 })]) assert.throws(operation, code('RESOURCE_NOT_FOUND'));
});

test('R24 即时撤去部门写权限后无法继续新建，已有部门不变', () => {
  const model = createDemoModel();
  login(model);
  const ids = model.rolePermissions(3).permissions.filter(item => item.key !== 'departments.create').map(item => item.id);
  model.saveRolePermissions(3, ids);
  login(model, 'SG000002');
  assert.ok(model.departments().every(item => !item.canCreate));
  assert.throws(() => model.createDepartment({ name: '不可创建', parentId: 3 }), code('PERMISSION_DENIED'));
});

test('R28 18系统115模块359功能与原Excel摘要一致，缺失与别名不丢失', () => {
  const workbook = fs.readFileSync(path.resolve(__dirname, '../../市政公司应用软件功能权限清单.xlsx'));
  assert.equal(crypto.createHash('sha256').update(workbook).digest('hex'), source.sourceSha256);
  assert.equal(source.systems.length, 18);
  assert.equal(source.moduleCount, 115);
  assert.equal(source.features.length, 359);
  assert.deepEqual(source.normalizations.map(item => item.cell), ['H117', 'G310']);
  assert.equal(source.features.find(item => item.row === 340).roleRights[4], null);
  assert.equal(source.features.find(item => item.row === 310).roleRights[2], 'view/编辑/审批');
  assert.equal(source.apiMappingsProvided, false);
});

test('R28 业务资源没有编造API，全部严格等于本行其他角色操作并集', () => {
  const model = createDemoModel();
  login(model);
  const catalog = model.permissionCatalog();
  assert.equal(catalog.systems.length, 19);
  assert.ok(catalog.permissions.some(item => item.name === '查看本人'));
  assert.ok(catalog.permissions.some(item => item.name === '编辑本人'));
  assert.ok(!catalog.permissions.some(item => item.name.includes('全部（')));
  for (const feature of source.features) {
    const union = [...new Set(feature.roleRights.slice(1).filter(Boolean).flatMap(value => value.split('/')).filter(value => !['无', '全部'].includes(value)).map(value => value === 'view' ? '查看' : value))].sort();
    assert.deepEqual([...feature.operations].sort(), union);
    const adminOperations = catalog.permissions.filter(item => item.sourceKind === 'operation' && item.sourceRow === feature.row && item.referenceRoleIndices.includes(0)).map(item => item.name).sort();
    assert.deepEqual(adminOperations, union);
  }
  for (const system of source.systems) {
    assert.equal(model.apis({ systemId: system.id }).total, 0);
    assert.ok(model.resources({ systemId: system.id }).permissions.every(item => item.apiIds.length === 0));
  }
  assert.ok(model.resources({ systemId: 18 }).permissions.filter(item => item.sourceRow === 340 && item.sourceKind === 'operation').every(item => !item.referenceRoleIndices.includes(4)));
});

test('R11/R28 完整Permission子集包含业务系统，角色选权保存保留跨系统ids', () => {
  const model = createDemoModel();
  login(model);
  const leaderIds = new Set(model.rolePermissions(2).permissions.map(item => item.id));
  const current = model.rolePermissions(3).permissions.filter(item => leaderIds.has(item.id)).map(item => item.id);
  const externalId = model.resources({ systemId: 2 }).permissions.find(item => item.sourceKind === 'operation' && !leaderIds.has(item.id)).id;
  model.saveRolePermissions(3, current);
  login(model, 'SG000001');
  assert.ok(model.candidateRoles(8, { size: 100 }).items.some(item => item.id === 3));
  login(model);
  model.saveRolePermissions(3, [...current, externalId]);
  assert.ok(model.rolePermissions(3).permissions.some(item => item.id === externalId));
  login(model, 'SG000001');
  assert.ok(!model.candidateRoles(8, { size: 100 }).items.some(item => item.id === 3));
  assert.throws(() => model.assign(8, [3]), code('MANAGEMENT_SCOPE_DENIED'));
});

/** 不模拟DOM，仅检查工作区草稿、作用域和最终保存语义。 */
function screensHarness() {
  const model = createDemoModel();
  const state = { route: 'roles', views: {}, epoch: 0, session: login(model) };
  const context = vm.createContext({ MunicipalPrototype: { DemoError: Error }, structuredClone, Set, Map });
  vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../pages.js'), 'utf8'), context);
  const screens = context.MunicipalScreens.createScreens({
    state: () => state, model: () => model, h: value => String(value ?? ''), icon: () => '', busy: () => '', errorBox: () => '', pagination: () => '',
    render() {}, renderOverlays() {}, request: operation => Promise.resolve().then(operation), handleSessionError: () => false,
    writeOperation: operation => { operation(); state.modal = null; state.confirm = null; },
  });
  return { screens, state, model };
}
const settled = () => new Promise(resolve => setImmediate(resolve));

test('R28 工作区仅正常选权，跨系统切换保留草稿并整体保存', async () => {
  const { screens, state, model } = screensHarness();
  const role = model.createRole({ name: '跨系统草稿', code: 'CROSS_SYSTEM_DRAFT' });
  screens.handleAction('role-permissions', role.id, {});
  await settled();
  state.modal.draft.add(1);
  screens.change({ id: 'role-permission-system', value: '2', dataset: {} });
  const businessId = model.resources({ systemId: 2 }).permissions.find(item => item.sourceKind === 'operation').id;
  screens.change({ id: 'permission-check-' + businessId, dataset: { permissionId: String(businessId) }, checked: true });
  screens.change({ id: 'role-permission-system', value: '18', dataset: {} });
  assert.doesNotMatch(screens.modal(state.modal), /应用到当前系统草稿|原表参考角色|待接口细化/);
  assert.ok(state.modal.draft.has(1));
  assert.ok([...state.modal.draft].some(id => state.modal.labels.get(id).systemId === 2));
  assert.ok([...state.modal.draft].every(id => state.modal.labels.get(id).systemId !== 18));
  screens.change({ id: 'role-permission-system', value: '2', dataset: {} });
  assert.match(screens.modal(state.modal), /全部系统已选 2 项/);
  screens.handleAction('screen-save', 0, {});
  screens.confirmAction();
  assert.equal(model.rolePermissions(role.id).permissions.length, 2);
});

test('R24 部门页面从服务端标记禁用本人，创建父级仅取可创建节点', async () => {
  const { screens, state, model } = screensHarness();
  login(model, 'SG000002'); state.session = model.me(); state.route = 'departments';
  await screens.load();
  assert.match(screens.page('departments'), /本人直属部门/);
  screens.handleAction('department-rename', 3, {});
  assert.equal(state.modal, undefined);
  screens.handleAction('department-create', 3, {});
  assert.equal(state.modal.values.parentId, 3);
  assert.equal(state.modal.values.type, '项目');
  assert.ok(state.modal.catalog.every(item => [3, 5, 6].includes(item.id)));
  assert.doesNotMatch(screens.modal(state.modal), /value="10"/);
  assert.doesNotMatch(screens.modal(state.modal), /<option[^>]*>部门<|<option[^>]*>分公司</);
});

test('R24 类型层级限制在模型执行，项目不可新增，非根不可创建分公司', () => {
  const model = createDemoModel();
  login(model);
  assert.deepEqual(model.departments().find(item => item.id === 1).allowedChildTypes, ['分公司', '部门', '项目']);
  assert.deepEqual(model.departments().find(item => item.id === 10).allowedChildTypes, ['部门', '项目']);
  assert.deepEqual(model.departments().find(item => item.id === 3).allowedChildTypes, ['项目']);
  assert.equal(model.departments().find(item => item.id === 5).canCreate, false);
  for (const [parentId, types] of [[10, ['分公司', '公司']], [3, ['部门', '分公司']], [5, ['部门', '项目', '分公司']]]) {
    for (const type of types) assert.throws(() => model.createDepartment({ name: '非法层级', parentId, type }), code('INVALID_ARGUMENT'));
  }
  assert.equal(model.createDepartment({ name: '新分公司', parentId: 1, type: '分公司' }).type, '分公司');
  assert.equal(model.createDepartment({ name: '新项目', parentId: 3, type: '项目' }).type, '项目');
});

test('R09/R26 账号启用与角色停用独立，停用角色保留关系但不提供管理能力', () => {
  const model = createDemoModel();
  login(model);
  const person = model.currentRoles(7);
  assert.equal(person.enabled, true);
  assert.ok(person.roles.some(item => item.id === 90 && !item.enabled));
  assert.throws(() => login(model, 'SG000006'), code('MANAGEMENT_ACCESS_DENIED'));
  assert.equal(model.currentRoles(7).enabled, true);
});

test('R24 部门按父子子树连续展示，直属及含下级人数满足加总口径', () => {
  const model = createDemoModel();
  login(model);
  let rows = model.departments();
  assert.deepEqual(rows.slice(0, 6).map(item => item.id), [1, 2, 3, 5, 6, 4]);
  assert.equal(rows.find(item => item.id === 3).depth, 1);
  assert.equal(rows.find(item => item.id === 5).depth, 2);
  const verifyCounts = () => {
    rows = model.departments();
    for (const item of rows) {
      const childTotal = rows.filter(child => child.parentId === item.id).reduce((sum, child) => sum + child.total, 0);
      assert.equal(item.total, item.direct + childTotal, item.name);
    }
    assert.equal(rows.reduce((sum, item) => sum + item.direct, 0), model.list({ size: 1 }).total);
  };
  verifyCounts();
  const project = model.createDepartment({ name: '后建项目', type: '项目', parentId: 3 });
  assert.deepEqual(model.departments().slice(0, 7).map(item => item.id), [1, 2, 3, 5, 6, project.id, 4]);
  model.move(5, project.id);
  verifyCounts();
  login(model, 'SG000002');
  assert.equal(model.departments()[0].id, 3);
  assert.equal(model.departments()[0].depth, 0);
  assert.ok(model.departments().slice(1).every(item => item.depth === 1));
});

test('R24 部门搜索保留祖先及缩进，不把项目变成根的平级节点', async () => {
  const { screens, state } = screensHarness();
  state.route = 'departments';
  await screens.load();
  screens.input({ id: 'screen-search', value: '滨江', dataset: {} });
  screens.submit('screen-filter');
  await settled();
  const html = screens.page('departments');
  assert.deepEqual([...html.matchAll(/data-department-row="(\d+)"/g)].map(match => Number(match[1])), [1, 3, 5]);
  assert.match(html, /总人数（含下级）/);
  assert.match(html, /父部门总人数已包含项目人数/);
  assert.match(html, /padding-left:40px/);
});
