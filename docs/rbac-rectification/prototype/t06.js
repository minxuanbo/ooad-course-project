/* T06: in-memory resource catalogue for contract review. */
(() => {
  const R=window.R, esc=R.escape;
  const ui={id:null,ids:[],query:'',page:0,size:5,error:null};
  const guard=(method,path)=>R.guard(method==='GET'?'rbac.resources.read':'rbac.resources.write',{global:true,method,path});
  function resource(id,items) {
    if(!Number.isSafeInteger(id)||id<1)R.fail(400,'INVALID_ARGUMENT','资源 ID 必须是正安全整数');
    const item=items.find(x=>x.id===id);
    if(!item)R.fail(404,'RESOURCE_NOT_FOUND','本权限系统内未找到该资源');
    if(item.systemId!==1)R.fail(403,'MANAGEMENT_SCOPE_DENIED','本轮只开放本权限系统资源');
    return item;
  }
  function read(systemId=1) {
    ['/api/systems','/api/permissions/tree','/api/api-resources','/api/permissions/{id}/apis'].forEach(path=>guard('GET',path));
    if(systemId!==1)R.fail(403,'MANAGEMENT_SCOPE_DENIED','本轮不开放外系统目录');
    R.queryFailure();
    return R.state.permissions.filter(p=>p.systemId===1);
  }
  function replaceApis(id,ids) {
    guard('PUT','/api/permissions/{id}/apis');
    const permission=resource(id,R.state.permissions);
    R.checkIds(ids,R.state.apis);
    if(ids.some(apiId=>R.state.apis.find(a=>a.id===apiId).systemId!==permission.systemId))R.fail(400,'INVALID_ARGUMENT','权限只能关联同系统 API');
    R.atomic(next=>{next.permissions.find(p=>p.id===id).apiIds=[...ids];});
  }
  function patchPermission(id,patch) {
    guard('PATCH','/api/permissions/{id}');
    const permission=resource(id,R.state.permissions);
    if(!patch||Array.isArray(patch)||!Object.keys(patch).length||Object.keys(patch).some(k=>!['parentId','type','enabled'].includes(k)))R.fail(400,'INVALID_ARGUMENT','只接受 parentId、type、enabled 的必要变更');
    const updated={...permission,...patch};
    if(!['MENU','BUTTON'].includes(updated.type)||typeof updated.enabled!=='boolean'||updated.parentId!==null&&(!Number.isSafeInteger(updated.parentId)||updated.parentId<1))R.fail(400,'INVALID_ARGUMENT','父节点、类型或启用状态格式不正确');
    const parent=updated.parentId===null?null:R.state.permissions.find(p=>p.id===updated.parentId);
    if(updated.parentId!==null&&!parent)R.fail(400,'INVALID_ARGUMENT','父权限不存在');
    if(parent&&parent.systemId!==permission.systemId)R.fail(400,'INVALID_ARGUMENT','权限父子必须属于同一系统');
    const seen=new Set([id]);
    for(let node=parent;node;node=R.state.permissions.find(p=>p.id===node.parentId)) {
      if(seen.has(node.id))R.fail(400,'INVALID_ARGUMENT','权限树不能自指或形成循环');
      seen.add(node.id);
    }
    if(parent?.type==='BUTTON'||updated.type==='BUTTON'&&R.state.permissions.some(p=>p.parentId===id))R.fail(400,'INVALID_ARGUMENT','按钮不能有子节点，有子节点的菜单不能改为按钮');
    R.atomic(next=>Object.assign(next.permissions.find(p=>p.id===id),patch));
  }
  function patchApi(id,patch) {
    guard('PATCH','/api/api-resources/{id}');
    resource(id,R.state.apis);
    if(!patch||Array.isArray(patch)||Object.keys(patch).length!==1||typeof patch.enabled!=='boolean')R.fail(400,'INVALID_ARGUMENT','API 仅接受 enabled 布尔值');
    R.atomic(next=>{next.apis.find(a=>a.id===id).enabled=patch.enabled;});
  }
  R.t06={read,replaceApis,patchPermission,patchApi};
  function select(id) {ui.id=id;ui.ids=[...resource(id,R.state.permissions).apiIds];}
  function finish(action,message) {R.run(()=>{action();ui.error=null;R.render();R.notify(message);});}
  function reload() {
    try{read();ui.error=null;R.render();R.notify('已重新查询本权限系统资源');}
    catch(error){ui.error=error;R.render();R.notify(error.message,error.code,error.status);}
  }
  function tree(nodes,parentId=null) {
    return nodes.filter(p=>p.parentId===parentId).map(p=>`<li class="permission-node"><button type="button" data-select-permission="${p.id}" aria-pressed="${ui.id===p.id}">${esc(p.name)} <small class="muted">${p.type==='MENU'?'菜单':'按钮'} · ${p.enabled?'启用':'停用'}</small></button>${nodes.some(c=>c.parentId===p.id)?`<ul>${tree(nodes,p.id)}</ul>`:''}</li>`).join('');
  }
  const references=id=>R.state.permissions.filter(p=>p.systemId===1&&p.apiIds.includes(id)).map(p=>p.name).join('、')||'尚无权限引用';
  function examples() {
    return `<details class="panel"><summary>评审用例：复用与拒绝</summary><p class="muted">模拟请求经过同一权限、范围、输入及原子保护入口。失败后保存数据不变。</p><div class="toolbar"><button type="button" data-t06="reuse">载入 API 复用示例</button><button type="button" data-t06="foreign-map">跨系统映射</button><button type="button" data-t06="duplicate">重复关系</button><button type="button" data-t06="cycle">树循环</button><button type="button" data-t06="button-child">按钮子节点</button><button type="button" data-t06="critical">停用关键管理 API</button><button type="button" data-t06="foreign-read">请求外系统目录</button></div><p class="hint">复用示例把资源树查询 API 加入“资源映射维护”的待保存选择；保存后可看到两个引用。其他用例尝试非法提交。关键 API 用例触发最后完整维护者保护。可切换模拟身份后复查无权拒绝。</p></details>`;
  }
  R.views.t06=()=>{
    try{['/api/systems','/api/permissions/tree','/api/api-resources','/api/permissions/{id}/apis'].forEach(path=>guard('GET',path));}
    catch(error){return `<section id="t06" class="stack"><h1>资源目录与 API 映射</h1><div class="panel"><h2>当前身份不能读取资源目录</h2><p>${esc(error.status)} · ${esc(error.code)} · ${esc(error.message)}</p><div class="actions"><button type="button" data-t06="reload">重试读取</button><button type="button" data-t06="denied-write">验证修改请求</button></div><p class="muted">资源目录读写均要求共同根范围和对应有效权限。根部门成员身份本身不授予维护权。</p></div></section>`;}
    const permissions=R.state.permissions.filter(p=>p.systemId===1);
    if(!permissions.some(p=>p.id===ui.id))select(permissions.find(p=>p.code==='rbac.resources.read')?.id??permissions[0].id);
    const selected=permissions.find(p=>p.id===ui.id), system=R.state.systems.find(s=>s.id===1);
    const candidates=R.state.apis.filter(a=>a.systemId===1&&`${a.method} ${a.path}`.toLowerCase().includes(ui.query.toLowerCase()));
    const page=R.pageItems(candidates,ui.page,ui.size);
    const saved=selected.apiIds.map(id=>R.state.apis.find(a=>a.id===id)).filter(Boolean);
    const parents=permissions.map(p=>`<option value="${p.id}" ${selected.parentId===p.id?'selected':''}>${esc(p.name)} · ${p.type}${p.id===selected.id?'（自身）':''}</option>`).join('');
    return `<section id="t06" class="stack"><div class="toolbar"><div><h1>资源目录与 API 映射</h1><p class="muted">${esc(system.name)} · ${esc(system.code)} · ${system.enabled?'启用':'停用'} · 全部为模拟数据</p></div><div class="actions"><button type="button" data-t06="reload">重新查询</button><button type="button" data-t06="save" class="primary">保存映射</button></div></div>
      ${ui.error?`<div class="panel" role="alert"><p>${esc(ui.error.status)} · ${esc(ui.error.code)} · 查询失败；下方保留上次数据。</p><button type="button" data-t06="reload">重试查询</button></div>`:''}
      <div class="split"><aside class="panel"><h2>菜单与按钮树</h2><p class="muted">父菜单只作导航，不自动授予子按钮 API。</p><ul class="tree">${tree(permissions)}</ul></aside>
      <section class="panel stack"><div><h2>${esc(selected.name)}</h2><p class="muted"><code>${esc(selected.code)}</code> · ID ${selected.id} · 系统 rbac</p></div>
      <form id="t06-permission-form" class="stack"><div class="toolbar"><label>父权限<select name="parentId"><option value="" ${selected.parentId===null?'selected':''}>根节点</option>${parents}</select></label><label>类型<select name="type"><option value="MENU" ${selected.type==='MENU'?'selected':''}>菜单</option><option value="BUTTON" ${selected.type==='BUTTON'?'selected':''}>按钮</option></select></label><label class="check"><input type="checkbox" name="enabled" ${selected.enabled?'checked':''}>启用权限</label></div><div class="actions"><button type="submit">保存节点设置</button></div></form>
      <div><h3>已保存 API 映射 · ${saved.length}</h3>${saved.length?`<ul>${saved.map(a=>`<li><code>${esc(a.method)} ${esc(a.path)}</code> · ${a.enabled?'启用':'停用'}</li>`).join('')}</ul>`:'<p class="empty">当前权限未映射 API，不提供接口访问能力。</p>'}</div><p class="hint">系统、权限编码和名称只读。停用保留关联；下一请求重算。所有写入均须保留至少一名拥有完整 K 的有效根部门用户。</p></section></div>
      <section class="panel stack"><div class="toolbar"><div><h2>为“${esc(selected.name)}”选择 API</h2><p class="muted">同系统 API · 已选 <span id="t06-selected-count">${ui.ids.length}</span> 项，选择跨页保留</p></div><form id="t06-search" class="toolbar"><label>方法或路径<input name="query" maxlength="100" value="${esc(ui.query)}" placeholder="GET 或 /api/roles"></label><button type="submit">搜索</button><button type="button" data-t06="reset-search">重置</button></form></div>
      <div class="table-wrap"><table><caption>API 方法、服务端路径模板及反向引用</caption><thead><tr><th>选择</th><th>方法与路径模板</th><th>当前引用权限（OR）</th><th>状态</th><th>必要维护</th></tr></thead><tbody>${page.items.length?page.items.map(a=>`<tr><td><input type="checkbox" data-api-id="${a.id}" aria-label="关联 ${esc(a.method)} ${esc(a.path)}" ${ui.ids.includes(a.id)?'checked':''}></td><td><code>${esc(a.method)} ${esc(a.path)}</code><br><small class="muted">rbac · API ${a.id}</small></td><td>${esc(references(a.id))}</td><td>${a.enabled?'启用':'停用'}</td><td><button type="button" data-toggle-api="${a.id}" class="${a.enabled?'danger':''}">${a.enabled?'停用':'启用'} API</button></td></tr>`).join(''):'<tr><td colspan="5" class="empty">没有匹配的 API；重置搜索可查看全部目录。</td></tr>'}</tbody></table></div>
      <div class="toolbar"><div class="actions"><button type="button" data-t06="previous" ${ui.page===0?'disabled':''}>上一页</button><span>第 ${ui.page+1} 页 · 共 ${page.total} 项 · 每页 ${ui.size} 项</span><button type="button" data-t06="next" ${(ui.page+1)*ui.size>=page.total?'disabled':''}>下一页</button></div><div class="actions"><button type="button" data-t06="clear">清空选择</button><button type="button" data-t06="restore">恢复已保存选择</button><button type="button" data-t06="save" class="primary">保存映射</button></div></div><p class="hint">清空选择后仍需保存才会提交空数组。保存整体替换本权限映射；失败不会改动原映射。同 API 被多个有效权限引用时，用户命中任一授权即可访问该 API，仍须通过服务端范围检查。</p></section>
      ${examples()}</section>`;
  };
  R.bindings.t06=()=>{
    const root=document.querySelector('#t06');
    root.addEventListener('change',event=>{
      const input=event.target.closest('[data-api-id]');
      if(!input)return;
      const id=Number(input.dataset.apiId);
      ui.ids=input.checked?[...ui.ids,id]:ui.ids.filter(x=>x!==id);
      root.querySelector('#t06-selected-count').textContent=ui.ids.length;
    });
    root.addEventListener('submit',event=>{
      event.preventDefault();
      if(event.target.id==='t06-search') {
        finish(()=>{read();const query=new FormData(event.target).get('query').trim();if(query.length>100)R.fail(400,'INVALID_ARGUMENT','搜索最多100个字符');ui.query=query;ui.page=0;},'查询完成');
      } else if(event.target.id==='t06-permission-form') {
        const form=new FormData(event.target),enabled=form.has('enabled');
        const selected=R.state.permissions.find(p=>p.id===ui.id);
        if(selected.enabled&&!enabled&&!window.confirm('停用权限会使其 API 授权失效，关联会保留。确认提交？'))return;
        finish(()=>patchPermission(ui.id,{parentId:form.get('parentId')===''?null:Number(form.get('parentId')),type:form.get('type'),enabled}),'节点设置已保存');
      }
    });
    root.addEventListener('click',event=>{
      const row=event.target.closest('[data-select-permission]');
      if(row){finish(()=>{read();select(Number(row.dataset.selectPermission));},'已查询该权限及 API 映射');return;}
      const api=event.target.closest('[data-toggle-api]');
      if(api) {
        const item=R.state.apis.find(a=>a.id===Number(api.dataset.toggleApi));
        if(!window.confirm(`${item.enabled?'停用':'启用'} ${item.method} ${item.path}？引用该 API 的权限都会受影响；关联保留。`))return;
        finish(()=>patchApi(item.id,{enabled:!item.enabled}),'API 状态已保存');return;
      }
      const action=event.target.closest('[data-t06]')?.dataset.t06;
      if(!action)return;
      if(action==='reload'){reload();return;}
      if(action==='denied-write'){finish(()=>patchPermission(1,{enabled:true}),'写入已通过');return;}
      if(action==='save') {
        const selected=R.state.permissions.find(p=>p.id===ui.id);
        if(selected.apiIds.some(id=>!ui.ids.includes(id))&&!window.confirm('此次保存将撤销未选 API 的映射，确认整体替换？'))return;
        finish(()=>replaceApis(ui.id,ui.ids),'映射已整体保存');return;
      }
      if(action==='clear'){ui.ids=[];R.render();return;}
      if(action==='restore'){select(ui.id);R.render();return;}
      if(action==='reset-search'){finish(()=>{read();ui.query='';ui.page=0;},'已重置搜索');return;}
      if(action==='previous'||action==='next'){finish(()=>{read();ui.page+=action==='next'?1:-1;},'API 分页查询完成');return;}
      if(action==='reuse') {
        finish(()=>{
          read();select(R.state.permissions.find(p=>p.code==='rbac.resources.write').id);
          const shared=R.state.apis.find(a=>a.method==='GET'&&a.path==='/api/permissions/tree'&&a.systemId===1);
          if(!ui.ids.includes(shared.id))ui.ids.push(shared.id);
          ui.query='/api/permissions/tree';ui.page=0;
        },'已载入复用示例，点击“保存映射”后生效');return;
      }
      const workspace=R.state.permissions.find(p=>p.code==='rbac.workspace');
      const buttons=R.state.permissions.filter(p=>p.systemId===1&&p.type==='BUTTON');
      const attempts={
        'foreign-map':()=>replaceApis(ui.id,[99]),
        duplicate:()=>{const id=R.state.apis.find(a=>a.systemId===1).id;replaceApis(ui.id,[id,id]);},
        cycle:()=>patchPermission(workspace.id,{parentId:buttons[0].id}),
        'button-child':()=>patchPermission(buttons[0].id,{parentId:buttons[1].id}),
        critical:()=>patchApi(R.state.apis.find(a=>a.method==='PUT'&&a.path==='/api/permissions/{id}/apis').id,{enabled:false}),
        'foreign-read':()=>read(2)
      };
      if(attempts[action])finish(attempts[action],'模拟请求已通过');
    });
  };
})();
