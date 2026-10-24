'use strict';
// 纯离线课程原型：所有授权与日志只保存在页面内存，不发起网络请求。
const actionCodes={查看:'read',新增:'create',编辑:'update',删除:'delete',审批:'approve',导出:'export',配置:'configure'};
const catalogRoles=[[],[],[],[],[]];
let selectedModule='', operationBusy=false, requestSequence=0;
const originalInitialData=initialData, originalShell=shell;
function safeCode(value){return value.normalize('NFKC').replace(/[^\p{L}\p{N}]+/gu,'_').replace(/^_|_$/g,'').toLowerCase();}
function buildCatalog(){
  applications.length=0;permissions.length=0;
  let appName='',moduleName='';
  for(const row of PROTOTYPE_ROWS){
    if(row[0].trim())appName=row[0].trim();if(row[1].trim())moduleName=row[1].trim();
    const feature=row[2].trim();if(!appName||!feature)continue;
    let app=null;for(const a of applications)if(a.name===appName)app=a;
    if(!app){app={id:applications.length,name:appName,code:safeCode(appName),description:'功能目录来源于课程权限清单'};applications.push(app);}
    const roleActions=[],all=new Set();
    for(let r=0;r<5;r++){
      const set=new Set();for(const item of row[r+4].split(/[/、,，]/)){const value=item.trim();if(value&&value!=='无'){set.add(value);all.add(value);}}
      roleActions.push(set);
    }
    if(roleActions[0].has('全部'))for(const a of Object.keys(actionCodes))all.add(a);
    all.delete('全部');
    for(const action of all){
      const p={id:permissions.length,app:app.id,module:moduleName,feature,action,description:row[3],code:app.code+':'+safeCode(moduleName)+':'+safeCode(feature)+':'+(actionCodes[action]||safeCode(action))};
      permissions.push(p);
      for(let r=0;r<5;r++)if(roleActions[r].has('全部')||roleActions[r].has(action))catalogRoles[r].push(p.id);
    }
  }
}
buildCatalog();
initialData=function(){originalInitialData();rolePermissions=[];for(const ids of catalogRoles)rolePermissions.push(ids.slice());};
permissionLabel=function(p){return applications[p.app].name+' / '+p.module+' / '+p.feature+' / '+p.action;};
function currentName(){return admin()?'演示管理员':'示例员工甲';}
function canViewLogs(){return mgCan('audit.read');}
function canUse(p){return allowedPermissions().includes(p.id);}
function modeLabel(){return '离线演示 · 不连接后台';}
function localLog(a,p,allowed,requestId='',reason=''){
  logs.unshift({time:new Date().toLocaleString('zh-CN'),user:currentName(),app:a.name,code:p?.code||'system:enter',detail:p?p.module+' / '+p.feature+' / '+p.action:'系统准入',allowed,requestId,reason:reason||(allowed?'授权通过':'无权限或授权已回收')});
}
logEvent=function(a,allowed){localLog(a,null,allowed);};
function dropSession(){operationBusy=false;actor=null;selectedApp=null;selectedModule='';}
loginView=function(){
  actor=null;closeModal();
  document.querySelector('.prototype-bar span').textContent='纯离线演示 · 不连接后台 · 不执行业务操作';
  root.innerHTML='<div class="login-layout"><section class="login-story"><div class="brand"><span class="mark">市</span>市政集团 · 课程原型</div><div class="story"><div class="eyebrow">IDENTITY & ACCESS MANAGEMENT</div><h1>权限可见，<br>操作可验。</h1><p>从应用、模块到功能操作，<br>检查员工每一步的访问权限。</p><div class="diagram"><span>系统</span><i>—</i><span>模块</span><i>—</i><span>功能</span></div><small>'+applications.length+' 个系统 · '+permissions.length+' 项操作权限</small></div></section><section class="login-panel"><div class="login-card"><span class="pill">统一登录 · 界面原型</span><h2>欢迎回来</h2><form id="prototype-login"><label>演示身份<select id="identity"><option value="employee">普通员工 · 示例员工甲</option><option value="admin">系统管理员 · 全平台</option><option value="company">公司领导 · 道路工程分公司</option><option value="department">部长 · 总公司综合办公室</option><option value="deputy">副部长 · 总公司综合办公室</option><option value="project">项目经理 · 道路公司第一项目部</option><option value="projectDeputy">项目副经理 · 道路公司第一项目部</option><option value="projectChief">项目总工程师 · 桥梁公司第一项目部</option></select></label><p id="login-error" class="error" role="alert"></p><button class="primary login-submit" type="submit">登录并查看我的应用 →</button></form><div class="demo"><strong>演示边界</strong><p>授权来自 Excel 初始角色，可在原型管理页面修改。</p><p>无需账号密码或后台服务。操作仅模拟鉴权，日志仅管理员可见，刷新页面恢复初始数据。</p></div></div></section></div>';
};
shell=function(){
  if(!actor)return loginView();
  if(page==='system'&&(!applications[selectedApp]||!appCount(selectedApp))){
    page='portal';selectedApp=null;selectedModule='';
    notify('该系统无有效权限或授权已被回收，已返回我的应用。');
  }
  if(page==='logs'&&!canViewLogs())page='portal';
  // 所有视图使用同一壳层，菜单顺序、身份栏和页面布局保持一致。
  if(!['portal','system',...nav.map(item=>item[0])].includes(page))page='portal';
  originalShell();
  document.querySelector('.prototype-bar span').textContent='纯离线演示 · 不连接后台 · 刷新即重置';
  if(page==='dashboard'){
    const values=document.querySelectorAll('.metric strong');
    if(values[0])values[0].textContent=users.filter(u=>mgCan('user.read',u)).length;
    if(values[2])values[2].textContent=applications.length;
    if(values[3])values[3].textContent=permissions.length;
    const paragraphs=document.querySelectorAll('.dashboard-columns .panel:last-child p');
    if(paragraphs[0])paragraphs[0].textContent='人员统计受当前管理范围限制；使用虚构员工与 Excel 完整目录演示。';
  }
};
portal=function(view){
  let count=0,cards='';for(const a of applications){const n=appCount(a.id);if(!n)continue;count++;
    cards+='<article class="card app-card"><span class="app-icon">'+esc(a.name[0])+'</span>'+badge('有权限')+'<h2>'+esc(a.name)+'</h2><p>'+n+' 项操作已授权</p><p class="muted">系统 → 功能模块 → 功能点 → 操作</p>'+button('进入系统 →','enter',a.id,'primary')+'</article>';
  }
  view.innerHTML='<div class="welcome"><div><span class="pill">我的应用</span><h2>'+count+' 个系统有权限使用</h2><p>仅显示当前已获授权的系统。</p></div></div>'+demoControls()+(count?'<div class="app-grid">'+cards+'</div>':'<section class="card empty" role="status">'+'暂无可用应用，请联系管理员分配权限。'+'</section>');
};
function demoControls(){return '<div class="prototype-hint">离线授权来自角色权限并集。'+(!admin()?button(revokedDemo?'恢复演示权限':'模拟撤回全部权限','revoke'):'')+'</div>';}
function systemView(view){
  const a=applications[selectedApp];if(!a||!appCount(a.id)){page='portal';selectedApp=null;selectedModule='';return shell();}
  const modules=[];for(const p of permissions)if(p.app===a.id&&!modules.includes(p.module))modules.push(p.module);
  if(selectedModule&&!modules.includes(selectedModule))selectedModule='';
  const allowed=new Set(allowedPermissions());let moduleHTML='',features='';
  for(let i=0;i<modules.length;i++){
    let n=0,total=0;for(const p of permissions)if(p.app===a.id&&p.module===modules[i]){total++;if(allowed.has(p.id))n++;}
    moduleHTML+='<button class="module-tile '+(selectedModule===modules[i]?'selected':'')+'" data-action="module" data-id="'+i+'"><strong>'+esc(modules[i])+'</strong><small>'+n+' / '+total+' 项操作有权限</small></button>';
  }
  if(selectedModule){
    const groups=new Map();for(const p of permissions)if(p.app===a.id&&p.module===selectedModule){if(!groups.has(p.feature))groups.set(p.feature,[]);groups.get(p.feature).push(p);}
    for(const [feature,items] of groups){let buttons='',n=0;for(const p of items){const ok=allowed.has(p.id);if(ok)n++;buttons+='<button type="button" class="operation '+(ok?'granted':'denied')+'" data-action="operation" data-id="'+p.id+'" '+(!ok||operationBusy?'disabled':'')+' title="'+esc(ok?'点击后再次模拟鉴权':'无此操作权限')+'">'+esc(p.action)+(ok?'':' · 无权限')+'</button>';}
      features+='<article class="card feature-card"><div class="feature-heading"><h3>'+esc(feature)+'</h3>'+badge(n?'已授权 '+n+' 项':'未授权',n>0)+'</div><p class="muted">'+esc(items[0].description)+'</p><div class="operation-list">'+buttons+'</div></article>';
    }
  }
  view.innerHTML='<div class="prototype-breadcrumb">'+button('← 我的应用','navigate','portal')+'<span>'+esc(a.name)+(selectedModule?' / '+esc(selectedModule):'')+'</span></div>'+demoControls()+'<h2>功能模块 <small>选择模块查看功能点</small></h2><div class="module-grid">'+moduleHTML+'</div><section class="features"><h2>'+esc(selectedModule||'请选择上方功能模块')+'</h2>'+features+'</section>';
}
logsView=function(view){
  if(!canViewLogs()){view.innerHTML='<p class="error" role="alert">无权查看鉴权日志，仅系统管理员可访问。</p>';return;}
  let rows='';for(const l of logs)rows+='<tr><td>'+esc(l.time)+'</td><td>'+esc(l.user)+'</td><td>'+esc(l.app)+'<small>'+esc(l.detail)+'</small></td><td class="permission-code">'+esc(l.code)+'<small>'+esc(l.requestId)+'</small></td><td>'+badge(l.allowed?'允许':'拒绝',l.allowed)+'</td><td>'+esc(l.reason)+'</td></tr>';
  view.innerHTML='<section class="card"><div class="toolbar"><div><strong>本次演示鉴权日志</strong><p class="muted">'+'离线模拟记录，不写入后台数据库；切换身份保留记录，刷新或重置演示后清空。'+'</p><small>灰色按钮不可点击；每次进入系统和执行操作均按当前演示授权检查。</small></div></div><div class="table-scroll"><table><thead><tr><th>本地时间</th><th>用户</th><th>系统 / 功能</th><th>权限编码 / 请求号</th><th>结果</th><th>原因</th></tr></thead><tbody>'+(rows||'<tr><td colspan="6" class="empty">暂无鉴权记录，请进入应用并点击有权限的操作。</td></tr>')+'</tbody></table></div></section>';
};
appsView=function(view){let html='';for(const a of applications){let n=0;for(const p of permissions)if(p.app===a.id)n++;html+='<article class="card app-card"><h2>'+esc(a.name)+'</h2><p>'+n+' 项操作权限</p><p class="muted">已提供功能权限演示，非真实业务接入</p>'+button('查看功能目录','enter',a.id)+'</article>';}view.innerHTML='<div class="app-grid">'+html+'</div>';};
function enterSystem(id){
  if(!actor)return;
  selectedApp=Number(id);selectedModule='';const a=applications[selectedApp];if(!a)return;
  const admitted=appCount(a.id)>0;localLog(a,null,admitted);
  if(!admitted){page='portal';selectedApp=null;shell();notify('无权进入该系统。');return;}
  page='system';shell();
}
function runOperation(id){
  const p=permissions[Number(id)];if(!actor||!p||!canUse(p)||operationBusy)return;
  operationBusy=true;
  try{
    const allowed=canUse(p);
    localLog(applications[p.app],p,allowed,'demo-'+Date.now()+'-'+(++requestSequence));
    notify((allowed?'模拟鉴权通过：':'模拟鉴权拒绝：')+p.feature+' / '+p.action+'。未执行业务操作。');
  }finally{operationBusy=false;shell();}
}
// 捕获新增入口，避免旧离线事件重复处理。
document.addEventListener('click',function(event){
  const b=event.target.closest('[data-action],#reset-demo');if(!b)return;
  const action=b.dataset.action,id=b.dataset.id;
  if(!['navigate','enter','module','operation','refresh','revoke','logout'].includes(action)&&b.id!=='reset-demo')return;
  event.preventDefault();event.stopImmediatePropagation();if(b.disabled)return;
  if(action==='logout'||b.id==='reset-demo'){
    dropSession();if(b.id==='reset-demo'){initialData();revokedDemo=false;query='';company='';department='';pageNumber=0;}loginView();return;
  }
  if(!actor)return;
  if(action==='navigate'){page=id;selectedApp=null;selectedModule='';shell();}
  else if(action==='enter')enterSystem(id);
  else if(action==='module'){const names=[];for(const p of permissions)if(p.app===selectedApp&&!names.includes(p.module))names.push(p.module);selectedModule=names[Number(id)]||'';shell();}
  else if(action==='operation')runOperation(id);
  else if(action==='refresh')shell();
  else if(action==='revoke'&&!admin()){revokedDemo=!revokedDemo;shell();}
},true);
document.addEventListener('submit',function(event){
  if(event.target.id!=='prototype-login')return;event.preventDefault();event.stopImmediatePropagation();
  const identity=document.getElementById('identity').value;
  if(identity==='employee'&&person(1).state==='DEPARTED'){
    document.getElementById('login-error').textContent='该演示员工已离职停用，请选择管理员或重置演示。';return;
  }
  actor=identity;page='portal';selectedApp=null;selectedModule='';shell();
  let count=0,only=0;
  for(const a of applications)if(appCount(a.id)){count++;only=a.id;}
  if(count===1)enterSystem(only);
},true);
const extraStyle=document.createElement('style');extraStyle.textContent='.module-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px;margin:18px 0 28px}.module-tile{text-align:left;background:#fff;border:1px solid #dce4ef;border-radius:12px;padding:18px;color:#253957;min-height:90px}.module-tile small{display:block;margin-top:10px;color:#687b98}.module-tile.selected{border-color:#3865e8;background:#edf3ff;box-shadow:0 0 0 1px #3865e8}.feature-card{padding:22px;margin-bottom:14px}.feature-heading{display:flex;justify-content:space-between;gap:14px;align-items:center}.feature-heading h3{margin:0}.operation-list{display:flex;flex-wrap:wrap;gap:10px;margin-top:18px}.operation{padding:9px 17px;border-radius:8px;font-weight:600}.operation.granted{background:#eaf1ff;color:#2859cd;border:1px solid #bdd0ff}.operation:disabled{background:#f0f2f5;color:#8993a1;border:1px solid #e0e4e9;cursor:not-allowed;opacity:1;box-shadow:none}.prototype-breadcrumb{display:flex;gap:16px;align-items:center;margin-bottom:18px;flex-wrap:wrap}.features h2{margin-bottom:18px}.permission-code{max-width:340px;overflow-wrap:anywhere}.login-card label{display:block;margin-top:16px}[hidden]{display:none!important}';document.head.appendChild(extraStyle);
initialData();loginView();
