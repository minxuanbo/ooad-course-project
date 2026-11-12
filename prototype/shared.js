/* In-memory review model only. No network or persistence. */
(() => {
  const operations = {
    'rbac.users.read': [['GET','/api/departments/tree'],['GET','/api/users'],['GET','/api/users/{id}/roles']],
    'rbac.users.roles.write': [['GET','/api/users/{id}/assignable-roles'],['PUT','/api/users/{id}/roles']],
    'rbac.users.disable': [['PATCH','/api/users/{id}/status']],
    'rbac.users.department.write': [['PUT','/api/users/{id}/department']],
    'rbac.resources.read': [['GET','/api/systems'],['GET','/api/permissions/tree'],['GET','/api/api-resources'],['GET','/api/permissions/{id}/apis']],
    'rbac.resources.write': [['PATCH','/api/permissions/{id}'],['PATCH','/api/api-resources/{id}'],['PUT','/api/permissions/{id}/apis']],
    'rbac.roles.read': [['GET','/api/roles'],['GET','/api/roles/{id}/permissions']],
    'rbac.roles.write': [['POST','/api/roles'],['PATCH','/api/roles/{id}']],
    'rbac.roles.permissions.write': [['PUT','/api/roles/{id}/permissions']],
    'rbac.logs.read': [['GET','/api/authorization-logs']]
  };
  const labels = ['人员检索','人员角色分配','人员停用','跨部门调动','资源目录查询','资源映射维护','角色查询','角色定义维护','角色权限配置','鉴权日志查询'];
  const kCodes = ['rbac.users.read','rbac.users.roles.write','rbac.resources.read','rbac.resources.write','rbac.roles.read','rbac.roles.write','rbac.roles.permissions.write'];
  function seed() {
    const apis = [], permissions = [{id:1,systemId:1,parentId:null,type:'MENU',code:'rbac.workspace',name:'权限管理',enabled:true,apiIds:[]}];
    Object.entries(operations).forEach(([code,routes],i) => {
      const apiIds = routes.map(([method,path]) => { const id=apis.length+1; apis.push({id,systemId:1,method,path,enabled:true}); return id; });
      permissions.push({id:i+2,systemId:1,parentId:1,type:'BUTTON',code,name:labels[i],enabled:true,apiIds});
    });
    // shortcut: foreign resource is an illegal-association fixture, replace only after T14 defines actual course operations.
    apis.push({id:99,systemId:2,method:'GET',path:'/mock/foreign-resource',enabled:true});
    permissions.push({id:99,systemId:2,parentId:null,type:'MENU',code:'mock.foreign',name:'外系统模拟资源（非课程契约）',enabled:true,apiIds:[99]});
    const ids = codes => permissions.filter(p=>codes.includes(p.code)).map(p=>p.id);
    const departments = [{id:1,parentId:null,name:'演示共同根'},{id:2,parentId:1,name:'工程部'},{id:3,parentId:2,name:'道路组'},{id:4,parentId:1,name:'财务部'},{id:5,parentId:2,name:'项目组'}];
    const roles = [
      {id:1,code:'demo-maintainer',name:'演示维护角色',enabled:true,permissionIds:permissions.filter(p=>p.systemId===1).map(p=>p.id)},
      {id:2,code:'demo-department',name:'演示部门管理',enabled:true,permissionIds:ids(['rbac.users.read','rbac.users.roles.write','rbac.users.disable','rbac.users.department.write'])},
      {id:3,code:'demo-employee',name:'演示普通员工',enabled:true,permissionIds:[1]},
      {id:4,code:'demo-log-reader',name:'演示全局查询',enabled:true,permissionIds:ids(['rbac.users.read','rbac.logs.read'])},
      {id:5,code:'demo-people-reader',name:'演示人员查询',enabled:true,permissionIds:ids(['rbac.users.read'])},
      {id:6,code:'demo-foreign',name:'外系统模拟角色',enabled:true,permissionIds:[99]}
    ];
    const users = [
      {id:1,username:'demo.root',name:'演示维护者',departmentId:1,enabled:true,roleIds:[1]},
      {id:2,username:'demo.logs',name:'演示日志查询者',departmentId:1,enabled:true,roleIds:[4]},
      {id:3,username:'demo.department',name:'演示工程管理员',departmentId:2,enabled:true,roleIds:[2]},
      {id:4,username:'demo.employee',name:'演示普通员工',departmentId:3,enabled:true,roleIds:[3]},
      {id:5,username:'demo.foreign',name:'演示外系统用户',departmentId:2,enabled:true,roleIds:[6]}
    ];
    ['林桥','陈路','许城','周明','吴河','赵建','郑岩','李景','王川','张岸'].forEach((name,i)=>users.push({id:i+6,username:`demo.person${i+1}`,name:`${name}（模拟）`,departmentId:[2,3,4,5][i%4],enabled:i!==9,roleIds:i%2?[3]:[5]}));
    users.forEach(user => { user.phone = `199000${String(user.id).padStart(5,'0')}`; });
    return {systems:[{id:1,code:'rbac',name:'本权限系统',enabled:true},{id:2,code:'mock',name:'外系统模拟项',enabled:true}],departments,roles,users,permissions,apis,logs:[]};
  }
  const R = window.R = {
    state:seed(), actorId:null, page:'t04', selectedUserId:4, views:{}, bindings:{}, failNext:false, operations, kCodes,
    escape:value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),
    fail(status,code,message){throw Object.assign(new Error(message),{status,code});},
    user(){return R.state.users.find(u=>u.id===R.actorId);},
    isRoot(user){return !!user && user.departmentId===1;},
    inScope(id,user=R.user(),state=R.state){
      if(!user)return false;
      const seen=new Set();
      while(id!=null&&!seen.has(id)){if(id===user.departmentId)return true;seen.add(id);id=state.departments.find(d=>d.id===id)?.parentId;}
      return false;
    },
    effective(user,state=R.state){
      if(!user?.enabled)return new Set();
      const roleIds=new Set(user.roleIds);
      const ids=new Set(state.roles.filter(r=>r.enabled&&roleIds.has(r.id)).flatMap(r=>r.permissionIds));
      return new Set(state.permissions.filter(p=>ids.has(p.id)&&p.enabled&&state.systems.some(s=>s.id===p.systemId&&s.enabled)&&p.apiIds.some(id=>state.apis.some(a=>a.id===id&&a.enabled&&a.systemId===p.systemId))).map(p=>p.id));
    },
    apiAllowed(user,method,path,state=R.state){
      const effective=R.effective(user,state);
      return state.apis.some(a=>a.enabled&&a.systemId===1&&a.method===method&&a.path===path&&state.permissions.some(p=>effective.has(p.id)&&p.systemId===1&&p.apiIds.includes(a.id)));
    },
    has(code,user=R.user(),state=R.state){return (operations[code]||[]).length>0&&operations[code].every(([m,p])=>R.apiAllowed(user,m,p,state));},
    canEnter(user,state=R.state){return Object.values(operations).flat().some(([m,p])=>R.apiAllowed(user,m,p,state));},
    isAdmin(user,state=R.state){return !!user?.enabled&&R.isRoot(user)&&kCodes.every(c=>R.has(c,user,state));},
    guard(code,{global=false,departmentId,targetId,blockSelf=false,method,path}={}){
      const actor=R.user();
      if(!actor)R.fail(401,'AUTHENTICATION_REQUIRED','请先用模拟账号登录');
      if(!actor.enabled)R.fail(401,'SESSION_INVALID','当前账号已停用');
      if(!(method&&path?R.apiAllowed(actor,method,path):R.has(code,actor)))R.fail(403,'PERMISSION_DENIED','没有执行该操作的有效权限');
      if(global&&!R.isRoot(actor))R.fail(403,'MANAGEMENT_SCOPE_DENIED','此操作仅限共同根范围');
      if(departmentId!==undefined&&!R.inScope(departmentId,actor))R.fail(403,'MANAGEMENT_SCOPE_DENIED','目标部门不在本人及下级范围');
      if(targetId!==undefined){
        const target=R.state.users.find(u=>u.id===targetId);
        if(!target){if(!R.isRoot(actor))R.fail(403,'MANAGEMENT_SCOPE_DENIED','目标不在可管理范围');R.fail(404,'RESOURCE_NOT_FOUND','人员不存在');}
        if(!R.inScope(target.departmentId,actor)||(!R.isRoot(actor)&&(R.isAdmin(target)||blockSelf&&actor.id===targetId)))R.fail(403,'MANAGEMENT_SCOPE_DENIED','不能修改该人员或自己的授权/部门');
      }
      return actor;
    },
    checkIds(ids,collection){
      if(!Array.isArray(ids)||ids.some(id=>!Number.isSafeInteger(id)||id<1)||new Set(ids).size!==ids.length)R.fail(400,'INVALID_ARGUMENT','ids 必须是无重复的正整数数组');
      if(ids.some(id=>!collection.some(x=>x.id===id)))R.fail(400,'INVALID_ARGUMENT','包含不存在的关联 ID');
      return ids;
    },
    pageItems(items,page=0,size=20){
      if(!Number.isSafeInteger(page)||page<0||!Number.isSafeInteger(size)||size<1||size>100)R.fail(400,'INVALID_ARGUMENT','page 必须非负；size 必须为 1–100');
      return {items:[...items].sort((a,b)=>a.id-b.id).slice(page*size,(page+1)*size),total:items.length,page,size};
    },
    queryFailure(){if(R.failNext){R.failNext=false;const toggle=document.querySelector('#fail-next');if(toggle)toggle.checked=false;R.fail(500,'INTERNAL_ERROR','模拟请求失败，可重试；保存状态未改变');}},
    atomic(mutate){
      const next=structuredClone(R.state);
      mutate(next);
      if(!next.users.some(u=>R.isAdmin(u,next)))R.fail(409,'LAST_ADMIN_REQUIRED','变更会失去最后一名完整维护者，已全部撤销');
      R.queryFailure();
      R.state=next;
    },
    record(eventType,allowed,reason,method,path,status,user=R.user()){
      const id=R.state.logs.length+1;
      R.state.logs.unshift({id,occurredAt:new Date().toISOString(),userId:user?.id??null,username:user?.username??null,appCode:'rbac',allowed,requestId:`demo-${id}`,reason,eventType,httpMethod:method,requestPath:path,httpStatus:status});
    },
    notify(message,code='OK',status=200){
      const node=document.querySelector('#feedback');if(!node)return;
      node.className=status>=400?'feedback error':'feedback';node.textContent=`${status} · ${code} · ${message}`;node.setAttribute('role',status>=400?'alert':'status');
    },
    run(action){try{return action();}catch(e){R.notify(e.message,e.code||'INTERNAL_ERROR',e.status||500);return null;}},
    open(page){R.page=page;R.render();},
    render(){
      const actor=R.user();
      document.querySelector('#identity').textContent=actor?`${actor.name} / ${R.state.departments.find(d=>d.id===actor.departmentId)?.name}`:'尚未登录';
      document.querySelectorAll('[data-page]').forEach(b=>b.setAttribute('aria-current',b.dataset.page===R.page?'page':'false'));
      try {
        document.querySelector('#view').innerHTML=R.views[R.page]?.()||'<h1>原型模块准备中</h1>';
        R.bindings[R.page]?.();
      } catch(e) {
        document.querySelector('#view').innerHTML='<h1>此业务暂不可访问</h1><p>用具备相应权限和范围的模拟账号重试。</p>';
        R.notify(e.message,e.code||'INTERNAL_ERROR',e.status||500);
      }
    },
    reset(){R.state=seed();R.actorId=null;R.failNext=false;document.querySelector('#fail-next').checked=false;R.page='t04';R.render();R.notify('模拟数据已重置，刷新页面也会清空全部改动');}
  };
  document.addEventListener('DOMContentLoaded',()=>{
    document.querySelectorAll('[data-page]').forEach(b=>b.addEventListener('click',()=>R.open(b.dataset.page)));
    document.querySelector('#logout').addEventListener('click',()=>{R.actorId=null;R.open('t04');R.notify('已退出模拟登录');});
    document.querySelector('#reset').addEventListener('click',R.reset);
    document.querySelector('#fail-next').addEventListener('change',e=>R.failNext=e.target.checked);
    R.render();
  });
})();
