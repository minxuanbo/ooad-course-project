import { test, expect } from '@playwright/test'

/** 只替换HTTP，界面真实执行登录、分页、弹窗、权限按钮和提交。 */
async function setup(page, mode = 'admin') {
  const state = { rows: [{ id: 1, code: 'rbac', name: 'RBAC权限管理', enabled: true,
    connectionStatus: 'CONNECTED' }], writes: [], errors: [], calls: [] }
  const grants = mode === 'login' ? [] : ['GET /api/systems/page']
  if (mode === 'admin') grants.push('POST /api/systems', 'PATCH /api/systems/{id}', 'DELETE /api/systems/{id}')
  grants.push('POST /api/auth/login')
  const user = { id: 1, username: 'application-fixture', name: '测试管理员', departmentId: 1,
    departmentName: '测试总公司', managementLevel: 5, globalScope: true,
    routes: grants.map((grant) => { const [method, path] = grant.split(' '); return { method, path } }) }
  page.on('pageerror', (error) => state.errors.push(error.message))
  await page.route('**/api/**', async (route) => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
    state.calls.push(method + ' ' + path)
    let data, status = 200
    if (path === '/api/auth/login') data = { token: 'application-fixture-token', user, expiresIn: 3600 }
    else if (path === '/api/me') data = { user }
    else if (method === 'GET' && path === '/api/systems/page') {
      const pageNumber = Number(url.searchParams.get('page') || 0), size = Number(url.searchParams.get('size') || 20)
      const q = (url.searchParams.get('q') || '').toLowerCase(), enabled = url.searchParams.get('enabled')
      const rows = state.rows.filter((row) => (row.code + row.name).toLowerCase().includes(q) &&
        (enabled === null || String(row.enabled) === enabled))
      data = { items: rows.slice(pageNumber * size, (pageNumber + 1) * size), total: rows.length, page: pageNumber, size }
    } else {
      expect(mode).toBe('admin')
      const body = method === 'DELETE' ? null : request.postDataJSON()
      state.writes.push({ method, path, body })
      if (method === 'POST') {
        data = { id: state.rows.length + 1, ...body, enabled: true, connectionStatus: 'PENDING' }
        state.rows.push(data); status = 201
      } else {
        data = state.rows.find((row) => path === '/api/systems/' + row.id)
        Object.assign(data, method === 'DELETE' ? { enabled: false } : body)
      }
    }
    await route.fulfill({ status, json: { code: 'OK', data } })
  })
  await page.goto('/login')
  await page.getByLabel('工号或手机号', { exact: true }).fill('application-fixture')
  await page.getByLabel('登录密码', { exact: true }).fill('fixture-only-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '已进入权限管理工作区', exact: true })).toBeVisible()
  return state
}

/** 第三个系统不需改源码，移除只停用接入且恢复原来编号。 */
test('R34 应用管理可新增第三系统、编辑、移除和恢复', async ({ page }, testInfo) => {
  const state = await setup(page)
  await page.getByRole('navigation').getByRole('link', { name: '应用管理', exact: true }).click()
  await page.getByRole('button', { name: '新增应用', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('系统编码', { exact: true }).fill('third-system')
  await dialog.getByLabel('系统名称', { exact: true }).fill('第三个系统')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  const row = page.getByRole('row').filter({ hasText: 'third-system' })
  await expect(row).toContainText('待接入')
  await page.screenshot({ path: testInfo.outputPath('applications.png'), fullPage: true })
  await row.getByRole('button', { name: '编辑', exact: true }).click()
  await dialog.getByLabel('系统名称', { exact: true }).fill('第三系统改名')
  await dialog.getByRole('button', { name: '保存', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  await expect(row).toContainText('第三系统改名')
  await row.getByRole('button', { name: '移除', exact: true }).click()
  await expect(dialog).toContainText('保留')
  await dialog.getByRole('button', { name: '确认移除', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  await expect(row).toContainText('已移除')
  await row.getByRole('button', { name: '恢复', exact: true }).click()
  await dialog.getByRole('button', { name: '确认恢复', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(state.writes.map((item) => item.method)).toEqual(['POST', 'PATCH', 'DELETE', 'PATCH'])
  expect(state.rows.filter((item) => item.code === 'third-system')).toHaveLength(1)
  expect(state.errors).toEqual([])
})

/** 读取应用不默认授写入；新按钮与其他工作区遵守同一灰显约定。 */
test('R17/R34 应用只读账号保留灰色维护按钮且不发送写入', async ({ page }) => {
  const state = await setup(page, 'reader')
  await page.getByRole('navigation').getByRole('link', { name: '应用管理', exact: true }).click()
  for (const name of ['新增应用', '编辑', '移除']) {
    const button = page.getByRole('button', { name, exact: true })
    await expect(button).toBeVisible(); await expect(button).toBeDisabled()
    await button.evaluate((element) => element.click())
  }
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(state.writes).toEqual([])
  expect(state.errors).toEqual([])
})

/** 登录本身不隐式给出总览或任何业务菜单，也不查询其业务数据。 */
test('R08 只授登录显示空工作台，不显示总览业务菜单或查询统计', async ({ page }) => {
  const state = await setup(page, 'login')
  await expect(page.getByRole('navigation').getByRole('link', { name: '总览', exact: true })).toHaveCount(0)
  await expect(page.getByRole('navigation').getByRole('link', { name: '应用管理', exact: true })).toHaveCount(0)
  expect(state.calls.every((call) => call === 'POST /api/auth/login' || call === 'GET /api/me')).toBe(true)
  expect(state.errors).toEqual([])
})
