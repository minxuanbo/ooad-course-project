/** 使用两个生产前端与真实后端复验课程菜单/按钮，不开启网络mock或录制凭据。 */
import { createRequire } from 'node:module'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(path.join(root, 'frontend/package.json'))
const { chromium, expect } = require('@playwright/test')
const output = path.join(root, '.local/course-integration/browser')
await fs.mkdir(output, { recursive: true })
const password = process.env.UI_FIXTURE_PASSWORD
if (!password) throw Error('请通过环境提供虚构夹具口令')
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const adminContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const courseContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const adminPage = await adminContext.newPage()
const coursePage = await courseContext.newPage()
const evidence = []
let originalIds, adminToken

/** 恢复夹具和定位权限使用真实RBAC接口；演示撤权写入使用RBAC页面。 */
async function rbac(apiPath, method = 'GET', body) {
  const response = await fetch('http://127.0.0.1:8081/api' + apiPath, {
    method, headers: { Authorization: 'Bearer ' + adminToken, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw Error(`RBAC夹具操作失败：HTTP${response.status}`)
  return (await response.json()).data
}

/** 从当前完整角色集合中切换指定课程权限，再显式确认整体保存。 */
async function revokeInUi(code) {
  await adminPage.getByRole('row').filter({ hasText: 'course-operator' }).getByRole('button', { name: '配置权限' }).click()
  const dialog = adminPage.getByRole('dialog')
  await dialog.getByLabel('所属系统').selectOption('2')
  const label = dialog.locator('.candidate-list label').filter({ hasText: new RegExp(`${code} ·`) })
  await label.getByRole('checkbox').uncheck()
  await dialog.getByRole('button', { name: '保存完整集合', exact: true }).click()
  await dialog.getByRole('button', { name: '确认保存完整集合', exact: true }).click()
  await expect(dialog).not.toBeVisible()
}

try {
  await adminPage.goto('http://127.0.0.1:5173/login')
  await adminPage.getByLabel('账号', { exact: true }).fill('ui-admin')
  await adminPage.getByLabel('密码', { exact: true }).fill(password)
  await adminPage.getByRole('button', { name: '登录 →' }).click()
  await adminPage.waitForURL('http://127.0.0.1:5173/')
  adminToken = await adminPage.evaluate(() => sessionStorage.getItem('rbacToken'))
  originalIds = (await rbac('/roles/51/permissions')).ids
  await adminPage.goto('http://127.0.0.1:5173/roles')
  await adminPage.getByLabel('搜索角色').fill('course-operator')
  await adminPage.getByRole('button', { name: '查询', exact: true }).click()
  await expect(adminPage.getByRole('row').filter({ hasText: 'course-operator' })).toBeVisible()
  evidence.push('RBAC生产前端真实登录并读取课程角色')

  await coursePage.goto('http://127.0.0.1:5174/')
  await coursePage.getByRole('tab', { name: '教师登录' }).click()
  await coursePage.getByPlaceholder('请输入工号').fill('ui-user-05')
  await coursePage.locator('input[name="password"]').fill(password)
  await coursePage.getByRole('button', { name: /登录/ }).last().click()
  await coursePage.waitForURL('**/teacher/courses')
  await expect(coursePage.getByText('RBAC真实联调课程', { exact: true }).first()).toBeVisible()
  const courseToken = await coursePage.evaluate(() => localStorage.getItem('cpm.accessToken'))
  evidence.push('老师原始课程前端真实教师登录并显示数据库课程')
  await coursePage.goto('http://127.0.0.1:5174/admin/terms')
  await expect(coursePage.getByText('联调基准学期', { exact: true })).toBeVisible()
  await expect(coursePage.getByRole('button', { name: '新建学期', exact: true })).toBeEnabled()
  await coursePage.screenshot({ path: path.join(output, '01-authorized.png'), fullPage: true })
  evidence.push('MTR菜单与MTRC按钮授权有效')

  await revokeInUi('MTRC')
  await coursePage.reload()
  await expect(coursePage.getByText('联调基准学期', { exact: true })).toBeVisible()
  await expect(coursePage.getByRole('button', { name: '取消当前', exact: true })).toBeEnabled()
  await expect(coursePage.getByRole('button', { name: '新建学期', exact: true })).toBeDisabled()
  expect((await coursePage.evaluate(() => localStorage.getItem('cpm.accessToken'))) === courseToken).toBe(true)
  await coursePage.screenshot({ path: path.join(output, '02-button-revoked.png'), fullPage: true })
  evidence.push('RBAC页面撤回MTRC，课程重读页面禁用按钮，令牌未替换')
  await rbac('/roles/51/permissions', 'PUT', { ids: originalIds })
  await adminPage.reload()
  await adminPage.getByLabel('搜索角色').fill('course-operator')
  await adminPage.getByRole('button', { name: '查询', exact: true }).click()
  await expect(adminPage.getByRole('row').filter({ hasText: 'course-operator' })).toBeVisible()
  await revokeInUi('MTR')
  await coursePage.goto('http://127.0.0.1:5174/admin/terms')
  await expect(coursePage.getByRole('heading', { name: '无法访问' })).toBeVisible()
  await coursePage.screenshot({ path: path.join(output, '03-menu-revoked.png'), fullPage: true })
  evidence.push('RBAC页面撤回MTR，课程直达菜单被实时守卫拒绝')
  await rbac('/roles/51/permissions', 'PUT', { ids: originalIds })
  await coursePage.goto('http://127.0.0.1:5174/admin/terms')
  await expect(coursePage.getByRole('button', { name: '新建学期', exact: true })).toBeEnabled()
  evidence.push('恢复授权后原登录可再次访问')
} finally {
  if (originalIds && adminToken) await rbac('/roles/51/permissions', 'PUT', { ids: originalIds })
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(evidence, null, 2) + '\n')
  await browser.close()
}
console.log(`两个生产前端真实交互通过：${evidence.length}项；截图位于.local/course-integration/browser。`)
