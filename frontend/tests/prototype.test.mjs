import fs from 'node:fs'; import vm from 'node:vm'; import assert from 'node:assert/strict'; import { fileURLToPath } from 'node:url'; import test from 'node:test';
test('offline prototype uses one shell and enforces management views and scopes',()=>{
const dir=fileURLToPath(new URL('../../docs/原型/',import.meta.url));
const html=fs.readFileSync(dir+'06-界面原型.html','utf8');
const nodes=new Map();
function node(id){if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',className:'',open:false,close(){this.open=false},showModal(){this.open=true}});return nodes.get(id)}
const ctx=vm.createContext({console,setTimeout:()=>0,clearTimeout(){},document:{getElementById:node,querySelector:node,querySelectorAll:()=>[],addEventListener(){},createElement:()=>({}),head:{appendChild(){}}}});
for(const m of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g))if(m[1].trim())vm.runInContext(m[1],ctx);
vm.runInContext(fs.readFileSync(dir+'原型权限清单.js','utf8'),ctx);
vm.runInContext(fs.readFileSync(dir+'原型应用鉴权.js','utf8'),ctx);
vm.runInContext(fs.readFileSync(dir+'原型管理权限.js','utf8'),ctx);
vm.runInContext(fs.readFileSync(dir+'原型审计日志.js','utf8'),ctx);
let sidebar;
for(const page of ['portal','dashboard','users','roles','apps','logs','organizations']){
 vm.runInContext(`actor='admin';page='${page}';shell()`,ctx);
 const markup=node('app').innerHTML;
 const nav=markup.match(/<nav>(.*?)<\/nav>/s)[1];
 assert.equal((nav.match(/class="active"/g)||[]).length,1);
 assert(nav.includes(`data-id="${page}" class="active"`));
 const stable=markup.match(/<aside.*?<\/aside>/s)[0].replaceAll('class="active"','class=""');
 if(sidebar)assert.equal(stable,sidebar);sidebar=stable;
 assert(markup.includes('权限管理平台'));assert(markup.includes('class="avatar"'));
}
vm.runInContext("actor='admin';selectedApp=0;page='system';shell()",ctx);
assert(node('app').innerHTML.includes('data-id="portal" class="active"'));
vm.runInContext("actor='employee';page='logs';shell()",ctx);
assert.equal(vm.runInContext('page',ctx),'portal');
assert(!node('app').innerHTML.includes('data-id="logs"'));
console.log('PASS: shared shell, six menu states, application highlighting and employee log restriction');

vm.runInContext("actor='company';page='users';shell()",ctx);
assert(node('view').innerHTML.includes('道路工程分公司'));
assert(!node('view').innerHTML.includes('桥梁工程分公司'));
assert(!node('view').innerHTML.includes('管理权限与范围'));
for (const actor of ['company','department','deputy','project','projectDeputy','projectChief']) {
 vm.runInContext(`actor='${actor}';page='users';shell()`,ctx);
 assert(node('app').innerHTML.includes('data-id="users"'));
 assert(!node('app').innerHTML.includes('data-id="logs"'));
}
vm.runInContext("actor='department';page='organizations';shell()",ctx);
assert(node('view').innerHTML.includes('综合办公室'));
assert(!node('view').innerHTML.includes('项目一组'));
vm.runInContext("mgScopes[2]=[{roleId:2,scope:'DEPARTMENT',company:'道路工程分公司',department:'第一项目部'}];shell()",ctx);
assert(!node('view').innerHTML.includes('综合办公室'));
assert(node('view').innerHTML.includes('第一项目部'));
vm.runInContext("mgRolePermissions[2]=[];page='users';shell()",ctx);
assert.equal(vm.runInContext('page',ctx),'portal');
assert.equal(vm.runInContext('mgScopes[2].length',ctx),1);
assert.equal(vm.runInContext("actor='deputy';mgCan('user.read')",ctx),false);
assert.equal(vm.runInContext("actor='projectChief';mgCan('user.read')",ctx),true);
console.log('PASS: five existing roles, per-employee exact scopes, shared role operations and administrator-only logs');

// The editor stages changes until Save and only renders applicable scope fields.
vm.runInContext("initialData();actor='admin';mgEditor(2)",ctx);
assert(node('modal').innerHTML.includes('① 分配角色'));
assert(node('modal').innerHTML.includes('② 设置管理范围'));
assert(!node('modal').innerHTML.includes('name="addScope"'));
vm.runInContext("mgChange('scope',0,'COMPANY')",ctx);
assert(node('mg-editor-body').innerHTML.includes('data-mg-field="company"'));
assert(!node('mg-editor-body').innerHTML.includes('data-mg-field="department"'));
vm.runInContext("mgChange('company',0,'道路工程分公司');mgChange('scope',0,'DEPARTMENT')",ctx);
assert.equal(vm.runInContext('mgDraftScopes[0].company',ctx),undefined);
vm.runInContext("mgChange('company',0,'道路工程分公司')",ctx);
assert(node('mg-editor-body').innerHTML.includes('data-mg-field="department"'));
assert(!node('mg-editor-body').innerHTML.includes('value="工程技术部"'));
vm.runInContext("mgChange('department',0,'第一项目部');mgChange('company',0,'总公司')",ctx);
assert.equal(vm.runInContext('mgDraftScopes[0].department',ctx),undefined);
assert.equal(vm.runInContext('mgSaveAssignments()',ctx),false);
assert.equal(vm.runInContext('mgScopes[2][0].company',ctx),'总公司');
vm.runInContext("mgChange('assignedRole',0,'0',true);mgChange('roleId',0,'0')",ctx);
assert(!node('mg-editor-body').innerHTML.includes('data-mg-field="company"'));
assert(!node('mg-editor-body').innerHTML.includes('data-mg-field="department"'));
assert.equal(vm.runInContext('mgDraftScopes[0].company',ctx),undefined);
vm.runInContext("closeModal();mgEditor(2)",ctx);
assert.equal(vm.runInContext('mgDraftRoles.includes(0)',ctx),false);
vm.runInContext("mgAddScope();mgChange('scope',1,'COMPANY');mgChange('company',1,'道路工程分公司')",ctx);
assert.equal(vm.runInContext('mgScopes[2].length',ctx),1);
assert.equal(vm.runInContext('mgSaveAssignments()',ctx),true);
assert.equal(vm.runInContext('mgScopes[2].length',ctx),2);
vm.runInContext("mgChange('assignedRole',0,'2',false)",ctx);
assert.equal(vm.runInContext('mgDraftScopes.length',ctx),0);
assert.equal(vm.runInContext('mgScopes[2].length',ctx),2);
console.log('PASS: conditional scope fields, company-linked departments, separate sections and save/cancel drafts');

vm.runInContext("initialData();actor='admin';for(let i=0;i<25;i++)prototypeAudit('audit-user','LOGIN_FAILED',false,'LOGIN_FAILED');logsView(document.getElementById('view'))",ctx);
assert.equal(vm.runInContext('auditSnapshot.length',ctx),25);
assert(node('view').innerHTML.includes('加载更多'));
vm.runInContext("prototypeAudit('new-user','UNAUTHENTICATED',false,'AUTHENTICATION_REQUIRED')",ctx);
assert.equal(vm.runInContext('auditSnapshot.length',ctx),25);
for(const key of ['user','app','result','event','from','to','storage'])node('audit-'+key).value=key==='storage'?'ALL':key==='user'?'audit-user':'';
vm.runInContext('auditSearch()',ctx);assert.equal(vm.runInContext('auditSnapshot.length',ctx),25);
vm.runInContext("for(const l of logs)l.at='2025-01-01T00:00:00.000Z'",ctx);
node('audit-before').value='2026-01-01T00:00';vm.runInContext('auditArchive()',ctx);
assert.equal(vm.runInContext('logs.filter(l=>l.archived).length',ctx),26);
node('audit-storage').value='ACTIVE';vm.runInContext('auditSearch()',ctx);assert.equal(vm.runInContext('auditSnapshot.length',ctx),0);
node('audit-storage').value='ARCHIVED';vm.runInContext('auditSearch()',ctx);assert.equal(vm.runInContext('auditSnapshot.length',ctx),25);
vm.runInContext('initialData()',ctx);assert.equal(vm.runInContext('auditSnapshot.length',ctx),0);
console.log('PASS: audit snapshot pagination, filter matching, retained archive and reset');

});
