import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

// 当前实现图：只截取StructuredAuthenticationService.require相关成员，目标DDD设计不在此生成。
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const out = resolve(root, 'docs/design')
const require = createRequire(import.meta.url)
const sharp = require(
  process.env.UML_SHARP_MODULE || resolve(dirname(process.execPath), '../node_modules/sharp'),
)
const esc = (value) =>
  String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
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
  auth: 'backend/src/main/java/com/municipal/rbaccore/service/structured/auth/StructuredAuthenticationService.java',
  identities:
    'backend/src/main/java/com/municipal/rbaccore/service/port/security/IdentityTokenStore.java',
  filter: 'backend/src/main/java/com/municipal/rbaccore/service/port/security/RequestContext.java',
}
for (const [key, path] of Object.entries(sources)) {
  const code = readFileSync(resolve(root, path), 'utf8')
  const expected =
    key === 'auth'
      ? [
          'CurrentIdentity require(String header)',
          'findById(identities.require(header))',
          'filter(UserEntity::isEnabled)',
          'orElseThrow(BusinessException::unauthorized)',
        ]
      : key === 'filter'
        ? ['void actor(']
        : ['Long require(String header)']
  for (const text of expected)
    if (!code.includes(text)) throw Error(`${path}不再包含图中操作：${text}`)
}

const boxes = [
  {
    id: 'auth',
    x: 600,
    y: 390,
    w: 500,
    h: 250,
    title: 'StructuredAuthenticationService',
    fields: [
      '- users: UserJpaRepository',
      '- identities: IdentityTokenStore',
      '- requestContext: RequestContext',
    ],
    methods: ['+ require(header: String): CurrentIdentity'],
  },
  {
    id: 'users',
    x: 40,
    y: 380,
    w: 440,
    h: 170,
    title: '«interface» UserJpaRepository',
    fields: [],
    methods: ['+ findById(id: Long): Optional<UserEntity>', '  （继承JpaRepository的方法）'],
  },
  {
    id: 'identities',
    x: 600,
    y: 105,
    w: 500,
    h: 170,
    title: '«interface» IdentityTokenStore',
    fields: [],
    methods: ['+ require(header: String): Long'],
  },
  {
    id: 'user',
    x: 1220,
    y: 380,
    w: 430,
    h: 190,
    title: 'UserEntity',
    fields: ['- enabled: boolean'],
    methods: ['+ isEnabled(): boolean', '+ getId(), getUsername(), getDepartment()'],
  },
  {
    id: 'filter',
    x: 600,
    y: 800,
    w: 500,
    h: 160,
    title: '«interface» RequestContext',
    fields: [],
    methods: ['+ actor(id: Long, username: String): void'],
  },
  {
    id: 'identity',
    x: 1220,
    y: 770,
    w: 430,
    h: 190,
    title: '«record» CurrentIdentity',
    fields: ['id: Long', 'username: String', 'departmentId: Long'],
    methods: [],
  },
]
function classBox(b) {
  const split = b.y + 54 + Math.max(1, b.fields.length) * 29 + 12
  return `<rect x="${b.x}" y="${b.y}" width="${b.w}" height="${b.h}" fill="white" stroke="${ink}" stroke-width="2"/><path d="M${b.x},${b.y + 54}H${b.x + b.w} M${b.x},${split}H${b.x + b.w}" stroke="${ink}"/>
  ${label(b.x + b.w / 2, b.y + 35, b.title, b.title.length > 25 ? 21 : 24, 'middle')}
  ${b.fields.map((v, i) => label(b.x + 16, b.y + 84 + i * 29, v, 20)).join('')}
  ${b.methods.map((v, i) => label(b.x + 16, split + 31 + i * 29, v, v.length > 43 ? 17 : 19)).join('')}`
}
const edges = [
  {
    from: 'auth',
    to: 'users',
    points: [
      [600, 455],
      [480, 455],
    ],
    role: [514, 440, 'users'],
  },
  {
    from: 'auth',
    to: 'identities',
    points: [
      [850, 390],
      [850, 275],
    ],
    role: [735, 330, 'identities'],
  },
  {
    from: 'auth',
    to: 'user',
    points: [
      [1100, 470],
      [1220, 470],
    ],
    dependency: true,
  },
  {
    from: 'auth',
    to: 'filter',
    points: [
      [850, 640],
      [850, 800],
    ],
    role: [662, 725, 'requestContext'],
  },
  {
    from: 'auth',
    to: 'identity',
    points: [
      [1100, 575],
      [1160, 575],
      [1160, 850],
      [1220, 850],
    ],
    dependency: true,
  },
]
function assertNoOverlap() {
  for (let i = 0; i < boxes.length; i++)
    for (const b of boxes.slice(i + 1)) {
      const a = boxes[i]
      if (a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y)
        throw Error(`类框重叠：${a.id}/${b.id}`)
    }
}
function assertEdgesClearOfBoxes() {
  for (const edge of edges)
    for (let i = 1; i < edge.points.length; i++) {
      const [x1, y1] = edge.points[i - 1],
        [x2, y2] = edge.points[i]
      for (const b of boxes.filter((v) => v.id !== edge.from && v.id !== edge.to)) {
        const crosses =
          x1 === x2
            ? x1 > b.x && x1 < b.x + b.w && Math.max(y1, y2) > b.y && Math.min(y1, y2) < b.y + b.h
            : y1 > b.y && y1 < b.y + b.h && Math.max(x1, x2) > b.x && Math.min(x1, x2) < b.x + b.w
        if (crosses) throw Error(`连线${edge.from}/${edge.to}穿过${b.id}`)
      }
    }
}
assertNoOverlap()
assertEdgesClearOfBoxes()
const classBody =
  label(
    30,
    77,
    '当前结构化实现 · 只展示require相关成员；users/identities/requestContext为字段关联，虚线为局部对象、返回类型或静态调用依赖。',
    18,
  ) +
  edges
    .map(
      (e) =>
        `<polyline points="${e.points.map((p) => p.join(',')).join(' ')}" fill="none" stroke="${ink}" stroke-width="1.8" ${e.dependency ? 'stroke-dasharray="8 5"' : ''} marker-end="url(#open)"/>${e.role ? label(...e.role, 18) : ''}`,
    )
    .join('') +
  boxes.map(classBox).join('')

mkdirSync(out, { recursive: true })
const value = document(1700, 1030, '当前身份校验 · 类图', classBody)
writeFileSync(resolve(out, 'current-auth-class.svg'), value, 'utf8')
await sharp(Buffer.from(value)).png().toFile(resolve(out, 'current-auth-class.png'))
process.stdout.write(
  'Generated current class SVG + PNG. Difference-only sequences use build-ddd-design.mjs. Inspect PNG before delivery.\n',
)
