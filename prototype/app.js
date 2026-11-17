/** 无模块加载、无远端依赖；可直接双击index.html运行。 */
(function () {
'use strict';
const { createDemoModel, DemoError, DEMOS, DEMO_PASSWORD } = globalThis.MunicipalPrototype;

const paths = {
  search: '<circle cx="10.7" cy="10.7" r="6.4"/><path d="m16 16 4.2 4.2"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/><circle cx="9" cy="7" r="4"/>',
  building: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M9 7h1m4 0h1M9 11h1m4 0h1M9 15h1m4 0h1m-4 6v-3h2v3"/>',
  folder: '<path d="M3 6a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  down: '<path d="m6 9 6 6 6-6"/>',
  left: '<path d="m15 5-7 7 7 7"/>',
  refresh: '<path d="M20 7v5h-5M4 17v-5h5"/><path d="M6.1 6.1A8 8 0 0 1 19.5 9M4.5 15A8 8 0 0 0 18 18"/>',
  shield: '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/><path d="m8.5 12 2.3 2.3 4.7-4.7"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
  eyeOff: '<path d="m3 3 18 18M9.9 5.2A11 11 0 0 1 12 5c6.5 0 10 7 10 7a19 19 0 0 1-3.1 4.1M6.1 6.1A19 19 0 0 0 2 12s3.5 7 10 7a12 12 0 0 0 5.1-1.2"/>',
  arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>',
  more: '<circle cx="5" cy="12" r=".8"/><circle cx="12" cy="12" r=".8"/><circle cx="19" cy="12" r=".8"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.1"/>',
  move: '<path d="M3 7h17m-5-5 5 5-5 5M21 17H4m5-5-5 5 5 5"/>',
  ban: '<circle cx="12" cy="12" r="9"/><path d="m6 6 12 12"/>',
  logout: '<path d="M9 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4m6-13 5 5-5 5M8 12h12"/>',
  filter: '<path d="M4 5h16l-6 7v6l-4 2v-8Z"/>',
  project: '<rect x="3" y="6" width="18" height="15" rx="2"/><path d="M8 6V3h8v3m-13 7h18m-11 0v3h4v-3"/>',
  warning: '<path d="m12 3 10 18H2Z"/><path d="M12 9v5m0 3v.1"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 6 9 7 9-7"/>',
  overview: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  resource: '<path d="m12 3 9 5-9 5-9-5Zm-9 9 9 5 9-5m-18 5 9 5 9-5"/>',
  history: '<path d="M3 11a9 9 0 1 1 2.7 7M3 4v7h7m2-5v6l4 2"/>',
  button: '<rect x="3" y="6" width="18" height="12" rx="3"/><path d="M8 12h8"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
};
const icon = (name, extra = '') => '<svg class="icon ' + extra + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (paths[name] || paths.info) + '</svg>';
const h = value => String(value ?? '').replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character]));
const logo = () => '<div class="brand"><div class="logo-mark" aria-hidden="true"><i></i><i></i><i></i><i></i></div><div><strong>市政权限</strong><small>MUNICIPAL ACCESS</small></div></div>';
const avatar = (person, large = false) => '<span class="avatar ' + ['blue', 'purple', 'teal', 'sand'][person.id % 4] + (large ? ' large' : '') + '" aria-hidden="true">' + h(person.name.slice(-2)) + '</span>';
const status = person => '<span class="status ' + (person.enabled ? 'on' : 'off') + '">' + (person.enabled ? '启用' : '已停用') + '</span>';
const busy = () => '<span class="spinner" aria-hidden="true"></span>';
const errorBox = (error, extra = '') => !error ? '' : '<div class="error-box ' + extra + '" role="alert"><strong>' + h(error.message) + '</strong><div class="error-id">' + h(error.code) + ' · 请求标识 ' + h(error.requestId) + '</div></div>';
const state = () => ({
  session: null, account: '', password: '', showPassword: false, loginBusy: false, loginError: null,
  tree: [], expanded: new Set([1, 3, 10, 12, 20, 22]), treeSearch: '', departmentId: null,
  qDraft: '', q: '', enabled: '', page: 0, size: 10, data: null, listBusy: false, pageError: null,
  modal: null, confirm: null, review: false, reviewError: null, menu: null,
  nextFailure: '', failureTarget: 'write', slow: false, writeBusy: false, epoch: 0, listSequence: 0,
  route: 'overview', views: {},
});
let model = createDemoModel();
let ui = state();
let toastTimer;
let focusReturn = null;
const app = document.querySelector('#app');
const overlays = document.querySelector('#overlays');
const screens = globalThis.MunicipalScreens.createScreens({
  state: () => ui, model: () => model, h, icon, busy, errorBox, pagination,
  render, renderOverlays, request, notify, handleSessionError, writeOperation, navigate,
});

/** 只演示请求延迟及错误，不连接服务端；身份变化会丢弃旧请求。 */
async function request(operation, { write = false } = {}) {
  const epoch = ui.epoch;
  const failure = ui.failureTarget === 'any' || write ? ui.nextFailure : '';
  if (failure) ui.nextFailure = '';
  await new Promise(resolve => setTimeout(resolve, ui.slow ? 1600 : 320));
  if (epoch !== ui.epoch) throw new DOMException('旧身份响应已丢弃', 'AbortError');
  if (failure) {
    const failures = {
      network: [0, 'NETWORK_ERROR', '网络连接暂时不可用，请重试'],
      forbidden: [403, 'PERMISSION_DENIED', '当前操作未获授权，请重新核对权限'],
      conflict: [409, 'DATA_CONFLICT', '数据已发生变化，本次修改未保存，请重新核对'],
      missing: [404, 'RESOURCE_NOT_FOUND', '该人员已不存在，请关闭后刷新列表'],
      server: [500, 'INTERNAL_ERROR', '服务暂时不可用，请稍后重试'],
      session: [401, 'SESSION_INVALID', '登录身份已失效，请重新登录'],
    };
    throw new DemoError(...failures[failure]);
  }
  return operation();
}
const aborted = error => error.name === 'AbortError';
function handleSessionError(error) {
  if (error.status !== 401 || !ui.session) return false;
  signOut();
  ui.loginError = error;
  render();
  return true;
}
function notify(message) {
  clearTimeout(toastTimer);
  document.querySelector('#toasts').innerHTML = '<div class="toast">' + icon('check') + '<span>' + h(message) + '</span></div>';
  toastTimer = setTimeout(() => { document.querySelector('#toasts').textContent = ''; }, 3800);
}
function saveFocus() {
  const element = document.activeElement;
  return element?.id || element?.dataset?.act ? {
    id: element.id, action: element.dataset.act, item: element.dataset.id, page: element.dataset.page,
    start: element.selectionStart, end: element.selectionEnd,
  } : null;
}
function restoreFocus(saved) {
  if (!saved) return;
  const element = saved.id ? document.getElementById(saved.id) : [...document.querySelectorAll('[data-act]')].find(candidate =>
    !candidate.disabled && candidate.dataset.act === saved.action && candidate.dataset.id === saved.item && candidate.dataset.page === saved.page);
  if (!element || element.disabled) return;
  element.focus({ preventScroll: true });
  if (typeof element.setSelectionRange === 'function' && saved.start !== null) {
    try { element.setSelectionRange(saved.start, saved.end); } catch { /* select不支持文本选区。 */ }
  }
}
function restoreOriginFocus() {
  if (typeof focusReturn === 'string') document.getElementById(focusReturn)?.focus({ preventScroll: true });
  else restoreFocus(focusReturn);
}
function visiblePath(id) {
  const names = [];
  for (let current = ui.tree.find(item => item.id === id); current; current = ui.tree.find(item => item.id === current.parentId)) names.unshift(current.name);
  return names;
}
const scopeName = () => model.department(ui.session.departmentId)?.name || '当前部门';
function canWrite(person, action) {
  return model.canManageUser(person.id, action);
}
function roleTags(roles = []) {
  return roles.length ? roles.slice(0, 2).map(role => '<span class="badge ' + (role.enabled ? 'blue' : 'gray') + '" title="' + h(role.code) + '">' + h(role.name) + (!role.enabled ? ' · 角色已停用' : '') + '</span>').join('')
    + (roles.length > 2 ? '<span class="badge gray" title="' + h(roles.slice(2).map(role => role.name).join('、')) + '">+' + (roles.length - 2) + '</span>' : '')
    : '<span class="muted">未分配角色</span>';
}
function render() {
  const saved = saveFocus();
  app.innerHTML = ui.session ? workspace() : loginPage();
  restoreFocus(saved);
  renderOverlays();
}

