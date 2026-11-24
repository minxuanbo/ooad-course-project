import { test, expect } from '@playwright/test'

/** 真实Vue配可控HTTP，不连接正式服务。 */
async function setup(page, mode = 'admin') {
  const routes = [{method:'POST',path:'/api/auth/login'},{method:'GET',path:'/api/systems/page'},
    {method:'GET',path:'/api/systems/{id}/onboarding'},
    {method:'PUT',path:'/api/roles/{id}/permissions'},
    ...['/import','/check','/complete'].map(suffix=>({method:'POST',path:'/api/systems/{id}/onboarding'+suffix}))]
  if (mode === 'reader') routes.splice(3)
  const user={id:1,username:'fixture',name:'管理员',managementLevel:5,globalScope:true,routes}
  const system={id:2,code:'documents',name:'文档系统',enabled:true,connectionStatus:'PENDING'}
  const plan={baseFingerprint:'a'.repeat(64),entryUrl:null,loginPermissionCode:'LOGIN',apis:[{method:'GET',path:'/documents',enabled:true}],permissions:[
    {code:'LOGIN',name:'登录应用',type:'BUTTON',parentCode:null,enabled:true,apiKeys:[]},
    {code:'DOC_MENU',name:'文档管理',type:'MENU',parentCode:null,enabled:true,apiKeys:['GET /documents']},
    {code:'DOC_READ',name:'查询文档',type:'BUTTON',parentCode:'DOC_MENU',enabled:true,apiKeys:['GET /documents']}],operations:[],roleGrants:[{roleId:3,permissionCodes:['LOGIN','DOC_MENU','DOC_READ']}]}
  const roles=[{id:3,code:'employee',name:'员工',type:'SYSTEM_BUILTIN',enabled:true}]
  const state={writes:[],checks:[],errors:[],system,plan}
  page.on('pageerror',error=>state.errors.push(error.message))
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname
    let data
    if(path==='/api/auth/login')data={token:'fixture-token',user,expiresIn:3600}
    else if(path==='/api/me'){
      if(mode==='refresh-failure' && state.writes.length){await route.fulfill({status:500,json:{code:'INTERNAL_ERROR',message:'身份刷新暂不可用'}});return}
      data={user}
    }
    else if(path==='/api/systems/page')data={items:[{id:2,code:'documents',name:'文档系统',enabled:true,connectionStatus:'PENDING'}],total:1,page:0,size:20}
    else if(path==='/api/systems/2/onboarding')data={system:state.system,plan:state.plan,roles}
    else if(path.endsWith('/onboarding/import'))data={operations:[{operationId:'listDocuments',method:'GET',path:'/documents',requiresPermission:true,permissionRoles:[]}]}
    else if(path.endsWith('/onboarding/check')){
      state.checks.push(route.request().postDataJSON());const active=state.system.connectionStatus==='CONNECTED'
      data={configurationValid:true,errors:[],protocolChecks:[{code:'LOGIN',status:active?'PASS':'PENDING',message:active?'真实应用登录成功':'待接入应用按预期拒绝登录，完成启用后自动复检',httpStatus:active?200:403}]}
    }else if(path.endsWith('/onboarding/complete')){
      const body=route.request().postDataJSON();state.writes.push(body)
      if(mode==='stale'){await route.fulfill({status:409,json:{code:'ONBOARDING_STALE',message:'配置已变化，请重新打开向导'}});return}
      state.plan={...body.plan,baseFingerprint:'b'.repeat(64)};state.system.connectionStatus='CONNECTED';data={system:state.system,plan:state.plan,roles}
    }else throw Error('Unexpected '+path)
    await route.fulfill({json:{code:'OK',data}})
  })
  await page.goto('/login')
  await page.getByLabel('工号或手机号',{exact:true}).fill('fixture')
  await page.getByLabel('登录密码',{exact:true}).fill('fixture-password')
  await page.getByRole('button',{name:'登录',exact:true}).click()
  await page.getByRole('navigation').getByRole('link',{name:'应用管理',exact:true}).click()
  return state
}

/** 原版本没有配置接入入口，先确认行为失败。 */
test('R35 管理员打开应用接入向导',async({page})=>{
  await setup(page)
  await page.getByRole('button',{name:'配置接入',exact:true}).click()
  await expect(page.getByRole('heading',{name:'应用接入向导',exact:true})).toBeVisible()
  await expect(page.getByLabel('系统编码',{exact:true})).toHaveValue('documents')
})

