import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import assert from 'node:assert/strict'

// 只验证仓库唯一离线原型的加载和安全边界，不作为正式前后端验收。
function currentPrototype() {
  const directory = new URL('../../prototype/', import.meta.url)
  const html = readFileSync(new URL('index.html', directory), 'utf8')
  const context = vm.createContext({ crypto: globalThis.crypto, structuredClone })
  for (const filename of ['applications.js', 'catalog.js', 'model.js']) {
    assert(html.includes(`src="./${filename}"`), `原型入口未加载${filename}`)
    vm.runInContext(readFileSync(new URL(filename, directory), 'utf8'), context)
  }
  return context.MunicipalPrototype
}

test('R17/R18 current prototype loads one offline workspace and the source matrix', () => {
  const { createDemoModel, DEMO_PASSWORD } = currentPrototype()
  const model = createDemoModel()
  model.login('admin', DEMO_PASSWORD)
  assert.equal(model.list().total, 168)
  assert.equal(model.permissionCatalog().systems.length, 19)
  assert.equal(model.rolePermissions(5).permissions.filter(item => item.sourceKind === 'operation').length, 94)
})

test('R02/R11/R16 current prototype preserves scope, failed transfer and root-only logs', () => {
  const { createDemoModel, DEMO_PASSWORD } = currentPrototype()
  const model = createDemoModel()
  model.login('admin', DEMO_PASSWORD)
  model.saveRolePermissions(3, [...model.rolePermissions(3).permissions.map(item => item.id), 20])
  model.login('SG000002', DEMO_PASSWORD)
  const before = JSON.stringify(model.currentRoles(5))
  assert.throws(() => model.move(5, 10), error => error.code === 'MANAGEMENT_SCOPE_DENIED')
  assert.equal(JSON.stringify(model.currentRoles(5)), before)
  assert.throws(() => model.logs(), error => error.code === 'MANAGEMENT_SCOPE_DENIED')
})

test('R09/R14 current prototype refuses cross-system API mappings without partial changes', () => {
  const { createDemoModel, DEMO_PASSWORD } = currentPrototype()
  const model = createDemoModel()
  model.login('admin', DEMO_PASSWORD)
  const business = model.resources({ systemId: 2 }).permissions.find(item => item.type === 'BUTTON')
  const before = JSON.stringify(model.permissionApis(business.id))
  assert.throws(() => model.savePermissionApis(business.id, [1]), error => error.code === 'INVALID_ARGUMENT')
  assert.equal(JSON.stringify(model.permissionApis(business.id)), before)
})
