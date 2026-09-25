'use strict';
// 过程式组织：命名函数、顺序、分支、循环。记录对象仅作为数据容器。
let token = sessionStorage.getItem('rbacToken') || '';
let page = 'dashboard', pageNumber = 0, query = '', admin = false;
let editorKind, editorId, returnFocus;
const root = document.getElementById('app');
const nav = [['dashboard','工作台','◫'],['users','用户与授权','♙'],['roles','角色与权限','◇'],['apps','业务系统','▦'],['logs','鉴权日志','≡']];
function esc(value) { return String(value ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;'); }
async function api(path,method='GET',body) {
  let response;
  try { response=await fetch('./api'+path,{method:method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)}); }
  catch(error){throw Error('无法连接服务。请启动项目，通过 http://localhost:8081 访问。');}
  const text=await response.text();let data=null;
  if(text){try{data=JSON.parse(text);}catch(error){throw Error('接口返回了网页，请检查访问地址：http://localhost:8081。');}}
  if(!response.ok){if(response.status===401&&path!='/auth/login'){token='';sessionStorage.removeItem('rbacToken');renderLogin();}throw Error(data?.error||'请求失败：'+response.status);}
  return data;
}
function notify(message){document.getElementById('notice').textContent=message;document.getElementById('notice').className='visible';setTimeout(clearNotice,4500);}
function clearNotice(){document.getElementById('notice').className='';}
function renderLogin(message=''){
  root.innerHTML='<div class="login-layout"><section class="login-story"><div class="brand"><span class="mark">市</span> 市政集团 <small>数字化管理平台</small></div><div class="story"><div class="eyebrow">IDENTITY & ACCESS MANAGEMENT</div><h1>统一身份，<br>有序授权。</h1><p>连接人员、角色与业务系统，<br>让每一次访问都有据可依。</p><div class="diagram"><span>用户</span><i>──</i><span>角色</span><i>──</i><span>权限</span></div><small>统一认证 / 按角色授权 / 访问留痕</small></div><footer>市政公司统一权限管理系统</footer></section><section class="login-panel"><div class="login-card"><span class="pill">管理控制台</span><h2>欢迎回来</h2><p class="muted">登录账号，开始管理组织的访问权限。</p><form id="login"><label for="account">账号</label><input id="account" autocomplete="username" placeholder="输入工号、手机号或用户名" required><label for="password">密码</label><div class="password-field"><input id="password" autocomplete="current-password" type="password" placeholder="输入登录密码" required><button type="button" id="reveal" class="text-button">显示</button></div><p id="login-error" class="error" role="alert">'+esc(message)+'</p><button type="submit" class="primary login-submit">登录控制台 →</button></form><div class="demo"><strong>课程演示账号</strong><p>管理员 <code>admin</code> / <code>admin123</code></p><p>员工 <code>SG000001</code> / <code>123456</code></p></div></div><footer>统一权限管理 · RBAC0</footer></section></div>';
  document.getElementById('login').onsubmit=login;document.getElementById('reveal').onclick=revealPassword;
}
function revealPassword(){const input=document.getElementById('password');input.type=input.type==='password'?'text':'password';document.getElementById('reveal').textContent=input.type==='password'?'显示':'隐藏';}
async function login(event){
  event.preventDefault();const button=event.currentTarget.querySelector('[type=submit]');button.disabled=true;button.textContent='正在验证身份…';document.getElementById('login-error').textContent='';
  try{const data=await api('/auth/login','POST',{account:document.getElementById('account').value.trim(),password:document.getElementById('password').value});token=data.token;sessionStorage.setItem('rbacToken',token);await start();}
  catch(error){const box=document.getElementById('login-error');if(box)box.textContent=error.message;}
  finally{button.disabled=false;button.textContent='登录控制台 →';}
}
async function start(){
  const me=await api('/me');admin=false;for(const role of me.roles)if(role.code==='系统管理员')admin=true;
  let links='';for(const item of nav){if(item[0]==='logs'&&!admin)continue;links+='<button data-page="'+item[0]+'"><span>'+item[2]+'</span>'+item[1]+'</button>';}
  root.innerHTML='<div class="shell"><aside class="sidebar"><div class="brand"><span class="mark">市</span><div>统一权限管理<small>市政集团</small></div></div><div class="nav-label">管理空间</div><nav>'+links+'</nav><div class="sidebar-bottom"><span class="dot"></span> 统一权限服务<small>RBAC0 · 课程项目</small></div></aside><div class="workspace"><header class="topbar"><span>市政集团 <i>/</i> 权限管理平台</span><div class="profile"><span class="avatar">'+esc(me.user.name[0])+'</span>'+esc(me.user.name)+'<button id="logout" class="text-button">退出</button></div></header><section class="content"><div class="page-heading"><div><div class="eyebrow">ACCESS CONSOLE</div><h1 id="title"></h1><p id="description" class="muted"></p></div><button id="refresh" class="secondary">↻ 刷新数据</button></div><div id="view"></div><footer class="content-footer">市政公司统一权限管理系统<span>权限按角色合并 · 未授权访问默认拒绝</span></footer></section></div></div>';
  root.onclick=navigate;document.getElementById('logout').onclick=logout;document.getElementById('refresh').onclick=refresh;await show('dashboard');
}
function navigate(event){const button=event.target.closest('[data-page]');if(button){pageNumber=0;query='';show(button.dataset.page);}}
function refresh(){show(page);}
async function logout(){try{await api('/auth/logout','POST');token='';sessionStorage.removeItem('rbacToken');renderLogin();}catch(error){notify(error.message);}}
async function show(selected){
  page=selected;for(const item of nav)if(item[0]===page)document.getElementById('title').textContent=item[1];
  const descriptions={dashboard:'查看组织授权概况，快速进入常用管理操作。',users:'查找员工，分配与回收角色，查看当前授权。',roles:'维护角色的功能权限，统一管理访问范围。',apps:'查看已接入的业务系统与功能权限目录。',logs:'追踪每次后端鉴权的允许与拒绝结果。'};
  document.getElementById('description').textContent=descriptions[page];for(const b of root.querySelectorAll('[data-page]'))b.classList.toggle('active',b.dataset.page===page);
  const view=document.getElementById('view');view.innerHTML='<div class="empty">正在加载数据…</div>';
  try{if(page==='dashboard')await dashboard(view);else if(page==='users')await users(view);else if(page==='roles')await roles(view);else if(page==='apps')await apps(view);else await logs(view);}
  catch(error){if(view.isConnected)view.innerHTML='<div class="empty error" role="alert">'+esc(error.message)+'</div>';}
}
async function dashboard(view){
  const d=await api('/dashboard');const applications=await api('/applications');if(page!=='dashboard')return;
  let cards='';const metrics=[['users','组织用户','♙'],['roles','授权角色','◇'],['applications','接入系统','▦'],['permissions','操作权限','⌘']];
  for(const m of metrics)cards+='<article class="card metric"><div>'+m[1]+'<span>'+m[2]+'</span></div><strong>'+Number(d[m[0]]).toLocaleString('zh-CN')+'</strong><small>当前数据库统计</small></article>';
  let rows='';for(let i=0;i<Math.min(applications.length,5);i++){const a=applications[i];rows+='<div class="app-row"><span class="app-icon">'+esc(a.name[0])+'</span><div><strong>'+esc(a.name)+'</strong><small>'+a.permissionCount+' 项操作权限</small></div>'+badge(a.enabled)+'</div>';}
  view.innerHTML='<div class="welcome"><div><span class="pill">组织访问管理</span><h2>让权限清晰，让管理有序</h2><p>从用户到角色，再到业务功能，在同一处完成授权管理。</p><button class="primary" data-page="users">进入用户授权 →</button></div><div class="welcome-symbol" aria-hidden="true">◇</div></div><div class="metrics">'+cards+'</div><div class="dashboard-columns"><section class="card panel"><div class="section-title"><h2>业务系统</h2><button class="text-button" data-page="apps">查看全部 →</button></div>'+rows+'</section><section class="card panel"><h2>授权管理流程</h2><div class="step"><b>01</b><div><strong>选择组织用户</strong><p>通过工号、姓名或手机号查找员工。</p></div></div><div class="step"><b>02</b><div><strong>配置角色与权限</strong><p>用户可持有多个角色，有效权限取并集。</p></div></div><div class="step"><b>03</b><div><strong>查看鉴权结果</strong><p>每次权限检查保留允许或拒绝记录。</p></div></div><div class="summary-strip">'+d.departments+' 个部门 <span>·</span> '+d.authLogs+' 条鉴权记录</div></section></div>';
}
function badge(enabled){return '<span class="badge '+(enabled?'green':'')+'">'+(enabled?'已启用':'已停用')+'</span>';}
async function users(view){
  const data=await api('/users?q='+encodeURIComponent(query)+'&page='+pageNumber+'&size=20');if(page!=='users')return;
  let html='<section class="card"><form class="toolbar" id="search-form"><input id="search" aria-label="搜索用户" placeholder="搜索姓名、工号或手机号" value="'+esc(query)+'"><button class="primary">查询</button><span class="muted">每页 20 人</span></form><div class="table-scroll"><table><thead><tr><th>员工</th><th>工号</th><th>所属组织</th><th>岗位</th><th>当前角色</th><th>状态</th><th>操作</th></tr></thead><tbody>';
  for(const u of data)html+='<tr><td><strong>'+esc(u.name)+'</strong></td><td class="mono">'+esc(u.employeeNo)+'</td><td>'+esc(u.company)+'<small>'+esc(u.department)+'</small></td><td>'+esc(u.position)+'</td><td>'+esc(u.roles||'未分配角色')+'</td><td>'+badge(u.status==='ACTIVE')+'</td><td><button class="text-button edit-user" data-id="'+u.id+'" data-name="'+esc(u.name)+'">'+(admin?'分配角色':'查看角色')+'</button></td></tr>';
  if(!data.length)html+='<tr><td colspan="7" class="empty">未找到匹配的用户</td></tr>';
  view.innerHTML=html+'</tbody></table></div><div class="pagination"><span>第 '+(pageNumber+1)+' 页 · 本页 '+data.length+' 人</span><div><button id="previous" class="secondary" '+(pageNumber===0?'disabled':'')+'>上一页</button> <button id="next" class="secondary" '+(data.length<20?'disabled':'')+'>下一页</button></div></div></section>';
  document.getElementById('search-form').onsubmit=searchUsers;document.getElementById('previous').onclick=previous;document.getElementById('next').onclick=next;for(const b of view.querySelectorAll('.edit-user'))b.onclick=editUser;
}
function searchUsers(e){e.preventDefault();query=document.getElementById('search').value.trim();pageNumber=0;show('users');}
function previous(){pageNumber--;show('users');}function next(){pageNumber++;show('users');}
async function roles(view){const data=await api('/roles');if(page!=='roles')return;let html='<div class="role-grid">';for(const r of data)html+='<article class="card role-card"><span class="role-icon">◇</span>'+badge(r.enabled)+'<h2>'+esc(r.name)+'</h2><p class="muted">'+esc(r.description)+'</p><div class="role-counts"><div><strong>'+Number(r.userCount).toLocaleString('zh-CN')+'</strong><small>关联用户</small></div><div><strong>'+r.permissionCount+'</strong><small>操作权限</small></div></div><button class="secondary edit-role" data-id="'+r.id+'" data-name="'+esc(r.name)+'">'+(admin?'配置角色权限':'查看角色权限')+' →</button></article>';view.innerHTML=html+'</div>';for(const b of view.querySelectorAll('.edit-role'))b.onclick=editRole;}
async function apps(view){const data=await api('/applications');if(page!=='apps')return;let html='<div class="app-grid">';for(const a of data)html+='<article class="card app-card"><span class="app-icon">'+esc(a.name[0])+'</span>'+badge(a.enabled)+'<h2>'+esc(a.name)+'</h2><p class="muted">'+a.permissionCount+' 项操作权限</p><small>系统标识</small><code>'+esc(a.code)+'</code></article>';view.innerHTML=html+'</div>';}
async function logs(view){const data=await api('/authorization-logs?limit=100');if(page!=='logs')return;let html='<section class="card"><div class="toolbar"><strong>最近鉴权记录</strong><span class="muted">最近 100 条 · 时间为 UTC</span></div><div class="table-scroll"><table><thead><tr><th>鉴权时间</th><th>用户账号</th><th>目标系统</th><th>权限编码</th><th>结果</th><th>原因</th></tr></thead><tbody>';for(const l of data)html+='<tr><td class="nowrap">'+esc(l.occurredAt)+'</td><td>'+esc(l.username)+'</td><td>'+esc(l.appCode)+'</td><td class="code-cell">'+esc(l.permissionCode)+'</td><td><span class="badge '+(l.allowed?'green':'red')+'">'+(l.allowed?'允许':'拒绝')+'</span></td><td>'+esc(l.reason)+'</td></tr>';if(!data.length)html+='<tr><td colspan="6" class="empty">暂无鉴权记录，业务系统调用鉴权接口后将在此显示。</td></tr>';view.innerHTML=html+'</tbody></table></div></section>';}
async function editUser(event){await openEditor('users',event.currentTarget);}async function editRole(event){await openEditor('roles',event.currentTarget);}
async function openEditor(kind,button){
  returnFocus=button;editorKind=kind;editorId=Number(button.dataset.id);button.disabled=true;
  try{const data=await api('/'+kind+'/'+editorId+(kind==='users'?'/roles':'/permissions'));let options='';
    for(const item of data){const text=kind==='users'?item.name:item.appName+' / '+item.module+' / '+item.feature+' / '+item.action;options+='<label class="choice"><input type="checkbox" value="'+item.id+'" '+(item.assigned?'checked ':'')+(!admin?'disabled':'')+'><span>'+esc(text)+'</span></label>';}
    document.body.insertAdjacentHTML('beforeend','<dialog id="editor"><form id="editor-form"><header><div><small>授权配置</small><h2>'+esc(button.dataset.name)+'</h2></div><button type="button" id="close" class="secondary" aria-label="关闭">×</button></header><p class="muted">'+(kind==='users'?'选择用户持有的角色。取消所有选项可回收全部角色。':'搜索只筛选显示项，保存时保留其他已选权限。')+'</p><input id="option-search" aria-label="筛选授权选项" placeholder="筛选选项…"><div class="choices">'+options+'</div><p id="save-error" class="error" role="alert"></p><footer><span id="selected-count"></span><button type="button" id="cancel" class="secondary">取消</button>'+(admin?'<button class="primary" id="save">保存授权</button>':'')+'</footer></form></dialog>');
    const d=document.getElementById('editor');d.showModal();d.addEventListener('cancel',closeEditor);d.onchange=countSelected;document.getElementById('close').onclick=closeEditor;document.getElementById('cancel').onclick=closeEditor;document.getElementById('option-search').oninput=filterOptions;document.getElementById('editor-form').onsubmit=saveEditor;countSelected();
  }catch(error){notify(error.message);}finally{button.disabled=false;}
}
function countSelected(){document.getElementById('selected-count').textContent='已选择 '+document.querySelectorAll('#editor input[type=checkbox]:checked').length+' 项';}
function filterOptions(){const text=document.getElementById('option-search').value.toLowerCase();for(const label of document.querySelectorAll('.choice'))label.hidden=!label.textContent.toLowerCase().includes(text);}
function closeEditor(event){if(event)event.preventDefault();const d=document.getElementById('editor');if(d){d.close();d.remove();}if(returnFocus?.isConnected)returnFocus.focus();}
async function saveEditor(event){event.preventDefault();if(!admin)return;const ids=[];for(const i of document.querySelectorAll('#editor input[type=checkbox]:checked'))ids.push(Number(i.value));const b=document.getElementById('save');b.disabled=true;b.textContent='正在保存…';try{await api('/'+editorKind+'/'+editorId+(editorKind==='users'?'/roles':'/permissions'),'PUT',{ids:ids});closeEditor();notify('授权已保存');await show(page);}catch(error){const box=document.getElementById('save-error');if(box)box.textContent=error.message;}finally{b.disabled=false;b.textContent='保存授权';}}
async function initialize(){renderLogin();if(location.protocol==='file:'){renderLogin('请启动项目，通过 http://localhost:8081 访问；直接打开 HTML 无法登录。');return;}if(token){try{await start();}catch(error){renderLogin(error.message);}}}
initialize();
