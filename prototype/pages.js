/** 离线工作区：与人员页面共用会话、模拟请求、模态框和写入反馈。 */
(function () {
'use strict';
const { DemoError } = globalThis.MunicipalPrototype;
function createScreens(context) {
  const { h, icon, busy, errorBox, pagination } = context;
  const state = () => context.state();
  const model = () => context.model();
  const globalCan = key => model().canGlobal(key);
  const view = page => state().views[page] ||= { data: null, busy: false, error: null, sequence: 0, q: '', qDraft: '', enabled: '', page: 0, systemId: 1, mode: 'permissions', selected: 1000, filters: { userId: '', username: '', allowed: '', event: '', appCode: '', from: '', to: '' }, limit: 12 };
  const rankNames = { 0: '自定义 · 仅管理员可授', 1: '普通员工', 2: '项目经理', 3: '部门经理', 4: '公司领导', 5: '系统管理员' };
  const dateTime = value => new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(value));
  const eventLabel = type => ({ AUTHORIZATION: '请求鉴权', LOGIN_SUCCESS: '登录成功', LOGIN_FAILED: '登录失败', UNAUTHENTICATED: '未认证访问' }[type] || '未知事件');
  const enabledTag = enabled => '<span class="status ' + (enabled ? 'on' : 'off') + '">' + (enabled ? '启用' : '已停用') + '</span>';
  const btn = (label, action, id = '', extra = '') => '<button type="button" class="btn ' + extra + '" data-act="' + action + '" data-id="' + id + '" ' + (extra.includes('unavailable') ? 'disabled' : '') + '>' + label + '</button>';
  const empty = (title, detail = '') => '<div class="empty-state"><div class="empty-icon">' + icon('search') + '</div><h3>' + h(title) + '</h3><p>' + h(detail) + '</p></div>';
  const loader = () => '<div class="empty-state">' + busy() + '<p>正在加载，请稍候</p></div>';
  const itemsPage = (data, action, noun = '条记录') => '<div class="pagination"><span>共 <strong>' + (data?.total ?? 0) + '</strong> ' + noun + '</span>' + pagination(data?.total ?? 0, data?.page ?? 0, data?.size ?? 10, action) + '</div>';
  function navigation() {
    return [
      { page: 'overview', title: '总览', group: '工作台', icon: 'overview', visible: model().can('users') },
      { page: 'departments', title: '部门管理', group: '组织管理', icon: 'building', visible: model().canManageDepartments() },
      { page: 'people', title: '人员管理', group: '组织管理', icon: 'users', visible: model().can('users') },
      { page: 'roles', title: '角色与权限', group: '权限管理', icon: 'shield', visible: globalCan('roles.list') },
      { page: 'resources', title: '资源与API', group: '权限管理', icon: 'resource', visible: globalCan('permissions.tree') || globalCan('apis.list') },
      { page: 'logs', title: '鉴权日志', group: '安全审计', icon: 'history', visible: globalCan('logs') },
    ].filter(item => item.visible);
  }
  function heading(title, subtitle, action = '') {
    const current = view(state().route);
    return '<div class="page-heading"><div><h1>' + h(title) + '</h1><p>' + h(subtitle) + '</p></div><div class="page-actions">' + action + btn(current.busy ? busy() + '加载中' : icon('refresh') + '刷新', 'screen-refresh', '', current.busy ? 'unavailable' : '') + '</div></div>';
  }
  function filterForm(page, placeholder, withStatus = true) {
    const current = view(page);
    return '<form class="filter-row management-filter" id="screen-filter"><div class="input-shell">' + icon('search') + '<input id="screen-search" aria-label="' + h(placeholder) + '" placeholder="' + h(placeholder) + '" maxlength="100" value="' + h(current.qDraft) + '"></div>'
      + (withStatus ? '<select id="screen-status" aria-label="筛选启用状态"><option value="">全部状态</option><option value="true" ' + (current.enabled === 'true' ? 'selected' : '') + '>启用</option><option value="false" ' + (current.enabled === 'false' ? 'selected' : '') + '>已停用</option></select>' : '')
      + '<button class="btn primary" id="screen-query" type="submit">查询</button>' + btn('重置', 'screen-reset', '', 'quiet') + '</form>';
  }
  function overviewPage(data) {
    const scope = data.scope.name;
    const cards = [['范围内人员', data.total, 'users', '包含本部门及下级'], ['启用账号', data.enabled, 'user', '可用状态不等于拥有管理权限'], ['组织节点', data.departments, 'building', '公司、部门及项目节点'], ['停用账号', data.disabled, 'ban', '角色与部门归属仍保留']];
    const maximum = Math.max(1, ...data.branches.map(item => item.total));
    const shortcuts = navigation().filter(item => item.page !== 'overview');
    return `<section class="welcome-banner"><div><span class="eyebrow">WORKSPACE OVERVIEW</span><h2>${h(state().session.name)}，欢迎回来</h2><p>从组织到授权，让每一项管理都有清晰的边界。</p><span class="scope-pill">${icon('building')}${h(scope)}及下级</span></div><div class="welcome-orbit" aria-hidden="true">${icon('shield')}<i></i><i></i><i></i></div></section>
      <div class="metric-grid">${cards.map(([label, value, glyph, note], index) => '<div class="panel metric-card"><div class="row between"><span>' + label + '</span><span class="metric-icon tone-' + index + '">' + icon(glyph) + '</span></div><strong>' + value + '<small>' + (index === 2 ? '个' : '人') + '</small></strong><p>' + note + '</p></div>').join('')}</div>
      <div class="overview-grid"><section class="panel"><div class="panel-heading"><h2>${state().session.departmentId === 1 ? '公司组织概况' : '管理范围概况'}</h2><span class="badge blue">${h(scope)}</span></div><div class="distribution"><p class="hint">下级组织人员分布 · 点击查看对应人员</p>${data.branches.length ? data.branches.map(item => '<button class="distribution-row" data-act="overview-people" data-id="' + item.id + '"><span>' + h(item.name) + '</span><span class="bar-track"><i style="width:' + Math.max(3, item.total / maximum * 100) + '%"></i></span><strong>' + item.total + '<small>人</small></strong>' + icon('chevron') + '</button>').join('') : '<div class="leaf-overview">' + icon('project') + '<strong>当前已是末级组织</strong><p>可在人员管理中查看全部 ' + data.total + ' 名直属人员。</p></div>'}<div class="distribution-foot">本部门直属 <strong>${data.direct}</strong> 人 · 人员总数包含全部下级</div></div></section>
      <section class="panel"><div class="panel-heading"><h2>快捷入口</h2><span class="hint">按当前权限展示</span></div><div class="shortcut-grid">${shortcuts.map(item => '<button data-act="route" data-page="' + item.page + '"><span>' + icon(item.icon) + '</span><strong>' + item.title + '</strong>' + icon('chevron') + '</button>').join('')}</div><div class="overview-tip">${icon('shield')}<div><strong>权限与岗位各司其职</strong><p>职位为只读人事资料。角色决定系统能力，部门限定管理范围。</p></div></div></section></div>
      ${data.latestLogs ? '<section class="panel overview-activity"><div class="panel-heading"><h2>最近鉴权活动</h2><button class="link-button" data-act="route" data-page="logs">查看日志 ' + icon('chevron') + '</button></div><div class="activity-list">' + data.latestLogs.map(item => '<div><span class="activity-dot ' + (item.allowed ? 'success' : 'rejected') + '"></span><span class="grow">' + h(item.username || '未识别主体') + '<small>' + h(eventLabel(item.eventType)) + ' · ' + h(item.httpMethod + ' ' + item.requestPath) + '</small></span><span class="badge ' + (item.allowed ? 'blue' : 'amber') + '">' + (item.allowed ? '允许' : '拒绝') + '</span><time>' + dateTime(item.occurredAt) + '</time></div>').join('') + '</div></section>' : ''}`;
  }
  function departmentPage(data) {
    const current = view('departments');
    const matches = new Set(data.filter(item => item.name.toLowerCase().includes(current.q.toLowerCase())).map(item => item.id));
    const visible = new Set(matches);
    if (current.q) {
      for (const item of data.filter(item => matches.has(item.id))) {
        for (let parent = data.find(node => node.id === item.parentId); parent; parent = data.find(node => node.id === parent.parentId)) visible.add(parent.id);
      }
    }
    const list = data.filter(item => visible.has(item.id));
    const name = id => data.find(item => item.id === id)?.name || '—';
    return '<section class="panel">' + filterForm('departments', '搜索部门名称', false)
      + '<div class="department-count-note"><strong>人数口径</strong><span>直属人数只统计本节点；总人数包含全部下级及停用账号。父部门总人数已包含项目人数，请勿重复相加。</span></div>'
      + '<div class="table-wrap management-table"><table><thead><tr><th>部门名称</th><th>组织类型</th><th>上级部门</th><th>直属人数</th><th>总人数（含下级）</th><th>操作</th></tr></thead><tbody>'
      + list.map(item => '<tr data-department-row="' + item.id + '"><td><span class="department-name" style="padding-left:' + (item.depth * 20) + 'px">' + (item.depth ? '<span class="tree-branch" aria-hidden="true">└</span>' : '') + icon(item.type === '公司' || item.type === '分公司' ? 'building' : item.type === '项目' ? 'project' : 'folder') + h(item.name) + '</span>' + (item.isOwn ? '<small class="cell-sub">本人直属部门</small>' : current.q && !matches.has(item.id) ? '<small class="cell-sub">上级路径</small>' : '') + '</td><td><span class="badge gray">' + h(item.type) + '</span></td><td>' + h(name(item.parentId)) + '</td><td>' + item.direct + ' 人</td><td><strong>' + item.total + '</strong> 人</td><td><div class="row-actions"><button class="link-button" data-act="department-create" data-id="' + item.id + '" ' + (!item.canCreate ? 'disabled' : '') + '>新增下级</button><button class="link-button" data-act="department-rename" data-id="' + item.id + '" ' + (!item.canRename ? 'disabled title="本人部门只读"' : '') + '>修改名称</button><button class="link-button text-danger" data-act="department-delete" data-id="' + item.id + '" ' + (!item.canDelete ? 'disabled title="' + h(item.deleteReason) + '"' : '') + '>删除</button></div></td></tr>').join('')
      + '</tbody></table>' + (!list.length ? empty('没有匹配的部门') : '') + '</div><div class="panel-note">' + icon('info') + '局部管理员仅维护下级。总公司可建分公司/部门/项目，分公司可建部门/项目，部门仅建项目，项目不可新增下级。仅删无人无下级的空部门，根不可删，不调整层级。</div></section>';
  }
  function rolePage(data) {
    return '<section class="panel">' + filterForm('roles', '搜索角色名称或编码') + '<div class="table-wrap management-table"><table><thead><tr><th>角色名称</th><th>角色编码</th><th>授予级别</th><th>状态</th><th>操作</th></tr></thead><tbody>'
      + data.items.map(item => '<tr><td><strong>' + h(item.name) + '</strong></td><td class="mono muted">' + h(item.code) + '</td><td><span class="badge ' + (item.authorityLevel ? 'blue' : 'gray') + '">' + h(rankNames[item.authorityLevel]) + '</span></td><td>' + enabledTag(item.enabled) + '</td><td><div class="row-actions"><button class="link-button" data-act="role-permissions" data-id="' + item.id + '" ' + (!globalCan('roles.permissions.read') ? 'disabled' : '') + '>配置权限</button><button class="link-button" data-act="role-edit" data-id="' + item.id + '" ' + (!globalCan('roles.patch') ? 'disabled' : '') + '>编辑</button><button class="link-button ' + (item.enabled ? 'text-danger' : '') + '" data-act="role-toggle" data-id="' + item.id + '" ' + (!globalCan('roles.patch') ? 'disabled' : '') + '>' + (item.enabled ? '停用' : '启用') + '</button></div></td></tr>').join('')
      + '</tbody></table>' + (!data.items.length ? empty('没有符合条件的角色', '请调整名称、编码或状态') : '') + '</div>' + itemsPage(data, 'screen-page', '个角色') + '<div class="panel-note">' + icon('info') + '授予级别按固定元数据判断，改名不会改变级别。新建自定义角色默认仅系统管理员可授予。</div></section>';
  }
  function resourceTree(permissions, parent = null, depth = 0) {
    return permissions.filter(item => item.parentId === parent).map(item => '<button class="resource-node ' + (view('resources').selected === item.id ? 'selected' : '') + '" style="padding-left:' + (14 + depth * 19) + 'px" data-act="resource-select" data-id="' + item.id + '">' + icon(item.type === 'MENU' ? 'folder' : 'button') + '<span>' + h(item.name) + '</span>' + (!item.enabled ? '<small>停用</small>' : '') + '</button>' + resourceTree(permissions, item.id, depth + 1)).join('');
  }
  function resourcePage(data) {
    const current = view('resources');
    const selected = data.resources?.permissions.find(item => item.id === current.selected);
    const systemName = data.resources?.systems.find(item => item.id === current.systemId)?.name || '统一权限管理系统';
    const systemSelector = '<div class="system-picker"><label for="resource-system">所属系统</label><select id="resource-system">' + (data.resources?.systems || [{ id: 1, name: systemName }]).map(item => '<option value="' + item.id + '" ' + (current.systemId === item.id ? 'selected' : '') + '>' + h(item.name) + '</option>').join('') + '</select></div>';
    const tabs = '<div class="workspace-tabs">' + (data.resources ? '<button class="' + (current.mode === 'permissions' ? 'active' : '') + '" data-act="resource-mode" data-mode="permissions">菜单与按钮</button>' : '')
      + (globalCan('apis.list') ? '<button class="' + (current.mode === 'apis' ? 'active' : '') + '" data-act="resource-mode" data-mode="apis">API资源</button>' : '')
      + (data.resources && current.systemId !== 1 ? '<button class="' + (current.mode === 'matrix' ? 'active' : '') + '" data-act="resource-mode" data-mode="matrix">原表角色矩阵</button>' : '') + '</div>';
    const prefix = systemSelector + tabs;
    if (current.mode === 'matrix' && current.systemId !== 1) return prefix + matrixPage(data.resources, current);
    if (current.mode === 'apis' && current.systemId !== 1) return prefix + '<section class="panel">' + empty('该系统暂无API资源', '这些业务系统尚未实现，此处仅演示跨系统权限配置能力；后续实际接入仅课程管理系统。') + '</section>';
    if (current.mode === 'apis' || !data.resources) return prefix + '<section class="panel">' + filterForm('resources', '搜索HTTP方法或API路径') + '<div class="table-wrap management-table"><table><thead><tr><th>HTTP方法</th><th>API路径</th><th>说明</th><th>状态</th><th>操作</th></tr></thead><tbody>'
      + (data.apis?.items || []).map(item => '<tr><td><span class="method ' + item.method.toLowerCase() + '">' + item.method + '</span></td><td class="mono">' + h(item.path) + '</td><td>' + h(item.name) + '</td><td>' + enabledTag(item.enabled) + '</td><td><button class="link-button ' + (item.enabled ? 'text-danger' : '') + '" data-act="api-toggle" data-id="' + item.id + '" ' + (!globalCan('apis.patch') ? 'disabled' : '') + '>' + (item.enabled ? '停用' : '启用') + '</button></td></tr>').join('')
      + '</tbody></table>' + (!data.apis?.items.length ? empty('没有匹配的API资源') : '') + '</div>' + itemsPage(data.apis, 'screen-page', '项API') + '<div class="panel-note">' + icon('info') + '方法与路径只读；启停会影响全部引用该API的有效授权。</div></section>';
    return prefix + '<div class="resource-layout"><section class="panel"><div class="panel-heading"><h2>权限目录</h2><span class="count">' + data.resources.permissions.length + '</span></div><div class="resource-tree">' + resourceTree(data.resources.permissions) + '</div></section>'
      + '<section class="panel resource-detail"><div class="panel-heading"><h2>' + h(selected?.name) + '</h2>' + (selected ? '<span class="badge blue">' + (selected.type === 'MENU' ? '菜单' : '按钮') + '</span>' : '') + '</div>'
      + (selected ? '<div class="resource-info"><dl class="detail-list"><dt>权限编码</dt><dd class="mono">' + h(selected.code) + '</dd><dt>所属系统</dt><dd>' + h(systemName) + '</dd><dt>上级权限</dt><dd>' + h(data.resources.permissions.find(item => item.id === selected.parentId)?.name || '无上级') + '</dd><dt>启用状态</dt><dd>' + enabledTag(selected.enabled) + '</dd>' + (selected.sourceRow ? '<dt>原表位置</dt><dd>第' + selected.sourceRow + '行</dd>' : '') + '</dl>' + btn('编辑属性', 'permission-edit', selected.id, !globalCan('permissions.patch') ? 'unavailable' : '')
        + '<div class="divider"></div><div class="section-title"><h3>已映射API <span class="muted">' + (data.mapping?.apis.length ?? '—') + '</span></h3><button class="link-button" data-act="permission-mapping" data-id="' + selected.id + '" ' + (!globalCan('permissions.apis.read') ? 'disabled' : '') + '>配置映射</button></div>'
        + (data.mapping ? data.mapping.apis.length ? data.mapping.apis.map(item => '<div class="mapped-api"><span class="method ' + item.method.toLowerCase() + '">' + item.method + '</span><span class="mono grow">' + h(item.path) + '</span>' + enabledTag(item.enabled) + '</div>').join('') : empty('尚未关联API', current.systemId === 1 ? '菜单选择和子权限相互独立，不会自动继承API。' : '原表未提供API，不代表拥有本RBAC管理能力。') : '<p class="hint">当前没有读取映射的权限。</p>')
        + '<p class="hint">停用父菜单不会级联停用直接授予的子权限。</p></div>' : empty('请选择一个权限节点')) + '</section></div>';
  }
  /** 保留五角色原始单元格，不把职级当权限继承。 */
  function matrixPage(resources, current) {
    const rows = resources.matrix.filter(item => [item.module, item.name, item.description].some(value => value.toLowerCase().includes(current.q.toLowerCase())));
    const visible = rows.slice(current.page * 10, (current.page + 1) * 10);
    return '<section class="panel">' + filterForm('resources', '搜索模块或功能点', false)
      + '<div class="table-wrap management-table"><table class="matrix-table"><thead><tr><th>模块 / 功能点</th>' + resources.roleNames.map(name => '<th>' + h(name) + '</th>').join('') + '</tr></thead><tbody>'
      + visible.map(item => '<tr><td><small class="cell-sub">' + h(item.module) + ' · 行' + item.row + '</small><strong>' + h(item.name) + '</strong><small class="cell-sub">' + h(item.description) + '</small></td>' + item.roleRights.map(value => '<td><span class="badge ' + (value === null ? 'amber' : value === '无' ? 'gray' : 'blue') + '">' + h(value === null ? '未规定' : value) + '</span></td>').join('') + '</tr>').join('')
      + '</tbody></table>' + (!rows.length ? empty('没有匹配的功能点') : '') + '</div>' + itemsPage({ total: rows.length, page: current.page, size: 10 }, 'screen-page', '个功能点')
      + '<div class="panel-note">' + icon('info') + '来源：' + h(resources.source.file) + '。矩阵是原表参考；角色实际配置请到“角色与权限”。view保留原文、按查看识别；空白为未规定。“全部”仅指本行其他角色所列操作的并集，表外不新增；查看本人/编辑本人保持限定。</div></section>';
  }
  function logRows(items) {
    return items.map(item => '<tr><td class="log-time">' + dateTime(item.occurredAt) + '<small>北京时间</small></td><td><strong>' + h(item.username || '未识别主体') + '</strong><small class="cell-sub">' + (item.userId ? '用户ID ' + item.userId : '主体未知') + '</small></td><td><span class="badge gray">' + (eventLabel(item.eventType)) + '</span></td><td><span class="status ' + (item.allowed ? 'on' : 'off') + '">' + (item.allowed ? '允许' : '拒绝') + '</span></td><td><span class="mono">' + item.httpMethod + '</span><small class="cell-sub mono">' + h(item.requestPath) + '</small></td><td><span class="mono">' + h(item.reason) + '</span><small class="cell-sub">HTTP ' + item.httpStatus + '</small></td><td><button class="link-button" data-act="log-detail" data-id="' + item.id + '">查看详情</button></td></tr>').join('');
  }
  function logPage(data) {
    const current = view('logs'), filters = current.filters;
    return '<section class="panel"><form class="log-filters" id="logs-filter"><label>工号<input id="log-username" data-log-filter="username" placeholder="精确工号" value="' + h(filters.username) + '"></label><label>用户ID<input id="log-userId" data-log-filter="userId" type="number" min="1" placeholder="内部用户ID" value="' + h(filters.userId) + '"></label>'
      + '<label>鉴权结果<select data-log-filter="allowed" id="log-allowed"><option value="">全部结果</option><option value="true" ' + (filters.allowed === 'true' ? 'selected' : '') + '>允许</option><option value="false" ' + (filters.allowed === 'false' ? 'selected' : '') + '>拒绝</option></select></label>'
      + '<label>事件类型<select data-log-filter="event" id="log-event">' + [['', '全部事件'], ['AUTHORIZATION', '请求鉴权'], ['LOGIN_SUCCESS', '登录成功'], ['LOGIN_FAILED', '登录失败'], ['UNAUTHENTICATED', '未认证访问']].map(([value, name]) => '<option value="' + value + '" ' + (filters.event === value ? 'selected' : '') + '>' + name + '</option>').join('') + '</select></label>'
      + '<label>系统<select data-log-filter="appCode" id="log-system"><option value="">全部系统</option><option value="RBAC" ' + (filters.appCode === 'RBAC' ? 'selected' : '') + '>统一权限管理系统</option></select></label>'
      + '<label>开始时间<input type="datetime-local" id="log-from" data-log-filter="from" value="' + h(filters.from) + '"></label><label>结束时间<input type="datetime-local" id="log-to" data-log-filter="to" value="' + h(filters.to) + '"></label>'
      + '<label>每批条数<select id="log-limit">' + [6, 12, 24, 50].map(value => '<option value="' + value + '" ' + (current.limit === value ? 'selected' : '') + '>' + value + '条</option>').join('') + '</select></label><div class="log-filter-actions"><button class="btn primary" id="logs-query" type="submit">查询</button>' + btn('重置', 'logs-reset', '', 'quiet') + '</div></form>'
      + '<div class="log-summary"><span>匹配总数 <strong>' + data.total + '</strong></span><span>已加载 <strong>' + data.items.length + '</strong> 条</span><span class="snapshot-label">' + (current.filtersDirty ? '筛选已修改，请点击查询' : '同一查询快照 · #' + data.snapshotId) + '</span></div>'
      + '<div class="table-wrap management-table"><table class="logs-table"><thead><tr><th>事件时间</th><th>操作主体</th><th>事件类型</th><th>鉴权结果</th><th>访问目标</th><th>原因 / 响应</th><th>操作</th></tr></thead><tbody>' + logRows(data.items) + '</tbody></table>' + (!data.items.length ? empty('没有符合筛选条件的日志') : '') + '</div>'
      + '<div class="load-more-bar">' + (data.hasMore ? btn(current.loadingMore ? busy() + '加载中' : '继续加载', 'logs-more', '', current.loadingMore || current.filtersDirty || current.busy ? 'unavailable' : '') : '<span class="hint">已加载全部匹配记录</span>') + '<span class="hint">刷新后才纳入新事件 · 无归档或导出操作</span></div></section>';
  }
  function page(pageName) {
    const current = view(pageName);
    const descriptions = {
      overview: ['总览', '查看组织概况，从当前管理范围开始。'],
      departments: ['部门管理', '新增下级、修改名称、删除空部门，按组织范围维护。'],
      roles: ['角色与权限', '维护共享角色，明确每一项可执行能力。'],
      resources: ['资源与API', '连接菜单、按钮与实际受控接口。'],
      logs: ['鉴权日志', '检索登录与鉴权事件，追踪每一次访问判断。'],
    };
    const [title, subtitle] = descriptions[pageName] || descriptions.overview;
    const action = pageName === 'roles' && globalCan('roles.create') ? btn(icon('plus') + '新建角色', 'role-new', '', 'primary') : '';
    const header = heading(title, subtitle, action) + errorBox(current.error, 'page-error');
    if (current.busy && !current.data) return header + '<section class="panel">' + loader() + '</section>';
    if (!current.data) return header + '<section class="panel">' + empty('暂时无法加载内容', '可刷新重试，或核对当前操作权限。') + '</section>';
    return header + ({ overview: overviewPage, departments: departmentPage, roles: rolePage, resources: resourcePage, logs: logPage }[pageName] || overviewPage)(current.data);
  }
  function logQuery(current) {
    const source = current.filters;
    return { ...source, username: source.username.trim(), userId: source.userId.trim(),
      from: source.from ? new Date(source.from).toISOString() : '', to: source.to ? new Date(source.to).toISOString() : '', limit: current.limit };
  }
  const logDraftSignature = current => JSON.stringify({ filters: current.filters, limit: current.limit });
  async function load(pageName = state().route, { more = false } = {}) {
    if (pageName === 'logs' && more && view(pageName).filtersDirty) return;
    const current = view(pageName), sequence = ++current.sequence, epoch = state().epoch;
    current.busy = !more; current.loadingMore = more; current.error = null;
    context.render();
    try {
      const query = { q: current.q, enabled: current.enabled, page: current.page, size: 10, systemId: current.systemId };
      const requestedLogDraft = pageName === 'logs' ? logDraftSignature(current) : null;
      const frozenLogQuery = pageName === 'logs' ? (more ? current.snapshotQuery : logQuery(current)) : null;
      if (more && !frozenLogQuery) throw new DemoError(400, 'INVALID_CURSOR', '请先重新查询');
      const result = await context.request(() => {
        const session = model().me();
        // 日志页不使用部门树；进入人员页时由其自身重读，日志查询不附带无关受控请求。
        const tree = pageName === 'logs' ? null : model().can('tree') ? model().tree() : [];
        let data;
        if (pageName === 'overview') data = model().overview();
        if (pageName === 'departments') data = model().departments();
        if (pageName === 'roles') data = model().roles(query);
        if (pageName === 'logs') data = model().logs({ ...frozenLogQuery, cursor: more ? current.data.nextCursor : null });
        if (pageName === 'resources') {
          const resources = globalCan('permissions.tree') ? model().resources({ systemId: current.systemId }) : null;
          const selected = resources?.permissions.some(item => item.id === current.selected) ? current.selected : resources?.permissions[0]?.id;
          data = { resources, selected, apis: globalCan('apis.list') ? model().apis(query) : null,
            mapping: selected && globalCan('permissions.apis.read') ? model().permissionApis(selected) : null };
        }
        return { data, tree, session };
      });
      if (epoch !== state().epoch || sequence !== current.sequence) return;
      current.data = more ? { ...result.data, items: [...current.data.items, ...result.data.items] } : result.data;
      if (pageName === 'logs' && !more) {
        current.snapshotQuery = frozenLogQuery;
        current.filtersDirty = logDraftSignature(current) !== requestedLogDraft;
      }
      if (pageName === 'resources') current.selected = result.data.selected;
      if (result.tree !== null) state().tree = result.tree;
      state().session = result.session;
    } catch (error) {
      if (error.name === 'AbortError' || epoch !== state().epoch || sequence !== current.sequence || context.handleSessionError(error)) return;
      current.error = error;
      if (!more) current.data = null;
    } finally {
      if (epoch === state().epoch && sequence === current.sequence) {
        current.busy = false; current.loadingMore = false;
        if (state().route === pageName) context.render();
      }
    }
  }
  const modalShell = (modal, title, content, { drawer = false, footer = true, saveLabel = '保存更改' } = {}) =>
    '<dialog class="' + (drawer ? 'drawer' : 'modal') + '" id="main-dialog" aria-labelledby="screen-modal-title"><div class="' + (drawer ? 'drawer-shell' : '') + '"><header class="' + (drawer ? 'drawer-header' : 'modal-header') + '"><div><h2 id="screen-modal-title">' + h(title) + '</h2>' + (modal.targetName ? '<p>' + h(modal.targetName) + '</p>' : '') + '</div><button class="icon-button" data-act="close-modal" aria-label="关闭' + h(title) + '" ' + (state().writeBusy ? 'disabled' : '') + '>' + icon('close') + '</button></header>'
      + '<div class="' + (drawer ? 'drawer-body' : 'modal-body') + '">' + errorBox(modal.error) + content + '</div><footer class="' + (drawer ? 'drawer-footer' : 'modal-footer') + '">'
      + (drawer ? '<span class="hint">' + (!footer ? '只读查看' : dirty(modal) ? '有未保存的修改' : '取消不会保存修改') + '</span><div class="row">' : '')
      + btn(footer ? '取消' : '关闭', 'close-modal') + (footer ? '<button class="btn primary" data-act="screen-save" ' + (modal.loading || modal.candidateBusy || state().writeBusy || !canSave(modal) || !dirty(modal) ? 'disabled' : '') + '>' + (state().writeBusy ? busy() + '保存中' : saveLabel) + '</button>' : '') + (drawer ? '</div>' : '') + '</footer></div></dialog>';
  const isCollectionEditor = modal => ['screen-role-permissions', 'screen-mapping'].includes(modal?.kind);
  function canSave(modal) {
    if (!modal || (isCollectionEditor(modal) && !modal.initialReadComplete)) return false;
    return {
      'screen-rename': model().can('departments.rename'), 'screen-department-new': model().can('departments.create'), 'screen-role-new': globalCan('roles.create'),
      'screen-role-edit': globalCan('roles.patch'), 'screen-role-permissions': globalCan('roles.permissions.write') && !!modal.catalog?.length,
      'screen-permission': globalCan('permissions.patch'), 'screen-mapping': globalCan('permissions.apis.write') && globalCan('apis.list'),
    }[modal.kind] || false;
  }
  function dirty(modal) {
    if (!modal?.kind?.startsWith('screen-') || modal.loading) return false;
    if (isCollectionEditor(modal) && !modal.initialReadComplete) return false;
    if (modal.kind === 'screen-role-permissions' || modal.kind === 'screen-mapping') return modal.savedIds.length !== modal.draft.size || modal.savedIds.some(id => !modal.draft.has(id));
    if (modal.kind === 'screen-role-new') return !!(modal.values.name || modal.values.code);
    if (modal.kind === 'screen-department-new') return !!modal.values.name;
    if (modal.kind === 'screen-rename') return modal.values.name !== modal.original.name;
    if (modal.kind === 'screen-role-edit') return modal.values.name !== modal.original.name;
    if (modal.kind === 'screen-permission') return ['parentId', 'type', 'enabled'].some(key => modal.values[key] !== modal.original[key]);
    return false;
  }
  const field = (label, id, value, attrs = '') => '<div class="field"><label for="' + id + '">' + label + '</label><div class="input-shell"><input id="' + id + '" value="' + h(value) + '" ' + attrs + '></div></div>';
  function permissionCheckboxTree(modal, items, parent = null, depth = 0) {
    return items.filter(item => item.parentId === parent).map(item => '<label class="permission-checkbox" style="padding-left:' + (14 + depth * 22) + 'px"><input type="checkbox" id="permission-check-' + item.id + '" data-permission-id="' + item.id + '" ' + (modal.draft.has(item.id) ? 'checked ' : '') + (!item.enabled || !globalCan('roles.permissions.write') ? 'disabled ' : '') + '><span class="grow"><strong>' + h(item.name) + '</strong><small>' + h(item.code) + '</small></span><span class="badge ' + (item.enabled ? 'gray' : 'amber') + '">' + (!item.enabled ? '已停用' : item.type === 'MENU' ? '菜单' : '按钮') + '</span></label>' + permissionCheckboxTree(modal, items, item.id, depth + 1)).join('');
  }
  function selectedTags(modal) {
    const ids = [...modal.draft].filter(id => modal.kind !== 'screen-role-permissions' || !modal.labels.has(id) || modal.labels.get(id).systemId === modal.systemId);
    const visible = modal.expandedTags ? ids : ids.slice(0, 12);
    const label = id => {
      const item = modal.labels.get(id);
      const parent = item?.sourceKind === 'operation' ? modal.labels.get(item.parentId) : null;
      return (parent ? parent.name + ' · ' : '') + (item?.name || item?.path || '项目 ' + id);
    };
    return '<div class="selected-tags">' + (visible.map(id => '<span class="badge blue">' + h(label(id)) + '<button data-act="screen-remove" data-id="' + id + '" aria-label="移除已选' + h(label(id)) + '" ' + (!canSave(modal) ? 'disabled' : '') + '>' + icon('close') + '</button></span>').join('') || '<span class="hint">当前系统尚未选择</span>') + '</div>'
      + (ids.length > 12 ? '<button class="link-button" data-act="selected-expand">' + (modal.expandedTags ? '收起' : '展开全部' + ids.length + '项') + '</button>' : '');
  }
  function modal(modal) {
    if (!modal?.kind?.startsWith('screen-')) return '';
    if (modal.loading) return modalShell(modal, modal.title, loader(), { drawer: true });
    if (isCollectionEditor(modal) && !modal.initialReadComplete) {
      return modalShell(modal, modal.kind === 'screen-role-permissions' ? '配置角色权限' : '配置API映射',
        '<div class="empty-state"><h3>尚未取得完整配置</h3><p>原集合尚未读取，不能将其视为空集合编辑或保存。请重试完整读取。</p>'
        + btn(icon('refresh') + '重新读取完整配置', 'screen-retry-initial') + '</div>', { drawer: true, footer: false });
    }
    if (modal.kind === 'screen-department-new') return modalShell(modal, '新增下级部门', field('部门名称', 'edit-name', modal.values.name, 'maxlength="100"')
      + '<div class="field"><label for="department-parent">上级部门</label><select id="department-parent">' + modal.catalog.filter(item => item.canCreate).map(item => '<option value="' + item.id + '" ' + (item.id === modal.values.parentId ? 'selected' : '') + '>' + h(item.name) + ' · ID ' + item.id + '</option>').join('') + '</select></div>'
      + '<div class="field"><label for="department-type">组织类型</label><select id="department-type">' + (modal.catalog.find(item => item.id === modal.values.parentId)?.allowedChildTypes || []).map(type => '<option ' + (type === modal.values.type ? 'selected' : '') + '>' + type + '</option>').join('') + '</select></div><p class="hint">类型由上级限制，项目不能新增下级。创建后不支持调整层级，新节点初始无人。</p>', { saveLabel: '创建部门' });
    if (modal.kind === 'screen-rename') return modalShell(modal, '修改部门名称', '<p>只更新名称，不改变层级、人员归属或授权范围。</p>' + field('部门名称', 'edit-name', modal.values.name, 'maxlength="100"') + '<p class="hint">上级部门：' + h(modal.parentName) + ' · ID ' + modal.original.id + '</p>');
    if (modal.kind === 'screen-role-new' || modal.kind === 'screen-role-edit') return modalShell(modal, modal.kind === 'screen-role-new' ? '新建角色' : '编辑角色', '<div class="form-stack">'
      + field('角色名称', 'edit-name', modal.values.name, 'maxlength="100"')
      + field('角色编码', 'edit-code', modal.values.code, (modal.kind === 'screen-role-edit' ? 'readonly ' : '') + 'maxlength="100"')
      + '<div class="readonly-note">' + icon('shield') + '<span>' + (modal.kind === 'screen-role-new' ? '新建角色初始无权限；默认仅系统管理员可授予。' : h(rankNames[modal.original.authorityLevel]) + ' · 改名不改变授予级别') + '</span></div><p class="hint">' + (modal.kind === 'screen-role-new' ? '创建后再显式配置权限，不自动继承其他角色。' : '角色名称会同步到全部已授予人员。编码不可修改。') + '</p></div>', { saveLabel: modal.kind === 'screen-role-new' ? '创建角色' : '保存更改' });
    if (modal.kind === 'screen-role-permissions') return modalShell(modal, '配置角色权限', '<div class="system-picker"><label for="role-permission-system">配置系统</label><select id="role-permission-system">' + modal.systems.map(item => '<option value="' + item.id + '" ' + (modal.systemId === item.id ? 'selected' : '') + '>' + h(item.name) + '</option>').join('') + '</select></div><p class="hint">切换系统保留全部选择。每个权限独立勾选，父菜单不自动包含子项。</p>'
      + (modal.systemId !== 1 ? '<p class="hint">本系统未实现业务页面或API，仅演示跨系统授权。“全部”为原表本行操作并集，不单列权限。实际选权请逐项勾选；矩阵可在资源页查看。</p>' : '')
      + '<div class="section-title"><h3>全部系统已选 ' + modal.draft.size + ' 项</h3><span class="hint">下方仅显示当前系统</span></div>' + selectedTags(modal) + '<div class="divider"></div>'
      + (modal.catalog.length ? '<div class="permission-checkbox-tree">' + permissionCheckboxTree(modal, modal.catalog.filter(item => item.systemId === modal.systemId)) + '</div>' : '<p class="hint">当前无权限树查询能力，只能查看已授集合。</p>') + '<p class="hint">保存影响全部持有该角色的人员。停用项须明确移除，其他系统选择一并保留。</p>', { drawer: true });
    if (modal.kind === 'screen-permission') return modalShell(modal, '编辑权限属性', '<div class="form-stack">' + field('权限名称', 'permission-name', modal.original.name, 'readonly') + field('权限编码', 'permission-code', modal.original.code, 'readonly')
      + '<div class="field"><label for="permission-parent">上级权限</label><select id="permission-parent"><option value="">无上级</option>' + modal.catalog.filter(item => item.type === 'MENU' && item.id !== modal.original.id).map(item => '<option value="' + item.id + '" ' + (item.id === modal.values.parentId ? 'selected' : '') + '>' + h(item.name) + '</option>').join('') + '</select></div>'
      + '<div class="field"><label for="permission-type">权限类型</label><select id="permission-type"><option value="MENU" ' + (modal.values.type === 'MENU' ? 'selected' : '') + '>菜单</option><option value="BUTTON" ' + (modal.values.type === 'BUTTON' ? 'selected' : '') + '>按钮</option></select></div><label class="toggle-line"><span>启用该权限</span><input type="checkbox" id="permission-enabled" ' + (modal.values.enabled ? 'checked' : '') + '></label><p class="hint">按钮不能有子节点；父子同系统、无环。修改会影响全部引用。</p></div>');
    if (modal.kind === 'screen-mapping') return modalShell(modal, '配置API映射', '<div class="section-title"><h3>已选API ' + modal.draft.size + '</h3></div>' + selectedTags(modal)
      + '<p class="hint">保留跨页完整选择。停用API可保留映射，但不会贡献有效能力。</p><form class="drawer-search" id="mapping-filter"><div class="input-shell">' + icon('search') + '<input id="mapping-search" aria-label="搜索映射候选API" placeholder="搜索HTTP方法或路径" value="' + h(modal.qDraft) + '"></div><button class="btn" type="submit">查询</button></form>'
      + (modal.candidateBusy ? loader() : '<div class="candidate-list">' + (modal.candidates?.items || []).map(item => '<label class="candidate ' + (modal.draft.has(item.id) ? 'checked' : '') + '"><input type="checkbox" id="api-check-' + item.id + '" data-api-id="' + item.id + '" ' + (modal.draft.has(item.id) ? 'checked' : '') + '><div class="grow"><strong><span class="method ' + item.method.toLowerCase() + '">' + item.method + '</span> <span class="mono">' + h(item.path) + '</span></strong><p>' + h(item.name) + (item.enabled ? '' : ' · 已停用') + '</p></div></label>').join('') + '</div><div class="candidate-footer"><span>共' + (modal.candidates?.total || 0) + '项API</span>' + pagination(modal.candidates?.total || 0, modal.page, 6, 'mapping-page') + '</div>'), { drawer: true });
    if (modal.kind === 'screen-log-detail') {
      const entry = modal.original;
      return modalShell(modal, '鉴权事件详情', '<div class="log-result ' + (entry.allowed ? 'allowed' : 'denied') + '">' + icon('shield') + '<div><strong>鉴权' + (entry.allowed ? '允许' : '拒绝') + '</strong><p>HTTP ' + entry.httpStatus + ' · ' + h(entry.reason) + '</p></div></div><dl class="detail-list log-detail-list">'
        + [['事件ID', entry.id], ['事件时间', dateTime(entry.occurredAt) + '（北京时间）'], ['UTC原值', entry.occurredAt], ['事件类型', entry.eventType], ['用户ID', entry.userId ?? '未识别'], ['工号', entry.username ?? '未识别'], ['系统', entry.appCode], ['权限编码', entry.permissionCode || '未定位'], ['HTTP方法', entry.httpMethod], ['安全路径', entry.requestPath], ['请求标识', entry.requestId]].map(([label, value]) => '<dt>' + label + '</dt><dd class="mono">' + h(value) + '</dd>').join('') + '</dl><p class="hint">鉴权允许与业务成功分别记录，不包含手机号、口令、令牌或查询串。</p>', { drawer: true, footer: false });
    }
    return '';
  }
  function confirmScreen(title, message, perform, subject = '', button = '确认修改', danger = false) {
    state().confirm = { kind: 'screen', title, message, perform, subject, button, danger, error: null };
    context.renderOverlays();
  }
  function confirmation(confirm) {
    if (confirm?.kind !== 'screen') return '';
    return '<dialog class="modal" id="confirm-dialog" aria-labelledby="confirm-title"><header class="modal-header"><h2 id="confirm-title">' + h(confirm.title) + '</h2></header><div class="modal-body"><div class="confirm-symbol">' + icon('warning') + '</div>' + (confirm.subject ? '<strong>' + h(confirm.subject) + '</strong>' : '') + '<p>' + h(confirm.message) + '</p>' + errorBox(confirm.error) + '</div><footer class="modal-footer"><button class="btn" data-act="cancel-confirm" ' + (state().writeBusy ? 'disabled' : '') + '>取消</button><button class="btn ' + (confirm.danger ? 'danger' : 'primary') + '" data-act="confirm" ' + (state().writeBusy ? 'disabled' : '') + '>' + (state().writeBusy ? busy() + '处理中' : h(confirm.button)) + '</button></footer></dialog>';
  }
  async function openModal(kind, id) {
    const modal = { kind, id, error: null, loading: true, initialReadComplete: false, title: '读取配置',
      savedIds: [], draft: new Set(), labels: new Map(), catalog: [], systems: [], candidates: null,
      candidateBusy: false, page: 0, q: '', qDraft: '' };
    state().modal = modal; context.renderOverlays();
    try {
      const result = await context.request(() => {
        if (kind === 'screen-role-permissions') { const catalog = globalCan('permissions.tree') ? model().permissionCatalog() : { systems: [], permissions: [] }; return { current: model().rolePermissions(id), catalog: catalog.permissions, systems: catalog.systems }; }
        if (kind === 'screen-mapping') {
          const current = model().permissionApis(id);
          return { current, candidates: globalCan('apis.list') ? model().apis({ page: 0, size: 6, systemId: current.permission.systemId }) : { items: [], total: 0 } };
        }
      });
      if (state().modal !== modal) return;
      const saved = kind === 'screen-role-permissions' ? result.current.permissions : result.current.apis;
      modal.targetName = kind === 'screen-role-permissions' ? result.current.role.name : result.current.permission.name;
      modal.savedIds = saved.map(item => item.id); modal.draft = new Set(modal.savedIds);
      modal.original = result.current; modal.catalog = result.catalog || [];
      modal.systems = result.systems || []; modal.systemId = kind === 'screen-mapping' ? result.current.permission.systemId : 1;
      [...saved, ...(result.candidates?.items || []), ...modal.catalog].forEach(item => modal.labels.set(item.id, item));
      modal.candidates = result.candidates; modal.page = 0; modal.q = ''; modal.qDraft = '';
      modal.initialReadComplete = true;
    } catch (error) {
      if (error.name === 'AbortError' || state().modal !== modal || context.handleSessionError(error)) return;
      modal.error = error;
    } finally { if (state().modal === modal) { modal.loading = false; context.renderOverlays(); } }
  }
  async function mappingCandidates() {
    const modal = state().modal;
    if (modal?.kind !== 'screen-mapping' || !modal.initialReadComplete || modal.loading) return;
    const sequence = modal.sequence = (modal.sequence || 0) + 1;
    modal.candidateBusy = true; modal.error = null; context.renderOverlays();
    try {
      const result = await context.request(() => model().apis({ q: modal.q, page: modal.page, size: 6, systemId: modal.systemId }));
      if (state().modal === modal && sequence === modal.sequence) { modal.candidates = result; result.items.forEach(item => modal.labels.set(item.id, item)); }
    } catch (error) {
      if (error.name !== 'AbortError' && state().modal === modal && !context.handleSessionError(error)) modal.error = error;
    } finally { if (state().modal === modal && sequence === modal.sequence) { modal.candidateBusy = false; context.renderOverlays(); } }
  }
  function write(operation, message, confirmed = false) {
    return context.writeOperation(operation, message, { confirmation: confirmed, refresh: () => load(state().route) });
  }
  function saveModal(confirmed = false) {
    const modal = state().modal;
    if (!dirty(modal) || state().writeBusy || !canSave(modal)) return;
    const values = modal.values ? structuredClone(modal.values) : null;
    const ids = modal.draft ? [...modal.draft] : null;
    const save = () => {
      if (modal.kind === 'screen-rename') return model().renameDepartment(modal.original.id, values.name);
      if (modal.kind === 'screen-department-new') return model().createDepartment(values);
      if (modal.kind === 'screen-role-new') {
        const result = model().createRole(values);
        const current = view('roles'); current.q = result.code; current.qDraft = result.code; current.enabled = ''; current.page = 0;
        return result;
      }
      if (modal.kind === 'screen-role-edit') return model().patchRole(modal.original.id, { name: values.name });
      if (modal.kind === 'screen-role-permissions') return model().saveRolePermissions(modal.id, ids);
      if (modal.kind === 'screen-permission') return model().patchPermission(modal.original.id, values);
      if (modal.kind === 'screen-mapping') return model().savePermissionApis(modal.id, ids);
    };
    if (isCollectionEditor(modal) && !confirmed) {
      confirmScreen(modal.draft.size ? '确认替换完整配置？' : '确认清空全部配置？', '将以当前完整选择替换已保存集合，影响全部引用。失败不会部分生效。', () => saveModal(true), modal.targetName, modal.draft.size ? '确认保存' : '确认清空', !modal.draft.size);
    } else write(save, '修改已保存', confirmed);
  }
  function handleAction(action, id, button) {
    const current = view(state().route);
    switch (action) {
      case 'route': context.navigate(button.dataset.page); return true;
      case 'screen-refresh': if (!current.busy && !current.loadingMore) load(); return true;
      case 'overview-people': context.navigate('people', { departmentId: id }); return true;
      case 'screen-reset': current.q = ''; current.qDraft = ''; current.enabled = ''; current.page = 0; load(); return true;
      case 'screen-page': current.page = Number(button.dataset.page); load(); return true;
      case 'department-rename': {
        const item = current.data.find(item => item.id === id);
        if (!item?.canRename) return true;
        state().modal = { kind: 'screen-rename', original: item, values: { name: item.name }, parentName: current.data.find(node => node.id === item.parentId)?.name || '无上级', targetName: item.name };
        context.renderOverlays(); return true;
      }
      case 'department-create': {
        const item = current.data.find(item => item.id === id);
        if (!item?.canCreate) return true;
        state().modal = { kind: 'screen-department-new', catalog: current.data, values: { name: '', parentId: id, type: item.allowedChildTypes[0] } };
        context.renderOverlays(); return true;
      }
      case 'department-delete': {
        const item = current.data.find(item => item.id === id);
        if (!item?.canDelete) return true;
        confirmScreen('删除这个空部门？', '仅删除当前空部门，不调整其他部门层级。删除后无法撤销，可重新新增。', () => write(() => model().deleteDepartment(id), '部门已删除', true), item.name, '确认删除', true);
        return true;
      }
      case 'role-new': state().modal = { kind: 'screen-role-new', values: { name: '', code: '' } }; context.renderOverlays(); return true;
      case 'role-edit': {
        const item = current.data.items.find(item => item.id === id);
        state().modal = { kind: 'screen-role-edit', original: item, values: { name: item.name, code: item.code }, targetName: item.name };
        context.renderOverlays(); return true;
      }
      case 'role-toggle': {
        const item = current.data.items.find(item => item.id === id);
        confirmScreen(item.enabled ? '停用这个角色？' : '启用这个角色？', '修改影响全部持有该角色的人员，关联会保留。最后维护者受保护。', () => write(() => model().patchRole(id, { enabled: !item.enabled }), '角色状态已更新', true), item.name, item.enabled ? '确认停用' : '确认启用', item.enabled);
        return true;
      }
      case 'role-permissions': openModal('screen-role-permissions', id); return true;
      case 'screen-retry-initial': {
        const modal = state().modal;
        if (isCollectionEditor(modal) && !modal.loading && !modal.initialReadComplete) openModal(modal.kind, modal.id);
        return true;
      }
      case 'selected-expand': state().modal.expandedTags = !state().modal.expandedTags; context.renderOverlays(); return true;
      case 'resource-mode': current.mode = button.dataset.mode; current.page = 0; load(); return true;
      case 'resource-select': current.selected = id; load(); return true;
      case 'permission-edit': {
        if (!globalCan('permissions.patch')) return true;
        const item = current.data.resources.permissions.find(item => item.id === id);
        state().modal = { kind: 'screen-permission', original: item, catalog: current.data.resources.permissions, targetName: item.name, values: { parentId: item.parentId, type: item.type, enabled: item.enabled } };
        context.renderOverlays(); return true;
      }
      case 'permission-mapping': openModal('screen-mapping', id); return true;
      case 'mapping-page': if (state().modal?.initialReadComplete) { state().modal.page = Number(button.dataset.page); mappingCandidates(); } return true;
      case 'api-toggle': {
        const item = current.data.apis.items.find(item => item.id === id);
        confirmScreen(item.enabled ? '停用这个API资源？' : '启用这个API资源？', '修改会即时影响所有映射到该API的有效授权，映射关系本身保留。', () => write(() => model().patchApi(id, !item.enabled), 'API状态已更新', true), item.method + ' ' + item.path, item.enabled ? '确认停用' : '确认启用', item.enabled);
        return true;
      }
      case 'screen-remove': if (canSave(state().modal)) { state().modal.draft.delete(id); context.renderOverlays(); } return true;
      case 'screen-save': saveModal(); return true;
      case 'logs-more': if (!current.filtersDirty && !current.loadingMore && !current.busy && current.data?.hasMore) load('logs', { more: true }); return true;
      case 'logs-reset': current.filters = { userId: '', username: '', allowed: '', event: '', appCode: '', from: '', to: '' }; current.limit = 12; load(); return true;
      case 'log-detail': state().modal = { kind: 'screen-log-detail', original: current.data.items.find(item => item.id === id), targetName: '请求详情' }; context.renderOverlays(); return true;
      default: return false;
    }
  }
  function input(element) {
    if (element.id === 'screen-search') view(state().route).qDraft = element.value;
    if (element.id === 'edit-name' && state().modal?.values) state().modal.values.name = element.value;
    if (element.id === 'edit-code' && state().modal?.kind === 'screen-role-new') state().modal.values.code = element.value;
    if (element.id === 'mapping-search' && state().modal?.kind === 'screen-mapping' && state().modal.initialReadComplete) state().modal.qDraft = element.value;
    if (element.dataset.logFilter) { view('logs').filters[element.dataset.logFilter] = element.value; view('logs').filtersDirty = true; context.render(); }
    if (['edit-name', 'edit-code'].includes(element.id)) context.renderOverlays();
  }
  function change(element) {
    if (element.id === 'department-parent' && state().modal?.kind === 'screen-department-new') {
      const modal = state().modal;
      modal.values.parentId = Number(element.value);
      const types = modal.catalog.find(item => item.id === modal.values.parentId)?.allowedChildTypes || [];
      if (!types.includes(modal.values.type)) modal.values.type = types[0];
      context.renderOverlays();
    }
    if (element.id === 'department-type' && state().modal?.kind === 'screen-department-new') state().modal.values.type = element.value;
    if (element.id === 'resource-system') { const current = view('resources'); current.systemId = Number(element.value); current.selected = null; current.data = null; current.q = ''; current.qDraft = ''; current.page = 0; load(); }
    if (element.id === 'role-permission-system' && state().modal?.initialReadComplete) { state().modal.systemId = Number(element.value); state().modal.expandedTags = false; context.renderOverlays(); }
    if (element.id === 'screen-status') { const current = view(state().route); current.enabled = element.value; current.page = 0; load(); }
    if (element.id === 'mobile-navigation') context.navigate(element.value);
    if (element.dataset.permissionId && state().modal?.kind === 'screen-role-permissions' && canSave(state().modal)) {
      const id = Number(element.dataset.permissionId); if (element.checked) state().modal.draft.add(id); else state().modal.draft.delete(id); context.renderOverlays();
    }
    if (element.dataset.apiId && state().modal?.kind === 'screen-mapping' && canSave(state().modal)) {
      const id = Number(element.dataset.apiId); if (element.checked) state().modal.draft.add(id); else state().modal.draft.delete(id); context.renderOverlays();
    }
    if (state().modal?.kind === 'screen-permission') {
      if (element.id === 'permission-parent') state().modal.values.parentId = element.value ? Number(element.value) : null;
      if (element.id === 'permission-type') state().modal.values.type = element.value;
      if (element.id === 'permission-enabled') state().modal.values.enabled = element.checked;
      if (element.id.startsWith('permission-')) context.renderOverlays();
    }
    if (element.dataset.logFilter) { view('logs').filters[element.dataset.logFilter] = element.value; view('logs').filtersDirty = true; context.render(); }
    if (element.id === 'log-limit') { view('logs').limit = Number(element.value); view('logs').filtersDirty = true; context.render(); }
  }
  function submit(id) {
    if (id === 'screen-filter') { const current = view(state().route); current.q = current.qDraft.trim(); current.page = 0; load(); return true; }
    if (id === 'logs-filter') { load('logs'); return true; }
    if (id === 'mapping-filter') { const modal = state().modal; if (modal?.kind === 'screen-mapping' && modal.initialReadComplete) { modal.q = modal.qDraft.trim(); modal.page = 0; mappingCandidates(); } return true; }
    return false;
  }
  return { navigation, page, load, modal, dirty, confirmation, handleAction, input, change, submit,
    confirmAction() { if (state().confirm?.kind !== 'screen') return false; if (!state().writeBusy) state().confirm.perform(); return true; } };
}
globalThis.MunicipalScreens = { createScreens };
})();
