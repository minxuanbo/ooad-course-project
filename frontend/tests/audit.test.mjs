import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import assert from 'node:assert/strict'
test('API unwraps the envelope and exposes business error and request ID', async () => {
  let response = { ok: true, status: 200, text: async () => JSON.stringify({ code: 'OK', data: null, requestId: 'one' }) }
  const context = vm.createContext({ reactive: x => x, sessionStorage: { getItem: () => '' }, fetch: async () => response, AbortSignal })
  vm.runInContext(readFileSync(new URL('../src/api.js', import.meta.url), 'utf8').replace(/^import .*$/gm, '').replace(/export /g, ''), context)
  assert.equal(await vm.runInContext("api('/auth/logout','POST')", context), null)
  response = { ok: false, status: 403, text: async () => JSON.stringify({ code: 'MANAGEMENT_SCOPE_DENIED', message: '范围外', data: null, requestId: 'two' }) }
  await assert.rejects(vm.runInContext("api('/users')", context), e => e.code === 'MANAGEMENT_SCOPE_DENIED' && e.requestId === 'two' && e.status === 403)
  response = { ok: true, status: 200, text: async () => '[]' }
  await assert.rejects(vm.runInContext("api('/users')", context), /格式/)
})
test('log pagination binds filters to the first page and archive errors remain visible', async () => {
  const calls = []; let fail = false, unmount
  const context = vm.createContext({ ref: value => ({ value }), URLSearchParams, onMounted() {}, onUnmounted: fn => { unmount = fn }, api: async (path, method, body) => {
    calls.push({ path, method, body })
    if (fail) throw Error('archive failed')
    if (method === 'POST') return { archived: 1, hasMore: false }
    return { items: [{ id: calls.length }], nextCursor: 'cursor-one', hasMore: true }
  } })
  const source = readFileSync(new URL('../src/pages/AuditLogs.vue', import.meta.url), 'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  vm.runInContext(source, context); const run = s => vm.runInContext(s, context)
  await run("filters.value.username='first';load()"); await run("filters.value.username='changed';load(true)")
  assert.match(calls[1].path, /username=first/); assert.match(calls[1].path, /cursor=cursor-one/); assert.equal(run('rows.value.length'), 2)
  fail = true; await run("before.value='2026-01-01T00:00';archive()")
  assert.equal(run('error.value'), 'archive failed'); assert.equal(calls.length, 3)
  fail = false; await run('archive()'); assert.match(run('notice.value'), /1 条/)
  unmount(); await run('load()'); assert.equal(run('rows.value.length'), 0)
})
test('handover DTO null represents an employed user before starting a handover', () => {
  const context = vm.createContext({ ref: value => ({ value }), reactive: x => x, computed: fn => ({ get value() { return fn() } }), defineProps: () => ({ target: { id: 1 } }), defineEmits: () => () => {}, onMounted() {} })
  const source = readFileSync(new URL('../src/components/Handover.vue', import.meta.url), 'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  assert.equal(vm.runInContext('data.value={handover:null};state.value', context), 'EMPLOYED')
  assert.equal(vm.runInContext("data.value={handover:{state:'HANDOVER'}};state.value", context), 'HANDOVER')
})

test('organization edits use PATCH without changing company membership; creation uses POST', async () => {
  const calls = []
  const context = vm.createContext({ ref: value => ({ value }), onMounted() {}, notify() {}, session: {}, api: async (path, method, body) => { calls.push({path,method,body}); return {companies:[],departments:[]} } })
  const source = readFileSync(new URL('../src/pages/Organizations.vue', import.meta.url), 'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  vm.runInContext(source,context)
  await vm.runInContext("edit('departments',{id:12,code:'D12',name:'原名'});form.value.name='新名称';save()",context)
  assert.equal(calls[0].method,'PATCH'); assert.equal(calls[0].path,'/organizations/departments/12')
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0].body)),{code:'D12',name:'新名称'})
  calls.length=0
  await vm.runInContext("edit('departments',null);form.value={code:'NEW',name:'新部门',companyId:2};save()",context)
  assert.equal(calls[0].method,'POST'); assert.equal(calls[0].body.companyId,2)
})
