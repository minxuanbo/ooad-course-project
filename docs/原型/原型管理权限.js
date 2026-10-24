'use strict';
// Offline model: role operations + per-employee role scopes. No network calls.
const mgCatalog = {'user.read':'人员查询','user.roles':'用户角色分配','role.read':'角色查看','role.permissions':'角色权限配置','org.read':'组织查看','org.write':'组织维护','app.read':'系统目录查看','audit.read':'审计日志查看','handover.start':'离职发起','handover.complete':'离职完成','management.grant':'管理授权配置'};
const mgRoutes = {dashboard:'user.read',users:'user.read',roles:'role.read',apps:'app.read',logs:'audit.read',organizations:'org.read'};
let mgScopes={},mgRolePermissions=[],mgEditing=1,mgOrganizations=[];
function mgId(){return {admin:0,employee:1,company:7,department:2,deputy:8,project:3,projectDeputy:4,projectChief:5}[actor];}
function mgName(){return actor==='admin'?'系统管理员':person(mgId())?.position+' · '+person(mgId())?.name;}
function mgEffective(){const uid=mgId(),roles=uid===0?[0]:person(uid)?.roles||[];return (mgScopes[uid]||[]).filter(s=>roles.includes(s.roleId)).flatMap(s=>(mgRolePermissions[s.roleId]||[]).map(permission=>({...s,permission})));}
function mgGlobal(permission){return mgEffective().some(g=>g.permission===permission&&g.scope==='GLOBAL');}
function mgScope(g,u){return g.scope==='GLOBAL'||u&&g.company===u.company&&(g.scope==='COMPANY'||g.department===u.department);}
function mgCan(permission,u){return !!actor&&mgEffective().some(g=>g.permission===permission&&(!u||mgScope(g,u)));}
function mgTarget(permission,u){return !!u&&mgCan(permission,u)&&(mgGlobal(permission)||(u.id!==mgId()&&!(mgScopes[u.id]||[]).some(s=>u.roles.includes(s.roleId)&&mgRolePermissions[s.roleId].length)));}
function mgPage(view){return ['portal','system'].includes(view)||mgCan(mgRoutes[view]);}
function mgSummary(){return [...new Set(mgEffective().map(g=>roleNames[g.roleId]+'：'+(g.scope==='GLOBAL'?'全平台':g.company+(g.scope==='DEPARTMENT'?' / '+g.department:''))))].join('；')||'无管理范围';}
const mgInitial=initialData;
initialData=function(){
  mgInitial();mgOrganizations=[];
  for(const u of users)if(!mgOrganizations.some(d=>d.company===u.company&&d.name===u.department))mgOrganizations.push({company:u.company,name:u.department,parent:''});
  const local=['user.read','user.roles','role.read','org.read','app.read','handover.start','handover.complete'];
  mgRolePermissions=[Object.keys(mgCatalog),[...local,'org.write'],local.slice(),local.slice(),[]];
  mgScopes={0:[{roleId:0,scope:'GLOBAL'}]};
  for(const u of users)mgScopes[u.id]=u.roles.filter(r=>r!==4).map(roleId=>({roleId,scope:roleId===1?'COMPANY':'DEPARTMENT',company:u.company,...(roleId===1?{}:{department:u.department})}));
};
currentName=function(){return mgName();};
let mgDraftRoles=[],mgDraftScopes=[];
function mgEditor(id){
  if(!mgGlobal('management.grant'))return notify('仅系统管理员可配置');mgEditing=Number(id);
  mgDraftRoles=person(id).roles.slice();mgDraftScopes=(mgScopes[mgEditing]||[]).map(g=>({...g}));
  openModal(person(id).name+' · 分配角色与管理范围','<div id="mg-editor-body">'+mgEditorMarkup()+'</div>','<button type="submit" class="primary">保存全部更改</button>','mg-form');
}
function mgEditorMarkup(){
  const roles=roleNames.map((n,i)=>'<label class="choice"><input type="checkbox" name="assignedRole" value="'+i+'" '+(mgDraftRoles.includes(i)?'checked':'')+'>'+esc(n)+'</label>').join('');
  const option=(value,label,selected)=>'<option value="'+esc(String(value))+'" '+(value===selected?'selected':'')+'>'+esc(label)+'</option>';
  const select=(field,i,options)=>'<select data-mg-field="'+field+'" data-index="'+i+'" required>'+options+'</select>';
  let rows='';
  for(const [i,g] of mgDraftScopes.entries()){
    rows+='<fieldset class="mg-scope-row"><legend>范围 '+(i+1)+'</legend><label>应用于哪个角色'+select('roleId',i,mgDraftRoles.map(r=>option(r,roleNames[r],g.roleId)).join(''))+'</label>';
    rows+='<label>范围类型'+select('scope',i,(g.roleId===0?['GLOBAL']:['COMPANY','DEPARTMENT']).map(t=>option(t,{GLOBAL:'全平台',COMPANY:'指定公司',DEPARTMENT:'指定部门／项目部'}[t],g.scope)).join(''))+'</label>';
    if(g.scope!=='GLOBAL')rows+='<label>公司'+select('company',i,option('','请选择公司',g.company||'')+[...new Set(mgOrganizations.map(d=>d.company))].map(c=>option(c,c,g.company)).join(''))+'</label>';
    if(g.scope==='DEPARTMENT')rows+='<label>部门／项目部'+select('department',i,option('','请选择部门／项目部',g.department||'')+mgOrganizations.filter(d=>d.company===g.company).map(d=>option(d.name,d.name,g.department)).join(''))+'</label>';
    rows+='<p class="muted">'+(g.scope==='GLOBAL'?'覆盖全平台，无需选择公司或部门。':g.scope==='COMPANY'?'覆盖所选公司的全部部门，无需另选部门。':'仅覆盖所选部门／项目部。请先选公司，再选部门。')+'</p>'+button('移除此范围','mg-remove',i,'text-button')+'</fieldset>';
  }
  return '<section class="mg-editor-section"><h3>① 分配角色</h3><p>决定这名员工能执行哪些操作。角色的权限定义在“角色与权限”中统一修改。</p><div class="mg-role-grid">'+roles+'</div></section><section class="mg-editor-section"><h3>② 设置管理范围</h3><p>为上方已选角色设置可管理的公司或部门。同一角色可以添加多个范围。</p>'+ (rows||'<p class="mg-empty">尚未设置管理范围。仅有角色时，业务功能权限生效，但该角色没有后台管理范围。</p>')+'<button type="button" class="secondary" data-action="mg-add" '+(!mgDraftRoles.length?'disabled':'')+'>＋ 添加范围</button><p class="muted">添加、移除和角色调整均在点击“保存全部更改”后生效；取消不会修改现有分配。撤销角色会同时移除其范围。</p></section>';
}
function mgRenderEditor(){document.getElementById('mg-editor-body').innerHTML=mgEditorMarkup();}
function mgChange(field,index,value,checked){
  if(field==='assignedRole'){
    const role=Number(value);mgDraftRoles=checked?[...new Set([...mgDraftRoles,role])]:mgDraftRoles.filter(r=>r!==role);
    mgDraftScopes=mgDraftScopes.filter(g=>mgDraftRoles.includes(g.roleId));
  }else{
    const g=mgDraftScopes[index];if(!g)return;
    if(field==='roleId'){g.roleId=Number(value);g.scope=g.roleId===0?'GLOBAL':'DEPARTMENT';delete g.company;delete g.department;}
    if(field==='scope'){g.scope=value;delete g.company;delete g.department;}
    if(field==='company'){g.company=value;delete g.department;}
    if(field==='department')g.department=value;
  }
  mgRenderEditor();
}
function mgAddScope(){
  if(!mgDraftRoles.length)return;const roleId=mgDraftRoles[0];
  mgDraftScopes.push({roleId,scope:roleId===0?'GLOBAL':'DEPARTMENT'});mgRenderEditor();
}
function mgSaveAssignments(){
  if(!mgGlobal('management.grant'))return false;
  for(const g of mgDraftScopes){
    if(!mgDraftRoles.includes(g.roleId)||(g.roleId===0)!==(g.scope==='GLOBAL')){notify('请检查角色与范围类型');return false;}
    if(g.scope!=='GLOBAL'&&!mgOrganizations.some(d=>d.company===g.company)){notify('请选择公司');return false;}
    if(g.scope==='DEPARTMENT'&&!mgOrganizations.some(d=>d.company===g.company&&d.name===g.department)){notify('请选择该公司下的部门');return false;}
  }
  person(mgEditing).roles=mgDraftRoles.slice();mgScopes[mgEditing]=mgDraftScopes.map(g=>({...g}));return true;
}
document.addEventListener('change',event=>{
  const t=event.target;if(t.form?.id!=='mg-form')return;
  if(t.name==='assignedRole')mgChange('assignedRole',0,t.value,t.checked);
  else if(t.dataset.mgField)mgChange(t.dataset.mgField,Number(t.dataset.index),t.value);
});
let mgRoleEditing=0;
function mgRoleEditor(id){
  if(!mgGlobal('role.permissions'))return;mgRoleEditing=Number(id);
  openModal(roleNames[id]+' · 管理操作权限','<p>修改会影响持有此角色且已配置范围的员工。范围仍按每位员工分别设置。</p>'+Object.entries(mgCatalog).map(([p,n])=>'<label class="choice"><input type="checkbox" name="managementPermission" value="'+p+'" '+(mgRolePermissions[id].includes(p)?'checked':'')+' '+(Number(id)!==0&&['audit.read','management.grant','role.permissions'].includes(p)?'disabled':'')+'>'+esc(n)+'</label>').join(''),'<button type="submit" class="primary">保存角色权限</button>','mg-role-form');
}
function organizationView(view){
  let rows='';for(const [i,d] of mgOrganizations.entries())if(mgCan('org.read',{company:d.company,department:d.name}))rows+='<tr><td>'+esc(d.company)+'</td><td>'+esc(d.name)+'</td><td>'+(mgCan('org.write',{company:d.company,department:d.name})?button('修改名称','mg-org-edit',i,'text-button'):'')+'</td></tr>';
  view.innerHTML='<section class="card panel"><h2>授权范围内的组织</h2><p>部门管理员仅可维护范围内已有部门；公司／平台管理员可新增部门。组织移动未开放。</p>'+(mgEffective().some(g=>g.permission==='org.write'&&g.scope!=='DEPARTMENT')?button('新增部门','mg-org-new'):'')+'<table><thead><tr><th>公司</th><th>部门</th><th>操作</th></tr></thead><tbody>'+rows+'</tbody></table></section>';
}
let mgOrgIndex=-1;
document.addEventListener('click',event=>{
  const b=event.target.closest('[data-action]');if(!b)return;const action=b.dataset.action,id=b.dataset.id;
  if(!['management-grant','mg-remove','mg-add','mg-org-edit','mg-org-new','mg-role-permissions'].includes(action))return;
  event.preventDefault();event.stopImmediatePropagation();
  if(action==='management-grant')return mgEditor(id);
  if(action==='mg-role-permissions')return mgRoleEditor(id);
  if(action==='mg-add'){if(mgGlobal('management.grant'))mgAddScope();return;}
  if(action==='mg-remove'){if(!mgGlobal('management.grant'))return;mgDraftScopes.splice(Number(id),1);mgRenderEditor();return;}
  mgOrgIndex=action==='mg-org-new'?-1:Number(id);
  const org=mgOrganizations[mgOrgIndex];
  if(org&&!mgCan('org.write',{company:org.company,department:org.name}))return;
  const companies=[...new Set(mgOrganizations.map(d=>d.company))].filter(company=>mgEffective().some(g=>g.permission==='org.write'&&(g.scope==='GLOBAL'||g.scope==='COMPANY'&&g.company===company)));
  openModal(org?'修改部门名称':'新增部门',(org?'<p>'+esc(org.company)+'</p>':'<label>公司<select name="company">'+companies.map(c=>'<option>'+esc(c)+'</option>').join('')+'</select></label>')+'<label>部门名称<input name="name" required maxlength="191" value="'+esc(org?.name||'')+'"></label>','<button class="primary" type="submit">保存演示组织</button>','mg-org-form');
},true);
document.addEventListener('submit',event=>{
  const id=event.target.id;if(!['mg-form','mg-org-form','mg-role-form'].includes(id))return;event.preventDefault();event.stopImmediatePropagation();
  const v=new FormData(event.target);
  if(id==='mg-role-form'){
    if(!mgGlobal('role.permissions'))return;const selected=v.getAll('managementPermission');
    if(mgRoleEditing===0&&!selected.includes('management.grant'))return notify('必须保留系统管理员的角色范围配置权限');
    if(mgRoleEditing!==0&&selected.some(p=>['audit.read','management.grant','role.permissions'].includes(p)))return notify('仅系统管理员可拥有此权限');
    mgRolePermissions[mgRoleEditing]=selected;closeModal();shell();return;
  }
  if(id==='mg-form'){if(mgSaveAssignments()){closeModal();shell();}return;}
  const name=String(v.get('name')||'').trim();if(!name)return;
  if(mgOrgIndex<0)mgOrganizations.push({company:v.get('company'),name,parent:''});
  else{const d=mgOrganizations[mgOrgIndex],before=d.name;if(!mgCan('org.write',{company:d.company,department:d.name}))return;d.name=name;for(const u of users)if(u.company===d.company&&u.department===before)u.department=name;for(const scopes of Object.values(mgScopes))for(const g of scopes)if(g.company===d.company&&g.department===before)g.department=name;for(const child of mgOrganizations)if(child.company===d.company&&child.parent===before)child.parent=name;}
  closeModal();shell();
},true);
initialData();loginView();