function illustration() {
  return '<svg class="story-illustration" viewBox="0 0 480 260" fill="none" aria-hidden="true"><ellipse cx="249" cy="225" rx="193" ry="24" fill="#e5edfb"/><path d="m71 199 132-66 202 67-133 46Z" fill="#eaf0fc" stroke="#d8e4f7"/><path d="M112 117 173 85l53 27v98l-61 31-53-26Z" fill="#fff" stroke="#ccdcf5"/><path d="m173 85 53 27v98l-53 27Z" fill="#e1ebfc"/><path d="M112 117 173 147l53-35m-53 35v90" stroke="#ccdcf5"/><path d="M240 65 298 34l69 35v133l-59 32-68-35Z" fill="#fff" stroke="#cbdcf6"/><path d="m298 34 69 35v133l-59 32-10-133Z" fill="#d4e3fc"/><path d="m240 65 68 35 59-31m-59 31v134" stroke="#bdd2f4"/><path d="M251 89v13l12 6V95m-12 23v13l12 6v-13m-12 23v13l12 6v-13m-12 23v13l12 6v-13m14-90v13l12 6V108m-12 23v13l12 6v-13m-12 23v13l12 6v-13m-12 23v13l12 6v-13m-145-50v12l11 6v-12m-11 22v12l11 6v-12m-11 22v12l11 6v-12" fill="#bfd4f5"/><path d="m322 116 12-6m-12 28 12-6m-12 28 12-6m-12 28 12-6m-132-29 12-6m-12 28 12-6m-12 28 12-6" stroke="#a8c6f4" stroke-width="4"/><path d="M79 145v-25a13 13 0 0 1 7-12l39-20m240 64 35 17a15 15 0 0 1 8 13v19M211 66l16-8a14 14 0 0 1 12 0" stroke="#adc8f5" stroke-dasharray="4 5"/><rect x="53" y="135" width="51" height="51" rx="14" fill="white" stroke="#d8e5fa"/><path d="m79 147 12 5v8c0 7-12 14-12 14s-12-7-12-14v-8Z" fill="#eff5ff" stroke="#8db1ef"/><path d="m73 159 4 4 8-9" stroke="#5a8ee0" stroke-width="2"/><rect x="376" y="191" width="55" height="40" rx="10" fill="white" stroke="#d8e5fa"/><circle cx="393" cy="205" r="4" fill="#c7d9f7"/><path d="M386 219v-3a7 7 0 0 1 14 0v3m8-14h13m-13 8h9" stroke="#9ebce8" stroke-width="2"/><circle cx="227" cy="56" r="4" fill="#80a8ee"/><circle cx="407" cy="176" r="3" fill="#98b7ee"/><circle cx="79" cy="129" r="3" fill="#80a8ee"/></svg>';
}
function loginPage() {
  return `<main class="login-page">
    <section class="login-story">${logo()}<div class="story-copy"><span class="eyebrow">Connected people. Clear access.</span><h1>管理有界，<br>协作有序。</h1><p>让组织、人员与权限各归其位，<br>为每一次协作建立清晰的边界。</p></div>${illustration()}
      <div class="story-chips"><span>${icon('building')}统一组织</span><span>${icon('users')}高效协作</span><span>${icon('shield')}权限可控</span></div>
    </section>
    <section class="login-main"><span class="prototype-tag">交互原型 · 第六轮</span><form class="login-form" id="login-form" novalidate aria-busy="${ui.loginBusy}">
      <span class="eyebrow">Municipal workspace</span><h2>欢迎登录</h2><p>登录市政公司统一权限管理系统</p>
      <div class="field"><label for="login-account">工号或手机号</label><div class="input-shell">${icon('user')}<input id="login-account" name="account" type="text" autocomplete="username" maxlength="100" placeholder="请输入工号或手机号" value="${h(ui.account)}" aria-invalid="${!!ui.loginError}" ${ui.loginBusy ? 'disabled' : ''}></div></div>
      <div class="field"><label for="login-password">登录密码</label><div class="input-shell">${icon('lock')}<input id="login-password" name="password" type="${ui.showPassword ? 'text' : 'password'}" autocomplete="current-password" maxlength="200" placeholder="请输入密码" value="${h(ui.password)}" ${ui.loginBusy ? 'disabled' : ''}><button class="icon-button" type="button" data-act="password" aria-label="${ui.showPassword ? '隐藏密码' : '显示密码'}">${icon(ui.showPassword ? 'eyeOff' : 'eye')}</button></div></div>
      ${errorBox(ui.loginError)}
      <button type="submit" class="btn primary login-submit" ${ui.loginBusy ? 'disabled' : ''}>${ui.loginBusy ? busy() + '正在登录' : '登 录' + icon('arrow')}</button>
      <div class="login-note">${icon('shield')}仅向已获管理权限的账号开放</div>
      <details class="demo-login" open><summary>试用演示账号 <span>选择身份，自动填入 ${icon('down')}</span></summary>
        <div class="account-grid">${DEMOS.map(demo => '<button class="btn small" type="button" data-act="fill-demo" data-id="' + demo.id + '" ' + (ui.loginBusy ? 'disabled' : '') + '>' + h(demo.label) + '</button>').join('')}</div>
        <p>演示口令：<span class="mono">${DEMO_PASSWORD}</span> · 公司领导也可使用 <span class="mono">13800000001</span> 登录。<br>全部为虚构数据，刷新即可还原；不会访问真实账号。</p>
      </details>
    </form><div class="login-footer">市政公司 · 统一权限管理系统</div></section>
  </main>`;
}
function workspace() {
  const navigation = screens.navigation();
  const current = navigation.find(item => item.page === ui.route) || { group: '工作台', title: '访问反馈' };
  let group = '';
  const navMarkup = navigation.map(item => {
    const label = group !== item.group ? '<div class="nav-label">' + h(item.group) + '</div>' : '';
    group = item.group;
    return label + '<button id="nav-' + item.page + '" class="nav-item ' + (ui.route === item.page ? 'active' : '') + '" data-act="route" data-page="' + item.page + '" title="' + item.title + '" ' + (ui.route === item.page ? 'aria-current="page"' : '') + '>' + icon(item.icon) + '<span>' + item.title + '</span>' + (ui.route === item.page ? '<i class="nav-dot"></i>' : '') + '</button>';
  }).join('');
  return `<div class="workspace">
    <aside class="sidebar">${logo()}<nav aria-label="主导航">${navMarkup}</nav>
      <div class="scope-card">${icon('shield')}<div class="text-small muted">当前管理范围</div><strong>${h(scopeName())}及下级</strong><p>操作权限与组织范围<br>共同限定可管理的人员。</p></div><div class="sidebar-foot">${icon('lock')}统一管理 · 安全有序</div>
    </aside>
    <div class="workspace-main"><header class="topbar"><div class="breadcrumb"><span>${h(current.group)}</span>${icon('chevron')}<strong>${h(current.title)}</strong></div><select id="mobile-navigation" class="mobile-page-select" aria-label="切换工作区">${navigation.map(item => '<option value="' + item.page + '" ' + (ui.route === item.page ? 'selected' : '') + '>' + item.title + '</option>').join('')}</select>
      <div class="topbar-right"><button class="review-entry" data-act="review">原型评审</button><div class="identity"><button id="identity-trigger" data-act="identity" aria-haspopup="menu" aria-expanded="${ui.menu === 'identity'}">${avatar(ui.session)}<span><span class="identity-name">${h(ui.session.name)}</span><span class="identity-role">${h(ui.session.username)}</span></span>${icon('down')}</button></div></div>
    </header>
    <main class="page-content" aria-busy="${ui.route === 'people' ? ui.listBusy : !!ui.views[ui.route]?.busy}">${ui.route === 'people' ? peopleContent() : screens.page(ui.route)}<footer class="page-foot"><span>市政公司统一权限管理系统</span><span>第六轮原型 · 虚构模拟数据 · 可离线运行</span></footer></main></div>
  </div>`;
}
function peopleContent() {
  const root = model.department(ui.departmentId);
  return `<div class="page-heading"><div><h1>人员管理</h1><p>查看人员职位与已授角色，按组织范围调整授权。</p></div><div class="page-actions"><span class="permission-note">${icon('shield')}范围：${h(scopeName())}及下级</span><button class="btn" data-act="refresh" ${ui.listBusy ? 'disabled' : ''}>${ui.listBusy ? busy() : icon('refresh')}刷新列表</button></div></div>
      ${errorBox(ui.pageError, 'page-error')}
      <div class="people-layout"><section class="panel departments-panel" aria-label="部门组织树"><div class="panel-heading"><h2>组织架构</h2><span class="count">${ui.tree.length}</span></div>
        <div class="input-shell tree-search">${icon('search')}<input id="tree-search" aria-label="搜索可见部门" placeholder="搜索部门" value="${h(ui.treeSearch)}"></div><div class="tree-caption">选择部门，包含其下级人员</div>
        <div class="tree" role="group" aria-label="可管理部门">${treeMarkup()}</div><div class="tree-bottom">${icon('info')}仅展示你可管理的组织范围</div>
      </section>
      <section class="panel personnel-panel" aria-label="人员列表" aria-busy="${ui.listBusy}">
        <div class="table-heading"><h2>${h(root?.name || scopeName())}</h2><span class="badge">含下级部门</span></div>
        <form class="filter-row" id="people-filter"><div class="input-shell">${icon('search')}<input id="people-search" name="q" placeholder="搜索姓名或工号" aria-label="搜索姓名或工号" maxlength="100" value="${h(ui.qDraft)}"></div>
          <select id="people-status" aria-label="账号状态"><option value="" ${ui.enabled === '' ? 'selected' : ''}>全部状态</option><option value="true" ${ui.enabled === 'true' ? 'selected' : ''}>启用</option><option value="false" ${ui.enabled === 'false' ? 'selected' : ''}>已停用</option></select>
          <button class="btn primary" type="submit">查询</button><button class="btn quiet" type="button" data-act="reset-filter">重置</button>
        </form>
        <div class="table-wrap">${tableMarkup()}</div>
        <div class="pagination"><span>共 <strong>${ui.data?.total ?? '—'}</strong> 名人员${ui.q ? ' · 当前搜索“' + h(ui.q) + '”' : ''}</span>${pagination(ui.data?.total ?? 0, ui.page, ui.size, 'people-page', true)}</div>
      </section></div>
      <div class="personnel-footnote">${icon('info')}账号状态与角色状态独立；停用角色保留关联但不贡献权限。职位只读，人员写受级别、权限子集及范围限制。</div>`;
}
function treeMarkup() {
  if (!ui.tree.length) return '<div class="empty-state"><p>' + (ui.listBusy ? '正在加载组织' : '暂无可用组织') + '</p></div>';
  const search = ui.treeSearch.trim().toLowerCase();
  const matches = new Set(ui.tree.filter(item => item.name.toLowerCase().includes(search)).map(item => item.id));
  const visible = new Set(matches);
  for (const id of matches) {
    for (let item = ui.tree.find(node => node.id === id); item?.parentId; item = ui.tree.find(node => node.id === item.parentId)) visible.add(item.parentId);
  }
  function nodeMarkup(item, level) {
    if (search && !visible.has(item.id)) return '';
    const children = ui.tree.filter(node => node.parentId === item.id);
    const expanded = search || ui.expanded.has(item.id);
    return '<div class="tree-node ' + (ui.departmentId === item.id ? 'selected' : '') + '" style="padding-left:' + (level * 14 + 3) + 'px">'
      + (children.length ? '<button class="tree-toggle" data-act="tree-toggle" data-id="' + item.id + '" aria-label="' + (expanded ? '折叠' : '展开') + h(item.name) + '" aria-expanded="' + !!expanded + '">' + icon(expanded ? 'down' : 'chevron') + '</button>' : '<span class="tree-toggle"></span>')
      + '<button id="department-' + item.id + '" class="tree-select" data-act="department" data-id="' + item.id + '" ' + (ui.departmentId === item.id ? 'aria-current="true"' : '') + '>' + icon(item.type === '公司' || item.type === '分公司' ? 'building' : item.type === '项目' ? 'project' : 'folder') + '<span>' + h(item.name) + '</span></button></div>'
      + (expanded ? children.map(child => nodeMarkup(child, level + 1)).join('') : '');
  }
  if (search && !matches.size) return '<div class="empty-state"><p>未找到匹配的部门</p></div>';
  return ui.tree.filter(item => item.parentId === null).map(item => nodeMarkup(item, 0)).join('');
}
function tableMarkup() {
  if (ui.listBusy) return Array.from({ length: 7 }, () => '<div class="skeleton-row"><span class="skeleton avatar"></span><span class="skeleton"></span><span class="skeleton"></span><span class="skeleton"></span><span class="skeleton"></span></div>').join('');
  if (!ui.data) return '<div class="empty-state"><div class="empty-icon">' + icon('shield') + '</div><h3>暂时无法加载人员</h3><p>请核对当前权限或重试</p><button class="btn small" data-act="refresh">重新加载</button></div>';
  if (!ui.data.items.length) return '<div class="empty-state"><div class="empty-icon">' + icon('search') + '</div><h3>' + (ui.page > 0 ? '这一页暂无人员' : '没有符合条件的人员') + '</h3><p>' + (ui.page > 0 ? '人员数据可能已变化，可返回第一页查看。' : '试试其他关键词、状态或部门。') + '</p><button class="btn small" data-act="' + (ui.page > 0 ? 'first-page' : 'reset-filter') + '">' + (ui.page > 0 ? '返回第一页' : '清空筛选') + '</button></div>';
  return '<table><thead><tr><th scope="col">人员</th><th scope="col">工号</th><th scope="col">所属部门</th><th scope="col">职位</th><th scope="col">已授角色</th><th scope="col">账号状态</th><th scope="col">操作</th></tr></thead><tbody>' + ui.data.items.map(person => {
    const path = visiblePath(person.departmentId);
    if (!path.length) path.push(person.departmentName || '当前部门');
    const assignable = canWrite(person, 'assign') && model.can('currentRoles') && model.can('candidates');
    return '<tr><td><button class="person-button" id="person-' + person.id + '" data-act="person" data-id="' + person.id + '" aria-label="查看' + h(person.name) + '的人员信息">' + avatar(person) + '<strong>' + h(person.name) + (person.id === ui.session.id ? '<span class="self-label">本人</span>' : '') + '</strong></button></td>'
      + '<td><span class="mono muted">' + h(person.username) + '</span></td><td><span class="department-cell"><span>' + h(path.at(-1)) + '</span>' + (path.length > 1 ? '<small>' + h(path.slice(0, -1).join(' / ')) + '</small>' : '') + '</span></td>'
      + '<td class="position-cell">' + h(person.position || '未提供') + '</td><td><div class="role-tags">' + roleTags(person.roles) + '</div></td><td>' + status(person) + '</td><td><div class="row-actions"><button class="link-button" id="assign-' + person.id + '" data-act="roles" data-id="' + person.id + '" ' + (!assignable ? 'disabled title="本人、同级、上级或当前权限不允许此操作"' : '') + '>分配角色</button><button class="icon-button" id="more-' + person.id + '" data-act="more" data-id="' + person.id + '" aria-label="' + h(person.name) + '的更多操作" aria-haspopup="menu">' + icon('more') + '</button></div></td></tr>';
  }).join('') + '</tbody></table>';
}
function pagination(total, page, size, action, sizeSelector = false) {
  const pages = Math.max(1, Math.ceil(total / size));
  const visible = [...new Set([0, Math.max(0, page - 1), page, Math.min(pages - 1, page + 1), pages - 1])].sort((a, b) => a - b);
  let previous = -1;
  return '<div class="page-buttons" aria-label="分页"><button data-act="' + action + '" data-page="' + Math.max(0, page - 1) + '" aria-label="上一页" ' + (page <= 0 || ui.listBusy ? 'disabled' : '') + '>' + icon('left') + '</button>' + visible.map(number => {
    const markup = (number - previous > 1 ? '<span>…</span>' : '') + '<button data-act="' + action + '" data-page="' + number + '" class="' + (page === number ? 'active' : '') + '" aria-label="第' + (number + 1) + '页" ' + (page === number ? 'aria-current="page"' : '') + '>' + (number + 1) + '</button>';
    previous = number;
    return markup;
  }).join('') + '<button data-act="' + action + '" data-page="' + (page + 1) + '" aria-label="下一页" ' + (page + 1 >= pages || ui.listBusy ? 'disabled' : '') + '>' + icon('chevron') + '</button>'
    + (sizeSelector ? '<select id="page-size" aria-label="每页人数">' + [10, 20, 50].map(value => '<option value="' + value + '" ' + (value === size ? 'selected' : '') + '>' + value + '条/页</option>').join('') + '</select>' : '') + '</div>';
}
async function loadPeople() {
  const sequence = ++ui.listSequence;
  const epoch = ui.epoch;
  ui.listBusy = true;
  ui.pageError = null;
  ui.menu = null;
  render();
  const query = { departmentId: ui.departmentId, q: ui.q, enabled: ui.enabled, page: ui.page, size: ui.size };
  try {
    const result = await request(() => {
      const session = model.me();
      const tree = model.can('tree') ? model.tree() : [];
      const departmentId = tree.some(item => item.id === query.departmentId) ? query.departmentId : session.departmentId;
      const page = departmentId === query.departmentId ? query.page : 0;
      return { tree, session, departmentId, data: model.list({ ...query, departmentId, page }) };
    });
    if (sequence !== ui.listSequence || epoch !== ui.epoch) return;
    ui.tree = result.tree;
    ui.data = result.data;
    ui.session = result.session;
    ui.departmentId = result.departmentId;
    ui.page = result.data.page;
  } catch (error) {
    if (aborted(error) || sequence !== ui.listSequence || epoch !== ui.epoch || handleSessionError(error)) return;
    ui.pageError = error;
    ui.data = null;
    if (error.status === 403) {
      ui.session = model.me();
      ui.tree = [];
    }
  } finally {
    if (sequence === ui.listSequence && epoch === ui.epoch) { ui.listBusy = false; render(); }
  }
}
function navigate(route, { departmentId } = {}) {
  if (!['overview', 'people', 'departments', 'roles', 'resources', 'logs'].includes(route)) return;
  ui.route = route;
  ui.menu = null;
  ui.modal = null;
  ui.confirm = null;
  if (departmentId) { ui.departmentId = departmentId; ui.page = 0; ui.q = ''; ui.qDraft = ''; ui.enabled = ''; }
  window.scrollTo(0, 0);
  if (route === 'people') loadPeople(); else screens.load(route);
}

