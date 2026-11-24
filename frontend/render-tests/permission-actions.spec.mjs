import { test, expect } from '@playwright/test'

const people = [
  { id: 2, username: 'fixture-member', name: '测试下级', departmentId: 1,
    departmentName: '测试部门', enabled: true, writable: true,
    roles: [{ id: 7, name: '测试角色', enabled: true }] },
  { id: 3, username: 'fixture-peer', name: '测试受保护人员', departmentId: 1,
    departmentName: '测试部门', enabled: true, writable: false, roles: [] },
]
const role = { id: 7, code: 'fixture-role', name: '测试角色', enabled: true, type: 'GLOBAL_CUSTOM' }
const permission = { id: 9, parentId: null, name: '测试菜单', code: 'fixture.menu',
  type: 'MENU', enabled: true, children: [] }

/** 执行正式Vue页面，HTTP仅用虚构夹具；未授予的请求直接记为越权测试失败。 */
async function fixture(page, grants, { revokeStatus = false } = {}) {
  const state = { calls: [], denied: [], errors: [], revoked: false, people: structuredClone(people) }
  const user = { id: 1, username: 'fixture-reader', name: '测试操作员', enabled: true,
    departmentId: 1, departmentName: '测试部门', managementLevel: 5, globalScope: true }
  const routes = () => grants.filter((grant) => !state.revoked || !grant.startsWith('PATCH '))
    .map((grant) => { const [method, path] = grant.split(' '); return { method, path } })
  page.on('pageerror', (error) => state.errors.push(error.message))
  await page.route('**/api/**', async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    state.calls.push({ method, path })
    let data
    if (path === '/api/auth/login') {
      data = { token: 'permission-render-fixture', user: { ...user, routes: routes() }, expiresIn: 3600 }
    } else if (path === '/api/me') data = { user: { ...user, routes: routes() } }
    else {
      const template = path.replace(/\/(users|roles|permissions)\/\d+/g, '/$1/{id}')
      if (!grants.includes(method + ' ' + template)) {
        state.denied.push({ method, path })
        await route.fulfill({ status: 403, json: { code: 'PERMISSION_DENIED', message: '未授权的夹具请求' } })
        return
      }
      if (method === 'PATCH' && path.endsWith('/status')) {
        if (revokeStatus) {
          state.revoked = true
          await route.fulfill({ status: 403, json: { code: 'PERMISSION_DENIED', message: '测试权限已撤销' } })
          return
        }
        const body = request.postDataJSON()
        expect(body).toEqual({ enabled: false })
        state.people.find((item) => path === `/api/users/${item.id}/status`).enabled = false
        data = null
      } else if (path === '/api/users') data = { items: state.people, total: state.people.length }
      else if (/\/users\/\d+\/roles$/.test(path))
        data = { roles: state.people.find((item) => path === `/api/users/${item.id}/roles`).roles,
          lockedRoleIds: [7] }
      else if (path === '/api/departments') data = [{ id: 1, parentId: null, name: '测试部门',
        type: 'DEPARTMENT', directUsers: 2, totalUsers: 2,
        canCreate: false, canEdit: false, canDelete: false }]
      else if (path === '/api/roles') data = { items: [role], total: 1 }
      else if (/\/roles\/\d+\/permissions$/.test(path)) data = { ids: [9], items: [permission] }
      else if (path === '/api/permissions/tree') data = [permission]
      else if (/\/permissions\/\d+\/apis$/.test(path))
        data = { ids: [11], items: [{ id: 11, method: 'GET', path: '/fixture', enabled: true }] }
      else if (path === '/api/api-resources')
        data = { items: [{ id: 11, method: 'GET', path: '/fixture', enabled: true }], total: 1 }
      else throw Error('夹具缺少已授权的响应：' + method + ' ' + path)
    }
    await route.fulfill({ status: 200, json: { code: 'OK', data } })
  })
  await page.goto('/login')
  await page.getByLabel('工号或手机号', { exact: true }).fill('fixture-reader')
  await page.getByLabel('登录密码', { exact: true }).fill('fixture-only-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '已进入权限管理工作区', exact: true })).toBeVisible()
  return state
}

