import { test, expect } from '@playwright/test'

const loginPath = '/api/auth/login'
const identityPath = '/api/me'
const overviewPath = '/api/overview'
const activityPath = '/api/authorization-logs'
const departmentPath = '/api/departments'
const logoutPath = '/api/auth/logout'

/** 只替换HTTP响应；登录、Vue挂载、路由切换和Teleport均执行正式页面代码。 */
async function fixture(page, { holdIdentity = false, failOverview = false } = {}) {
  const calls = []
  const errors = []
  page.renderErrors = errors
  let token = '', sessions = 0, releaseIdentity
  const identityGate = new Promise((resolve) => { releaseIdentity = resolve })
  const user = {
    id: 1, username: 'fixture-admin', name: '测试管理员', enabled: true,
    departmentId: 1, departmentName: '测试总公司', globalScope: true, managementLevel: 5,
    routes: [overviewPath, activityPath, departmentPath].map((path) => ({ method: 'GET', path })),
  }
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (/Vue warn|Teleport|TypeError|ReferenceError/.test(message.text()))
      errors.push(message.text().split('\n')[0])
  })
  await page.route('**/api/**', async (route) => {
    const request = route.request(), path = new URL(request.url()).pathname
    calls.push({ path, method: request.method() })
    let data = null
    if (path === loginPath && request.method() === 'POST') {
      token = 'render-fixture-' + ++sessions
      data = { token, user, expiresIn: 43200 }
    } else if (path === logoutPath && request.method() === 'POST') {
      token = ''
    } else {
      if (!token || request.headers().authorization !== 'Bearer ' + token) {
        await route.fulfill({ status: 401, json: { code: 'SESSION_INVALID', message: '测试身份失效' } })
        return
      }
      if (path === identityPath) {
        if (holdIdentity) await identityGate
        data = { user }
      } else if (path === overviewPath) {
        if (failOverview) {
          await route.fulfill({ status: 500, json: { code: 'INTERNAL_ERROR', message: '测试统计暂不可用' } })
          return
        }
        data = {
          departmentId: 1, departmentName: '测试总公司', totalUsers: 4,
          enabledUsers: 3, disabledUsers: 1, totalDepartments: 1, distribution: [],
        }
      } else if (path === activityPath) {
        data = { items: [], total: 0, hasMore: false, systems: [] }
      } else if (path === departmentPath) {
        data = [{ id: 1, name: '测试总公司', type: 'HEADQUARTERS', parentId: null,
          directUsers: 4, totalUsers: 4, canCreate: false, canEdit: false, canDelete: false }]
      } else throw Error('渲染夹具未声明接口：' + path)
    }
    await route.fulfill({ status: 200, json: { code: 'OK', data } })
  })
  return { calls, errors, releaseIdentity }
}

/** 失败时仅记录虚构夹具的渲染异常，便于区分接口和真实DOM故障。 */
test.afterEach(async ({ page }, info) => {
  if (info.status !== info.expectedStatus) console.log(JSON.stringify({ renderErrors: page.renderErrors }))
})

/** 必须从实际登录表单进入，不能预置token后直达工作区绕过挂载过程。 */
async function submitLogin(page) {
  await page.getByLabel('账号', { exact: true }).fill('fixture-admin')
  await page.getByLabel('密码', { exact: true }).fill('fixture-only-password')
  await page.getByRole('button', { name: '登录 →', exact: true }).click()
}

/** 首次登录无需刷新就显示统计、右上操作及下一菜单，退出重登亦保持完整页面。 */
test('R17/R25 登录与退出重登直接显示首页数据及下一菜单，无需刷新', async ({ page }) => {
  const state = await fixture(page)
  await page.goto('/login')
  for (let attempt = 0; attempt < 2; attempt++) {
    await submitLogin(page)
    await expect(page.locator('.overview-welcome')).toBeVisible()
    await expect(page.locator('.overview-metric')).toHaveCount(4)
    await expect(page.locator('#page-actions .page-refresh')).toBeVisible()
    await page.getByRole('navigation').getByRole('link', { name: '部门管理', exact: true }).click()
    await expect(page.getByRole('heading', { name: '部门管理', level: 1, exact: true })).toBeVisible()
    await expect(page.locator('#page-actions .page-refresh')).toBeVisible()
    await page.getByRole('button', { name: '退出', exact: true }).click()
    await expect(page.getByRole('button', { name: '登录 →', exact: true })).toBeVisible()
  }
  expect(state.calls.filter((call) => call.path === overviewPath)).toHaveLength(2)
  expect(state.errors).toEqual([])
})

/** 本人能力仍需先返回才允许进入；延迟返回后一次挂载完整首页。 */
test('R17/R25 身份读取延迟时等待验证，返回后显示完整首页', async ({ page }) => {
  const state = await fixture(page, { holdIdentity: true })
  await page.goto('/login')
  await submitLogin(page)
  await expect(page.getByRole('button', { name: '正在验证身份…', exact: true })).toBeVisible()
  expect(state.calls.filter((call) => call.path === overviewPath)).toHaveLength(0)
  state.releaseIdentity()
  await expect(page.locator('.overview-welcome')).toBeVisible()
  await expect(page.locator('#page-actions .page-refresh')).toBeVisible()
  expect(state.errors).toEqual([])
})

/** 统计失败也必须显示错误与重试，不能把空白当成成功加载。 */
test('R17/R25 登录后统计失败显示错误与重试，工作区不白屏', async ({ page }) => {
  const state = await fixture(page, { failOverview: true })
  await page.goto('/login')
  await submitLogin(page)
  await expect(page.getByRole('alert')).toContainText('测试统计暂不可用')
  await expect(page.getByRole('button', { name: '重试', exact: true })).toBeVisible()
  await expect(page.locator('#page-actions .page-refresh')).toBeVisible()
  expect(state.errors).toEqual([])
})

/** 打开遗留失效会话时只自动读取一次身份；回到登录后不继续请求业务数据。 */
test('R17 失效会话自动回登录且不重复请求，重新登录后直接显示首页', async ({ page }) => {
  const state = await fixture(page)
  await page.addInitScript(() => sessionStorage.setItem('rbacToken', 'expired-render-fixture'))
  await page.goto('/')
  await expect(page.getByRole('button', { name: '登录 →', exact: true })).toBeVisible()
  await expect(page.locator('#notice')).toContainText('测试身份失效')
  expect(state.calls.map((call) => call.path)).toEqual([identityPath])
  expect(await page.evaluate(() => sessionStorage.getItem('rbacToken'))).toBeNull()
  await submitLogin(page)
  await expect(page.locator('.overview-welcome')).toBeVisible()
  await expect(page.locator('#page-actions .page-refresh')).toBeVisible()
  expect(state.errors).toEqual([])
})
