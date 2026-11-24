import { test, expect } from '@playwright/test'

const systems = [{ id: 1, name: 'RBAC权限管理系统' }, { id: 2, name: '课程项目管理系统' }]
const trees = {
  1: [{ id: 10, systemId: 1, name: 'RBAC菜单', code: 'rbac.menu', type: 'MENU', enabled: true, children: [] }],
  2: [{ id: 21, systemId: 2, name: '课程菜单', code: 'course.menu', type: 'MENU', enabled: true, children: [
    { id: 22, systemId: 2, name: '已停用课程按钮', code: 'course.disabled', type: 'BUTTON', enabled: false, children: [] },
    { id: 23, systemId: 2, name: '编辑课程', code: 'course.edit', type: 'BUTTON', enabled: true, children: [] },
  ] }],
}

/** 登录实际Vue，只替换HTTP为多系统夹具，不连接正式数据库。 */
async function setup(page, { selected = [10, 21, 22] } = {}) {
  const state = { ids: [...selected], writes: [], errors: [], failCourse: false, holdCourse: false, releaseCourse: null }
  const routes = ['GET /api/roles', 'GET /api/roles/{id}/permissions', 'PUT /api/roles/{id}/permissions',
    'GET /api/permissions/tree', 'GET /api/systems'].map(value => {
    const [method, path] = value.split(' '); return { method, path }
  })
  const user = { id: 1, name: '测试管理员', managementLevel: 5, globalScope: true, routes }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname
    let data
    if (path === '/api/auth/login') data = { token: 'permission-summary-fixture', user, expiresIn: 3600 }
    else if (path === '/api/me') data = { user }
    else if (path === '/api/roles') data = { items: [{ id: 7, code: 'summary-role', name: '测试跨系统角色', enabled: true, type: 'GLOBAL_CUSTOM' }], total: 1 }
    else if (path === '/api/roles/7/permissions') {
      if (request.method() === 'PUT') { state.ids = request.postDataJSON().ids; state.writes.push([...state.ids]) }
      data = { roleId: 7, ids: state.ids }
    } else if (path === '/api/systems') data = systems
    else if (path === '/api/permissions/tree') {
      const id = url.searchParams.get('systemId')
      if (id === '2' && state.holdCourse) await new Promise(resolve => { state.releaseCourse = resolve })
      if (id === '2' && state.failCourse) {
        await route.fulfill({ status: 500, json: { code: 'READ_FAILED', message: '测试系统读取失败' } }); return
      }
      data = trees[id]
    } else throw Error('夹具不允许额外请求：' + path)
    await route.fulfill({ json: { code: 'OK', data } })
  })
  await page.goto('/login')
  await page.getByLabel('工号或手机号', { exact: true }).fill('summary-fixture')
  await page.getByLabel('登录密码', { exact: true }).fill('fixture-only-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('navigation').getByRole('link', { name: '角色与权限', exact: true }).click()
  await page.getByRole('button', { name: '配置权限', exact: true }).click()
  await expect(page.getByRole('dialog').locator('.candidate-list')).toBeVisible()
  return state
}

/** 摘要按系统投影，搜索仅筛候选；跨系统增删后仍提交完整草稿并保留停用项。 */
test('R13/R17 已选权限只显示当前系统，搜索不隐藏已选，保存保留其他系统草稿', async ({ page }) => {
  const state = await setup(page), dialog = page.getByRole('dialog'), summary = dialog.locator('.selected-summary')
  await expect(summary).toContainText('当前系统已选 1 项')
  await expect(summary).toContainText('RBAC菜单')
  await expect(summary).not.toContainText('ID 21')
  await dialog.getByRole('combobox', { name: '所属系统', exact: true }).selectOption('2')
  await expect(summary).toContainText('当前系统已选 2 项')
  await expect(summary).toContainText('已停用课程按钮')
  await expect(summary).not.toContainText('RBAC菜单')
  await dialog.getByLabel('搜索候选', { exact: true }).fill('编辑课程')
  await expect(dialog.locator('.candidate-list input')).toHaveCount(1)
  await expect(summary).toContainText('课程菜单')
  await dialog.locator('.candidate-list input').check()
  await dialog.getByRole('button', { name: '移除ID 21', exact: true }).click()
  await expect(summary).toContainText('当前系统已选 2 项')
  await dialog.getByRole('combobox', { name: '所属系统', exact: true }).selectOption('1')
  await expect(summary).toContainText('当前系统已选 1 项')
  await expect(summary).not.toContainText('课程')
  await dialog.getByRole('button', { name: '保存完整集合', exact: true }).click()
  await expect(dialog).toContainText('将保存完整的 3 项关联')
  await dialog.getByRole('button', { name: '确认保存完整集合', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(state.writes).toHaveLength(1)
  expect(state.writes[0].sort((a, b) => a - b)).toEqual([10, 22, 23])
  expect(state.errors).toEqual([])
})

/** 未加载或失败不能把旧系统的标签算给新系统，也不能伪装成已选零项。 */
test('R13/R17 当前系统摘要区分读取中、失败与真实空选，重试不丢授权', async ({ page }) => {
  const state = await setup(page, { selected: [21, 22] }), dialog = page.getByRole('dialog'), summary = dialog.locator('.selected-summary')
  await expect(summary).toContainText('当前系统已选 0 项')
  await expect(summary).not.toContainText('ID 21')
  state.holdCourse = true; state.failCourse = true
  await dialog.getByRole('combobox', { name: '所属系统', exact: true }).selectOption('2')
  await expect(summary).toContainText('正在读取当前系统已选权限')
  await expect(summary).not.toContainText('已选 0 项')
  await expect.poll(() => Boolean(state.releaseCourse)).toBe(true)
  state.releaseCourse()
  await expect(summary).toContainText('当前系统已选权限暂不可用')
  await expect(summary).not.toContainText('已选 0 项')
  state.holdCourse = false; state.failCourse = false
  await dialog.getByRole('button', { name: '重试候选', exact: true }).click()
  await expect(summary).toContainText('当前系统已选 2 项')
  await dialog.getByRole('button', { name: '保存完整集合', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存完整集合', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(state.writes).toEqual([[21, 22]])
  expect(state.errors).toEqual([])
})

/** 快速来回切换时，晚到的前一个系统响应不能覆盖当前摘要和勾选集合。 */
test('R13/R17 系统晚响应不能把其他系统权限回填当前摘要', async ({ page }) => {
  const state = await setup(page), dialog = page.getByRole('dialog'), summary = dialog.locator('.selected-summary')
  await expect(summary).toContainText('当前系统已选 1 项')
  state.holdCourse = true
  await dialog.getByRole('combobox', { name: '所属系统', exact: true }).selectOption('2')
  await expect.poll(() => Boolean(state.releaseCourse)).toBe(true)
  await dialog.getByRole('combobox', { name: '所属系统', exact: true }).selectOption('1')
  await expect(summary).toContainText('当前系统已选 1 项')
  const response = page.waitForResponse(r => new URL(r.url()).searchParams.get('systemId') === '2')
  state.releaseCourse(); await (await response).finished()
  await expect(summary).toContainText('当前系统已选 1 项')
  await expect(summary).not.toContainText('课程菜单')
  await dialog.getByRole('button', { name: '保存完整集合', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存完整集合', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(state.writes).toEqual([[10, 21, 22]])
  expect(state.errors).toEqual([])
})