/** 各步均由真实组件执行，预览和检查期间不产生发布写入。 */
async function prepareCompletion(page,mode='admin'){
  const state=await setup(page,mode)
  await page.getByRole('button',{name:'配置接入',exact:true}).click()
  await page.getByRole('button',{name:'下一步',exact:true}).click()
  await page.getByLabel('OpenAPI内容',{exact:true}).fill(JSON.stringify({openapi:'3.0.3',paths:{'/documents':{get:{operationId:'listDocuments'}}}}))
  await page.getByRole('button',{name:'预览导入',exact:true}).click()
  await expect(page.getByRole('heading',{name:'导入预览',exact:true})).toBeVisible()
  expect(state.writes).toEqual([])
  await page.getByRole('button',{name:'应用到草稿',exact:true}).click()
  await page.getByRole('button',{name:'下一步',exact:true}).click()
  await page.getByLabel('权限名称',{exact:true}).fill('登录文档系统')
  await page.getByRole('button',{name:'下一步',exact:true}).click()
  await page.getByLabel('文档管理（DOC_MENU）',{exact:true}).uncheck()
  await expect(page.getByLabel('查询文档（DOC_READ）',{exact:true})).toBeChecked()
  await page.getByRole('button',{name:'下一步',exact:true}).click()
  await page.getByLabel('专用测试账号（可选）',{exact:true}).fill('fixture-probe')
  await page.getByLabel('测试密码',{exact:true}).fill('fixture-probe-secret')
  await page.getByRole('button',{name:'开始检查',exact:true}).click()
  await expect(page.getByText('待接入应用按预期拒绝登录，完成启用后自动复检',{exact:false})).toBeVisible()
  await expect(page.getByRole('button',{name:'下一步',exact:true})).toBeDisabled()
  await page.getByLabel('接入开发者已验证业务后端执行RBAC鉴权，并保留业务对象权限判断',{exact:true}).check()
  await page.getByLabel('已在专用测试环境验证授撤权立即生效、越权操作被拒绝',{exact:true}).check()
  await page.getByRole('button',{name:'下一步',exact:true}).click()
  expect(state.writes).toEqual([]);return state
}

/** 启用后自动复检，口令不进入发布请求和浏览器存储。 */
test('R35 六步向导完成后自动复检且测试口令不持久化',async({page},testInfo)=>{
  const state=await prepareCompletion(page)
  await expect(page.getByText('撤回：DOC_MENU',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'完成接入',exact:true}).click()
  await expect(page.getByRole('heading',{name:'已完成接入',exact:true})).toBeVisible()
  await expect(page.getByText('真实应用登录成功',{exact:false})).toBeVisible()
  expect(state.writes).toHaveLength(1);expect(state.checks).toHaveLength(2)
  expect(state.writes[0].businessConfirmed).toBe(true)
  expect(JSON.stringify(state.writes)).not.toContain('fixture-probe-secret')
  expect(await page.evaluate(()=>JSON.stringify({...localStorage}))).not.toContain('fixture-probe-secret')
  expect(state.errors).toEqual([])
  await page.screenshot({path:testInfo.outputPath('onboarding-completed.png'),fullPage:true})
})

/** 只读权限不因打开向导而扩大。 */
test('R35 只读向导保留无权按钮并置灰',async({page})=>{
  const state=await setup(page,'reader')
  await page.getByRole('button',{name:'配置接入',exact:true}).click()
  await expect(page.getByLabel('访问入口（可选）',{exact:true})).toBeDisabled()
  await page.getByRole('button',{name:'下一步',exact:true}).click()
  await expect(page.getByRole('button',{name:'预览导入',exact:true})).toBeDisabled()
  expect(state.writes).toEqual([]);expect(state.checks).toEqual([])
})

/** 409保留尚未发布的草稿，不显示完成。 */
test('R35 旧草稿409保留内容并提示重新打开',async({page})=>{
  const state=await prepareCompletion(page,'stale')
  await page.getByRole('button',{name:'完成接入',exact:true}).click()
  await expect(page.getByRole('alert')).toContainText('配置已变化')
  await expect(page.getByRole('heading',{name:'已完成接入',exact:true})).toHaveCount(0)
  expect(state.system.connectionStatus).toBe('PENDING');expect(state.errors).toEqual([])
})

/** 已发布后的身份刷新失败不能跳过复检或保留口令。 */
test('R35 发布后身份刷新失败仍复检并清空凭据',async({page})=>{
  const state=await prepareCompletion(page,'refresh-failure')
  await page.getByRole('button',{name:'完成接入',exact:true}).click()
  await expect(page.getByRole('heading',{name:'已完成接入',exact:true})).toBeVisible()
  await expect(page.getByText('真实应用登录成功',{exact:false})).toBeVisible()
  expect(state.checks).toHaveLength(2)
  await expect(page.getByText('本次测试凭据已清空。',{exact:true})).toBeVisible()
  expect(state.errors).toEqual([])
})
