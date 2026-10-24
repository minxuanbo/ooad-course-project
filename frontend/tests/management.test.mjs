import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import assert from 'node:assert/strict'

test('navigation requires the specific capability and platform actions require global scope', () => {
  const source = readFileSync(new URL('../src/api.js', import.meta.url), 'utf8').replace(/^import .*$/gm, '').replace(/export /g, '')
  const context = vm.createContext({ reactive: x => x, sessionStorage: { getItem: () => null } })
  vm.runInContext(source, context)
  const run = s => vm.runInContext(s, context)
  run("session.me={canManage:true,managementGrants:[{permission:'audit.read',scope:'COMPANY',company:'A'}]}")
  assert.equal(run("can(routePermissions.logs)"), true)
  assert.equal(run("can(routePermissions.users)"), false)
  assert.equal(run("can('management.grant',true)"), false)
  run("session.me.managementGrants=[{permission:'role.permissions',scope:'COMPANY',company:'A'}]")
  assert.equal(run("can('role.permissions',true)"), false)
  run("session.me.managementGrants=[{permission:'role.permissions',scope:'GLOBAL'}]")
  assert.equal(run("can('role.permissions',true)"), true)
  run("session.me.managementGrants=[]")
  assert.equal(run("can('role.permissions',true)"), false)
  assert.equal(run('scopeSummary()'), '无管理范围')
})

test('scope-only changes unload old management data even when canManage remains true', async () => {
  const text = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
  const source = text.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  const session = { token: 'test', me: { canManage: true, managementGrants: [{ permission: 'user.read', scope: 'COMPANY', companyId: 1 }] } }
  const routes = []
  const context = vm.createContext({ session, ref: value => ({ value }), useRoute: () => ({ path: '/users', meta: { admin: true } }), useRouter: () => ({ replace: async p => routes.push(p) }), watch() {}, onMounted() {}, onUnmounted() {}, notify() {}, can: () => true, routePermissions: { users: 'user.read' }, refreshSession: async () => { session.me = { canManage: true, managementGrants: [{ permission: 'user.read', scope: 'COMPANY', companyId: 2 }] }; return session.me } })
  vm.runInContext(source, context)
  await vm.runInContext('sync()', context)
  assert.equal(vm.runInContext('revision.value', context), 1)
  assert.equal(routes.length, 0)
  context.can = () => false
  context.refreshSession = async () => { session.me = { canManage: false, managementGrants: [] }; return session.me }
  await vm.runInContext('sync()', context)
  assert.equal(vm.runInContext('revision.value', context), 2)
  assert.deepEqual(routes, ['/portal'])
})
