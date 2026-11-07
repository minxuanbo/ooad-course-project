/* T04 review prototype. All identities and requests are simulated in memory. */
(() => {
  const R = window.R, e = R.escape;
  let owner, tab = 'people', busy = '', loginAccount = 'demo.root';
  document.querySelector('#reset')?.addEventListener('click', () => { loginAccount = 'demo.root'; });
  let filters = {q:'', departmentId:'', enabled:'', page:0, size:5};
  const emptyLogFilters = () => ({userId:'', username:'', appCode:'', allowed:'', from:'', to:'', event:'', cursor:'', limit:100});
  let logFilters = emptyLogFilters(), people, logs, identity, logRows = [];
  const selected = (value, expected) => String(value) === String(expected) ? ' selected' : '';
  const departmentName = id => R.state.departments.find(d => d.id === id)?.name || '—';
  const json = value => `<details><summary>本次模拟响应（含 HTTP 状态）</summary><pre>${e(JSON.stringify(value, null, 2))}</pre></details>`;
  const error = response => `<div class="empty" role="alert"><strong>${e(response.status)} · ${e(response.code)}</strong><p>${e(response.message)}</p><button data-retry>重试查询</button></div>`;
  function request(action, path, method = 'GET', loginUser) {
    let response;
    try { response = {status:200, code:'OK', message:'成功', data:action()}; }
    catch (err) { response = {status:err.status || 500, code:err.code || 'INTERNAL_ERROR', message:err.message, data:null}; }
    R.record(path === '/api/auth/login' ? (response.status < 400 ? 'LOGIN_SUCCESS' : 'LOGIN_FAILED') : response.status === 401 ? 'UNAUTHENTICATED' : response.status === 403 ? 'REQUEST_DENIED' : 'AUTHORIZATION', response.status < 400, response.code, method, path, response.status, loginUser || R.user());
    response.requestId = R.state.logs[0].requestId;
    return response;
  }
  function login(account, password) {
    loginAccount = account;
    const user = R.state.users.find(u => u.username === account.trim());
    R.actorId = null;
    const response = request(() => {
      if (!account.trim() || !password || account.length > 100 || password.length > 200) R.fail(400, 'INVALID_ARGUMENT', '请输入用户名和口令');
      if (!user?.enabled || password !== 'demo') R.fail(401, 'LOGIN_FAILED', '账号或密码错误');
      if (!R.canEnter(user)) R.fail(403, 'MANAGEMENT_ACCESS_DENIED', '该账号没有本权限系统的有效管理权限');
      R.queryFailure();
      return {user:{id:user.id, username:user.username, name:user.name, departmentId:user.departmentId, enabled:user.enabled}};
    }, '/api/auth/login', 'POST', user);
    if (response.status === 200) R.actorId = user.id;
    return response;
  }
  function currentUser() {
    const user = R.user();
    if (!user) R.fail(401, 'AUTHENTICATION_REQUIRED', '请先登录');
    if (!user.enabled) R.fail(401, 'SESSION_INVALID', '当前账号已停用');
    return user;
  }
  function queryDepartments() {
    return request(() => {
      const actor = R.guard('rbac.users.read', {method:'GET', path:'/api/departments/tree'});
      R.queryFailure();
      return R.state.departments.filter(d => R.inScope(d.id, actor)).map(({id, parentId, name}) => ({id, parentId:id === actor.departmentId ? null : parentId, name}));
    }, '/api/departments/tree');
  }
  function queryUsers(input) {
    return request(() => {
      const actor = R.guard('rbac.users.read', {method:'GET', path:'/api/users'});
      const departmentId = input.departmentId === '' ? actor.departmentId : Number(input.departmentId);
      if (!Number.isSafeInteger(departmentId) || departmentId < 1) R.fail(400, 'INVALID_ARGUMENT', '部门 ID 必须为正安全整数');
      if (!R.isRoot(actor) && !R.inScope(departmentId, actor)) R.fail(403, 'MANAGEMENT_SCOPE_DENIED', '目标部门不在本人及下级范围');
      if (!R.state.departments.some(d => d.id === departmentId)) R.fail(404, 'RESOURCE_NOT_FOUND', '部门不存在');
      if (input.q.length > 100 || !['', 'true', 'false'].includes(input.enabled)) R.fail(400, 'INVALID_ARGUMENT', '搜索最多 100 字；状态仅接受 true 或 false');
      const q = input.q.trim().toLocaleLowerCase();
      const rows = R.state.users.filter(u => R.inScope(u.departmentId, actor))
        .filter(u => R.inScope(u.departmentId, {departmentId}))
        .filter(u => !q || `${u.name}\n${u.username}`.toLocaleLowerCase().includes(q))
        .filter(u => input.enabled === '' || u.enabled === (input.enabled === 'true'))
        .map(u => ({id:u.id, username:u.username, name:u.name, departmentId:u.departmentId, departmentName:departmentName(u.departmentId), enabled:u.enabled}));
      const page = R.pageItems(rows, Number(input.page), Number(input.size));
      R.queryFailure(); return page;
    }, '/api/users');
  }
  const encode = value => btoa(String.fromCharCode(...new TextEncoder().encode(value))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const decode = value => new TextDecoder('utf-8', {fatal:true}).decode(Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0)));
  function time(value) {
    if (!value) return null;
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/.test(value) || !Number.isFinite(Date.parse(value)) || new Date(value).toISOString().slice(0, 19) !== value.slice(0, 19)) R.fail(400, 'INVALID_ARGUMENT', '时间须为有效 UTC ISO-8601，例如 2026-11-07T00:00:00Z');
    return new Date(value).toISOString();
  }
  function queryLogs(input) {
    return request(() => {
      R.guard('rbac.logs.read', {global:true, method:'GET', path:'/api/authorization-logs'});
      const limit = Number(input.limit);
      if (!Number.isSafeInteger(limit) || limit < 1 || limit > 500) R.fail(400, 'INVALID_ARGUMENT', 'limit 必须为 1–500');
      if (input.username.length > 100 || input.appCode.length > 191 || input.event.length > 32 || input.cursor.length > 4096) R.fail(400, 'INVALID_ARGUMENT', '筛选字段或游标过长');
      const userId = input.userId === '' ? null : Number(input.userId);
      if (userId !== null && (!Number.isSafeInteger(userId) || userId < 1)) R.fail(400, 'INVALID_ARGUMENT', '用户 ID 必须为正安全整数');
      if (!['', 'true', 'false'].includes(input.allowed)) R.fail(400, 'INVALID_ARGUMENT', 'allowed 仅接受 true 或 false');
      const from = time(input.from), to = time(input.to);
      if (from && to && from >= to) R.fail(400, 'INVALID_ARGUMENT', '开始时间必须早于结束时间');
      const q = {userId, username:input.username || null, appCode:input.appCode || null, allowed:input.allowed === '' ? null : input.allowed === 'true', from, to, event:input.event || null, limit};
      const key = encode(JSON.stringify(q));
      let snapshotId = Math.max(0, ...R.state.logs.map(log => log.id)), before = Number.MAX_SAFE_INTEGER;
      if (input.cursor) {
        try {
          if (!/^[\w-]+$/.test(input.cursor)) throw new Error();
          const parts = decode(input.cursor).split(':');
          snapshotId = Number(parts[0]); before = Number(parts[1]);
          if (parts.length !== 3 || !Number.isSafeInteger(snapshotId) || snapshotId < 0 || !Number.isSafeInteger(before) || before < 1 || parts[2] !== key) throw new Error();
        } catch (_) { R.fail(400, 'INVALID_CURSOR', '游标无效或筛选条件已改变，请从首批查询'); }
      }
      const all = R.state.logs.filter(log => log.id <= snapshotId)
        .filter(log => userId === null || log.userId === userId)
        .filter(log => !q.username || log.username === q.username)
        .filter(log => !q.appCode || log.appCode === q.appCode)
        .filter(log => q.allowed === null || log.allowed === q.allowed)
        .filter(log => !from || log.occurredAt >= from)
        .filter(log => !to || log.occurredAt < to)
        .filter(log => !q.event || log.eventType === q.event);
      const rows = all.filter(log => log.id < before).sort((a, b) => b.id - a.id);
      R.queryFailure();
      const items = rows.slice(0, limit), hasMore = rows.length > limit;
      return {items, nextCursor:hasMore ? encode(`${snapshotId}:${items[items.length - 1].id}:${key}`) : null, hasMore, snapshotId, total:all.length};
    }, '/api/authorization-logs');
  }
  function queryIdentity() {
    return {
      me:request(() => {
        const u = currentUser(); R.queryFailure();
        return {user:{id:u.id, username:u.username, name:u.name, departmentId:u.departmentId, enabled:u.enabled}};
      }, '/api/me'),
      permissions:request(() => {
        const u = currentUser(); R.queryFailure();
        return R.state.permissions.filter(p => p.systemId === R.state.systems.find(s => s.code === 'rbac')?.id && R.effective(u).has(p.id)).map(({id, systemId, parentId, type, code, name}) => ({id, systemId, parentId, type, code, name}));
      }, '/api/me/permissions')
    };
  }
  let departments;
  function departmentTree() {
    const visible = departments?.data || [];
    const branch = parentId => visible.filter(d => d.parentId === parentId || parentId === null && !visible.some(p => p.id === d.parentId)).map(d => `<li><button data-department="${d.id}" aria-pressed="${Number(filters.departmentId || R.user().departmentId) === d.id}">${e(d.name)}</button>${visible.some(c => c.parentId === d.id) ? `<ul>${branch(d.id)}</ul>` : ''}</li>`).join('');
    return departments?.status >= 400 ? `<p role="alert">${e(departments.code)}：${e(departments.message)}</p>` : `<nav aria-label="可查询部门"><ul class="stack">${branch(null)}</ul></nav>`;
  }
  function loginView() {
    return `<h1>管理端登录</h1><p class="muted">RBAC 整改评审原型 · 全部为模拟账号，统一演示口令 demo。</p><div class="split"><section class="panel"><h2>使用用户名登录</h2><form id="t04-login" class="stack"><label>用户名<input name="account" autocomplete="username" maxlength="100" value="${e(loginAccount)}" required></label><label>口令<input name="password" type="password" autocomplete="current-password" maxlength="200" value="demo" required></label><button class="primary">登录</button></form><p class="hint">账号或口令错误、停用账号统一提示“账号或密码错误”；无管理权限单独拒绝。</p></section><section class="panel"><h2>可复核账号</h2><dl><dt>demo.root</dt><dd>共同根维护者，完整维护能力。</dd><dt>demo.logs</dt><dd>共同根日志查询者；无需完整维护能力 K。</dd><dt>demo.department</dt><dd>工程部及下级人员管理；日志和同级部门拒绝。</dd><dt>demo.employee / demo.foreign</dt><dd>普通员工 / 仅外系统权限；均拒绝本管理端登录。</dd><dt>demo.person10</dt><dd>停用账号；与口令错误使用相同提示。</dd></dl></section></div>`;
  }
  function peopleView() {
    const data = people?.data;
    return `<div class="split"><aside class="panel"><h2>部门范围</h2><p class="muted">仅本人部门及下级；选中部门包含全部后代。</p>${departmentTree()}<button id="t04-outside"${R.isRoot(R.user()) ? ' disabled' : ''}>${R.isRoot(R.user()) ? '根范围覆盖全部部门' : '尝试范围外查询'}</button></aside><section class="panel"><h2>人员检索</h2><form id="t04-search" class="toolbar"><label>姓名或用户名<input name="q" maxlength="100" value="${e(filters.q)}" placeholder="输入关键词"></label><label>账号状态<select name="enabled"><option value="">全部状态</option><option value="true"${selected(filters.enabled, 'true')}>启用</option><option value="false"${selected(filters.enabled, 'false')}>停用</option></select></label><label>每页<select name="size">${[5,20,50,100].map(n => `<option${selected(filters.size,n)}>${n}</option>`).join('')}</select></label><button class="primary">查询</button><button type="button" id="t04-clear">清空筛选</button></form>${busy === 'people' ? '<div class="empty" role="status" aria-busy="true">正在查询模拟人员…</div>' : people?.status >= 400 ? error(people) : `<p class="muted" role="status">符合条件 ${data?.total ?? 0} 人 · 第 ${Number(filters.page) + 1} 页 · 每页 ${filters.size} 人</p><div class="table-wrap"><table><thead><tr><th>人员</th><th>所属部门</th><th>账号状态</th><th>操作</th></tr></thead><tbody>${data?.items.length ? data.items.map(u => `<tr><td><strong>${e(u.name)}</strong><br><span class="muted">${e(u.username)} · #${u.id}</span></td><td>${e(u.departmentName)}</td><td>${u.enabled ? '启用' : '停用'}</td><td><button data-person="${u.id}">角色与调动</button></td></tr>`).join('') : `<tr><td colspan="4" class="empty">${data?.total ? '当前页没有人员；筛选总数保持不变，请返回前页。' : '没有符合条件的人员，请调整关键词、状态或部门。'}</td></tr>`}</tbody></table></div><div class="actions"><button id="t04-prev"${Number(filters.page) === 0 ? ' disabled' : ''}>上一页</button><button id="t04-next"${!data || (Number(filters.page) + 1) * Number(filters.size) >= data.total ? ' disabled' : ''}>下一页</button><form id="t04-jump" class="toolbar"><label>跳至页码<input name="page" type="number" min="1" max="1000000" value="${Number(filters.page) + 1}" required></label><button>跳转</button></form></div>`}<details><summary>验证请求边界</summary><form id="t04-boundary" class="toolbar"><label>部门 ID<input name="departmentId" value="${e(filters.departmentId || R.user().departmentId)}"></label><label>API 页码（从 0 起）<input name="page" value="${e(filters.page)}"></label><label>API 每页<input name="size" value="${e(filters.size)}"></label><button>发送模拟查询</button></form></details>${people ? json(people) : ''}</section></div>`;
  }
  function logsView() {
    return `<section class="panel"><h2>最小鉴权日志</h2><p class="muted">仅共同根范围且具备日志查询权限可查。复用游标快照；不提供归档、导出或业务变更历史。</p><form id="t04-logs" class="stack"><div class="toolbar"><label>用户名（精确）<input name="username" maxlength="100" value="${e(logFilters.username)}"></label><label>判定结果<select name="allowed"><option value="">全部结果</option><option value="true"${selected(logFilters.allowed,'true')}>允许</option><option value="false"${selected(logFilters.allowed,'false')}>拒绝</option></select></label><label>事件<select name="event"><option value="">全部事件</option>${['LOGIN_SUCCESS','LOGIN_FAILED','UNAUTHENTICATED','REQUEST_DENIED','AUTHORIZATION'].map(v => `<option${selected(logFilters.event,v)}>${v}</option>`).join('')}</select></label><label>每批<select name="limit">${[1,5,20,100,500].map(n => `<option${selected(logFilters.limit,n)}>${n}</option>`).join('')}</select></label><button class="primary">从首批查询</button><button type="button" id="t04-log-clear">清空筛选</button></div><details><summary>更多筛选与游标校验</summary><div class="toolbar"><label>操作者 ID<input name="userId" value="${e(logFilters.userId)}"></label><label>系统编码（精确）<input name="appCode" maxlength="191" value="${e(logFilters.appCode)}"></label><label>开始时间（UTC，含）<input name="from" value="${e(logFilters.from)}" placeholder="2026-11-07T00:00:00Z"></label><label>结束时间（UTC，不含）<input name="to" value="${e(logFilters.to)}" placeholder="2026-11-08T00:00:00Z"></label><label>验证游标<input name="cursor" maxlength="4096" placeholder="留空从首批查询"></label></div></details></form>${busy === 'logs' ? '<div class="empty" role="status" aria-busy="true">正在查询模拟日志…</div>' : logs?.status >= 400 ? error(logs) : `<p class="muted" role="status">快照内共 ${logs?.data.total ?? 0} 条 · 已载入 ${logRows.length} 条 · 快照 ${logs?.data.snapshotId ?? '—'} · ${logs?.data.hasMore ? '还有下一批' : '当前快照已到底'}</p><div class="table-wrap"><table><thead><tr><th>时间（UTC）</th><th>操作者</th><th>请求</th><th>结果与原因</th><th>事件 / requestId</th></tr></thead><tbody>${logRows.length ? logRows.map(log => `<tr><td>${e(log.occurredAt)}</td><td>${e(log.username || '身份未确定')}<br><span class="muted">${log.userId === null ? '—' : `#${log.userId}`}</span></td><td>${e(log.httpMethod)} ${e(log.requestPath)}</td><td>${log.allowed ? '允许' : '拒绝'} · ${e(log.httpStatus)}<br><span class="muted">${e(log.reason)}</span></td><td>${e(log.eventType)}<br><span class="muted">${e(log.requestId)}</span></td></tr>`).join('') : '<tr><td colspan="5" class="empty">当前条件没有鉴权记录，请调整筛选。</td></tr>'}</tbody></table></div><div class="actions"><button id="t04-log-next"${!logs?.data.hasMore ? ' disabled' : ''}>载入下一批</button><button id="t04-log-refresh">刷新快照</button></div>`}${logs ? json(logs) : ''}</section>`;
  }
  let model;
  R.views.t04 = () => {
    if (!R.user()) { owner = null; return loginView(); }
    if (owner !== R.actorId || model !== R.state) {
      owner = R.actorId; model = R.state; tab = 'people'; filters = {q:'',departmentId:'',enabled:'',page:0,size:5};
      departments = queryDepartments(); people = queryUsers(filters); logs = null; identity = null; logRows = []; logFilters = emptyLogFilters();
    }
    return `<h1>人员与鉴权</h1><p class="muted">模拟数据 · ${e(departmentName(R.user().departmentId))}及下级范围</p><div class="toolbar" role="group" aria-label="工作区"><button data-tab="people" aria-pressed="${tab === 'people'}">部门人员</button><button data-tab="logs" aria-pressed="${tab === 'logs'}">鉴权日志</button><button data-tab="identity" aria-pressed="${tab === 'identity'}">本人身份与权限</button></div>${tab === 'people' ? peopleView() : tab === 'logs' ? logsView() : `<section class="panel"><h2>本人身份</h2><p class="muted">本人查询仅要求有效身份；管理操作仍逐次校验具体权限及部门范围。</p><button id="t04-identity">刷新本人信息</button>${identity ? json(identity) : ''}</section>`}`;
  };
  function show(response) { R.notify(response.message, response.code, response.status); }
  function load(kind, append = false) {
    busy = kind; R.render();
    window.setTimeout(() => {
      busy = '';
      if (kind === 'people') { people = queryUsers(filters); departments = queryDepartments(); }
      else {
        logs = queryLogs(logFilters);
        if (logs.status === 200) logRows = append ? [...logRows, ...logs.data.items] : [...logs.data.items];
        else logRows = [];
      }
      R.render(); show(kind === 'people' ? people : logs);
    }, 120);
  }
  R.bindings.t04 = () => {
    document.querySelector('#t04-login')?.addEventListener('submit', event => {
      event.preventDefault(); const form = new FormData(event.currentTarget);
      const response = login(String(form.get('account')), String(form.get('password')));
      R.open('t04'); show(response);
    });
    document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => {
      tab = button.dataset.tab;
      if (tab === 'people') { people = queryUsers(filters); departments = queryDepartments(); }
      if (tab === 'logs') { logFilters.cursor = ''; logs = queryLogs(logFilters); logRows = logs.data?.items || []; }
      if (tab === 'identity') identity = queryIdentity();
      R.render(); if (tab === 'logs') show(logs);
    }));
    document.querySelectorAll('[data-department]').forEach(button => button.addEventListener('click', () => { filters.departmentId = button.dataset.department; filters.page = 0; load('people'); }));
    document.querySelectorAll('[data-person]').forEach(button => button.addEventListener('click', () => { R.selectedUserId = Number(button.dataset.person); R.open('t10'); }));
    document.querySelector('#t04-search')?.addEventListener('submit', event => {
      event.preventDefault(); const form = new FormData(event.currentTarget);
      filters = {...filters, q:String(form.get('q')), enabled:String(form.get('enabled')), size:Number(form.get('size')), page:0}; load('people');
    });
    document.querySelector('#t04-clear')?.addEventListener('click', () => { filters = {q:'',departmentId:'',enabled:'',page:0,size:5}; load('people'); });
    document.querySelector('#t04-prev')?.addEventListener('click', () => { filters.page = Number(filters.page) - 1; load('people'); });
    document.querySelector('#t04-next')?.addEventListener('click', () => { filters.page = Number(filters.page) + 1; load('people'); });
    document.querySelector('#t04-jump')?.addEventListener('submit', event => { event.preventDefault(); filters.page = Number(new FormData(event.currentTarget).get('page')) - 1; load('people'); });
    document.querySelector('#t04-boundary')?.addEventListener('submit', event => {
      event.preventDefault(); const form = new FormData(event.currentTarget);
      filters = {...filters, departmentId:String(form.get('departmentId')), page:Number(form.get('page')), size:Number(form.get('size'))}; load('people');
    });
    document.querySelector('#t04-outside')?.addEventListener('click', () => {
      const outside = R.state.departments.find(d => !R.inScope(d.id) && d.parentId === R.state.departments.find(d => d.id === R.user().departmentId)?.parentId);
      filters.departmentId = String(outside?.id || 999); filters.page = 0; load('people');
    });
    document.querySelector('#t04-logs')?.addEventListener('submit', event => {
      event.preventDefault(); const form = new FormData(event.currentTarget);
      Object.keys(logFilters).forEach(key => logFilters[key] = String(form.get(key) || ''));
      load('logs');
    });
    document.querySelector('#t04-log-clear')?.addEventListener('click', () => { logFilters = emptyLogFilters(); load('logs'); });
    document.querySelector('#t04-log-next')?.addEventListener('click', () => { logFilters.cursor = logs.data.nextCursor; load('logs', true); });
    document.querySelector('#t04-log-refresh')?.addEventListener('click', () => { logFilters.cursor = ''; load('logs'); });
    document.querySelector('#t04-identity')?.addEventListener('click', () => { identity = queryIdentity(); R.render(); });
    document.querySelector('[data-retry]')?.addEventListener('click', () => load(tab));
  };
})();
