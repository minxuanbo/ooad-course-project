import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { RequestGeneration, errorText } from '../src/lib/presentation.js'

/** 执行真实成员组件脚本，通过可控响应验证筛选与关闭后的数据隔离。 */
function members(props, api) {
  let unmount
  const session = { token: 'session-one', me: { user: { departmentId: 10 } } }
  const context = vm.createContext({
    ref: value => ({ value }), defineProps: () => props, defineEmits: () => () => {},
    onMounted() {}, onUnmounted: fn => { unmount = fn },
    session, api, URLSearchParams, RequestGeneration, errorText,
  })
  const source = readFileSync(new URL('../src/components/MemberList.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  return { run: code => vm.runInContext(code, context), session, close: () => unmount() }
}

test('R10/R17 部门成员默认直属，含下级切换和状态搜索均提交服务端', async () => {
  const calls = []
  const view = members({ department: { id: 10, name: '部门' } }, async path => {
    calls.push(path)
    return { items: [{ id: 1, departmentId: 10 }], total: 1 }
  })
  await view.run('load()')
  assert.match(calls[0], /departmentId=10/)
  assert.match(calls[0], /departmentScope=DIRECT/)
  await view.run("scope.value='SUBTREE'; q.value=' 员工 '; enabled.value='false'; load(0)")
  const query = new URLSearchParams(calls.at(-1).split('?')[1])
  assert.equal(query.get('departmentScope'), 'SUBTREE')
  assert.equal(query.get('q'), '员工')
  assert.equal(query.get('enabled'), 'false')
})

test('R10/R17 角色成员按保存关联查询，翻页保留筛选，失败清除旧成员', async () => {
  let fail = false
  const calls = []
  const view = members({ role: { id: 7, name: '已停用角色', enabled: false } }, async path => {
    calls.push(path)
    if (fail) throw Object.assign(Error('拒绝'), { status: 403 })
    return { items: [{ id: 3 }], total: 40 }
  })
  await view.run('load()')
  await view.run('load(1)')
  assert.match(calls.at(-1), /roleId=7/)
  assert.match(calls.at(-1), /page=1/)
  assert(!calls.at(-1).includes('departmentScope'))
  fail = true
  await view.run('load(0)')
  assert.equal(view.run('rows.value.length'), 0)
  assert.equal(view.run('total.value'), 0)
  assert.notEqual(view.run('error.value'), '')
})

test('R10/R17 关闭、筛选切换及身份变化不能让晚响应回填成员', async () => {
  for (const action of ['close', 'identity', 'query']) {
    const finishes = []
    const view = members({ department: { id: 10 } }, () => new Promise(resolve => finishes.push(resolve)))
    const pending = view.run('load()')
    let next
    if (action === 'close') view.close()
    if (action === 'identity') view.session.token = 'session-two'
    if (action === 'query') next = view.run("scope.value='SUBTREE'; load()")
    finishes[0]({ items: [{ id: 999 }], total: 1 })
    await pending
    assert.equal(view.run('rows.value.length'), 0, action)
    if (next) {
      finishes[1]({ items: [{ id: 2 }], total: 1 })
      await next
      assert.equal(view.run('rows.value[0].id'), 2)
    }
  }
})