function targetCard(person) {
  return '<div class="target-card">' + avatar(person, true) + '<div class="grow"><strong>' + h(person.name) + '</strong><span class="mono muted" style="margin-left:10px">' + h(person.username) + '</span><p>' + h(visiblePath(person.departmentId).join(' / ')) + '</p></div>' + status(person) + '</div>';
}
const draftChanged = modal => (modal?.kind === 'roles' && !modal.loading && (modal.savedIds.length !== modal.draft.size || modal.savedIds.some(id => !modal.draft.has(id)))) || screens.dirty(modal);
function roleDrawer(modal) {
  const selected = [...modal.draft];
  const inactive = selected.filter(id => modal.roleMap.get(id)?.enabled === false);
  return `<dialog class="drawer" id="main-dialog" aria-labelledby="roles-title"><div class="drawer-shell">
    <header class="drawer-header"><div><h2 id="roles-title">分配角色</h2><p>${h(modal.person.name)} · <span class="mono">${h(modal.person.username)}</span></p></div><button class="icon-button" data-act="close-modal" aria-label="关闭角色分配" ${ui.writeBusy ? 'disabled' : ''}>${icon('close')}</button></header>
    <div class="drawer-body">${targetCard(modal.person)}${errorBox(modal.error)}
    ${modal.loading ? '<div class="empty-state">' + busy() + '<p>正在读取当前角色</p></div>' : `
      <div class="section-title"><h3>已选角色 <span class="muted">${selected.length}</span></h3><button class="link-button text-small" data-act="clear-roles" ${!selected.length || ui.writeBusy ? 'disabled' : ''}>清空选择</button></div>
      <div class="selected-tags">${selected.length ? selected.map(id => {
        const role = modal.roleMap.get(id);
        return '<span class="badge ' + (role?.enabled === false ? 'amber' : 'blue') + '">' + h(role?.name || '角色 ' + id) + (role?.enabled === false ? ' · 角色已停用' : '') + '<button id="remove-role-' + id + '" data-act="remove-role" data-id="' + id + '" aria-label="移除' + h(role?.name) + '" ' + (ui.writeBusy ? 'disabled' : '') + '>' + icon('close') + '</button></span>';
      }).join('') : '<span class="hint">尚未选择角色</span>'}</div>
      ${inactive.length ? '<p class="hint warning">' + icon('info') + '<span>当前保留了已停用角色。它不会出现在候选中，提交前需明确移除；系统不会替你静默删除。</span></p>' : '<p class="hint">跨页勾选会保留。点击“保存更改”后，才替换该人员的角色。</p>'}
      <div class="divider"></div><div class="section-title"><h3>可分配角色</h3><span class="hint">同时满足授予级别与权限子集</span></div>
      <form class="drawer-search" id="role-filter"><div class="input-shell">${icon('search')}<input id="role-search" aria-label="搜索可分配角色" placeholder="搜索角色名称或编码" maxlength="100" value="${h(modal.qDraft)}"></div><button type="submit" class="btn">查询</button></form>
      ${modal.candidateBusy ? '<div class="empty-state">' + busy() + '<p>正在读取候选角色</p></div>' : modal.candidates ? '<div class="candidate-list">' + (modal.candidates.items.length ? modal.candidates.items.map(role => '<label class="candidate ' + (modal.draft.has(role.id) ? 'checked' : '') + '"><input type="checkbox" id="candidate-' + role.id + '" data-role-id="' + role.id + '" ' + (modal.draft.has(role.id) ? 'checked ' : '') + (ui.writeBusy ? 'disabled ' : '') + '><div><strong>' + h(role.name) + '<span class="mono">' + h(role.code) + '</span></strong><p>' + h(role.description) + '</p></div></label>').join('') : '<div class="empty-state"><h3>没有匹配的候选角色</h3><p>请调整搜索条件</p></div>') + '</div><div class="candidate-footer"><span>共' + modal.candidates.total + '个可分配角色</span>' + pagination(modal.candidates.total, modal.page, 6, 'role-page') + '</div>' : '<button class="btn" data-act="retry-candidates">重新加载候选</button>'}
    `}
    </div><footer class="drawer-footer"><p class="hint">${draftChanged(modal) ? '<span style="color:#b08b43">有未保存的修改</span>' : '取消不会修改已保存角色'}</p><div class="row"><button class="btn" data-act="close-modal" ${ui.writeBusy ? 'disabled' : ''}>取消</button><button class="btn primary" data-act="save-roles" ${modal.loading || modal.candidateBusy || !draftChanged(modal) || ui.writeBusy ? 'disabled' : ''}>${ui.writeBusy ? busy() + '保存中' : '保存更改'}</button></div></footer>
  </div></dialog>`;
}
function moveDialog(modal) {
  return `<dialog class="modal" id="main-dialog" aria-labelledby="move-title"><header class="modal-header"><h2 id="move-title">调动部门</h2><button class="icon-button" data-act="close-modal" aria-label="关闭调动部门" ${ui.writeBusy ? 'disabled' : ''}>${icon('close')}</button></header>
    <div class="modal-body"><p>调动保留账号及已授角色，后续管理范围按新部门计算。</p>${targetCard(modal.person)}<div class="field"><label for="move-department">目标部门</label><select id="move-department" ${ui.writeBusy ? 'disabled' : ''}><option value="">请选择目标部门</option>${ui.tree.map(item => '<option value="' + item.id + '" ' + (item.id === modal.person.departmentId ? 'disabled ' : '') + (Number(modal.destination) === item.id ? 'selected ' : '') + '>' + h(visiblePath(item.id).join(' / ')) + (item.id === modal.person.departmentId ? '（当前）' : '') + '</option>').join('')}</select></div>
      <div class="move-arrow">${icon('info')}仅能调入你当前管理范围内的部门</div>${errorBox(modal.error)}</div>
    <footer class="modal-footer"><button class="btn" data-act="close-modal" ${ui.writeBusy ? 'disabled' : ''}>取消</button><button class="btn primary" data-act="save-move" ${!modal.destination || ui.writeBusy ? 'disabled' : ''}>${ui.writeBusy ? busy() + '提交中' : '确认调动'}</button></footer></dialog>`;
}
function detailsDialog(modal) {
  return `<dialog class="modal" id="main-dialog" aria-labelledby="details-title"><header class="modal-header"><h2 id="details-title">人员信息</h2><button class="icon-button" data-act="close-modal" aria-label="关闭人员信息">${icon('close')}</button></header><div class="modal-body">${targetCard(modal.person)}<dl class="detail-list"><dt>工号</dt><dd class="mono">${h(modal.person.username)}</dd><dt>所属部门</dt><dd>${h(visiblePath(modal.person.departmentId).join(' / '))}</dd><dt>账号状态</dt><dd>${status(modal.person)}</dd><dt>当前角色</dt><dd><div class="selected-tags">${modal.loading ? '<span class="hint">' + busy() + ' 正在读取角色</span>' : modal.person.roles ? modal.person.roles.map(role => '<span class="badge ' + (role.enabled ? 'blue' : 'gray') + '">' + h(role.name) + (!role.enabled ? ' · 角色已停用' : '') + '</span>').join('') || '<span class="muted">未分配角色</span>' : '<span class="muted">暂时无法读取角色</span>'}</div></dd></dl>${errorBox(modal.error)}<p class="hint">人员基本信息只读，角色及部门调整请使用列表中的对应操作。</p></div><footer class="modal-footer"><button class="btn primary" data-act="close-modal">知道了</button></footer></dialog>`;
}
function confirmation() {
  const custom = screens.confirmation(ui.confirm);
  if (custom) return custom;
  const confirm = ui.confirm;
  const copy = {
    disable: ['停用这个账号？', '停用后，该人员将无法登录，已有登录身份也将失效。所属部门和已授角色会保留。', '确认停用'],
    empty: ['清空该人员的全部角色？', '保存后将撤销其所有角色。若已无有效管理权限，后续管理请求会被拒绝。', '确认清空并保存'],
    discard: ['放弃未保存的修改？', '已勾选的角色尚未保存。离开后，这次选择将被丢弃。', '放弃修改'],
    reset: ['重置全部演示数据？', '本轮原型中的角色、部门与停用调整会还原，并返回登录页。', '重置数据'],
    logout: ['退出并放弃修改？', '当前仍有未保存的角色选择，退出后不会保存。', '确认退出'],
    move: ['确认调动部门？', '该人员的账号和角色保持不变，后续按目标部门计算管理范围。', '确认调动'],
    clearDraft: ['清空当前选择？', '这只会清空编辑草稿，仍需点击“保存更改”才会生效。', '清空选择'],
  }[confirm.kind];
  return `<dialog class="modal" id="confirm-dialog" aria-labelledby="confirm-title"><header class="modal-header"><h2 id="confirm-title">${h(copy[0])}</h2><button class="icon-button" data-act="cancel-confirm" aria-label="取消确认" ${ui.writeBusy ? 'disabled' : ''}>${icon('close')}</button></header><div class="modal-body"><div class="confirm-symbol">${icon(confirm.kind === 'move' ? 'move' : 'warning')}</div>${confirm.person ? targetCard(confirm.person) : ''}<p>${h(copy[1])}</p>${confirm.kind === 'move' ? '<p class="hint"><strong>目标部门：</strong>' + h(visiblePath(Number(ui.modal.destination)).join(' / ')) + '</p>' : ''}${errorBox(confirm.error)}</div><footer class="modal-footer"><button class="btn" id="confirm-cancel" data-act="cancel-confirm" ${ui.writeBusy ? 'disabled' : ''}>${confirm.kind === 'discard' ? '继续编辑' : '取消'}</button><button class="btn ${['disable', 'empty', 'reset'].includes(confirm.kind) ? 'danger' : 'primary'}" data-act="confirm" ${ui.writeBusy ? 'disabled' : ''}>${ui.writeBusy ? busy() + '处理中' : h(copy[2])}</button></footer></dialog>`;
}
function reviewDialog() {
  return `<dialog class="modal review-modal" id="review-dialog" aria-labelledby="review-title"><header class="modal-header"><h2 id="review-title">原型评审工具</h2><button class="icon-button" data-act="close-review" aria-label="关闭评审工具">${icon('close')}</button></header><div class="modal-body">
    <div class="review-info">${icon('info')}<span>第六轮评审：部门增删改名、同级人员只读、跨系统资源与原表矩阵。168名虚构人员、15个初始部门；SG000001与SG000008为同分公司领导，可核对同级保护。</span></div>
    ${errorBox(ui.reviewError)}<div class="section-title"><h3>切换演示身份</h3><span class="hint">保留本次模拟数据</span></div>
    <div class="review-accounts">${DEMOS.map(demo => '<button class="review-account ' + (demo.id === ui.session.id ? 'current' : '') + '" data-act="switch-demo" data-id="' + demo.id + '"><strong>' + h(demo.label) + (demo.id === ui.session.id ? ' · 当前' : '') + '</strong><span>' + h(demo.note) + '</span></button>').join('')}</div>
    <div class="review-setting"><div><strong>下一次操作反馈</strong><p>失败不写入数据，草稿会保留。</p></div><select id="review-failure" aria-label="下一次模拟请求反馈">${[['', '正常响应'], ['network', '网络故障'], ['forbidden', '403 无权限'], ['missing', '404 不存在'], ['conflict', '409 冲突'], ['server', '500 服务故障'], ['session', '401 身份失效']].map(([value, label]) => '<option value="' + value + '" ' + (ui.nextFailure === value ? 'selected' : '') + '>' + label + '</option>').join('')}</select></div>
    <div class="review-setting"><div><strong>反馈触发时机</strong><p>选择“下一次保存”可检查写入失败后的草稿。</p></div><select id="review-failure-target" aria-label="模拟反馈触发时机"><option value="write" ${ui.failureTarget === 'write' ? 'selected' : ''}>下一次保存</option><option value="any" ${ui.failureTarget === 'any' ? 'selected' : ''}>下一次任意请求</option></select></div>
    <label class="review-setting"><div><strong>慢速加载</strong><p>观察加载状态与重复提交保护。</p></div><input type="checkbox" id="review-slow" ${ui.slow ? 'checked' : ''}></label>
    <div class="review-setting"><div><strong>模拟当前账号被撤权</strong><p>撤销角色后保留登录身份，下一次管理请求会拒绝。最后一名完整维护者受保护。</p></div><button class="btn small" data-act="revoke">模拟撤权</button></div>
    <div class="review-setting"><div><strong>重新开始</strong><p>还原虚构数据，回到登录页。</p></div><button class="btn small" data-act="reset-demo">重置数据</button></div>
  </div><footer class="modal-footer"><button class="btn primary" data-act="close-review">返回原型</button></footer></dialog>`;
}
function renderOverlays() {
  const saved = saveFocus();
  const scroll = document.querySelector('.drawer-body')?.scrollTop || 0;
  document.documentElement.style.overflow = ui.modal || ui.review || ui.confirm ? 'hidden' : '';
  overlays.innerHTML = (ui.modal ? screens.modal(ui.modal) || (ui.modal.kind === 'roles' ? roleDrawer(ui.modal) : ui.modal.kind === 'move' ? moveDialog(ui.modal) : detailsDialog(ui.modal)) : '')
    + (ui.review ? reviewDialog() : '') + (ui.confirm ? confirmation() : '');
  if (ui.writeBusy) overlays.querySelectorAll('input,select,textarea,button').forEach(element => { element.disabled = true; });
  overlays.querySelectorAll('dialog').forEach(dialog => {
    dialog.addEventListener('cancel', event => {
      event.preventDefault();
      if (ui.writeBusy) return;
      if (dialog.id === 'confirm-dialog') { ui.confirm = null; renderOverlays(); }
      else if (dialog.id === 'review-dialog') { ui.review = false; renderOverlays(); }
      else closeModal();
    });
    dialog.showModal();
  });
  if (ui.confirm && !saved) document.getElementById('confirm-cancel')?.focus();
  if (ui.menu && !ui.modal && !ui.review && !ui.confirm) renderMenu();
  const body = document.querySelector('.drawer-body');
  if (body) body.scrollTop = scroll;
  restoreFocus(saved);
}
function renderMenu() {
  const identity = ui.menu === 'identity';
  const person = identity ? null : ui.data?.items.find(item => item.id === ui.menu);
  const trigger = document.getElementById(identity ? 'identity-trigger' : 'more-' + ui.menu);
  if (!trigger || (!identity && !person)) return;
  const box = trigger.getBoundingClientRect();
  const menu = document.createElement('div');
  menu.className = 'popover';
  menu.setAttribute('role', 'menu');
  menu.setAttribute('aria-label', identity ? '账号操作' : person.name + '的更多操作');
  menu.style.right = Math.max(12, window.innerWidth - box.right) + 'px';
  menu.style.top = Math.min(box.bottom + 6, window.innerHeight - 160) + 'px';
  menu.innerHTML = identity ? '<button role="menuitem" data-act="logout">' + icon('logout') + '退出登录</button>'
    : '<button role="menuitem" data-act="person" data-id="' + person.id + '">' + icon('user') + '查看人员信息</button>'
    + '<button role="menuitem" data-act="move" data-id="' + person.id + '" ' + (!canWrite(person, 'move') ? 'disabled title="本人、同级、上级或当前权限不允许此操作"' : '') + '>' + icon('move') + '调动部门</button>'
    + '<button class="danger" role="menuitem" data-act="disable" data-id="' + person.id + '" ' + (!canWrite(person, 'disable') || !person.enabled ? 'disabled' : '') + '>' + icon('ban') + (person.enabled ? '停用账号' : '账号已停用') + '</button>';
  overlays.append(menu);
  menu.querySelector('button:not(:disabled)')?.focus();
}

