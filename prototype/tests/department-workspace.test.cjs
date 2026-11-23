const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createDemoModel, DEMO_PASSWORD } = require('../model.js');
const settled = () => new Promise(resolve => setImmediate(resolve));

function harness(account = 'admin', request = operation => Promise.resolve().then(operation)) {
  const model = createDemoModel();
  const state = { route: 'departments', views: {}, epoch: 0, session: model.login(account, DEMO_PASSWORD) };
  const runtime = vm.createContext({ MunicipalPrototype: { DemoError: Error }, structuredClone, Set, Map });
  vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../pages.js'), 'utf8'), runtime);
  const screens = runtime.MunicipalScreens.createScreens({
    state: () => state, model: () => model, h: value => String(value ?? ''), icon: () => '', busy: () => '',
    errorBox: () => '', pagination: () => '分页', request, render() {}, renderOverlays() {}, handleSessionError: () => false,
  });
  return { screens, state, model };
}

test('部门工作区：树选择、折叠及搜索独立于右侧当前节点', async () => {
  const { screens, state } = harness();
  await screens.load();
  assert.match(screens.page('departments'), /直属下级部门/);
  screens.handleAction('department-select', 3, {});
  let html = screens.page('departments');
  assert.match(html, /data-department-row="5"/);
  assert.doesNotMatch(html, /data-department-row="14"/);
  screens.handleAction('department-expand', 3, {});
  assert.equal(state.views.departments.departmentExpanded.has(3), false);
  screens.input({ id: 'department-tree-search', value: '东湖', dataset: {} });
  html = screens.page('departments');
  const tree = html.split('<nav class="org-tree"')[1].split('</nav>')[0];
  assert.match(tree, /东湖项目部/);
  assert.match(tree, /第一分公司/);
  assert.doesNotMatch(tree, /第二分公司/);
  assert.equal(state.views.departments.departmentId, 3);
  screens.input({ id: 'department-tree-search', value: '不存在', dataset: {} });
  assert.match(screens.page('departments'), /没有匹配的部门/);
});

test('部门工作区：局部账号不暴露根或其他分公司，本人改删及项目新增受限', async () => {
  const { screens, state } = harness('SG000001');
  await screens.load();
  let html = screens.page('departments');
  assert.doesNotMatch(html, /第二分公司|总公司/);
  assert.match(html, /data-act="department-rename" data-id="10" disabled/);
  screens.handleAction('department-select', 1, {});
  assert.equal(state.views.departments.departmentId, 10);
  screens.handleAction('department-select', 14, {});
  html = screens.page('departments');
  assert.match(html, /项目是末级节点/);
  screens.handleAction('department-create', 14, {});
  assert.equal(state.modal, undefined);
});

test('成员查询：直属与子树口径、角色过滤及未知角色返回空集合', () => {
  const { model } = harness();
  const direct = model.list({ departmentId: 3, departmentScope: 'DIRECT', size: 200 });
  const subtree = model.list({ departmentId: 3, size: 200 });
  assert.ok(subtree.total > direct.total);
  assert.ok(direct.items.every(item => item.departmentId === 3));
  const filtered = model.list({ departmentId: 3, roleId: 5, size: 200 });
  assert.ok(filtered.total > 0);
  assert.ok(filtered.items.every(item => item.roles.some(role => role.id === 5)));
  assert.equal(model.list({ roleId: 99999 }).total, 0);
  assert.throws(() => model.list({ departmentScope: 'DIRECT' }), error => error.code === 'INVALID_ARGUMENT');
  model.login('SG000001', DEMO_PASSWORD);
  assert.throws(() => model.list({ departmentId: 3, departmentScope: 'DIRECT' }), error => error.code === 'MANAGEMENT_SCOPE_DENIED');
  assert.ok(model.list({ roleId: 5, size: 200 }).items.every(item => [10, 11, 12, 13, 14].includes(item.departmentId)));
});

test('员工抽屉：默认直属、切换含下级、搜索及草稿阻止旧条件翻页', async () => {
  const { screens, state, model } = harness();
  await screens.load();
  screens.page('departments');
  screens.handleAction('department-members', 3, {});
  await settled();
  assert.equal(state.modal.data.total, model.list({ departmentId: 3, departmentScope: 'DIRECT' }).total);
  assert.equal(screens.dirty(state.modal), false);
  screens.change({ id: 'members-scope', value: 'SUBTREE', dataset: {} });
  await settled();
  assert.equal(state.modal.data.total, model.list({ departmentId: 3 }).total);
  screens.input({ id: 'members-search', value: 'SG000002', dataset: {} });
  assert.match(screens.modal(state.modal), /筛选已修改/);
  screens.handleAction('members-page', 0, { dataset: { page: '1' } });
  assert.equal(state.modal.memberBusy, false);
  screens.submit('members-filter');
  await settled();
  assert.equal(state.modal.data.total, 1);
  assert.equal(state.modal.data.items[0].username, 'SG000002');
});

test('员工抽屉：后发查询胜出，关闭后不回填，失败允许重试', async () => {
  const pending = [];
  const { screens, state } = harness('admin', operation => new Promise((resolve, reject) => pending.push({ resolve: () => resolve(operation()), reject })));
  const loading = screens.load(); pending.shift().resolve(); await loading;
  screens.page('departments');
  screens.handleAction('department-members', 3, {});
  screens.change({ id: 'members-scope', value: 'SUBTREE', dataset: {} });
  pending[1].resolve(); await settled();
  const total = state.modal.data.total;
  pending[0].resolve(); await settled();
  assert.equal(state.modal.data.total, total);
  pending.length = 0;
  screens.handleAction('members-retry', 0, {});
  pending.shift().reject(new Error('模拟失败')); await settled();
  assert.match(screens.modal(state.modal), /重新读取名单/);
  screens.handleAction('members-retry', 0, {});
  const closed = state.modal; state.modal = null;
  pending.shift().resolve(); await settled();
  assert.equal(state.modal, null);
  assert.equal(closed.data, null);
});

test('角色成员入口保留停用关系；日志IP明确为离线演示', async () => {
  const { screens, state, model } = harness();
  state.route = 'roles';
  await screens.load();
  state.views.roles.q = 'LEGACY_REVIEW';
  await screens.load();
  assert.match(screens.page('roles'), /role-members/);
  screens.handleAction('role-members', 90, {});
  await settled();
  assert.ok(state.modal.data.total > 0);
  assert.match(screens.modal(state.modal), /此角色已停用/);
  const entry = model.logs({ limit: 12 }).items[0];
  assert.equal(entry.clientIp, '192.0.2.24');
  const html = screens.modal({ kind: 'screen-log-detail', original: entry });
  assert.match(html, /来源IP/);
  assert.match(html, /虚构演示值/);
});
