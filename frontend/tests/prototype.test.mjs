import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import assert from 'node:assert/strict'

// 只验证迁移后的现行参考原型，不把内存模型当作生产鉴权或界面验收。
function referencePrototype() {
  const directory = new URL('../../prototype/', import.meta.url)
  const html = readFileSync(new URL('index.html', directory), 'utf8')
  const context = vm.createContext({ window: {}, structuredClone, TextEncoder, TextDecoder, btoa, atob,
    document: { addEventListener() {}, querySelector: () => null, querySelectorAll: () => [] } })
  for (const filename of ['shared.js', 't04.js', 't06.js', 't08.js', 't10.js']) {
    assert(html.includes(`src="${filename}"`), `原型入口未加载${filename}`)
    vm.runInContext(readFileSync(new URL(filename, directory), 'utf8'), context)
  }
  return context.window.R
}

test('R17/R18 reference prototype retains four reviewed modules after documentation relocation', () => {
  const model = referencePrototype()
  assert.deepEqual(Object.keys(model.views).sort(), ['t04', 't06', 't08', 't10'])
  assert.equal(model.state.departments.find(item => item.id === 2).parentId, 1)
})

test('R02/R11/R16 reference prototype retains department scope and does not permit retired management scopes', () => {
  const model = referencePrototype()
  model.actorId = 3
  assert.equal(model.guard('rbac.users.read', { method: 'GET', path: '/api/users' }).id, 3)
  assert.equal(model.t10.currentRoles(4).user.id, 4)
  const before = JSON.stringify(model.state)
  assert.throws(() => model.t10.transfer(4, 4), error => error.status === 403 && error.code === 'MANAGEMENT_SCOPE_DENIED')
  assert.equal(JSON.stringify(model.state), before)
  model.user().roleIds.push(4)
  assert.throws(() => model.guard('rbac.logs.read', { global: true, method: 'GET', path: '/api/authorization-logs' }), error => error.status === 403 && error.code === 'MANAGEMENT_SCOPE_DENIED')
})

test('R09/R14 reference prototype retains same-system mapping and current API authorization semantics', () => {
  const model = referencePrototype()
  model.actorId = 1
  const permission = model.state.permissions.find(item => item.code === 'rbac.logs.read')
  const before = [...permission.apiIds]
  assert.throws(() => model.t06.replaceApis(permission.id, [99]), error => error.status === 400 && error.code === 'INVALID_ARGUMENT')
  assert.deepEqual([...permission.apiIds], before)
  model.t06.patchApi(before[0], { enabled: false })
  assert.equal(model.apiAllowed(model.user(), 'GET', '/api/authorization-logs'), false)
})
