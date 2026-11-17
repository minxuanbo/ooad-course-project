import { readFileSync, readdirSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { svg, text, box, edge, assertGeometry, output, contactSheet } from './uml-svg.mjs'

// 单一设计注册表驱动包图、模块类图和只含差异的顺序图；不生成任何生产Java。
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const directory = resolve(root, 'docs/design')
const design = JSON.parse(readFileSync(resolve(directory, 'ddd-diagrams.json'), 'utf8'))
if (design.status !== 'DESIGN_ONLY') throw Error('DDD尚未编码，不能输出实现完成声明')
const generated = []
async function emit(name, document) {
  await output(directory, name, document)
  generated.push(name)
}
mkdirSync(directory, { recursive: true })

// 包图是UML包依赖图；类图/顺序图skill不替代包图语义。框内列包职责而非伪造Java类。
async function packageDiagram(ddd) {
  const boxes = ddd
    ? [
        {
          id: 'api',
          x: 30,
          y: 110,
          w: 440,
          h: 170,
          title: '«package» api',
          fields: ['auth/user/role/resource', 'department/audit/shared'],
          methods: [],
        },
        {
          id: 'port',
          x: 600,
          y: 110,
          w: 600,
          h: 170,
          title: '«package» service.port',
          fields: ['UseCase / Command / Query / Result'],
          methods: [],
        },
        {
          id: 'config',
          x: 1340,
          y: 110,
          w: 460,
          h: 170,
          title: '«package» config/bootstrap',
          fields: ['拟互斥策略装配 / 三种启动入口'],
          methods: [],
        },
        {
          id: 'app',
          x: 600,
          y: 470,
          w: 600,
          h: 230,
          title: '«package» ddd.application',
          fields: ['auth/user/role/resource/department', 'audit/importing/bootstrap/security'],
          methods: ['同层support：结果装配/错误翻译'],
          center: true,
        },
        {
          id: 'repo',
          x: 1340,
          y: 470,
          w: 460,
          h: 230,
          title: '«package» ddd.domain.repository',
          fields: ['仅领域模型输入输出'],
          methods: [
            'User/Role/Permission/ApiResource/System',
            'Organization/Authorization/Initialization',
          ],
        },
        {
          id: 'model',
          x: 1340,
          y: 900,
          w: 460,
          h: 190,
          title: '«package» ddd.domain.model',
          fields: ['聚合 / 值 / DomainPage / 事实', 'policy：纯领域判断'],
          methods: [],
        },
        {
          id: 'adapter',
          x: 600,
          y: 900,
          w: 600,
          h: 190,
          title: '«package» infrastructure.persistence.ddd',
          fields: ['领域仓储适配器及assembler'],
          methods: [],
        },
        {
          id: 'jpa',
          x: 30,
          y: 900,
          w: 440,
          h: 190,
          title: '«package» infrastructure',
          fields: ['persistence.entity/repository', 'security/web/logging/excel'],
          methods: [],
        },
      ]
    : [
        {
          id: 'config',
          x: 30,
          y: 110,
          w: 440,
          h: 170,
          title: '«package» config/bootstrap',
          fields: ['RuntimeConfiguration / 启动命令'],
          methods: [],
        },
        {
          id: 'api',
          x: 600,
          y: 110,
          w: 600,
          h: 170,
          title: '«package» api',
          fields: ['Controller / dto / vo / shared'],
          methods: [],
        },
        {
          id: 'port',
          x: 600,
          y: 410,
          w: 600,
          h: 190,
          title: '«package» service.port',
          fields: ['用例、支撑接口与输入输出'],
          methods: [],
        },
        {
          id: 'structured',
          x: 600,
          y: 820,
          w: 600,
          h: 220,
          title: '«package» service.structured',
          fields: [
            '事务脚本 / 授权与范围 / 根锁和K',
            'auth/user/role/resource/department',
            'audit/importing/bootstrap/security',
          ],
          methods: [],
          center: true,
        },
        {
          id: 'infra',
          x: 1340,
          y: 410,
          w: 460,
          h: 270,
          title: '«package» infrastructure',
          fields: ['persistence.entity/repository', 'security/web/logging/excel'],
          methods: ['不调用具体业务实现'],
        },
      ]
  const edges = ddd
    ? [
        {
          from: 'api',
          to: 'port',
          points: [
            [470, 200],
            [600, 200],
          ],
        },
        {
          from: 'app',
          to: 'port',
          kind: 'realization',
          points: [
            [900, 470],
            [900, 280],
          ],
        },
        {
          from: 'config',
          to: 'app',
          points: [
            [1340, 200],
            [1270, 200],
            [1270, 560],
            [1200, 560],
          ],
        },
        {
          from: 'app',
          to: 'repo',
          points: [
            [1200, 540],
            [1340, 540],
          ],
        },
        {
          from: 'app',
          to: 'model',
          points: [
            [1200, 650],
            [1240, 650],
            [1240, 1030],
            [1340, 1030],
          ],
        },
        {
          from: 'repo',
          to: 'model',
          points: [
            [1570, 700],
            [1570, 900],
          ],
        },
        {
          from: 'adapter',
          to: 'repo',
          kind: 'realization',
          points: [
            [1200, 940],
            [1290, 940],
            [1290, 630],
            [1340, 630],
          ],
        },
        {
          from: 'adapter',
          to: 'model',
          points: [
            [1200, 1065],
            [1340, 1065],
          ],
        },
        {
          from: 'adapter',
          to: 'jpa',
          points: [
            [600, 990],
            [470, 990],
          ],
        },
        {
          from: 'jpa',
          to: 'port',
          points: [
            [470, 1020],
            [520, 1020],
            [520, 230],
            [600, 230],
          ],
        },
      ]
    : [
        {
          from: 'config',
          to: 'api',
          points: [
            [470, 195],
            [600, 195],
          ],
        },
        {
          from: 'api',
          to: 'port',
          points: [
            [900, 280],
            [900, 410],
          ],
        },
        {
          from: 'structured',
          to: 'port',
          kind: 'realization',
          points: [
            [900, 820],
            [900, 600],
          ],
        },
        {
          from: 'structured',
          to: 'infra',
          points: [
            [1200, 920],
            [1270, 920],
            [1270, 580],
            [1340, 580],
          ],
        },
        {
          from: 'infra',
          to: 'port',
          points: [
            [1340, 480],
            [1200, 480],
          ],
        },
        {
          from: 'config',
          to: 'structured',
          points: [
            [250, 280],
            [250, 920],
            [600, 920],
          ],
        },
      ]
  assertGeometry(boxes, edges)
  await emit(
    ddd ? 'package-ddd-design' : 'package-structured-current',
    svg(
      1840,
      1160,
      ddd ? 'DDD目标 · 包图' : '结构化现状 · 包图',
      '虚线空心箭头：依赖；蓝色虚线空心三角：接口实现。DDD为待审核设计，不表示已编码。',
      edges.map(edge).join('') + boxes.map(box).join(''),
    ),
  )
}
await packageDiagram(false)
await packageDiagram(true)

for (const item of design.entries) {
  const portRoot = resolve(root, 'backend/src/main/java/com/municipal/rbaccore/service/port')
  const portFile = readdirSync(portRoot, { recursive: true }).find((value) =>
    value.endsWith(item.port + '.java'),
  )
  if (!portFile) throw Error(`共用契约不存在：${item.port}`)
  const portSource = readFileSync(resolve(portRoot, portFile), 'utf8')
  for (const operation of item.serviceMethods) {
    const method = operation.match(/^\+ (\w+)\(/)?.[1]
    if (!method || !portSource.includes(method + '('))
      throw Error(`目标用例方法不符合共用接口：${item.service}/${operation}`)
  }
  const jpa = {
    UserRepository: 'UserJpaRepository',
    RoleRepository: 'RoleJpaRepository',
    PermissionRepository: 'PermissionJpaRepository',
    ApiResourceRepository: 'ApiResourceJpaRepository',
    ApplicationSystemRepository: 'ApplicationSystemJpaRepository',
    OrganizationRepository: 'DepartmentJpaRepository',
    AuthorizationRepository: 'ApiResourceJpaRepository',
    InitializationRepository: 'ImportSourceJpaRepository',
    AuditQueryGateway: 'AuthorizationLogJpaRepository',
  }[item.repository]
  if (!jpa) throw Error(`缺少适配器的技术仓储来源：${item.repository}`)
  readFileSync(
    resolve(
      root,
      'backend/src/main/java/com/municipal/rbaccore/infrastructure/persistence/repository',
      jpa + '.java',
    ),
    'utf8',
  )
  const boxes = [
    {
      id: 'service',
      x: 580,
      y: 400,
      w: 640,
      h: 320,
      title: item.service,
      fields: [
        `- ${item.repoRole || 'repository'}: ${item.repository}`,
        `- ${item.supportRole || 'policy'}: ${item.policy}`,
      ],
      methods: item.serviceMethods,
      center: true,
    },
    {
      id: 'port',
      x: 580,
      y: 105,
      w: 640,
      h: 190,
      title: '«interface» ' + item.port,
      fields: [],
      methods: item.serviceMethods.slice(0, 2),
    },
    {
      id: 'repo',
      x: 20,
      y: 405,
      w: 450,
      h: 285,
      title: '«interface» ' + item.repository,
      fields: [],
      methods: item.repoMethods,
    },
    {
      id: 'model',
      x: 1330,
      y: 405,
      w: 450,
      h: 345,
      title: item.model,
      fields: item.fields,
      methods: item.methods,
    },
    {
      id: 'policy',
      x: 580,
      y: 850,
      w: 640,
      h: 195,
      title: (item.supportRole ? '«interface» ' : '') + item.policy,
      fields: [],
      methods: item.policyMethods,
    },
    {
      id: 'adapter',
      x: 20,
      y: 850,
      w: 450,
      h: 215,
      title: item.adapter,
      fields: [`- jpa: ${jpa}`],
      methods: item.repoMethods,
    },
  ]
  const edges = [
    {
      from: 'service',
      to: 'port',
      kind: 'realization',
      points: [
        [900, 400],
        [900, 295],
      ],
    },
    {
      from: 'service',
      to: 'repo',
      kind: 'association',
      points: [
        [580, 550],
        [470, 550],
      ],
      role: [482, 531, item.repoRole || 'repository'],
    },
    {
      from: 'service',
      to: 'model',
      points: [
        [1220, 550],
        [1330, 550],
      ],
    },
    {
      from: 'service',
      to: 'policy',
      kind: 'association',
      points: [
        [900, 720],
        [900, 850],
      ],
      role: [750, 788, item.supportRole || 'policy'],
    },
    {
      from: 'adapter',
      to: 'repo',
      kind: 'realization',
      points: [
        [245, 850],
        [245, 690],
      ],
    },
  ]
  assertGeometry(boxes, edges)
  await emit(
    'class-ddd-' + item.id,
    svg(
      1820,
      1120,
      'DDD目标 · ' + item.title + '类图',
      '待实现设计；Service居中。只截取关键成员，完整依赖和不变量见ddd-design.md；字段为关联，局部/返回类型为依赖。',
      edges.map(edge).join('') + boxes.map(box).join(''),
    ),
  )
}

// 每次调用有独立接收执行条；只画成功差异片段，共同前置/后置与异常翻译不重复。
for (const scenario of design.scenarios) {
  const source = readFileSync(
    resolve(root, 'backend/src/main/java/com/municipal/rbaccore', scenario.source),
    'utf8',
  )
  if (!source.includes(scenario.anchor)) throw Error(`结构化图源锚点已失效：${scenario.id}`)
  for (const variant of ['structured', 'ddd']) {
    const data = scenario[variant]
    const names = ['obj: 调用者', ...data.lifelines]
    if (names.some((value) => design.sharedSequenceExcluded.some((word) => value.includes(word))))
      throw Error(`差异图含共同技术参与者：${scenario.id}`)
    const centers = names.map((_, i) => 110 + i * 380)
    const width = centers.at(-1) + 210
    const y = (i) => 245 + i * 170
    const end = y(data.calls.length - 1) + 105
    const bars = [
      { who: 0, x: centers[0], top: 210, bottom: end },
      { who: 1, x: centers[1], top: y(0), bottom: end - 25 },
    ]
    for (let i = 1; i < data.calls.length; i++) {
      const [from, to] = data.calls[i]
      bars.push({
        who: to,
        x: centers[to] + (from === to ? 19 : 0),
        top: y(i),
        bottom: y(i) + 60,
        nested: from === to,
      })
    }
    let body = ''
    for (const loop of data.loops || []) {
      const top = y(loop.first) - 65,
        bottom = y(loop.last) + 82
      for (const b of bars.filter(
        (b) =>
          b.who !== 0 &&
          !(b.who === 1 && !b.nested) &&
          b.top >= y(loop.first) &&
          b.top <= y(loop.last),
      )) {
        if (b.top <= top || b.bottom >= bottom) throw Error('loop未完整包含接收执行条')
      }
      body += `<rect x="25" y="${top}" width="${width - 50}" height="${bottom - top}" fill="#f8fafc" stroke="#91a2b7"/><path d="M25,${top + 30}h70v-30" fill="none" stroke="#91a2b7"/>${text(35, top + 22, 'loop', 16)}${text(centers[1] + 26, top + 22, loop.text, 16)}`
    }
    body += centers
      .map((x, i) => {
        const meta = (data.meta || []).includes(i)
        return `<path d="M${x},165V${end + 20}" stroke="#9aa7b7" stroke-dasharray="7 6"/><rect x="${x - (i ? 170 : 90)}" y="100" width="${i ? 340 : 180}" height="65" fill="white" stroke="#223046"/>${meta ? text(x, 126, '«MetaClass»', 17, 'middle') + text(x, 151, ': ' + names[i], 20, 'middle', 318) : text(x, 139, names[i], 19, 'middle', i ? 318 : 160)}`
      })
      .join('')
    body += bars
      .map(
        (b) =>
          `<rect x="${b.x - 7}" y="${b.top}" width="14" height="${b.bottom - b.top}" fill="white" stroke="#223046"/>`,
      )
      .join('')
    for (let i = 0; i < data.calls.length; i++) {
      const [from, to, label] = data.calls[i]
      if (design.sharedSequenceExcluded.some((word) => label.includes(word)))
        throw Error(`差异图含共同流程：${label}`)
      const receiver = bars.find((b) => b.who === to && b.top === y(i))
      const sender = bars.find(
        (b) => b.who === from && b.top <= y(i) && b.bottom >= y(i) && !b.nested,
      )
      if (!receiver || !sender) throw Error(`调用未吸附执行条：${scenario.id}/${label}`)
      if (from === to) {
        const left = receiver.x + 7,
          start = sender.x + 7
        body += `<path d="M${start},${y(i) - 22}h65V${y(i)}H${left}" fill="none" stroke="#223046" marker-end="url(#filled)"/>${text(start + 78, y(i) - 8, i + 1 + ' ' + label, 17, 'start', width - start - 110)}`
      } else {
        const right = to > from
        const x1 = sender.x + (right ? 7 : -7),
          x2 = receiver.x + (right ? -7 : 7)
        body += `<path d="M${x1},${y(i)}H${x2}" stroke="#223046" marker-end="url(#filled)"/>${text(Math.min(x1, x2) + 12, y(i) - 14, i + 1 + ' ' + label, 18, 'start', Math.abs(x2 - x1) - 24)}`
      }
    }
    await emit(
      'sequence-' + scenario.id + '-' + variant,
      svg(
        width,
        end + 70,
        scenario.title + ' · ' + (variant === 'ddd' ? 'DDD目标差异' : 'Structured现状差异'),
        '仅成功差异片段；共同HTTP/身份/日志/锁后鉴权/K/提交均省略，不表示取消。赋值调用不重复画回复；异常约定见正文。',
        body,
      ),
    )
  }
}
writeFileSync(
  resolve(directory, 'ddd-generated-manifest.json'),
  JSON.stringify({ status: design.status, generated }, null, 2) + '\n',
)
if (process.env.UML_CONTACT_SHEET)
  await contactSheet(directory, generated, process.env.UML_CONTACT_SHEET)
process.stdout.write(
  `Generated ${generated.length} SVG + PNG pairs; package/class geometry, source anchors, sequence scope and activations checked. Open PNGs before delivery.\n`,
)