/** 无权按钮必须真实disabled，原生点击也不能打开弹窗或发起新请求。 */
async function disabled(button) {
  await expect(button).toBeVisible()
  await expect(button).toBeDisabled()
  await button.evaluate((element) => element.click())
}

/** 人员只读入口仍可读现有角色，但写入与受保护人员操作保留置灰。 */
test('R17 人员只读按钮保留显示，角色弹窗的移除、查询和保存均置灰', async ({ page }) => {
  const state = await fixture(page, ['GET /api/users', 'GET /api/users/{id}/roles'])
  await page.getByRole('navigation').getByRole('link', { name: '人员管理', exact: true }).click()
  const row = page.getByRole('row').filter({ hasText: 'fixture-member' })
  await disabled(row.getByRole('button', { name: '调动归属', exact: true }))
  await disabled(row.getByRole('button', { name: '停用', exact: true }))
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await row.getByRole('button', { name: '查看角色', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await disabled(dialog.getByRole('button', { name: '移除ID 7', exact: true }))
  await disabled(dialog.getByRole('button', { name: '查询', exact: true }))
  await disabled(dialog.getByRole('button', { name: '保存完整集合', exact: true }))
  await expect(dialog.locator('footer').getByRole('button', { name: '关闭', exact: true })).toBeEnabled()
  await page.screenshot({ path: '../.local/disabled-permission-actions-20261124/personnel-readonly.png', fullPage: true })
  expect(state.calls.some((call) => call.path.includes('assignable-roles'))).toBe(false)
  expect(state.denied).toEqual([])
  expect(state.errors).toEqual([])
})

/** 单一写权限不带出其他操作；保护目标继续禁用，同一操作仍能正常提交。 */
test('R17 只授予停用时仅停用可用，受保护目标仍置灰', async ({ page }) => {
  const state = await fixture(page, ['GET /api/users', 'PATCH /api/users/{id}/status'])
  await page.getByRole('navigation').getByRole('link', { name: '人员管理', exact: true }).click()
  const row = page.getByRole('row').filter({ hasText: 'fixture-member' })
  await disabled(row.getByRole('button', { name: '查看角色', exact: true }))
  await disabled(row.getByRole('button', { name: '调动归属', exact: true }))
  await disabled(page.getByRole('row').filter({ hasText: 'fixture-peer' }).getByRole('button', { name: '停用', exact: true }))
  await row.getByRole('button', { name: '停用', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认停用', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await disabled(row.getByRole('button', { name: '停用', exact: true }))
  expect(state.calls.filter((call) => call.method === 'PATCH')).toHaveLength(1)
  expect(state.denied).toEqual([])
  expect(state.errors).toEqual([])
})

/** 定义和成员读取分别受权，不因可以查看权限集合而开放其他操作。 */
test('R17 角色页只读时新建、成员、编辑与启停置灰，查看权限仍可用', async ({ page }) => {
  const state = await fixture(page, ['GET /api/roles', 'GET /api/roles/{id}/permissions'])
  await page.getByRole('navigation').getByRole('link', { name: '角色与权限', exact: true }).click()
  await disabled(page.getByRole('button', { name: '新建角色', exact: true }))
  const row = page.getByRole('row').filter({ hasText: 'fixture-role' })
  for (const name of ['查看成员', '编辑', '停用']) await disabled(row.getByRole('button', { name, exact: true }))
  await page.screenshot({ path: '../.local/disabled-permission-actions-20261124/roles-readonly.png', fullPage: true })
  await row.getByRole('button', { name: '查看权限', exact: true }).click()
  await disabled(page.getByRole('dialog').getByRole('button', { name: '保存完整集合', exact: true }))
  expect(state.denied).toEqual([])
  expect(state.errors).toEqual([])
})

/** 部门页读取员工需要独立权限，与新增/修改/删除统一显示禁用状态。 */
test('R17/R24 部门页无权查看员工时保留灰色入口，不发人员查询', async ({ page }) => {
  const state = await fixture(page, ['GET /api/departments'])
  await page.getByRole('navigation').getByRole('link', { name: '部门管理', exact: true }).click()
  for (const name of ['查看员工', '新增下级', '修改当前部门名称', '删除当前部门'])
    await disabled(page.getByRole('button', { name, exact: true }))
  expect(state.calls.some((call) => call.path === '/api/users')).toBe(false)
  expect(state.denied).toEqual([])
  expect(state.errors).toEqual([])
})

/** 资源分类和属性维护各自检查能力，映射只读不会触发候选目录查询。 */
test('R17 资源目录只读时其他分类及编辑置灰，映射可读但不能保存', async ({ page }) => {
  const state = await fixture(page, ['GET /api/permissions/tree', 'GET /api/permissions/{id}/apis'])
  await page.getByRole('navigation').getByRole('link', { name: '资源与API', exact: true }).click()
  await disabled(page.getByRole('button', { name: 'API资源', exact: true }))
  await disabled(page.getByRole('button', { name: '编辑属性', exact: true }))
  await page.getByRole('button', { name: '查看映射', exact: true }).click()
  await disabled(page.getByRole('dialog').getByRole('button', { name: '保存完整集合', exact: true }))
  expect(state.calls.some((call) => call.path === '/api/api-resources')).toBe(false)
  expect(state.denied).toEqual([])
  expect(state.errors).toEqual([])
})

/** 映射读取独立授权；无权时入口可见，但不预读或打开映射弹窗。 */
test('R17 没有映射读取权限时保留置灰入口，不自动请求映射', async ({ page }) => {
  const state = await fixture(page, ['GET /api/permissions/tree'])
  await page.getByRole('navigation').getByRole('link', { name: '资源与API', exact: true }).click()
  await disabled(page.getByRole('button', { name: '查看映射', exact: true }))
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(state.calls.some((call) => /\/permissions\/\d+\/apis$/.test(call.path))).toBe(false)
  expect(state.denied).toEqual([])
  expect(state.errors).toEqual([])
})

/** 只可读取API时保留菜单分类禁用，不会隐式请求权限树或开放API启停。 */
test('R17 仅能查询API时菜单分类及启停置灰', async ({ page }) => {
  const state = await fixture(page, ['GET /api/api-resources'])
  await page.getByRole('navigation').getByRole('link', { name: '资源与API', exact: true }).click()
  await disabled(page.getByRole('button', { name: '菜单与按钮', exact: true }))
  await disabled(page.getByRole('button', { name: '启停设置', exact: true }))
  await page.screenshot({ path: '../.local/disabled-permission-actions-20261124/api-readonly.png', fullPage: true })
  expect(state.calls.some((call) => call.path === '/api/permissions/tree')).toBe(false)
  expect(state.denied).toEqual([])
  expect(state.errors).toEqual([])
})

/** 实际403仍触发能力刷新，撤销后按钮保留禁用而不是继续可提交。 */
test('R17 写请求403后刷新身份，原按钮立即置灰并保留安全错误', async ({ page }) => {
  const state = await fixture(page, ['GET /api/users', 'PATCH /api/users/{id}/status'], { revokeStatus: true })
  await page.getByRole('navigation').getByRole('link', { name: '人员管理', exact: true }).click()
  const row = page.getByRole('row').filter({ hasText: 'fixture-member' })
  await row.getByRole('button', { name: '停用', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认停用', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('测试权限已撤销')
  await disabled(page.getByRole('dialog').getByRole('button', { name: '确认停用', exact: true }))
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
  await disabled(row.getByRole('button', { name: '停用', exact: true }))
  expect(state.calls.filter((call) => call.method === 'PATCH')).toHaveLength(1)
  expect(state.denied).toEqual([])
  expect(state.errors).toEqual([])
})
