import { readFileSync, readdirSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { svg, text, box, edge, assertGeometry, output, contactSheet } from './uml-svg.mjs'

// 单一设计注册表驱动包图、模块类图和只含差异的顺序图；不生成任何生产Java。
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const directory = resolve(root, 'docs/design')
const design = JSON.parse(readFileSync(resolve(directory, 'ddd-diagrams.json'), 'utf8'))
if (design.status !== 'IMPLEMENTED') throw Error('图件必须与当前双策略源码同步')
const javaRoot = resolve(root, 'backend/src/main/java/com/municipal/rbaccore')
const javaFiles = readdirSync(javaRoot, { recursive: true })
function requireJava(type) {
  const simple = type.includes('.') ? type.slice(0, type.indexOf('.')) : type
  const file = javaFiles.find((name) => name.split(/[\\/]/).at(-1) === `${simple}.java`)
  if (!file) throw Error(`缺少图件对应的Java类型：${type}`)
  return readFileSync(resolve(javaRoot, file), 'utf8')
}
const generated = []
// 全部HTTP用例的覆盖说明和图片使用同一注册表，防止删接口后留下虚假覆盖。
const contract=JSON.parse(readFileSync(resolve(root,'api/openapi.json'),'utf8'))
const actualOperations=Object.entries(contract.paths).flatMap(([path,item])=>
  Object.entries(item).filter(([,op])=>op.operationId).map(([method,op])=>({path,method:method.toUpperCase(),operationId:op.operationId})))
if(design.coverage.http.length!==actualOperations.length)throw Error('HTTP覆盖数量与OpenAPI不一致')
for(const actual of actualOperations) {
  const row=design.coverage.http.find(x=>x.operationId===actual.operationId)
  if(!row||row.path!==actual.path||row.method!==actual.method)throw Error('HTTP覆盖遗漏：'+actual.operationId)
  const [port,method]=row.useCase.split('.')
  if(!requireJava(port).includes(method+'('))throw Error('覆盖表用例方法不存在：'+row.useCase)
}
const backendPath=resolve(root,'docs/backend-design.md')
const backend=readFileSync(backendPath,'utf8')
const start='<!-- GENERATED: backend-coverage -->',end='<!-- END: backend-coverage -->'
if(!backend.includes(start)||!backend.includes(end))throw Error('缺少覆盖表生成边界')
const rows=['| HTTP操作 | 用例方法 | 差异图或省略理由 | 核对结论 |','| --- | --- | --- | --- |',
  ...design.coverage.http.map(x=>`| \`${x.method} ${x.path}\` | \`${x.useCase}\` | ${x.fragments} | ${x.reason} |`)]
writeFileSync(backendPath,backend.slice(0,backend.indexOf(start))+start+'\n'+rows.join('\n')+'\n'+backend.slice(backend.indexOf(end)))
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
          fields: ['auth/identity/user/role/resource', 'department/audit/onboarding/shared'],
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
          fields: ['互斥策略装配 / 三种启动入口'],
          methods: [],
        },
        {
          id: 'app',
          x: 600,
          y: 470,
          w: 600,
          h: 230,
          title: '«package» ddd.application',
          fields: ['auth/identity/user/role/resource', 'department/audit/importing/bootstrap/security/onboarding'],
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
          fields: ['领域仓储适配器及JpaDomainMapper'],
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
            'auth/identity/user/role/resource/department',
            'audit/importing/bootstrap/security/onboarding',
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
          points: [
            [1200, 970],
            [1290, 970],
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
      ddd ? 'DDD · 包图' : '结构化 · 包图',
      '文件夹形框表示包，虚线空心箭头表示包依赖；具体类型的接口实现关系见类图。',
      edges.map(edge).join('') + boxes.map((b) => {
        const tabWidth = Math.min(b.w - 20, Math.max(b.w * 0.6, 250, b.title.length * 11))
        return `<path d="M${b.x},${b.y + 42}V${b.y}h${tabWidth}v42h${b.w - tabWidth}v${b.h - 42}H${b.x}Z" fill="${b.center ? '#eef5ff' : 'white'}" stroke="#223046" stroke-width="1.5"/>` +
          text(b.x + 12, b.y + 28, b.title, 21, 'start', tabWidth - 24) +
          [...b.fields, ...b.methods].map((line, i) => text(b.x + 14, b.y + 80 + i * 30, line, 19, 'start', b.w - 28)).join('')
      }).join(''),
    ),
  )
}
await packageDiagram(false)
await packageDiagram(true)

