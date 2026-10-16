// Exercise the actual <script setup> procedures with controlled API/lifecycle
// dependencies. Browser layout and Vue rendering are verified separately.
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import assert from 'node:assert/strict'

function mountScript(file, api, query = {}) {
  const text = readFileSync(new URL('../src/pages/' + file, import.meta.url), 'utf8')
  const source = text.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  const state = { messages: [], routes: [], mounted: null, unmounted: null }
  const context = vm.createContext({
    ref(value) { return { value } }, api,
    useRoute() { return { params: { id: '1' }, query } },
    useRouter() { return { async replace(path) { state.routes.push(path) }, async push(path) { state.routes.push(path) } } },
    notify(message) { state.messages.push(message) },
    onMounted(fn) { state.mounted = fn }, onUnmounted(fn) { state.unmounted = fn },
    setInterval() { return 1 }, clearInterval() {}, window: { addEventListener() {}, removeEventListener() {} },
    crypto: { randomUUID() { return 'test-request' } }
  })
  vm.runInContext(source, context)
  state.run = function(code) { return vm.runInContext(code, context) }
  return state
}
function directoryApi() {
  const data = { fail: null, allowed: true, calls: [] }
  data.api = async function(path, method, body) {
    data.calls.push({ path, method, body })
    if (data.fail) throw data.fail
    if (path.endsWith('/enter')) return { allowed: true, application: { id: 1, name: 'OA', code: 'oa' } }
    if (path.endsWith('/modules')) return { modules: [{ id: 10, name: '公文管理' }, { id: 20, name: '会议管理' }] }
    if (path.endsWith('/features')) return { features: [{ id: 100, name: '收文登记', operations: [{ permissionCode: 'read', label: '查看', allowed: data.allowed }] }] }
    if (path === '/authorize') return { allowed: data.allowed }
    throw Error('Unexpected API: ' + path)
  }
  return data
}
test('employee portal has only server-authorized apps; failed sync discards old cards', async function() {
  let fail = false
  const page = mountScript('Portal.vue', async function() { if (fail) throw Error('offline'); return [{ id: 1, permissionCount: 1 }] })
  await page.run('sync()'); assert.equal(page.run('apps.value.length'), 1)
  fail = true; await page.run('sync()'); assert.equal(page.run('apps.value.length'), 0)
})
test('single authorized app enters after initial login; no repeated auto-navigation', async function() {
  const page = mountScript('Portal.vue', async function(path) { return path.endsWith('/enter') ? { allowed: true } : [{ id: 9, permissionCount: 1 }] }, { initial: '1' })
  await page.mounted(); assert.deepEqual(page.routes, ['/portal', '/applications/9'])
  await page.run('sync()'); assert.equal(page.routes.length, 2)
})
test('system operations are gated; denial refreshes state and stale enabled button is not trusted', async function() {
  const data = directoryApi(), page = mountScript('System.vue', data.api)
  await page.run('sync()'); await page.run('selected.value=10;sync()')
  await page.run("operate({allowed:false,permissionCode:'edit'}, {name:'收文'})")
  assert.equal(data.calls.filter(c => c.path === '/authorize').length, 0)
  data.allowed = false
  await page.run("operate({allowed:true,permissionCode:'read',label:'查看'}, {name:'收文'})")
  assert.equal(data.calls.filter(c => c.path === '/authorize').length, 1)
  assert.equal(page.run('features.value[0].operations[0].allowed'), false)
  assert(page.messages.some(m => m.includes('拒绝')))
})
test('directory revoke clears contents and returns to portal', async function() {
  const data = directoryApi(), page = mountScript('System.vue', data.api)
  await page.run('sync()'); await page.run('selected.value=10;sync()')
  data.fail = Object.assign(Error('revoked'), { status: 403 }); await page.run('sync()')
  assert.equal(page.run('application.value'), null); assert.equal(page.run('features.value.length'), 0)
  assert.deepEqual(page.routes, ['/portal'])
})
test('network failure disables operations, and unmounted response cannot restore content', async function() {
  const data = directoryApi(), page = mountScript('System.vue', data.api)
  await page.run('sync()'); data.fail = Error('offline'); await page.run('sync()')
  assert.equal(page.run('ready.value'), false); assert.equal(page.run('modules.value.length'), 0)
  await page.run("operate({allowed:true,permissionCode:'read'}, {name:'收文'})")
  assert.equal(data.calls.filter(c => c.path === '/authorize').length, 0)
  page.unmounted(); data.fail = null; await page.run('sync()'); assert.equal(page.run('ready.value'), false)
})
test('rapid module switching ignores earlier responses', async function() {
  const data = directoryApi(); let resolve
  const page = mountScript('System.vue', async function(path, method, body) {
    if (path.endsWith('/10/features')) return await new Promise(function(done) { resolve = done })
    return data.api(path, method, body)
  })
  await page.run('sync()'); page.run('selectModule(10)')
  await new Promise(function(done) { setImmediate(done) })
  page.run('selectModule(20)'); resolve({ features: [{ id: 999, name: 'stale' }] })
  await new Promise(function(done) { setImmediate(done) })
  assert.equal(page.run('selected.value'), 20); assert.notEqual(page.run('features.value[0]?.id'), 999)
})
