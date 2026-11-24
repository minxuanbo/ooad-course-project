import {readFileSync, readdirSync, writeFileSync} from 'node:fs'
import {resolve, dirname} from 'node:path'
import {fileURLToPath} from 'node:url'
import {svg, box, edge, assertGeometry, output, contactSheet} from './uml-svg.mjs'

// 从当前Java声明取成员，按与DDD相同模块生成结构化类图；不引入产品依赖。
const root=resolve(dirname(fileURLToPath(import.meta.url)), '..')
const directory=resolve(root,'docs/design')
const javaRoot=resolve(root,'backend/src/main/java/com/municipal/rbaccore')
const files=readdirSync(javaRoot,{recursive:true}).filter(f=>f.endsWith('.java'))
const design=JSON.parse(readFileSync(resolve(directory,'ddd-diagrams.json'),'utf8'))
function source(name) {
  const matches=files.filter(f=>f.split(/[\\/]/).at(-1)===name+'.java')
  if(matches.length!==1)throw Error('Java类型必须唯一存在：'+name)
  return readFileSync(resolve(javaRoot,matches[0]),'utf8')
}
function methods(name,selected) {
  const code=source(name)
  return selected.map(method=>{
    const inherited=/extends\s+(?:JpaRepository|JpaSpecificationExecutor)/.test(code)&&
      ['findById','findAll','findAllById','count','saveAndFlush','saveAllAndFlush'].includes(method)
    const setter=method.startsWith('set')&&/@(?:lombok\.)?Setter\b/.test(code)&&
      new RegExp('\\b'+method[3].toLowerCase()+method.slice(4)+'\\s*[;=]').test(code)
    if(!code.includes(method+'(')&&!inherited&&!setter)throw Error('模块所选方法不存在：'+name+'/'+method)
    return '+ '+method+'(...)'
  })
}
function fields(name,selected) {
  const code=source(name)
  const indexed=new Map([...code.matchAll(/private\s+(?:final\s+)?([\w.<>,? ]+)\s+(\w+)\s*(?:;|=)/g)].map(m=>[m[2],m[1].trim().split('.').at(-1)]))
  const record=code.match(/public record \w+\(([^)]+)\)/)
  if(record)for(const field of record[1].split(',')){const [type,key]=field.trim().split(/\s+/);indexed.set(key,type)}
  return selected.map(key=>{
    if(!indexed.has(key))throw Error('模块所选字段不存在：'+name+'/'+key)
    return '- '+key+': '+indexed.get(key)
  })
}
// id -> Service、字段仓储名、实际字段类型、辅助字段名、辅助字段类型、相关实体/读模型。
const modules={
  onboarding:['StructuredOnboardingService','systems','ApplicationSystemJpaRepository','codec','OnboardingCodec','ApplicationSystemEntity'],
  identity:['StructuredAuthenticationService','users','UserJpaRepository','permissions','StructuredEffectivePermissionService','UserEntity'],
  personnel:['StructuredUserManagementService','users','UserJpaRepository','authority','StructuredPersonnelAuthority','UserEntity'],
  query:['StructuredUserQueryService','users','UserJpaRepository','scope','StructuredDepartmentScopeService','UserEntity'],
  role:['StructuredRoleService','roles','RoleJpaRepository','access','StructuredGlobalManagementAccess','RoleEntity'],
  resource:['StructuredResourceService','permissions','PermissionJpaRepository','access','StructuredGlobalManagementAccess','PermissionEntity'],
  department:['StructuredDepartmentService','departments','DepartmentJpaRepository','scope','StructuredDepartmentScopeService','DepartmentEntity'],
  overview:['StructuredOverviewService','users','UserJpaRepository','departments','StructuredDepartmentService','DepartmentResult'],
  audit:['StructuredAuditLogService','queries','AuditQueryGateway','scope','StructuredDepartmentScopeService','LogPageResult'],
  importing:['StructuredImportService','sources','ImportSourceJpaRepository','planner','SourceWorkbookReader','UserEntity'],
  bootstrap:['StructuredInitialAuthorizationService','roles','RoleJpaRepository','planner','RoleBootstrapPlanner','RoleEntity'],
  maintenance:['StructuredMaintenanceGuard','users','UserJpaRepository','permissions','StructuredEffectivePermissionService','UserEntity'],
  'api-resource':['StructuredResourceService','apis','ApiResourceJpaRepository','access','StructuredGlobalManagementAccess','ApiResourceEntity'],
  systems:['StructuredResourceService','systems','ApplicationSystemJpaRepository','applications','ManagedApplications','ApplicationSystemEntity'],
  authorization:['StructuredEffectivePermissionService','apis','ApiResourceJpaRepository','applications','ManagedApplications','PermissionEntity'],
  'management-access':['StructuredGlobalManagementAccess','permissions','StructuredEffectivePermissionService','scope','StructuredDepartmentScopeService','CurrentIdentity'],
}
// 每组只选用例实际需要的成员，不按源码出现顺序凑前几个方法。
const slices={
 onboarding:{repo:['findById','saveAndFlush'],model:['setConnectionStatus'],support:['fingerprint'],fields:['id','code','enabled','connectionStatus']},
 identity:{repo:['findByUsernameOrPhone','findById'],model:[],support:['canEnter','permissions'],fields:['id','username','passwordHash','enabled','department']},
 personnel:{repo:['findById','saveAndFlush'],model:['assignRole','removeRole','moveTo'],support:['snapshot','canManage','requireManage'],fields:['id','department','enabled','roles']},
 query:{repo:['count','findAll','withRoles'],model:[],support:['filteredIds','allowedIds'],fields:['id','username','name','department']},
 role:{repo:['existsByCode','findById','saveAndFlush'],model:['belongTo','grantPermission','revokePermission'],support:['require','write'],fields:['id','type','ownerDepartment','application','managementLevel','enabled','permissions']},
 resource:{repo:['findById','findAllById'],model:['changeType','moveTo','mapApi','unmapApi'],support:['require','write'],fields:['id','system','parent','type','enabled']},
 department:{repo:['siblingNameExists','existsByParentIdAndDeletedFalse','saveAndFlush'],model:['moveTo','setDeleted'],support:['allowedIds','trustedRootId','isGlobal'],fields:['id','parent','name','type','deleted']},
 overview:{repo:['countByDepartmentIdInAndEnabledTrue'],model:[],support:['list'],fields:['id','parentId','directUsers','totalUsers']},
 audit:{repo:['list'],model:[],support:['isGlobal'],fields:['items','total']},
 importing:{repo:['findAll','saveAllAndFlush'],model:['updateProfile'],support:['read'],fields:['username','passwordHash','department']},
 bootstrap:{repo:['saveAndFlush'],model:['grantPermission'],support:['plan'],fields:['code','managementLevel','permissions']},
 maintenance:{repo:['findByDepartmentIdAndEnabledTrue'],model:[],support:['routes'],fields:['id','enabled','department','roles']},
 'api-resource':{repo:['findById','count','findAll','saveAndFlush'],model:['setEnabled'],support:['require','write'],fields:['id','system','httpMethod','pathTemplate','enabled']},
 systems:{repo:['findByCode'],model:[],support:['codes'],fields:['id','code','name','enabled','connectionStatus']},
 authorization:{repo:['effectiveApis'],model:[],support:['codes'],fields:['system','type','enabled','apis']},
 'management-access':{repo:['allows'],model:[],support:['isGlobal'],fields:['id','departmentId']},
}
const manifest=[]
for(const item of design.entries) {
  const [service,repoRole,repository,supportRole,support,model]=modules[item.id]||[]
  if(!service)throw Error('缺少结构化模块：'+item.id)
  const selected=slices[item.id]
  const implementation=source(service)
  for(const [role,type]of[[repoRole,repository],[supportRole,support]]) {
    if(!new RegExp('(?:\\.|\\s)'+type+'\\s+'+role+'\\s*;').test(implementation))
      throw Error('图件字段不对应实际Service：'+service+'/'+role)
  }
  for(const operation of item.serviceMethods) {
    const method=operation.match(/^\+ (\w+)\(/)?.[1]
    if(!method||!implementation.includes(method+'('))throw Error('图件方法失效：'+service+'/'+method)
  }
  const boxes=[
    {id:'service',x:580,y:400,w:640,h:320,title:service,fields:[`- ${repoRole}: ${repository}`,`- ${supportRole}: ${support}`],methods:item.serviceMethods,center:true},
    {id:'port',x:580,y:105,w:640,h:190,title:'«interface» '+item.port,fields:[],methods:item.serviceMethods.slice(0,2)},
    {id:'repository',x:20,y:405,w:450,h:285,title:(/interface\s/.test(source(repository))?'«interface» ':'')+repository,fields:[],methods:methods(repository,selected.repo)},
    {id:'model',x:1330,y:405,w:450,h:Math.max(370,110+(selected.fields.length+selected.model.length)*28),title:model,fields:fields(model,selected.fields),methods:methods(model,selected.model)},
    {id:'support',x:580,y:850,w:640,h:240,title:(/interface\s/.test(source(support))?'«interface» ':'')+support,fields:[],methods:methods(support,selected.support)},
  ]
  const edges=[
    {from:'service',to:'port',kind:'realization',points:[[900,400],[900,295]]},
    {from:'service',to:'repository',kind:'association',points:[[580,550],[470,550]],role:[478,531,repoRole,16,'start',96]},
    {from:'service',to:'model',points:[[1220,550],[1330,550]]},
    {from:'service',to:'support',kind:'association',points:[[900,720],[900,850]],role:[750,788,supportRole]},
  ]
  assertGeometry(boxes,edges)
  const name='class-structured-'+item.id
  await output(directory,name,svg(1820,1120,'structured · '+item.title+'类图','Service居中；依赖来自当前Java字段，相关实体或读模型不冒充DDD领域对象。',edges.map(edge).join('')+boxes.map(box).join('')))
  manifest.push({id:item.id,name,service,repository,support,model,port:item.port,selected})
}
writeFileSync(resolve(directory,'structured-generated-manifest.json'),JSON.stringify(manifest,null,2)+'\n')
const index=process.argv.indexOf('--contact-sheet')
if(index>=0)await contactSheet(directory,manifest.map(x=>x.name),resolve(root,process.argv[index+1]))
console.log(JSON.stringify({structuredClassDiagrams:manifest.length,sourceChecked:true}))