function identityOperations(source, names) {
  return names.map((name) => {
    const declaration = source.match(
      new RegExp(`^ +(?:public +)?(?:default +)?(static +)?([\\w.<>?, ]+) +${name}\\(([^)]*)\\)\\s*[;{]`, 'm'),
    )
    if (!declaration) throw Error(`身份类图缺少方法声明：${name}`)
    const [, staticModifier, result, parameters] = declaration
    const args = parameters.trim() ? parameters.split(',').map((value) => value.trim().split(/\s+/).at(-1)) : []
    const shownArgs = args.length > 3 ? [...args.slice(0, 2), '…'] : args
    return `+ ${name}(${shownArgs.join(', ')}): ${result}${staticModifier ? ' {static}' : ''}`
  })
}

async function applicationIdentityDiagram(ddd) {
  const strategy = ddd ? 'DDD' : 'structured'
  const service = (ddd ? 'Ddd' : 'Structured') + 'ApplicationIdentityService'
  const port = 'ApplicationIdentityUseCase'
  const serviceSource = requireJava(service)
  const portSource = requireJava(port)
  if (!serviceSource.includes(`implements ${port}`)) throw Error(`身份类图实现关系失效：${service}`)
  const names = ['login', 'current', 'allowed', 'user', 'logout', 'departments', 'departmentUsers', 'apiAllowed']

  const collaborators = [
    ddd ? ['repository', 'authorization', 'AuthorizationRepository', ['facts'], 20, 415]
      : ['repository', 'permissions', 'PermissionJpaRepository', ['directPermissions'], 20, 415],
    ddd ? ['scope', 'organizations', 'OrganizationRepository', ['load'], 20, 670]
      : ['scope', 'scope', 'StructuredDepartmentScopeService', ['allowedIds', 'isGlobal'], 20, 670],
    ['catalog', 'operations', 'ApiOperationCatalog', ['find', 'loginPermission'], 1470, 415],
    ddd ? ['policy', null, 'ApplicationAuthorizationPolicy', ['exported'], 1470, 670]
      : ['policy', 'effective', 'StructuredEffectivePermissionService', ['applicableRoleIds'], 1470, 670],
    ['directory', 'directory', 'IdentityDirectoryGateway', ['department', 'departments', 'users'], 645, 1030],
  ]
  const associations = [
    ['authentication', 'AuthenticationUseCase'],
    ['tokens', 'IdentityTokenStore'],
    ...collaborators.filter(([, role]) => role).map(([, role, type]) => [role, type]),
  ]
  const fields = associations.map(([role, type]) => {
    if (!new RegExp(`\\b${type} ${role};`).test(serviceSource))
      throw Error(`身份类图字段失效：${service}.${role}`)
    return `- ${role}: ${type}`
  })
  if (ddd && !serviceSource.includes('ApplicationAuthorizationPolicy.exported('))
    throw Error(`身份类图策略调用失效：${service}`)

  const boxes = [
    { id: 'service', x: 645, y: 385, w: 700, h: 520, title: service, fields, methods: identityOperations(serviceSource, names), center: true },
    { id: 'port', x: 645, y: 105, w: 700, h: 185, title: `«interface» ${port}`, methods: identityOperations(portSource, names.slice(0, 2)) },
    ...collaborators.map(([id, , type, operations, x, y]) => {
      const source = requireJava(type)
      const title = (new RegExp(`\\binterface ${type}\\b`).test(source) ? '«interface» ' : '') + type
      return { id, x, y, w: id === 'directory' ? 700 : 500, h: 128 + operations.length * 28,
        title, methods: identityOperations(source, operations) }
    }),
  ]
  const edges = [
    { from: 'service', to: 'port', kind: 'realization', points: [[995, 385], [995, 290]] },
    ...collaborators.map(([to, role, , , x, y]) => {
      if (to === 'directory') return { from: 'service', to, kind: 'association',
        points: [[995, 905], [995, y]], role: [979, 970, role, 16, 'end'] }
      const left = x === 20
      return { from: 'service', to, kind: role ? 'association' : 'dependency',
        points: [[left ? 645 : 1345, y + 100], [left ? 520 : 1470, y + 100]],
        ...(role ? { role: [left ? 582.5 : 1407.5, y + 82, role, 16, 'middle', 105] } : {}) }
    }),
  ]
  assertGeometry(boxes, edges)
  await emit(`class-${strategy.toLowerCase()}-application-identity`, svg(1990, 1280,
    `${strategy} · 通用应用身份类图`,
    'Service实现八项身份用例；身份目录和操作目录为共享端口。图中保留与本模块相关的成员。',
    edges.map(edge).join('') + boxes.map(box).join('')))
}
await applicationIdentityDiagram(false)
await applicationIdentityDiagram(true)

