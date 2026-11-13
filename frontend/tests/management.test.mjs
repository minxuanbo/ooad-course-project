import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { RequestGeneration, errorText, organizationRows } from '../src/lib/presentation.js'

function editor(api) {
  let unmount
  const calls = []
  const context = vm.createContext({
    ref: value => ({ value }), computed: read => ({ get value() { return read() } }),
    defineProps: () => ({ kind: 'permissions', readPath: '/roles/1/permissions', savePath: '/roles/1/permissions', readonly: false }),
    defineEmits: () => (...args) => calls.push(args), onMounted() {}, onUnmounted: fn => { unmount = fn },
    api, notify() {}, errorText, RequestGeneration, URLSearchParams, window: { confirm: () => true }
  })
  const source = readFileSync(new URL('../src/components/CollectionEditor.vue', import.meta.url), 'utf8').match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  return { run: code => vm.runInContext(code, context), unmount: () => unmount(), calls }
}

test('R23/R28 初始集合读取失败阻止候选查询及覆盖保存，完整重试恢复全部选择', async () => {
  let failed = true, writes = 0, candidates = 0
  const page = editor(async (path, method) => {
    if (method === 'PUT') { writes++; return {} }
    if (path === '/roles/1/permissions') { if (failed) throw Object.assign(Error('读取失败'), { status: 500 }); return { ids: [7, 9] } }
    if (path === '/systems') return [{ id: 1, name: 'RBAC' }]
    candidates++; return [{ id: 7, name: '人员', enabled: true, children: [] }]
  })
  await page.run('loadInitial()')
  assert.equal(page.run('ready.value'), false)
  await page.run('loadCandidates()'); await page.run('save()')
  assert.equal(candidates, 0); assert.equal(writes, 0)
  failed = false; await page.run('loadInitial()')
  assert.equal(page.run('ready.value'), true)
  assert.deepEqual([...page.run('selected.value')], [7, 9])
  await page.run('loadCandidates()')
  assert.deepEqual([...page.run('selected.value')], [7, 9])
})

test('R28 系统目录候选读取失败后可完整重试，保留初始ID', async () => {
  let systemReads = 0
  const page = editor(async path => {
    if (path === '/roles/1/permissions') return { ids: [7, 9] }
    if (path === '/systems') { if (++systemReads === 1) throw Error('网络断开'); return [{ id: 1, name: 'RBAC' }] }
    if (path.includes('systemId=')) {
      if (!new URLSearchParams(path.split('?')[1]).get('systemId')) throw Error('缺少系统')
      return []
    }
    throw Error(path)
  })
  await page.run('loadInitial()'); await page.run('loadCandidates()')
  assert.equal(systemReads, 2)
  assert.equal(page.run('candidateError.value'), '')
  assert.deepEqual([...page.run('selected.value')], [7, 9])
})

test('R28 已关闭弹窗忽略初读晚响应', async () => {
  let complete
  const page = editor(() => new Promise(resolve => { complete = resolve }))
  const pending = page.run('loadInitial()')
  page.unmount(); complete({ ids: [99] }); await pending
  assert.equal(page.run('ready.value'), false)
  assert.equal(page.run('selected.value.size'), 0)
})

test('R24 部门搜索保留范围内祖先并按子树连续显示，项目人数不重复归属', () => {
  const nodes = [{ id: 1, parentId: null, name: '总公司' }, { id: 2, parentId: 1, name: '工程部' }, { id: 4, parentId: 1, name: '财务部' }, { id: 3, parentId: 2, name: '第一项目' }]
  assert.deepEqual(organizationRows(nodes).map(item => item.id), [1,2,3,4])
  assert.deepEqual(organizationRows(nodes, '项目').map(item => [item.id, item.depth]), [[1,0],[2,1],[3,2]])
})

test('R23 空选保存需明确二次确认，候选翻页不代替已选集合', async () => {
  let writes = 0
  const page = editor(async (path, method, body) => {
    if (method === 'PUT') { assert.equal(body.ids.length, 0); writes++; return {} }
    if (path === '/roles/1/permissions') return { ids: [] }
    if (path === '/systems') return [{ id: 1, name: 'RBAC' }]
    return []
  })
  await page.run('loadInitial()'); await page.run('save()')
  assert.equal(writes, 0); assert.equal(page.run('confirmEmpty.value'), true)
  await page.run('save()'); assert.equal(writes, 1)
})