async function submitLogin() {
  if (ui.loginBusy) return;
  if (!ui.account.trim() || !ui.password) {
    ui.loginError = new DemoError(400, 'INVALID_ARGUMENT', !ui.account.trim() ? '请输入工号或手机号' : '请输入登录密码');
    render();
    document.getElementById(!ui.account.trim() ? 'login-account' : 'login-password')?.focus();
    return;
  }
  ui.loginBusy = true;
  ui.loginError = null;
  render();
  try {
    const session = await request(() => model.login(ui.account, ui.password));
    enterSession(session);
    notify('登录成功，欢迎' + session.name);
  } catch (error) {
    if (!aborted(error)) { ui.loginError = error; ui.loginBusy = false; render(); }
  }
}
function enterSession(session) {
  const epoch = ui.epoch + 1;
  const slow = ui.slow;
  Object.assign(ui, state(), { session, departmentId: session.departmentId, epoch, slow });
  ui.password = '';
  window.scrollTo(0, 0);
  document.querySelector('#toasts').textContent = '';
  ui.route = screens.navigation()[0]?.page || 'overview';
  if (ui.route === 'people') loadPeople(); else screens.load(ui.route);
}
function signOut() {
  model.logout();
  const epoch = ui.epoch + 1;
  ui = { ...state(), epoch };
  window.scrollTo(0, 0);
  overlays.innerHTML = '';
  render();
  document.getElementById('login-account')?.focus();
}
async function switchDemo(id) {
  ui.reviewError = null;
  try {
    const demo = DEMOS.find(item => item.id === id);
    const session = model.login(demo.account, DEMO_PASSWORD);
    enterSession(session);
    notify('已切换为' + demo.label);
  } catch (error) { ui.reviewError = error; renderOverlays(); }
}
async function openRoles(person) {
  focusReturn = 'assign-' + person.id;
  ui.menu = null;
  const modal = { kind: 'roles', person, loading: true, error: null, savedIds: [], draft: new Set(), roleMap: new Map(), page: 0, q: '', qDraft: '', candidates: null, candidateBusy: false, sequence: 0 };
  ui.modal = modal;
  renderOverlays();
  try {
    const result = await request(() => ({ person: model.currentRoles(person.id), candidates: model.candidateRoles(person.id) }));
    if (ui.modal !== modal) return;
    modal.person = result.person;
    modal.savedIds = result.person.roles.map(role => role.id);
    modal.draft = new Set(modal.savedIds);
    [...result.person.roles, ...result.candidates.items].forEach(role => modal.roleMap.set(role.id, role));
    modal.candidates = result.candidates;
  } catch (error) {
    if (aborted(error) || ui.modal !== modal || handleSessionError(error)) return;
    modal.error = error;
  } finally {
    if (ui.modal === modal) { modal.loading = false; renderOverlays(); }
  }
}
async function openDetails(person) {
  focusReturn = 'person-' + person.id;
  const modal = { kind: 'details', person, loading: true, error: null };
  ui.modal = modal;
  renderOverlays();
  try {
    const result = await request(() => model.currentRoles(person.id));
    if (ui.modal === modal) modal.person = result;
  } catch (error) {
    if (aborted(error) || ui.modal !== modal || handleSessionError(error)) return;
    modal.error = error;
  } finally {
    if (ui.modal === modal) { modal.loading = false; renderOverlays(); }
  }
}
async function loadCandidates() {
  const modal = ui.modal;
  if (modal?.kind !== 'roles' || modal.loading) return;
  const sequence = ++modal.sequence;
  modal.candidateBusy = true;
  modal.error = null;
  renderOverlays();
  const query = { q: modal.q, page: modal.page };
  try {
    const result = await request(() => model.candidateRoles(modal.person.id, query));
    if (ui.modal !== modal || sequence !== modal.sequence) return;
    modal.candidates = result;
    result.items.forEach(role => modal.roleMap.set(role.id, role));
  } catch (error) {
    if (aborted(error) || ui.modal !== modal || sequence !== modal.sequence || handleSessionError(error)) return;
    modal.error = error;
    modal.candidates = null;
  } finally {
    if (ui.modal === modal && sequence === modal.sequence) { modal.candidateBusy = false; renderOverlays(); }
  }
}
function closeModal() {
  if (ui.writeBusy) return;
  if (draftChanged(ui.modal)) { ui.confirm = { kind: 'discard' }; renderOverlays(); return; }
  ui.modal = null;
  renderOverlays();
  restoreOriginFocus();
}
async function writeOperation(operation, successMessage, { confirmation = false, refresh = null } = {}) {
  if (ui.writeBusy) return;
  const modal = ui.modal;
  const confirm = ui.confirm;
  ui.writeBusy = true;
  if (confirmation && confirm) confirm.error = null;
  else if (modal) modal.error = null;
  renderOverlays();
  try {
    await request(operation, { write: true });
    ui.modal = null;
    ui.confirm = null;
    ui.writeBusy = false;
    notify(successMessage);
    await (refresh ? refresh() : loadPeople());
    restoreOriginFocus();
  } catch (error) {
    if (aborted(error) || handleSessionError(error)) return;
    if (confirmation && ui.confirm === confirm && confirm) confirm.error = error;
    else if (ui.modal === modal && modal) modal.error = error;
    else ui.pageError = error;
    ui.writeBusy = false;
    render();
  }
}
function saveRoles(confirmed = false) {
  const modal = ui.modal;
  if (modal?.kind !== 'roles' || modal.loading || !draftChanged(modal) || ui.writeBusy) return;
  if (modal.draft.size === 0 && !confirmed) { ui.confirm = { kind: 'empty', person: modal.person }; renderOverlays(); return; }
  const ids = [...modal.draft];
  writeOperation(() => model.assign(modal.person.id, ids), '角色已更新', { confirmation: confirmed });
}
function confirmAction() {
  if (ui.writeBusy || !ui.confirm) return;
  if (screens.confirmAction()) return;
  const confirm = ui.confirm;
  switch (confirm.kind) {
    case 'disable':
      writeOperation(() => model.disable(confirm.person.id), '账号已停用，部门与角色已保留', { confirmation: true });
      break;
    case 'empty': saveRoles(true); break;
    case 'move': {
      const modal = ui.modal;
      const destination = Number(modal.destination);
      writeOperation(() => model.move(modal.person.id, destination), '部门调动已保存，原角色保持不变', { confirmation: true });
      break;
    }
    case 'clearDraft': ui.modal.draft.clear(); ui.confirm = null; renderOverlays(); break;
    case 'discard': ui.modal = null; ui.confirm = null; renderOverlays(); restoreOriginFocus(); break;
    case 'logout': signOut(); break;
    case 'reset': {
      const epoch = ui.epoch + 1;
      model = createDemoModel();
      ui = { ...state(), epoch };
      render();
      notify('演示数据已重置');
      break;
    }
  }
}