for (const item of design.entries) {
  const serviceSource = requireJava(item.service)
  const repositorySource = requireJava(item.repository)
  const supportSource = requireJava(item.policy)
  requireJava(item.model)
  const adapterSource = requireJava(item.adapter)
  const adapterFields = [...adapterSource.matchAll(/private\s+final\s+([\w.<>?, ]+)\s+(\w+)\s*;/g)]
    .map((match) => `- ${match[2]}: ${match[1]}`)
  if (!adapterFields.length) throw Error(`适配器缺少真实字段：${item.adapter}`)
  const adapterHeight = Math.max(215, 84 + (adapterFields.length + item.repoMethods.length) * 28)
  const portRoot = resolve(root, 'backend/src/main/java/com/municipal/rbaccore/service/port')
  const portFile = readdirSync(portRoot, { recursive: true }).find((value) =>
    value.endsWith(item.port + '.java'),
  )
  if (!portFile) throw Error(`共用契约不存在：${item.port}`)
  const portSource = readFileSync(resolve(portRoot, portFile), 'utf8')
  for (const operation of item.serviceMethods) {
    const method = operation.match(/^\+ (\w+)\(/)?.[1]
    if (!method || !portSource.includes(method + '(') || !serviceSource.includes(method + '('))
      throw Error(`图件方法不符合共用接口或实际用例：${item.service}/${operation}`)
  }
  for (const [type, source, methods] of [
    [item.repository, repositorySource, item.repoMethods],
    [item.policy, supportSource, item.policyMethods],
  ]) {
    for (const operation of methods) {
      const method = operation.match(/^\+ (\w+)\(/)?.[1]
      if (!method || !source.includes(method + '(')) throw Error(`图件方法已失效：${type}/${operation}`)
    }
  }
  const jpa = {
    UserRepository: 'UserJpaRepository',
    RoleRepository: 'RoleJpaRepository',
    PermissionRepository: 'PermissionJpaRepository',
    ApiResourceRepository: 'ApiResourceJpaRepository',
    ApplicationSystemRepository: 'ApplicationSystemJpaRepository',
    OnboardingRepository: 'ApplicationSystemJpaRepository',
    OrganizationRepository: 'DepartmentJpaRepository',
    AuthorizationRepository: 'UserJpaRepository',
    InitializationRepository: 'ImportSourceJpaRepository',
    AuditQueryGateway: 'AuthorizationLogJpaRepository',
    DddScope: 'DepartmentJpaRepository',
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
        ...(item.supportRole ? [`- ${item.supportRole}: ${item.policy}`] : []),
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
      title: (item.repositoryInterface === false ? '' : '«interface» ') + item.repository,
      fields: [],
      methods: item.repoMethods,
    },
    {
      id: 'model',
      x: 1330,
      y: 405,
      w: 450,
      h: Math.max(345, 110 + (item.fields.length + item.methods.length) * 28),
      title: item.model,
      fields: item.fields,
      methods: item.methods,
    },
    {
      id: 'policy',
      x: 580,
      y: 850,
      w: 640,
      h: Math.max(item.id === 'department' ? 270 : 195, 112 + item.policyMethods.length * 28),
      title: (item.supportRole && item.supportInterface !== false ? '«interface» ' : '') + item.policy,
      fields: [],
      methods: item.policyMethods,
    },
    {
      id: 'adapter',
      x: 20,
      y: 850,
      w: 450,
      h: adapterHeight,
      title: item.adapter,
      fields: adapterFields,
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
      role: [482, 531, item.repoRole || 'repository', 16, 'start', 94],
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
      kind: item.supportRole ? 'association' : 'dependency',
      points: [
        [900, 720],
        [900, 850],
      ],
      ...(item.supportRole ? { role: [750, 788, item.supportRole] } : {}),
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
  if (item.repositoryInterface === false) {
    boxes.pop()
    edges.pop()
  }
  assertGeometry(boxes, edges)
  await emit(
    'class-ddd-' + item.id,
    svg(
      1820,
      Math.max(item.id === 'department' ? 1175 : 1120, 850 + 132 + item.policyMethods.length * 28, 890 + adapterHeight),
      'DDD · ' + item.title + '类图',
      'Service居中。只展示当前实现的关键成员；字段为关联，局部/返回类型为依赖。',
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
  if (!requireJava(scenario.dddSource).includes(scenario.dddAnchor + '('))
    throw Error(`DDD图源锚点已失效：${scenario.id}`)
  for (const variant of ['structured', 'ddd']) {
    const data = scenario[variant]
    const names = ['obj: 调用者', ...data.lifelines]
    if (names.some((value) => design.sharedSequenceExcluded.some((word) => value.includes(word))))
      throw Error(`差异图含共同技术参与者：${scenario.id}`)
    for (let i = 1; i < names.length; i++) {
      const format = data.meta?.includes(i) ? /^\w+$/ : /^\w+: \w+(?:\.\w+)?$/
      if (!format.test(names[i]) || !data.calls.some(([from, to]) => i === from || i === to))
        throw Error(`生命线命名错误或没有消息：${scenario.id}/${names[i]}`)
    }
    for(const [,receiver,label] of data.calls.slice(1)) {
      const type=names[receiver].split(':').at(-1).trim()
      const java=requireJava(type)
      const method=label.match(/(?:^|=\s*)([A-Za-z_]\w*)\(/)?.[1]
      if(!method)throw Error('消息缺少真实方法：'+scenario.id+'/'+label)
      const inherited=/extends\s+(?:JpaRepository|JpaSpecificationExecutor)/.test(java)&&
        ['findById','findAll','findAllById','save','saveAndFlush','count'].includes(method)
      const lombokSetter=method.startsWith('set')&&/@(?:lombok\.)?Setter\b/.test(java)&&
        new RegExp('\\b'+method[3].toLowerCase()+method.slice(4)+'\\s*[;=]').test(java)
      if(!java.includes(method+'(')&&!inherited&&!lombokSetter)
        throw Error('消息方法不属于接收对象：'+type+'/'+method)
      const isStatic = new RegExp(`\\bstatic\\s+[^;={}]*\\b${method}\\(`).test(java)
      if (isStatic !== Boolean(data.meta?.includes(receiver)))
        throw Error(`静态调用与MetaClass不一致：${scenario.id}/${type}.${method}`)
    }
    const other=scenario[variant==='ddd'?'structured':'ddd']
    const messages=data.calls.slice(1).map(c=>c[2].replace(/^.*?=\s*/,''))
    const otherMessages=new Set(other.calls.slice(1).map(c=>c[2].replace(/^.*?=\s*/,'')))
    if(messages.length&&messages.every(x=>otherMessages.has(x)))
      throw Error('两套图只有改名而无实际调用差异：'+scenario.id)
    const centers = names.map((_, i) => 110 + i * 480)
    const width = Math.max(centers.at(-1) + 210, data.notes?.length ? centers[1] + 760 : 0)
    const y = (i) => 245 + i * 170
    const finalMessage = y(data.calls.length - 1)
    const loopBottom = (loop) => y(loop.last) + ((data.opts || []).some((opt) =>
      opt.first >= loop.first && opt.last === loop.last) ? 100 : 85)
    const notesTop = Math.max(finalMessage + 90,
      ...(data.loops || []).map((loop) => loopBottom(loop) + 20),
      ...(data.opts || []).map((opt) => y(opt.last) + 100),
      ...(data.alts || []).map((alt) => y(alt.elseLast) + 120))
    const end = data.notes?.length ? notesTop + 140 : finalMessage + 155
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
      const nestedOpt = (data.opts || []).some((opt) => opt.first === loop.first && opt.last <= loop.last)
      const top = y(loop.first) - (nestedOpt ? 95 : 65),
        bottom = loopBottom(loop)
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
    for (const opt of data.opts || []) {
      const top=y(opt.first)-70,bottom=y(opt.last)+80
      if(opt.first<1||opt.last>=data.calls.length)throw Error('opt索引失效：'+scenario.id)
      body += `<rect x="45" y="${top}" width="${width-90}" height="${bottom-top}" fill="none" stroke="#91a2b7"/><path d="M45,${top+28}h60v-28" fill="none" stroke="#91a2b7"/>${text(55,top+20,'opt',15)}${text(centers[1]+26,top+20,opt.text,16)}`
    }
    for(const alt of data.alts || []) {
      const top=y(alt.first)-65,divider=y(alt.elseFirst)-75,bottom=y(alt.elseLast)+100
      if(alt.first<1||alt.last>=alt.elseFirst||alt.elseLast>=data.calls.length)throw Error('alt索引失效：'+scenario.id)
      if(y(alt.last)+60>=divider)throw Error('alt分界切入接收执行条：'+scenario.id)
      body += `<rect x="25" y="${top}" width="${width-50}" height="${bottom-top}" fill="none" stroke="#91a2b7"/><path d="M25,${top+30}h65v-30 M25,${divider}H${width-25}" fill="none" stroke="#91a2b7"/>${text(35,top+22,'alt',16)}${text(centers[1]+26,top+22,alt.text,16)}${text(centers[1]+26,divider+22,alt.elseText,16)}`
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
    for(const note of data.notes || []) {
      const left=centers[note.who]+30, top=notesTop
      const height=30+note.lines.length*25
      if(top+height>=end-25)throw Error('说明压到执行条结尾：'+scenario.id)
      body += `<rect x="${left}" y="${top}" width="660" height="${height}" fill="#fffbea" stroke="#b6a56d"/>${note.lines.map((line,i)=>text(left+12,top+25+i*25,line,16,'start',636)).join('')}`
    }
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
        scenario.title + ' · ' + (variant === 'ddd' ? 'DDD差异' : 'Structured差异'),
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
