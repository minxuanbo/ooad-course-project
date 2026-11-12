import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

// 当前实现图：只截取CoreAuthService.require相关成员，目标DDD设计不在此生成。
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const out = resolve(root, 'docs/design')
const require = createRequire(import.meta.url)
const sharp = require(process.env.UML_SHARP_MODULE || 'sharp')
const esc = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
const ink = '#18212b'
const font = "'Microsoft YaHei','Noto Sans CJK SC',Arial,sans-serif"
function label(x, y, value, size = 20, anchor = 'start') {
  return `<text x="${x}" y="${y}" font-size="${size}" text-anchor="${anchor}" fill="${ink}">${esc(value)}</text>`
}
function document(width, height, title, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><defs>
  <marker id="open" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto"><path d="M1,1 L11,6 L1,11" fill="none" stroke="${ink}" stroke-width="1.6"/></marker>
  <marker id="filled" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto"><path d="M1,1 L11,6 L1,11 Z" fill="${ink}"/></marker></defs>
  <rect width="100%" height="100%" fill="white"/><g font-family="${font}">${label(30, 43, title, 28)}${body}</g></svg>`
}
// 源码存在性检查防止改名或删方法后仍生成貌似有效的旧图；语义仍须人工复核。
const sources = {
  auth: 'src/main/java/com/municipal/rbaccore/service/CoreAuthService.java',
  identities: 'src/main/java/com/municipal/rbaccore/security/BearerIdentityStore.java',
  filter: 'src/main/java/com/municipal/rbac/RequestAuditFilter.java',
}
for (const [key, path] of Object.entries(sources)) {
  const code = readFileSync(resolve(root, path), 'utf8')
  const expected = key === 'auth' ? ['CurrentIdentity require(String header)', 'findById(identities.require(header))', 'filter(User::isEnabled)', 'orElseThrow(ApiException::unauthorized)'] : key === 'filter' ? ['static void actor('] : ['Long require(String header)']
  for (const text of expected) if (!code.includes(text)) throw Error(`${path}不再包含图中操作：${text}`)
}

const boxes = [
  { id: 'auth', x: 600, y: 390, w: 500, h: 220, title: 'CoreAuthService', fields: ['- users: UserRepository', '- identities: BearerIdentityStore'], methods: ['+ require(header: String): CurrentIdentity'] },
  { id: 'users', x: 40, y: 380, w: 440, h: 170, title: '«interface» UserRepository', fields: [], methods: ['+ findById(id: Long): Optional<User>', '  （继承JpaRepository的方法）'] },
  { id: 'identities', x: 600, y: 105, w: 500, h: 170, title: 'BearerIdentityStore', fields: [], methods: ['+ require(header: String): Long'] },
  { id: 'user', x: 1220, y: 380, w: 430, h: 190, title: 'User', fields: ['- enabled: boolean'], methods: ['+ isEnabled(): boolean', '+ getId(), getUsername(), getDepartment()'] },
  { id: 'filter', x: 600, y: 800, w: 500, h: 160, title: 'RequestAuditFilter', fields: [], methods: ['+ actor(id: Long, username: String): void', '  {static}'] },
  { id: 'identity', x: 1220, y: 770, w: 430, h: 190, title: '«record» CurrentIdentity', fields: ['id: Long', 'username: String', 'departmentId: Long'], methods: [] },
]
function classBox(b) {
  const split = b.y + 54 + Math.max(1, b.fields.length) * 29 + 12
  return `<rect x="${b.x}" y="${b.y}" width="${b.w}" height="${b.h}" fill="white" stroke="${ink}" stroke-width="2"/><path d="M${b.x},${b.y + 54}H${b.x + b.w} M${b.x},${split}H${b.x + b.w}" stroke="${ink}"/>
  ${label(b.x + b.w / 2, b.y + 35, b.title, b.title.length > 25 ? 21 : 24, 'middle')}
  ${b.fields.map((v, i) => label(b.x + 16, b.y + 84 + i * 29, v, 20)).join('')}
  ${b.methods.map((v, i) => label(b.x + 16, split + 31 + i * 29, v, v.length > 43 ? 17 : 19)).join('')}`
}
const edges = [
  { from: 'auth', to: 'users', points: [[600, 455], [480, 455]], role: [514, 440, 'users'] },
  { from: 'auth', to: 'identities', points: [[850, 390], [850, 275]], role: [735, 330, 'identities'] },
  { from: 'auth', to: 'user', points: [[1100, 470], [1220, 470]], dependency: true },
  { from: 'auth', to: 'filter', points: [[850, 610], [850, 800]], dependency: true },
  { from: 'auth', to: 'identity', points: [[1100, 575], [1160, 575], [1160, 850], [1220, 850]], dependency: true },
]
function assertNoOverlap() {
  for (let i = 0; i < boxes.length; i++) for (const b of boxes.slice(i + 1)) {
    const a = boxes[i]
    if (a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y) throw Error(`类框重叠：${a.id}/${b.id}`)
  }
}
function assertEdgesClearOfBoxes() {
  for (const edge of edges) for (let i = 1; i < edge.points.length; i++) {
    const [x1, y1] = edge.points[i - 1], [x2, y2] = edge.points[i]
    for (const b of boxes.filter(v => v.id !== edge.from && v.id !== edge.to)) {
      const crosses = x1 === x2 ? x1 > b.x && x1 < b.x + b.w && Math.max(y1, y2) > b.y && Math.min(y1, y2) < b.y + b.h : y1 > b.y && y1 < b.y + b.h && Math.max(x1, x2) > b.x && Math.min(x1, x2) < b.x + b.w
      if (crosses) throw Error(`连线${edge.from}/${edge.to}穿过${b.id}`)
    }
  }
}
assertNoOverlap(); assertEdgesClearOfBoxes()
const classBody = label(30, 77, '当前结构化实现 · 只展示require相关成员；users/identities为字段关联，虚线为局部对象、返回类型或静态调用依赖。', 18)
  + edges.map(e => `<polyline points="${e.points.map(p => p.join(',')).join(' ')}" fill="none" stroke="${ink}" stroke-width="1.8" ${e.dependency ? 'stroke-dasharray="8 5"' : ''} marker-end="url(#open)"/>${e.role ? label(...e.role, 18) : ''}`).join('')
  + boxes.map(classBox).join('')

const centers = [90, 410, 750, 1080, 1400, 1720, 2050]
const names = ['obj: 调用者', 'auth: CoreAuthService', 'identities: BearerIdentityStore', 'users: UserRepository', 'user: User', 'ApiException', 'RequestAuditFilter']
const bars = [[0, 190, 1180], [1, 210, 1170], [2, 300, 355], [2, 475, 515], [3, 560, 600], [4, 690, 730], [5, 855, 895], [6, 1060, 1100]]
const messages = [
  [0, 1, 210, '1 require(header)'],
  [1, 2, 300, '2 require(header)'],
  [2, 0, 340, 'AUTHENTICATION_REQUIRED / SESSION_INVALID', true],
  [1, 2, 475, '3 userId = require(header)'],
  [1, 3, 560, '4 candidate = findById(userId)'],
  [1, 4, 690, '5 enabled = isEnabled()'],
  [1, 5, 855, '6 exception = unauthorized()'],
  [1, 0, 915, 'SESSION_INVALID（orElseThrow抛出）', true],
  [1, 6, 1060, '7 actor(userId, username)'],
  [1, 0, 1140, 'CurrentIdentity', true],
]
function frame(x, y, width, height, kind, condition) {
  return `<rect x="${x}" y="${y}" width="${width}" height="${height}" fill="none" stroke="#8793a0"/><rect x="${x}" y="${y}" width="65" height="30" fill="#f0f3f7" stroke="#8793a0"/>${label(x + 10, y + 22, kind, 18)}${label(x + 82, y + 22, condition, 18)}`
}
// 控制框在消息与执行条下层，公共方法的外层执行条保持连续。
let sequenceBody = label(30, 77, 'CoreAuthService.require · caller为调用该方法的Controller/门禁；省略简单get访问器及返回对象构造，异常未捕获不逐层转发。', 18)
sequenceBody += frame(25, 260, 2195, 950, 'alt', '[凭据缺失或失效]')
sequenceBody += `<path d="M25,410H2220" stroke="#8793a0" stroke-dasharray="7 4"/>${label(110, 441, '[凭据有效]', 18)}`
sequenceBody += frame(1000, 640, 1200, 125, 'opt', '[找到用户；Optional.filter调用User::isEnabled]')
sequenceBody += frame(25, 800, 2195, 385, 'alt', '[用户不存在或已停用]')
sequenceBody += `<path d="M25,960H2220" stroke="#8793a0" stroke-dasharray="7 4"/>${label(110, 990, '[用户存在且启用]', 18)}`
sequenceBody += centers.map((x, i) => `<path d="M${x},158V1230" stroke="#9ba6b1" stroke-dasharray="7 6"/><rect x="${x - (i === 0 ? 75 : 145)}" y="100" width="${i === 0 ? 150 : 290}" height="58" fill="white" stroke="${ink}"/>${i >= 5 ? label(x, 123, '«MetaClass»', 17, 'middle') + label(x, 148, ': ' + names[i], 19, 'middle') : label(x, 136, names[i], names[i].length > 28 ? 17 : 19, 'middle')}`).join('')
sequenceBody += bars.map(([i, top, bottom]) => `<rect x="${centers[i] - 7}" y="${top}" width="14" height="${bottom - top}" fill="white" stroke="${ink}"/>`).join('')
for (const [from, to, y, text, reply] of messages) {
  const right = centers[to] > centers[from]
  const x1 = centers[from] + (right ? 7 : -7), x2 = centers[to] + (right ? -7 : 7)
  const sender = bars.find(([i, top, bottom]) => i === from && y >= top && y <= bottom)
  const receiver = bars.find(([i, top, bottom]) => i === to && (reply ? y >= top && y <= bottom : y === top))
  if (!sender || !receiver) throw Error(`消息未对齐执行条：${text}`)
  sequenceBody += `<path d="M${x1},${y}H${x2}" fill="none" stroke="${ink}" stroke-width="1.7" ${reply ? 'stroke-dasharray="7 5"' : ''} marker-end="url(#${reply ? 'open' : 'filled'})"/>${label(Math.min(x1, x2) + 14, y - 12, text, 18)}`
}
mkdirSync(out, { recursive: true })
for (const [name, svg] of [['current-auth-class', document(1700, 1030, '当前身份校验 · 类图', classBody)], ['current-auth-sequence', document(2260, 1270, '当前身份校验 · 顺序图', sequenceBody)]]) {
  writeFileSync(resolve(out, name + '.svg'), svg, 'utf8')
  await sharp(Buffer.from(svg)).png().toFile(resolve(out, name + '.png'))
}
process.stdout.write('Generated 2 SVG + 2 PNG; source, class geometry and execution/message alignment checks passed. Inspect PNGs before delivery.\n')
