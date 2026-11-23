import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { organizationRows, RequestGeneration, errorText } from '../src/lib/presentation.js'

/** 运行真实部门页面脚本，用受控响应验证选中节点与授权变化，避免复制页面算法。 */
function workspace(api) {
  let unmount
  const session = { token: 'one', me: { user: { id: 1, departmentId: 10 } } }
  const context = vm.createContext({
    ref: (value) => ({ value }),
    computed: (read) => ({
      get value() {
        return read()
      },
    }),
    onMounted() {},
    onUnmounted: (fn) => {
      unmount = fn
    },
    watch() {},
    api,
    session,
    canRoute: () => true,
    notify() {},
    organizationRows,
    RequestGeneration,
    errorText,
  })
  const source = readFileSync(new URL('../src/pages/Departments.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  return { run: (code) => vm.runInContext(code, context), session, unmount: () => unmount() }
}

const departments = [
  {
    id: 10,
    parentId: null,
    name: '分公司',
    type: 'COMPANY',
    directUsers: 3,
    totalUsers: 8,
    canCreate: true,
    canEdit: false,
    canDelete: false,
  },
  {
    id: 11,
    parentId: 10,
    name: '工程部',
    type: 'DEPARTMENT',
    directUsers: 2,
    totalUsers: 5,
    canCreate: true,
    canEdit: true,
    canDelete: false,
  },
  {
    id: 12,
    parentId: 11,
    name: '滨江项目',
    type: 'PROJECT',
    directUsers: 3,
    totalUsers: 3,
    canCreate: false,
    canEdit: true,
    canDelete: false,
  },
  {
    id: 13,
    parentId: 10,
    name: '空部门',
    type: 'DEPARTMENT',
    directUsers: 0,
    totalUsers: 0,
    canCreate: true,
    canEdit: true,
    canDelete: true,
  },
]

test('R24/R17 部门树选择后只显示直属下级，搜索保留祖先且不改变当前部门', async () => {
  const calls = []
  const page = workspace(async (path) => {
    calls.push(path)
    return structuredClone(departments)
  })
  await page.run('load()')
  assert.equal(page.run('selected.value.id'), 10)
  assert.deepEqual([...page.run('directChildren.value.map(node => node.id)')], [11, 13])
  page.run('selectDepartment(nodes.value.find(node => node.id === 11))')
  assert.deepEqual([...page.run('directChildren.value.map(node => node.id)')], [12])
  page.run("q.value = '滨江'")
  assert.deepEqual([...page.run('treeRows.value.map(node => node.id)')], [10, 11, 12])
  assert.equal(page.run('selected.value.id'), 11)
  assert.deepEqual([...page.run('breadcrumbs.value.map(node => node.id)')], [10, 11])
  assert.deepEqual(calls, ['/departments'], '切换节点和搜索不附带后端请求')
})

test('R24/R17 折叠隐藏整棵后代，搜索暂时展开路径，清空后保留折叠选择', async () => {
  const page = workspace(async () => structuredClone(departments))
  await page.run('load()')
  page.run('toggleDepartment(10)')
  assert.deepEqual([...page.run('treeRows.value.map(node => node.id)')], [10])
  page.run("q.value = '滨江'")
  assert.deepEqual([...page.run('treeRows.value.map(node => node.id)')], [10, 11, 12])
  page.run("q.value = ''")
  assert.deepEqual([...page.run('treeRows.value.map(node => node.id)')], [10])
  page.run('selectDepartment(nodes.value.find(node => node.id === 12))')
  assert.ok(page.run('treeRows.value.some(node => node.id === 12)'))
  assert.equal(page.run('directChildren.value.length'), 0)
})

test('R24/R17 维护仅用服务端可执行标记，删除选中空部门后回到父部门', async () => {
  const calls = []
  let data = structuredClone(departments)
  const page = workspace(async (path, method = 'GET', body) => {
    calls.push({ path, method, body })
    if (method === 'DELETE') {
      data = data.filter((node) => node.id !== 13)
      return null
    }
    return structuredClone(data)
  })
  await page.run('load()')
  page.run('rename(nodes.value[0]); openDelete(nodes.value[0]); create(nodes.value[2])')
  assert.equal(page.run('editing.value'), null)
  assert.equal(page.run('deleting.value'), null)
  page.run('selectDepartment(nodes.value.find(node => node.id === 13)); openDelete(selected.value)')
  await page.run('remove(deleting.value)')
  assert.equal(page.run('selected.value.id'), 10)
  assert.deepEqual(
    calls.map((call) => [call.path, call.method]),
    [
      ['/departments', 'GET'],
      ['/departments/13', 'DELETE'],
      ['/departments', 'GET'],
    ],
  )
})

test('R24/R17 旧范围、较旧请求和卸载后的响应不回填部门详情', async () => {
  for (const action of ['scope', 'request', 'unmount']) {
    const finish = []
    const page = workspace(() => new Promise((resolve) => finish.push(resolve)))
    const pending = page.run('load()')
    let later
    if (action === 'scope') page.session.me.user.departmentId = 99
    if (action === 'request') later = page.run('load()')
    if (action === 'unmount') page.unmount()
    finish[0](structuredClone(departments))
    await pending
    assert.equal(page.run('nodes.value.length'), 0, action)
    if (later) {
      finish[1]([])
      await later
    }
    assert.equal(page.run('selected.value'), null, action)
  }
})

test('R24/R17 请求失败清除旧节点，403保存保留草稿且阻止再次写入', async () => {
  let fail = false,
    writes = 0
  const page = workspace(async (path, method = 'GET') => {
    if (method === 'PATCH') {
      writes++
      throw Object.assign(Error('范围已变化'), { status: 403 })
    }
    if (fail) throw Error('暂不可用')
    return structuredClone(departments)
  })
  await page.run('load()')
  page.run('rename(nodes.value[1]); name.value = "新名称"')
  await page.run('save()')
  await page.run('save()')
  assert.equal(writes, 1)
  assert.equal(page.run('name.value'), '新名称')
  assert.equal(page.run('actionDenied.value'), true)
  fail = true
  await page.run('load()')
  assert.equal(page.run('nodes.value.length'), 0)
  assert.equal(page.run('selected.value'), null)
  assert.match(page.run('error.value'), /暂不可用/)
})

/** 运行正式外壳的导航计算，检查分组随能力移除，不使用角色名称决定入口。 */
function navigation(permissions, globalScope) {
  const allowed = new Set(permissions)
  const context = vm.createContext({
    computed: (read) => ({
      get value() {
        return read()
      },
    }),
    watch() {},
    useRoute: () => ({ path: '/departments' }),
    useRouter: () => ({}),
    session: {},
    canRoute: (method, path, global) => allowed.has(path) && (!global || globalScope),
  })
  const source = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*$/gm, '')
  vm.runInContext(source, context)
  return vm.runInContext('navGroups.value', context)
}

test('R17 导航按原型分组，撤权后同时移除入口和空分组，根范围限制继续生效', () => {
  const groups = navigation(
    ['/departments', '/users', '/roles', '/systems', '/authorization-logs'],
    true,
  )
  assert.deepEqual(
    [...groups.map((group) => group.label)],
    ['工作台', '组织管理', '权限管理', '安全审计'],
  )
  assert.ok(groups.flatMap((group) => group.items).every((item) => item.icon))
  const restricted = navigation(['/departments', '/roles', '/authorization-logs'], false)
  assert.deepEqual([...restricted.map((group) => group.label)], ['工作台', '组织管理'])
  assert.deepEqual(
    [...restricted.flatMap((group) => group.items).map((item) => item.path)],
    ['/', '/departments'],
  )
  assert.deepEqual([...navigation([], false).map((group) => group.label)], ['工作台'])
})