document.addEventListener('submit', event => {
  if (['screen-filter', 'logs-filter', 'mapping-filter'].includes(event.target.id)) { event.preventDefault(); screens.submit(event.target.id); return; }
  if (!['login-form', 'people-filter', 'role-filter'].includes(event.target.id)) return;
  event.preventDefault();
  if (event.target.id === 'login-form') submitLogin();
  if (event.target.id === 'people-filter') { ui.q = ui.qDraft.trim(); ui.page = 0; loadPeople(); }
  if (event.target.id === 'role-filter' && ui.modal?.kind === 'roles') { ui.modal.q = ui.modal.qDraft.trim(); ui.modal.page = 0; loadCandidates(); }
});
document.addEventListener('input', event => {
  screens.input(event.target);
  const { id, value } = event.target;
  if (id === 'login-account') ui.account = value;
  if (id === 'login-password') ui.password = value;
  if (id === 'people-search') ui.qDraft = value;
  if (id === 'role-search' && ui.modal?.kind === 'roles') ui.modal.qDraft = value;
  if (id === 'tree-search') { ui.treeSearch = value; render(); }
});
document.addEventListener('change', event => {
  const element = event.target;
  screens.change(element);
  if (element.id === 'people-status') { ui.enabled = element.value; ui.page = 0; loadPeople(); }
  if (element.id === 'page-size') { ui.size = Number(element.value); ui.page = 0; loadPeople(); }
  if (element.dataset.roleId && ui.modal?.kind === 'roles' && !ui.writeBusy) {
    const id = Number(element.dataset.roleId);
    if (element.checked) ui.modal.draft.add(id); else ui.modal.draft.delete(id);
    renderOverlays();
  }
  if (element.id === 'move-department' && ui.modal?.kind === 'move') { ui.modal.destination = element.value; renderOverlays(); }
  if (element.id === 'review-failure') ui.nextFailure = element.value;
  if (element.id === 'review-failure-target') ui.failureTarget = element.value;
  if (element.id === 'review-slow') ui.slow = element.checked;
});
document.addEventListener('click', event => {
  const button = event.target.closest('[data-act]');
  if (!button || button.disabled) {
    if (ui.menu && !event.target.closest('.popover')) { ui.menu = null; renderOverlays(); }
    return;
  }
  const action = button.dataset.act;
  const id = Number(button.dataset.id);
  const person = ui.data?.items.find(item => item.id === id);
  if (!['more', 'identity'].includes(action)) ui.menu = null;
  const origin = saveFocus();
  const hadOverlay = !!(ui.modal || ui.confirm || ui.review);
  if (screens.handleAction(action, id, button)) {
    if (!hadOverlay && (ui.modal || ui.confirm)) focusReturn = origin;
    return;
  }
  switch (action) {
    case 'fill-demo': {
      const demo = DEMOS.find(item => item.id === id);
      ui.account = demo.account; ui.password = DEMO_PASSWORD; ui.loginError = null;
      render(); document.querySelector('.login-submit')?.focus(); break;
    }
    case 'password': ui.showPassword = !ui.showPassword; render(); document.getElementById('login-password')?.focus(); break;
    case 'refresh': loadPeople(); break;
    case 'people-home': ui.departmentId = ui.session.departmentId; ui.page = 0; loadPeople(); break;
    case 'department': ui.departmentId = id; ui.page = 0; loadPeople(); break;
    case 'tree-toggle': if (ui.expanded.has(id)) ui.expanded.delete(id); else ui.expanded.add(id); render(); break;
    case 'people-page': ui.page = Number(button.dataset.page); loadPeople(); break;
    case 'first-page': ui.page = 0; loadPeople(); break;
    case 'reset-filter': ui.q = ''; ui.qDraft = ''; ui.enabled = ''; ui.page = 0; loadPeople(); break;
    case 'roles': if (person) openRoles(person); break;
    case 'role-page': if (ui.modal?.kind === 'roles') { ui.modal.page = Number(button.dataset.page); loadCandidates(); } break;
    case 'retry-candidates': if (ui.modal?.roleMap.size) loadCandidates(); else if (ui.modal?.person) openRoles(ui.modal.person); break;
    case 'remove-role': if (ui.modal?.kind === 'roles' && !ui.writeBusy) { ui.modal.draft.delete(id); renderOverlays(); } break;
    case 'clear-roles': ui.confirm = { kind: 'clearDraft' }; renderOverlays(); break;
    case 'save-roles': saveRoles(); break;
    case 'close-modal': closeModal(); break;
    case 'person': if (person) { ui.menu = null; openDetails(person); } break;
    case 'move': if (person) { focusReturn = 'more-' + id; ui.modal = { kind: 'move', person, destination: '', error: null }; renderOverlays(); } break;
    case 'save-move': if (ui.modal?.destination) { ui.confirm = { kind: 'move', person: ui.modal.person }; renderOverlays(); } break;
    case 'disable': if (person) { focusReturn = 'more-' + id; ui.confirm = { kind: 'disable', person }; renderOverlays(); } break;
    case 'confirm': confirmAction(); break;
    case 'cancel-confirm': if (!ui.writeBusy) { ui.confirm = null; renderOverlays(); } break;
    case 'more': ui.menu = ui.menu === id ? null : id; renderOverlays(); break;
    case 'identity': ui.menu = ui.menu === 'identity' ? null : 'identity'; renderOverlays(); break;
    case 'logout': if (draftChanged(ui.modal)) { ui.confirm = { kind: 'logout' }; renderOverlays(); } else signOut(); break;
    case 'review': ui.review = true; ui.reviewError = null; renderOverlays(); break;
    case 'close-review': ui.review = false; renderOverlays(); document.querySelector('[data-act="review"]')?.focus(); break;
    case 'switch-demo': switchDemo(id); break;
    case 'reset-demo': ui.confirm = { kind: 'reset' }; renderOverlays(); break;
    case 'revoke':
      try { model.simulateRevocation(); ui.review = false; ui.session = model.me(); notify('已模拟撤销角色；下一次管理请求将重新鉴权'); render(); }
      catch (error) { ui.reviewError = error; renderOverlays(); }
      break;
  }
});
document.addEventListener('keydown', event => {
  if (!ui.menu) return;
  const items = [...document.querySelectorAll('.popover button:not(:disabled)')];
  if (event.key === 'Escape') {
    const trigger = ui.menu === 'identity' ? 'identity-trigger' : 'more-' + ui.menu;
    ui.menu = null; renderOverlays(); document.getElementById(trigger)?.focus(); event.preventDefault();
  }
  if (['ArrowUp', 'ArrowDown'].includes(event.key)) {
    const position = items.indexOf(document.activeElement);
    items[(position + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length]?.focus();
    event.preventDefault();
  }
});
window.addEventListener('resize', () => { if (ui.menu) { ui.menu = null; renderOverlays(); } });
render();
})();
