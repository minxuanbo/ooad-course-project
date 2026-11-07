/* T10: personnel changes for contract review; no network or persistence. */
(() => {
  const R=window.R, esc=R.escape;
  const blank=()=>({actor:null,id:null,ids:[],query:'',departmentId:null,pending:null,error:null});
  const ui=blank();
  ['#reset','#logout'].forEach(selector=>document.querySelector(selector)?.addEventListener('click',()=>Object.assign(ui,blank())));
  const dto=user=>({id:user.id,username:user.username,name:user.name,departmentId:user.departmentId,enabled:user.enabled,roleIds:[...user.roleIds]});
  const departmentName=id=>R.state.departments.find(d=>d.id===id)?.name||'未知部门';
  function target(code,method,path,id,blockSelf=false) {
    R.guard(code,{method,path});
    if(!Number.isSafeInteger(id)||id<1)R.fail(400,'INVALID_ARGUMENT','人员 ID 必须为正安全整数');
    R.guard(code,{method,path,targetId:id,blockSelf});
    return R.state.users.find(user=>user.id===id);
  }
  function candidates() {
    const own=R.effective(R.user());
    return R.state.roles.filter(role=>role.enabled&&[...R.effective({enabled:true,roleIds:[role.id]})].every(id=>own.has(id)));
  }
  function currentRoles(id) {
    const user=target('rbac.users.read','GET','/api/users/{id}/roles',id);
    R.queryFailure();
    return {user:dto(user),roles:user.roleIds.map(roleId=>R.state.roles.find(role=>role.id===roleId)).filter(Boolean).map(role=>({id:role.id,code:role.code,name:role.name,enabled:role.enabled}))};
  }
  function assignableRoles(id,{q='',page=0,size=20}={}) {
    target('rbac.users.roles.write','GET','/api/users/{id}/assignable-roles',id);
    if(typeof q!=='string'||q.length>100)R.fail(400,'INVALID_ARGUMENT','搜索文本最多 100 字符');
    const data=R.pageItems(candidates().filter(role=>`${role.name} ${role.code}`.toLocaleLowerCase().includes(q.trim().toLocaleLowerCase())),page,size);
    R.queryFailure();
    return {...data,items:data.items.map(role=>({id:role.id,code:role.code,name:role.name,enabled:role.enabled}))};
  }
  function replaceRoles(id,ids) {
    target('rbac.users.roles.write','PUT','/api/users/{id}/roles',id,true);
    R.checkIds(ids,R.state.roles);
    const allowed=new Set(candidates().map(role=>role.id));
    if(ids.some(roleId=>!allowed.has(roleId)))R.fail(403,'MANAGEMENT_SCOPE_DENIED','角色已停用或有效权限超出本人，不能分配');
    R.atomic(next=>{next.users.find(user=>user.id===id).roleIds=[...ids];});
    return dto(R.state.users.find(user=>user.id===id));
  }
  function disable(id,body) {
    target('rbac.users.disable','PATCH','/api/users/{id}/status',id);
    if(!body||Array.isArray(body)||Object.keys(body).length!==1||body.enabled!==false)R.fail(400,'INVALID_ARGUMENT','本轮仅接受 {enabled:false}，不提供人员恢复');
    R.atomic(next=>{next.users.find(user=>user.id===id).enabled=false;});
    return dto(R.state.users.find(user=>user.id===id));
  }
  function transfer(id,departmentId) {
    const actor=R.user();
    target('rbac.users.department.write','PUT','/api/users/{id}/department',id,true);
    if(!Number.isSafeInteger(departmentId)||departmentId<1)R.fail(400,'INVALID_ARGUMENT','departmentId 必须为正安全整数');
    if(!R.state.departments.some(department=>department.id===departmentId))R.fail(R.isRoot(actor)?404:403,R.isRoot(actor)?'RESOURCE_NOT_FOUND':'MANAGEMENT_SCOPE_DENIED','目标部门不在可管理范围');
    R.guard('rbac.users.department.write',{method:'PUT',path:'/api/users/{id}/department',departmentId});
    R.atomic(next=>{next.users.find(user=>user.id===id).departmentId=departmentId;});
    return dto(R.state.users.find(user=>user.id===id));
  }
  function nextAccess(id,departmentId) {
    const user=target('rbac.users.read','GET','/api/users/{id}/roles',id);
    const status=!user.enabled?401:!R.apiAllowed(user,'GET','/api/users')||!R.inScope(departmentId,user)?403:200;
    return {loginAllowed:R.canEnter(user),status,code:status===200?'OK':status===401?'SESSION_INVALID':R.apiAllowed(user,'GET','/api/users')?'MANAGEMENT_SCOPE_DENIED':'PERMISSION_DENIED'};
  }
  R.t10={currentRoles,assignableRoles,replaceRoles,disable,transfer,nextAccess};
  function select(id) {
    const result=currentRoles(id);
    Object.assign(ui,{id,ids:[...result.user.roleIds],departmentId:result.user.departmentId,pending:null,query:'',error:null});
    R.selectedUserId=id;
  }
  function finish(action,message) {
    R.run(()=>{action();ui.pending=null;R.render();R.notify(message);});
  }
  function confirmPanel(user) {
    if(!ui.pending)return '';
    const change=ui.pending;
    const summary=change.kind==='roles'?`角色整体替换：${JSON.stringify(user.roleIds)} → ${JSON.stringify(change.ids)}${Array.isArray(change.ids)&&change.ids.length===0?'。空数组会撤销全部角色':''}`:change.kind==='disable'?'停用账号；保留原部门和角色关系':`所属部门：${departmentName(user.departmentId)} → ${departmentName(change.departmentId)}；角色关系保留`;
    return `<section class="panel" id="t10-confirmation" role="region" aria-label="确认人员变更"><h2>确认变更：${esc(user.name)}</h2><p>${esc(summary)}</p><p>保存时按最新权限、原/目标范围和最后有效管理员重新检查；全部成功或全部撤销。授权变更影响后续请求；无管理权限时再次登录拒绝。</p><div class="actions"><button type="button" id="t10-confirm" class="primary">确认提交</button><button type="button" id="t10-cancel">返回修改</button></div></section>`;
  }
  function roleEditor(user) {
    try {
      target('rbac.users.roles.write','GET','/api/users/{id}/assignable-roles',user.id);
    } catch(error) {
      return `<section class="panel"><h2>可分配角色</h2><p class="error">${esc(error.status)} · ${esc(error.code)} · ${esc(error.message)}</p><p>候选角色使用人员专用接口；部门管理员不会查询全局角色目录。</p></section>`;
    }
    const options=candidates();
    const filtered=options.filter(role=>`${role.name} ${role.code}`.toLocaleLowerCase().includes(ui.query.toLocaleLowerCase()));
    const excluded=ui.ids.filter(id=>!options.some(role=>role.id===id));
    return `<section class="panel"><h2>分配或撤销角色</h2><p>角色有效权限必须是本人有效权限的子集；保存时再次计算，不信任页面候选。</p><form id="t10-role-search" class="toolbar"><label>候选角色名称或编码<input name="q" maxlength="100" value="${esc(ui.query)}"></label><button type="submit">查询候选</button></form>
      <form id="t10-roles"><fieldset><legend>可分配角色 · 共 ${filtered.length} 项</legend><div class="checks">${filtered.map(role=>`<label><input type="checkbox" name="t10-role" value="${role.id}" ${ui.ids.includes(role.id)?'checked':''}>${esc(role.name)} <small class="muted">${esc(role.code)}</small></label>`).join('')||'<p class="empty">没有匹配的候选角色；清空搜索可重试。</p>'}</div></fieldset>
      ${excluded.length?`<p class="error">已选 ID ${esc(excluded.join(', '))} 当前不可分配。可清空或逐项移除；不能继续提交超权或停用角色。</p><div class="actions">${excluded.map(id=>`<button type="button" data-remove-role="${id}">移除已选 ID ${id}</button>`).join('')}</div>`:''}
      <output>将提交 ids：<code>${esc(JSON.stringify(ui.ids))}</code></output><div class="actions"><button type="submit">核对并保存角色</button><button type="button" id="t10-clear" class="danger">清空全部选择</button><button type="button" id="t10-reload">恢复已保存角色</button></div></form><p class="hint">清空后仍需确认提交。重复、未知和非法 ID 拒绝，不静默去重；失败保留原角色与草稿。</p></section>`;
  }
  function details() {
    if(ui.id==null)return '<section class="panel"><h2>人员详情</h2><p class="empty">从人员检索进入，或选择范围内人员。</p></section>';
    if(ui.error)return '<section class="panel" role="alert"><h2>人员角色读取失败</h2><p class="error">'+esc(ui.error.status)+' · '+esc(ui.error.code)+' · '+esc(ui.error.message)+'</p><button type="button" id="t10-retry">重试读取</button></section>';
    let user;
    try{user=target('rbac.users.read','GET','/api/users/{id}/roles',ui.id);}
    catch(error){return `<section class="panel"><h2>无法读取所选人员</h2><p class="error">${esc(error.status)} · ${esc(error.code)} · ${esc(error.message)}</p><p>重新选择范围内人员；范围外拒绝不暴露其角色、状态或是否存在。</p></section>`;}
    const roles=user.roleIds.map(id=>R.state.roles.find(role=>role.id===id));
    const scope=R.state.departments.filter(department=>R.inScope(department.id,user)).map(department=>department.name).join('、');
    return `${confirmPanel(user)}<section class="panel"><h2>${esc(user.name)}</h2><p class="muted">${esc(user.username)} · ID ${user.id} · ${esc(departmentName(user.departmentId))} · ${user.enabled?'启用':'停用'}</p><h3>已保存角色</h3>${roles.length?`<ul>${roles.map(role=>`<li>${esc(role?.name||'未知角色')} · ${role?.enabled?'启用':'停用'} <small class="muted">ID ${role?.id??'未知'}</small></li>`).join('')}</ul>`:'<p class="empty">当前没有角色。</p>'}<p class="hint">角色、权限、API 和系统共同决定实际访问；角色名和岗位不授予管理权。</p></section>
      ${roleEditor(user)}
      <section class="panel"><h2>受限人员变更</h2><form id="t10-transfer" class="toolbar"><label>目标部门<select name="departmentId">${R.state.departments.filter(department=>R.inScope(department.id)).map(department=>`<option value="${department.id}" ${department.id===ui.departmentId?'selected':''}>${esc(department.name)}</option>`).join('')}</select></label><button type="submit">核对调动</button><button type="button" id="t10-disable" class="danger" ${user.enabled?'':'disabled'}>停用人员</button></form><p>调动同时检查原部门及目标部门。部门管理员不能改自己的角色/部门，也不能修改有效全局管理员；停用不删除关系，本轮无恢复入口。</p></section>
      <section class="panel"><h2>变更后的模拟访问</h2><p>再次管理登录：<strong>${R.canEnter(user)?'具备至少一项有效管理操作权限':'拒绝：停用或无有效管理权限'}</strong>。</p><p>当前人员查询能力：${R.apiAllowed(user,'GET','/api/users')?'有':'无'}；部门范围：${esc(scope||'无')}。</p><form id="t10-probe" class="toolbar"><label>人员查询的部门 ID<input type="number" name="departmentId" min="1" step="1" value="${user.departmentId}" required></label><button type="submit">模拟所选用户的下一请求</button></form><p class="hint">仅演示 GET /api/users 的权限和范围重算，不提供代他人调用的正式接口，也不替代真实登录、AOP 或 HTTP 验收。完整有效权限页未新增。</p></section>`;
  }
  R.views.t10=()=>{
    R.guard('rbac.users.read',{method:'GET',path:'/api/users'});
    R.guard('rbac.users.read',{method:'GET',path:'/api/departments/tree'});
    if(ui.actor!==R.actorId)Object.assign(ui,{...blank(),actor:R.actorId});
    if(ui.id!==R.selectedUserId) {
      try{select(R.selectedUserId);}catch(error){ui.id=R.selectedUserId;ui.ids=[];ui.pending=null;ui.error=error;}
    }
    const available=R.state.users.filter(user=>R.inScope(user.departmentId)).sort((a,b)=>a.id-b.id);
    return `<section id="t10"><h1>人员角色与受限变更</h1><p>全部为内存模拟数据。验证范围内人员的角色整体替换、停用与双范围调动是否会越权；每次提交经过具体方法、路径和原子保护。</p>
      <section class="panel"><h2>选择人员</h2><form id="t10-person" class="toolbar"><label>本人及下级部门人员<select name="userId">${available.map(user=>`<option value="${user.id}" ${user.id===ui.id?'selected':''}>${esc(user.name)} · ${esc(departmentName(user.departmentId))}</option>`).join('')}</select></label><button type="submit">读取当前角色</button><button type="button" id="t10-back">返回人员检索</button></form><p class="hint">完整搜索与分页复用 T04 人员检索；此处不新增人员或组织 CRUD。</p></section>
      <div class="split"><div>${details()}</div><aside class="panel"><h2>评审边界请求</h2><p>可直接构造请求，验证隐藏按钮不能代替权限与范围校验。先选择并读取目标，再提交变更。</p>
      <form id="t10-direct-read" class="stack"><label>直接请求人员 ID<input name="id" type="number" min="1" step="1" value="${ui.id??4}" required></label><button type="submit">请求当前角色</button></form>
      <form id="t10-direct-write" class="stack"><label>操作<select name="kind"><option value="roles">角色整体替换</option><option value="disable">停用人员</option><option value="transfer">跨部门调动</option></select></label><label>角色 ids JSON<textarea name="ids" rows="2">[]</textarea></label><label>目标部门 ID<input name="departmentId" type="number" min="1" step="1" value="4"></label><button type="submit">构造待确认变更</button></form>
      <p class="hint">部门管理员 demo.department：本人 3 的角色/部门拒绝；下级 4 可修改；同级部门人员 8、全局维护者 1 均拒绝。普通员工通过 T04 登录拒绝。清空维护者 1 的角色、停用或移出根触发最后管理员保护。IDs 与共同根 1 均为模拟值。</p></aside></div></section>`;
  };
  R.bindings.t10=()=>{
    document.querySelector('#t10-person').addEventListener('submit',event=>{event.preventDefault();finish(()=>select(Number(new FormData(event.currentTarget).get('userId'))),'已读取当前角色');});
    document.querySelector('#t10-retry')?.addEventListener('click',()=>finish(()=>select(ui.id),'人员角色重新读取成功'));
    document.querySelector('#t10-back').addEventListener('click',()=>R.open('t04'));
    document.querySelector('#t10-direct-read').addEventListener('submit',event=>{event.preventDefault();finish(()=>select(Number(new FormData(event.currentTarget).get('id'))),'已读取当前角色');});
    document.querySelector('#t10-role-search')?.addEventListener('submit',event=>{event.preventDefault();finish(()=>{const q=String(new FormData(event.currentTarget).get('q')).trim();assignableRoles(ui.id,{q});ui.query=q;},'候选角色查询完成');});
    document.querySelectorAll('[name="t10-role"]').forEach(input=>input.addEventListener('change',()=>{const id=Number(input.value);ui.ids=(input.checked?[...ui.ids.filter(item=>item!==id),id]:ui.ids.filter(item=>item!==id)).sort((a,b)=>a-b);ui.pending=null;R.render();}));
    document.querySelectorAll('[data-remove-role]').forEach(button=>button.addEventListener('click',()=>{ui.ids=ui.ids.filter(id=>id!==Number(button.dataset.removeRole));ui.pending=null;R.render();}));
    document.querySelector('#t10-clear')?.addEventListener('click',()=>{ui.ids=[];ui.pending=null;R.render();});
    document.querySelector('#t10-reload')?.addEventListener('click',()=>finish(()=>select(ui.id),'已恢复保存值，未保存角色已放弃'));
    document.querySelector('#t10-roles')?.addEventListener('submit',event=>{event.preventDefault();R.run(()=>{target('rbac.users.roles.write','PUT','/api/users/{id}/roles',ui.id,true);ui.pending={kind:'roles',ids:[...ui.ids]};R.render();});});
    document.querySelector('#t10-transfer')?.addEventListener('submit',event=>{event.preventDefault();R.run(()=>{target('rbac.users.department.write','PUT','/api/users/{id}/department',ui.id,true);ui.departmentId=Number(new FormData(event.currentTarget).get('departmentId'));ui.pending={kind:'transfer',departmentId:ui.departmentId};R.render();});});
    document.querySelector('#t10-disable')?.addEventListener('click',()=>R.run(()=>{target('rbac.users.disable','PATCH','/api/users/{id}/status',ui.id);ui.pending={kind:'disable'};R.render();}));
    document.querySelector('#t10-direct-write').addEventListener('submit',event=>{event.preventDefault();R.run(()=>{
      const form=new FormData(event.currentTarget),kind=form.get('kind');
      const codes={roles:['rbac.users.roles.write','PUT','/api/users/{id}/roles'],disable:['rbac.users.disable','PATCH','/api/users/{id}/status'],transfer:['rbac.users.department.write','PUT','/api/users/{id}/department']};
      target(...codes[kind],ui.id,kind!=='disable');
      let ids=[];
      if(kind==='roles'){try{ids=JSON.parse(String(form.get('ids')));}catch(error){R.fail(400,'INVALID_ARGUMENT','ids 必须是有效 JSON 数组');}}
      ui.pending=kind==='roles'?{kind,ids}:kind==='transfer'?{kind,departmentId:Number(form.get('departmentId'))}:{kind};
      R.render();
    });});
    document.querySelector('#t10-cancel')?.addEventListener('click',()=>{ui.pending=null;R.render();});
    document.querySelector('#t10-confirm')?.addEventListener('click',()=>finish(()=>{
      const change=ui.pending;if(!change)return;
      if(change.kind==='roles'){replaceRoles(ui.id,change.ids);ui.ids=[...change.ids];}
      else if(change.kind==='disable')disable(ui.id,{enabled:false});
      else transfer(ui.id,change.departmentId);
    },'人员变更已完整保存；后续请求使用最新有效授权和部门范围'));
    document.querySelector('#t10-probe')?.addEventListener('submit',event=>{event.preventDefault();R.run(()=>{
      const departmentId=Number(new FormData(event.currentTarget).get('departmentId'));
      if(!Number.isSafeInteger(departmentId)||departmentId<1)R.fail(400,'INVALID_ARGUMENT','部门 ID 必须为正安全整数');
      const result=nextAccess(ui.id,departmentId);
      R.notify(`模拟所选用户 GET /api/users?departmentId=${departmentId}；再次登录${result.loginAllowed?'可准入':'拒绝'}`,result.code,result.status);
    });});
  };
})();
