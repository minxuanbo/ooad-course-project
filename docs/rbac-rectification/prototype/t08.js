/* T08 review only: shared roles, independent permission selection, atomic replacement. */
(() => {
  const R = window.R;
  const blank = () => ({actor:null,roleId:null,ids:[],name:'',enabled:true,q:'',status:'',page:0,creating:false,code:'',newName:'',pending:null});
  const ui = blank();
  ['#reset','#logout'].forEach(selector=>document.querySelector(selector)?.addEventListener('click',()=>Object.assign(ui,blank())));
  const esc = R.escape;
  const guard = (code,method,path) => R.guard(code,{global:true,method,path});
  const roleById = (id,state=R.state) => {
    if(!Number.isSafeInteger(id)||id<1)R.fail(400,'INVALID_ARGUMENT','角色 ID 必须是正安全整数');
    const role=state.roles.find(item=>item.id===id);
    if(!role)R.fail(404,'RESOURCE_NOT_FOUND','角色不存在');
    return role;
  };
  const text = (value,label) => {
    const clean=String(value??'').trim();
    if(!clean||clean.length>100)R.fail(400,'INVALID_ARGUMENT',`${label}须为 1–100 字符`);
    return clean;
  };
  function loadRole(id) {
    guard('rbac.roles.read','GET','/api/roles/{id}/permissions');
    const role=roleById(id);
    R.queryFailure();
    Object.assign(ui,{roleId:id,ids:[...role.permissionIds].sort((a,b)=>a-b),name:role.name,enabled:role.enabled,pending:null});
  }
  function validatePermissions(ids,state=R.state) {
    R.checkIds(ids,state.permissions);
    if(!state.systems.some(s=>s.id===1&&s.enabled)||ids.some(id=>{
      const p=state.permissions.find(item=>item.id===id);
      return p.systemId!==1||!p.enabled;
    }))R.fail(400,'INVALID_ARGUMENT','只能保存本权限系统的启用权限；移除已停用或外系统项后重试');
  }
  function permissionTree(parentId=null,seen=new Set()) {
    return R.state.permissions.filter(p=>p.systemId===1&&p.parentId===parentId).sort((a,b)=>a.id-b.id).map(p=>{
      if(seen.has(p.id))return '';
      const next=new Set(seen).add(p.id);
      return `<li class="permission-node"><label class="check"><input type="checkbox" name="t08-permission" value="${p.id}" ${ui.ids.includes(p.id)?'checked':''}><span>${esc(p.name)} <span class="muted">${p.type==='MENU'?'菜单':'按钮'} · ${esc(p.code)}${p.enabled?'':' · 已停用'}</span></span></label><ul>${permissionTree(p.id,next)}</ul></li>`;
    }).join('');
  }
  function navigation() {
    const parents=new Set();
    ui.ids.forEach(id=>{
      let node=R.state.permissions.find(p=>p.id===id&&p.systemId===1);
      const seen=new Set();
      while(node?.parentId!=null&&!seen.has(node.parentId)) {
        seen.add(node.parentId);
        node=R.state.permissions.find(p=>p.id===node.parentId&&p.systemId===1);
        if(node?.type==='MENU'&&!ui.ids.includes(node.id))parents.add(node.id);
      }
    });
    return [...parents].map(id=>R.state.permissions.find(p=>p.id===id).name).join('、')||'无需补全';
  }
  function permissionPanel(role) {
    try {
      guard('rbac.resources.read','GET','/api/permissions/tree');
    } catch(e) {
      return `<section class="panel"><h2>权限配置</h2><p class="error">${esc(e.status)} · ${esc(e.code)} · ${esc(e.message)}</p><p>权限目录需要独立的资源查询权限，当前不能读取或编辑权限树。</p></section>`;
    }
    const missing=ui.ids.filter(id=>!R.state.permissions.some(p=>p.id===id&&p.systemId===1));
    return `<section class="panel"><h2>权限配置</h2><p>各项独立选择。勾选父菜单不会连选子按钮；补出的祖先只用于导航。</p>
      ${!role.enabled?'<p class="hint">角色已停用。配置仍保留，但不会为用户提供有效权限；启用后重新计算。</p>':''}
      <form id="t08-permissions"><fieldset><legend>本权限系统 · 已选 ${ui.ids.length} 项</legend><ul class="tree">${permissionTree()}</ul></fieldset>
      ${missing.length?`<p class="error">当前目录之外的已选 ID：${esc(missing.join(', '))}。清空后可移除；不能继续提交外系统项。</p>`:''}
      <output id="t08-selection">将提交 ids：<code>${esc(JSON.stringify(ui.ids))}</code></output><p class="hint">仅为导航补全：${esc(navigation())}。这些祖先不会加入 ids。</p>
      <div class="actions"><button type="submit">核对并保存权限</button><button type="button" id="t08-clear" class="danger">清空所选</button><button type="button" id="t08-reload">重新读取已保存权限</button></div></form>
      <p class="hint">整体替换；空数组撤销全部权限。写入失败、越权或最后管理员保护触发时，已保存关系全部保持原值。</p></section>`;
  }
  function confirmation(role) {
    if(!ui.pending)return '';
    const change=ui.pending;
    return `<section class="panel" id="t08-confirmation" role="region" aria-label="确认角色变更"><h2>确认变更：${esc(role.name)}</h2>
      ${change.kind==='permissions'?`<p>权限整体替换为 ${change.ids.length} 项${change.ids.length?'':'，将撤销全部权限'}。</p><p>原 ids：<code>${esc(JSON.stringify(role.permissionIds))}</code></p><p>新 ids：<code>${esc(JSON.stringify(change.ids))}</code></p>`:`<p>名称：${esc(role.name)} → ${esc(change.name)}</p><p>状态：${role.enabled?'启用':'停用'} → ${change.enabled?'启用':'停用'}</p>`}
      <p>变更影响使用这个共享角色的所有用户，后续请求按新状态重新判定。最后一名完整维护者仍须保留。</p>
      <div class="actions"><button type="button" id="t08-confirm" class="primary">确认保存</button><button type="button" id="t08-cancel">返回修改</button></div></section>`;
  }
  function details() {
    if(ui.roleId==null)return '<section class="panel"><h2>角色详情</h2><p class="empty">选择一个角色，查看定义与当前已选权限。</p></section>';
    guard('rbac.roles.read','GET','/api/roles/{id}/permissions');
    const role=roleById(ui.roleId);
    return `${confirmation(role)}<section class="panel"><h2>${esc(role.name)}</h2><p class="muted">全局共享 · 角色 ID ${role.id} · 编码 ${esc(role.code)}</p>
      <form id="t08-definition" class="stack"><label>角色名称<input id="t08-name" name="name" maxlength="100" required value="${esc(ui.name)}"></label><label class="check"><input id="t08-enabled" name="enabled" type="checkbox" ${ui.enabled?'checked':''}>启用角色</label><p class="hint">停用保留权限配置，但使该角色的授权失效；改名不改变权限与管理范围。</p><div><button type="submit">核对并保存定义</button></div></form></section>${permissionPanel(role)}`;
  }
  R.views.t08 = () => {
    guard('rbac.roles.read','GET','/api/roles');
    if(ui.actor!==R.actorId)Object.assign(ui,{actor:R.actorId,roleId:null,ids:[],pending:null,creating:false});
    const q=ui.q.toLocaleLowerCase();
    const rows=R.state.roles.filter(role=>(!q||`${role.code} ${role.name}`.toLocaleLowerCase().includes(q))&&(ui.status===''||role.enabled===(ui.status==='true')));
    const page=R.pageItems(rows,ui.page,5);
    return `<h1>角色与权限配置</h1><p>角色在全部部门共享。角色目录和变更均要求共同根范围及对应接口权限；身份、岗位与角色名称本身不会授权。</p><div class="split"><section class="panel"><h2>角色目录</h2>
      <form id="t08-filter" class="toolbar"><label>名称或编码<input name="q" value="${esc(ui.q)}" maxlength="100"></label><label>角色状态<select name="status"><option value="">全部</option><option value="true" ${ui.status==='true'?'selected':''}>启用</option><option value="false" ${ui.status==='false'?'selected':''}>停用</option></select></label><button type="submit">查询</button></form>
      <p class="muted">共 ${page.total} 个角色 · 第 ${page.page+1} 页 · 每页 ${page.size} 个</p><div class="table-wrap"><table><thead><tr><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody>${page.items.map(role=>`<tr ${role.id===ui.roleId?'aria-selected="true"':''}><td>${esc(role.name)}<br><span class="muted">${esc(role.code)}</span></td><td>${role.enabled?'启用':'停用'}</td><td><button type="button" data-t08-role="${role.id}" ${role.id===ui.roleId?'aria-current="true"':''}>配置</button></td></tr>`).join('')||'<tr><td colspan="3" class="empty">没有符合条件的角色。</td></tr>'}</tbody></table></div>
      <div class="actions"><button type="button" id="t08-prev" ${ui.page===0?'disabled':''}>上一页</button><button type="button" id="t08-next" ${(ui.page+1)*5>=page.total?'disabled':''}>下一页</button><button type="button" id="t08-create-open">${ui.creating?'收起新建':'新建角色定义'}</button></div>
      ${ui.creating?`<form id="t08-create" class="stack"><h3>新建角色</h3><label>角色编码<input name="code" maxlength="100" required value="${esc(ui.code)}"></label><label>角色名称<input name="name" maxlength="100" required value="${esc(ui.newName)}"></label><p class="hint">新角色启用且无权限；编码创建后保持不变，再在右侧配置权限。不提供角色继承、版本或审批。</p><div><button type="submit">创建空权限角色</button></div></form>`:''}</section><div>${details()}</div></div>
      <details><summary>本页评审口径</summary><p>全局共享角色、共同根维护和整体替换是方案建议，待小组接受；不是老师逐项规定。管理端登录需要有效管理 API 权限，普通员工与仅父菜单账号拒绝。日志即使被授予，也仅允许有日志查询权限的共同根维护者查看；日志不加入完整维护能力 K。</p><p>全部为内存模拟，非正式前端。真实账号来源、JPA、AOP、读写事务、CloneFactory、锁和真实 HTTP 留对应后端票验证。</p></details>`;
  };
  R.bindings.t08 = () => {
    document.querySelector('#t08-filter').addEventListener('submit',event=>{event.preventDefault();R.run(()=>{
      guard('rbac.roles.read','GET','/api/roles');
      const form=event.currentTarget;
      R.queryFailure();
      Object.assign(ui,{q:form.elements.q.value.trim(),status:form.elements.status.value,page:0});R.render();
    });});
    document.querySelectorAll('[data-t08-role]').forEach(button=>button.addEventListener('click',()=>R.run(()=>{loadRole(Number(button.dataset.t08Role));R.render();})));
    ['prev','next'].forEach((name,i)=>document.querySelector(`#t08-${name}`).addEventListener('click',()=>R.run(()=>{
      guard('rbac.roles.read','GET','/api/roles');R.queryFailure();ui.page+=i?1:-1;R.render();
    })));
    document.querySelector('#t08-create-open').addEventListener('click',()=>R.run(()=>{
      guard('rbac.roles.write','POST','/api/roles');ui.creating=!ui.creating;R.render();
    }));
    document.querySelector('#t08-create')?.addEventListener('submit',event=>{event.preventDefault();R.run(()=>{
      guard('rbac.roles.write','POST','/api/roles');
      const form=event.currentTarget;
      const code=text(form.elements.code.value,'角色编码'),name=text(form.elements.name.value,'角色名称');
      ui.code=code;ui.newName=name;
      let created;
      R.atomic(next=>{
        if(next.roles.some(role=>role.code===code))R.fail(409,'DATA_CONFLICT','角色编码已存在');
        created={id:Math.max(0,...next.roles.map(role=>role.id))+1,code,name,enabled:true,permissionIds:[]};next.roles.push(created);
      });
      Object.assign(ui,{roleId:created.id,ids:[],name,enabled:true,code:'',newName:'',creating:false,pending:null,q:'',status:'',page:Math.floor((R.state.roles.length-1)/5)});
      R.render();R.notify('已创建空权限角色，尚未为任何用户赋权','OK',201);
    });});
    document.querySelector('#t08-name')?.addEventListener('input',event=>{ui.name=event.target.value;ui.pending=null;document.querySelector('#t08-confirmation')?.remove();});
    document.querySelector('#t08-enabled')?.addEventListener('change',event=>{ui.enabled=event.target.checked;ui.pending=null;document.querySelector('#t08-confirmation')?.remove();});
    document.querySelector('#t08-definition')?.addEventListener('submit',event=>{event.preventDefault();R.run(()=>{
      guard('rbac.roles.write','PATCH','/api/roles/{id}');roleById(ui.roleId);
      const form=event.currentTarget;
      ui.name=form.elements.name.value;ui.enabled=form.elements.enabled.checked;
      ui.pending={kind:'definition',id:ui.roleId,name:text(ui.name,'角色名称'),enabled:ui.enabled};R.render();
    });});
    document.querySelectorAll('[name="t08-permission"]').forEach(input=>input.addEventListener('change',()=>{
      const id=Number(input.value);
      ui.ids=(input.checked?[...ui.ids.filter(item=>item!==id),id]:ui.ids.filter(item=>item!==id)).sort((a,b)=>a-b);ui.pending=null;R.render();
    }));
    document.querySelector('#t08-clear')?.addEventListener('click',()=>{ui.ids=[];ui.pending=null;R.render();});
    document.querySelector('#t08-reload')?.addEventListener('click',()=>R.run(()=>{loadRole(ui.roleId);R.render();R.notify('已重新读取保存值，未保存草稿已放弃');}));
    document.querySelector('#t08-permissions')?.addEventListener('submit',event=>{event.preventDefault();R.run(()=>{
      guard('rbac.roles.permissions.write','PUT','/api/roles/{id}/permissions');roleById(ui.roleId);validatePermissions(ui.ids);
      ui.pending={kind:'permissions',id:ui.roleId,ids:[...ui.ids]};R.render();
    });});
    document.querySelector('#t08-cancel')?.addEventListener('click',()=>{ui.pending=null;R.render();});
    document.querySelector('#t08-confirm')?.addEventListener('click',()=>R.run(()=>{
      const change=ui.pending;
      if(!change)return;
      guard(change.kind==='permissions'?'rbac.roles.permissions.write':'rbac.roles.write',change.kind==='permissions'?'PUT':'PATCH',change.kind==='permissions'?'/api/roles/{id}/permissions':'/api/roles/{id}');
      R.atomic(next=>{
        const role=roleById(change.id,next);
        if(change.kind==='permissions'){validatePermissions(change.ids,next);role.permissionIds=[...change.ids];}
        else {role.name=text(change.name,'角色名称');role.enabled=change.enabled;}
      });
      ui.pending=null;R.render();R.notify('角色变更已完整保存；使用该角色的用户在后续请求按最新授权判定');
    }));
  };
})();
