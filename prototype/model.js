/** 普通脚本兼容file://双击打开；封装内存模型，不污染其他原型脚本。 */
(function () {
'use strict';
const { createCatalog } = typeof module !== 'undefined' && module.exports ? require('./catalog.js') : globalThis.MunicipalCatalog;
/** 原型内存模型：人员/组织虚构，业务矩阵取自原Excel；不调用后端或持久化存储。 */
const PEOPLE = ['tree', 'users', 'currentRoles', 'candidates', 'assign', 'disable', 'move', 'departments.list', 'departments.rename', 'departments.create', 'departments.delete'];
/** 与现行17项维护恢复操作对应；日志、停用和调动不属于完整K。 */
const MAINTENANCE = [
  'tree', 'users', 'currentRoles', 'candidates', 'assign',
  'roles.list', 'roles.create', 'roles.patch', 'roles.permissions.read', 'roles.permissions.write',
  'systems', 'permissions.tree', 'apis.list', 'permissions.patch', 'apis.patch',
  'permissions.apis.read', 'permissions.apis.write',
];
const DEMO_PASSWORD = 'Demo@2026';
const DEMOS = [
  { id: 1, label: '系统管理员', account: 'admin', note: '部门、人员、角色、资源与日志' },
  { id: 2, label: '公司领导', account: 'SG000001', note: '第一分公司及下级' },
  { id: 3, label: '部门经理', account: 'SG000002', note: '工程管理部及下级' },
  { id: 4, label: '项目经理', account: 'SG000003', note: '滨江项目部' },
  { id: 5, label: '普通员工', account: 'SG000004', note: '演示管理端准入拒绝' },
];

/** 稳定错误外形用于演示HTTP语义，不是实际服务端响应。 */
class DemoError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
    this.requestId = 'demo-' + globalThis.crypto.randomUUID().slice(0, 8);
  }
}
const fail = (status, code, message) => { throw new DemoError(status, code, message); };

function createDemoModel() {
  const { permissions: permissionCatalog, apis: apiCatalog, systems, applicationSource } = createCatalog();
  const permissionIndex = new Map(permissionCatalog.flatMap(item => [[item.key, item], [item.id, item]]));
  /** 基础角色初始业务按钮按Excel列取值，显式加入所需祖先菜单，不产生权限继承。 */
  const initialBusinessPermissions = roleIndex => {
    const keys = new Set();
    for (const item of permissionCatalog.filter(item => item.sourceKind === 'operation' && item.referenceRoleIndices.includes(roleIndex))) {
      for (let current = item; current; current = permissionIndex.get(current.parentId)) keys.add(current.key);
    }
    return [...keys];
  };
  const departments = [
    { id: 1, parentId: null, name: '总公司', type: '公司' },
    { id: 2, parentId: 1, name: '综合管理部', type: '部门' },
    { id: 3, parentId: 1, name: '工程管理部', type: '部门' },
    { id: 4, parentId: 1, name: '财务管理部', type: '部门' },
    { id: 5, parentId: 3, name: '滨江项目部', type: '项目' },
    { id: 6, parentId: 3, name: '城北项目部', type: '项目' },
    { id: 10, parentId: 1, name: '第一分公司', type: '分公司' },
    { id: 11, parentId: 10, name: '综合管理部', type: '部门' },
    { id: 12, parentId: 10, name: '工程管理部', type: '部门' },
    { id: 13, parentId: 10, name: '财务管理部', type: '部门' },
    { id: 14, parentId: 12, name: '东湖项目部', type: '项目' },
    { id: 20, parentId: 1, name: '第二分公司', type: '分公司' },
    { id: 21, parentId: 20, name: '综合管理部', type: '部门' },
    { id: 22, parentId: 20, name: '工程管理部', type: '部门' },
    { id: 23, parentId: 22, name: '新城项目部', type: '项目' },
  ];
  const roles = [
    { id: 1, name: '系统管理员', code: 'SYSTEM_ADMIN', enabled: true, permissions: [...MAINTENANCE, 'disable', 'move', 'logs',
      'departments.rename', 'departments.list', 'departments.create', 'departments.delete',
      ...permissionCatalog.filter(item => item.systemId !== 1).map(item => item.key)], description: '完整系统维护与日志查询' },
    { id: 2, name: '公司领导', code: 'COMPANY_LEADER', enabled: true, permissions: [...PEOPLE, ...initialBusinessPermissions(1)], description: '范围内人员/部门管理，业务权限初始按Excel公司领导列' },
    { id: 3, name: '部门经理', code: 'DEPARTMENT_MANAGER', enabled: true, permissions: [...PEOPLE, ...initialBusinessPermissions(2)], description: '范围内人员/部门管理，业务权限初始按Excel部门经理列' },
    { id: 4, name: '项目经理', code: 'PROJECT_MANAGER', enabled: true, permissions: [...PEOPLE, ...initialBusinessPermissions(3)], description: '范围内人员/部门管理，业务权限初始按Excel项目经理列' },
    { id: 5, name: '普通员工', code: 'EMPLOYEE', enabled: true, permissions: initialBusinessPermissions(4), description: '业务权限初始按Excel普通员工列，无本RBAC管理操作权限' },
  ];
  const extraRoles = ['人员查询', '组织信息查阅', '项目人员联络', '工程人员协同', '综合事务协同',
    '资料审核协同', '项目现场协同', '质量人员协同', '合同人员协同', '安全人员协同',
    '财务人员协同', '档案人员协同', '计划人员协同', '设备人员协同', '成本人员协同', '采购人员协同'];
  extraRoles.forEach((name, index) => roles.push({
    id: 10 + index, name, code: 'DEMO_' + String(index + 1).padStart(2, '0'), enabled: true,
    permissions: index % 3 === 0 ? ['tree', 'users', 'currentRoles'] : [],
    description: index % 3 === 0 ? '查看范围内组织与人员，不含写操作' : '演示候选分页，无本管理端操作权限',
  }));
  roles.push({ id: 90, name: '历史项目审阅', code: 'LEGACY_REVIEW', enabled: false, permissions: ['users'], description: '已停用，仅在当前已授角色中保留' });
  roles.push({ id: 91, name: 'OA业务查看示例', code: 'OA_VIEW_SAMPLE', enabled: true,
    permissions: [permissionCatalog.find(item => item.systemId === 2 && item.type === 'BUTTON').key], description: '业务权限不等于RBAC管理端准入；默认仅管理员可授' });
  roles.forEach(item => {
    item.authorityLevel = item.id <= 5 ? 6 - item.id : 0;
    item.requiredGrantLevel = item.id <= 5 ? 6 - item.id : 5;
  });
  const users = [
    { id: 1, name: '系统管理员', username: 'admin', departmentId: 1, enabled: true, roleIds: [1], phone: null },
    { id: 2, name: '林知行', username: 'SG000001', departmentId: 10, enabled: true, roleIds: [2], phone: '13800000001' },
    { id: 3, name: '周予安', username: 'SG000002', departmentId: 3, enabled: true, roleIds: [3], phone: '13800000002' },
    { id: 4, name: '许景文', username: 'SG000003', departmentId: 5, enabled: true, roleIds: [4], phone: '13800000003' },
    { id: 5, name: '沈清禾', username: 'SG000004', departmentId: 5, enabled: true, roleIds: [5], phone: '13800000004' },
    { id: 6, name: '陈知远', username: 'SG000005', departmentId: 1, enabled: true, roleIds: [2], phone: '13800000005' },
    { id: 7, name: '宋亦宁', username: 'SG000006', departmentId: 3, enabled: true, roleIds: [5, 90], phone: '13800000006' },
    { id: 8, name: '陆明川', username: 'SG000007', departmentId: 12, enabled: true, roleIds: [3], phone: '13800000007' },
  ];
  const surnames = ['李', '王', '张', '刘', '陈', '杨', '赵', '黄', '周', '吴', '徐', '孙', '郑', '胡', '林', '郭'];
  const givenNames = ['沐晨', '清越', '景和', '书言', '予宁', '明轩', '知夏', '以安', '云舟', '星禾', '言溪'];
  const employeeDepartments = [2, 3, 4, 5, 6, 11, 12, 13, 14, 21, 22, 23];
  for (let id = 9; id <= 168; id += 1) {
    users.push({
      id, name: surnames[(id - 9) % surnames.length] + givenNames[Math.floor((id - 9) / surnames.length) % givenNames.length],
      username: 'SG' + String(id - 1).padStart(6, '0'),
      departmentId: employeeDepartments[(id - 9) % employeeDepartments.length],
      enabled: id % 23 !== 0,
      roleIds: id % 19 === 0 ? [5, 10] : [5],
      phone: '139' + String(id).padStart(8, '0'),
    });
  }
  const positions = ['技术员', '工程师', '资料员', '预算员', '安全员', '施工员', '会计', '综合办事员'];
  Object.assign(users.find(person => person.id === 9), { name: '李沐晨', departmentId: 10, roleIds: [2] });
  const principalPositions = { 1: '系统维护岗', 2: '分公司经理', 3: '部长', 4: '项目经理', 5: '工程师', 6: '副总经理', 7: '资料员', 8: '副部长', 9: '分公司副经理' };
  users.forEach(person => { person.position = principalPositions[person.id] || positions[(person.id - 9) % positions.length]; });
  let currentId = null;
  let nextDepartmentId = Math.max(...departments.map(item => item.id)) + 1;
  const department = id => departments.find(item => item.id === Number(id));
  const user = id => users.find(item => item.id === Number(id));
  const role = id => roles.find(item => item.id === Number(id));
  const descendants = id => {
    const selected = new Set([Number(id)]);
    for (let previous = -1; previous !== selected.size;) {
      previous = selected.size;
      departments.forEach(item => { if (selected.has(item.parentId)) selected.add(item.id); });
    }
    return selected;
  };
  const apiIndex = new Map(apiCatalog.map(item => [item.id, item]));
  const systemIndex = new Map(systems.map(item => [item.id, item]));
  const permission = key => permissionIndex.get(key);
  const validPermission = key => {
    const item = permission(key);
    return item?.enabled && systemIndex.get(item.systemId)?.enabled;
  };
  const effectivePermissionKeys = (person, localOnly = false) => new Set(person.roleIds.flatMap(id => {
    const item = role(id);
    return item?.enabled ? item.permissions.filter(key => validPermission(key) && (!localOnly || permission(key).systemId === 1)) : [];
  }));
  const effective = person => new Set([...effectivePermissionKeys(person)].flatMap(key => {
    const item = permission(key);
    return item.apiIds.map(id => apiIndex.get(id)).filter(api => api?.enabled && api.systemId === item.systemId).map(api => api.key);
  }));
  const authority = person => Math.max(0, ...person.roleIds.map(id => role(id)?.enabled ? role(id).authorityLevel : 0));
  const protectionLevel = person => Math.max(0, ...person.roleIds.map(id => {
    const item = role(id);
    return item ? (item.authorityLevel ? (item.enabled ? item.authorityLevel : 0) : item.requiredGrantLevel) : 0;
  }));
  const systemAdministrator = person => person.departmentId === 1 && authority(person) === 5;
  const complete = person => {
    if (!person.enabled || person.departmentId !== 1 || authority(person) !== 5) return false;
    const keys = effective(person);
    return MAINTENANCE.every(key => keys.has(key));
  };
  const targetWritable = (actor, selected) => systemAdministrator(actor)
    || (actor.id !== selected.id && protectionLevel(selected) < authority(actor) && !complete(selected));
  const requireIdentity = () => {
    const actor = user(currentId);
    if (!actor?.enabled) fail(401, 'SESSION_INVALID', '登录身份已失效，请重新登录');
    return actor;
  };
  const authorize = key => {
    const actor = requireIdentity();
    if (!effective(actor).has(key)) fail(403, 'PERMISSION_DENIED', '当前操作权限已失效，请刷新后重试');
    return actor;
  };
  const target = (id, key, write = false) => {
    const actor = authorize(key);
    const selected = user(id);
    if (!selected) {
      if (actor.departmentId !== 1) fail(403, 'MANAGEMENT_SCOPE_DENIED', '该人员不在你的管理范围内');
      fail(404, 'RESOURCE_NOT_FOUND', '该人员不存在，请刷新列表');
    }
    if (!descendants(actor.departmentId).has(selected.departmentId)) fail(403, 'MANAGEMENT_SCOPE_DENIED', '该人员不在你的管理范围内');
    if (write && !targetWritable(actor, selected)) {
      fail(403, 'MANAGEMENT_SCOPE_DENIED', '仅能修改级别严格低于本人的人员；本人、同级和上级只读');
    }
    return { actor, selected };
  };
  const candidates = actor => {
    const keys = effectivePermissionKeys(actor), level = authority(actor);
    return roles.filter(item => item.enabled && item.requiredGrantLevel <= level
      && item.permissions.filter(validPermission).every(key => keys.has(key)));
  };
  const preserveMaintenance = (selected, change) => {
    const previous = structuredClone(selected);
    change();
    if (!users.some(complete)) {
      Object.assign(selected, previous);
      fail(409, 'LAST_ADMIN_REQUIRED', '需至少保留一名完整系统维护者，本次修改未保存');
    }
  };
  const publicUser = person => ({
    id: person.id, name: person.name, username: person.username, departmentId: person.departmentId,
    position: person.position, enabled: person.enabled, roles: person.roleIds.map(id => structuredClone(role(id))).filter(Boolean),
  });
  const globalAuthorize = key => {
    const actor = authorize(key);
    if (actor.departmentId !== 1) fail(403, 'MANAGEMENT_SCOPE_DENIED', '该操作仅对全公司维护范围开放');
    return actor;
  };
  /** 共同根可建分公司；部门只可建项目，项目必须为叶子。 */
  const childTypes = node => node.id === 1 ? ['分公司', '部门', '项目']
    : ['公司', '分公司'].includes(node.type) ? ['部门', '项目'] : node.type === '部门' ? ['项目'] : [];
  const departmentAccess = (actor, id, key, parentSelection = false) => {
    if (!effective(actor).has(key) || authority(actor) < 2) return false;
    const node = department(id);
    if (!node || !descendants(actor.departmentId).has(node.id)) return false;
    if (parentSelection) return true;
    if (key === 'departments.delete' && node.id === 1) return false;
    return systemAdministrator(actor) || node.id !== actor.departmentId;
  };
  const departmentTarget = (id, key, parentSelection = false) => {
    const actor = authorize(key);
    const selected = department(id);
    if (!selected) fail(404, 'RESOURCE_NOT_FOUND', '该部门不存在');
    if (!departmentAccess(actor, id, key, parentSelection)) fail(403, 'MANAGEMENT_SCOPE_DENIED', '只能维护本人部门的下级，不能修改本人部门、上级或平级');
    return selected;
  };
  const named = (value, label) => {
    const text = String(value ?? '').trim();
    if (!text || text.length > 100) fail(400, 'INVALID_ARGUMENT', label + '应为1至100个字符');
    return text;
  };
  const paged = (items, { page = 0, size = 10 } = {}) => ({ items: structuredClone(items.slice(page * size, (page + 1) * size)), total: items.length, page, size });
  const requireRole = id => {
    const selected = role(id);
    if (!selected) fail(404, 'RESOURCE_NOT_FOUND', '该角色不存在');
    return selected;
  };
  const registeredPermission = id => {
    const item = permission(Number(id));
    if (!item) fail(404, 'RESOURCE_NOT_FOUND', '该权限不存在');
    if (!systems.some(system => system.id === item.systemId)) fail(403, 'MANAGEMENT_SCOPE_DENIED', '只能维护已登记系统的资源');
    return item;
  };
  const rolePermissionResult = selected => ({ role: structuredClone(selected), permissions: structuredClone(selected.permissions.map(permission).filter(Boolean)) });
  const permissionApiResult = selected => ({ permission: structuredClone(selected), apis: structuredClone(selected.apiIds.map(id => apiCatalog.find(item => item.id === id)).filter(Boolean)) });
  const logs = [];
  const cursorIndex = new Map();
  const record = ({ actor = null, eventType = 'AUTHORIZATION', allowed = true, reason = 'OK', httpMethod = 'GET', requestPath = '/api/users', httpStatus = 200, permissionCode = null, occurredAt = new Date().toISOString(), requestId = 'demo-' + globalThis.crypto.randomUUID().slice(0, 8) } = {}) => {
    const entry = { id: logs.length + 1, occurredAt, userId: actor?.id ?? null, username: actor?.username ?? null, eventType, allowed, reason, httpMethod, requestPath, httpStatus, permissionCode, appCode: 'RBAC', requestId };
    logs.push(entry);
    return entry;
  };
  for (let index = 0; index < 76; index += 1) {
    const actor = users[[0, 1, 2, 3, 5][index % 5]];
    const eventType = index % 13 === 0 ? 'LOGIN_FAILED' : index % 7 === 0 ? 'LOGIN_SUCCESS' : 'AUTHORIZATION';
    const loginFailed = eventType === 'LOGIN_FAILED';
    const loginEvent = loginFailed || eventType === 'LOGIN_SUCCESS';
    const permissionDenied = eventType === 'AUTHORIZATION' && index % 9 === 0;
    // 样例先确定事件，再生成一致的结果，不能将登录成功与拒绝状态独立拼接。
    record({ actor: loginFailed ? null : actor, eventType,
      allowed: !loginFailed && !permissionDenied, reason: loginFailed ? 'LOGIN_FAILED' : permissionDenied ? 'PERMISSION_DENIED' : 'OK',
      httpStatus: loginFailed ? 401 : permissionDenied ? 403 : 200, httpMethod: loginEvent ? 'POST' : 'GET',
      requestPath: loginEvent ? '/api/auth/login' : '/api/users', occurredAt: new Date(Date.now() - (80 - index) * 180000).toISOString() });
  }
  const model = {
    login(account, password) {
      const matches = users.filter(person => person.username === String(account).trim() || person.phone === String(account).trim());
      if (matches.length !== 1 || !matches[0].enabled || password !== DEMO_PASSWORD) fail(401, 'LOGIN_FAILED', '账号或密码错误');
      if (![...effective(matches[0])].some(key => apiCatalog.some(api => api.key === key && api.systemId === 1))) {
        fail(403, 'MANAGEMENT_ACCESS_DENIED', '该账号没有本权限系统的有效管理权限');
      }
      currentId = matches[0].id;
      return publicUser(matches[0]);
    },
    logout() { currentId = null; },
    me() { return publicUser(requireIdentity()); },
    can(key) { return currentId !== null && user(currentId)?.enabled && effective(user(currentId)).has(key); },
    canGlobal(key) { return currentId !== null && user(currentId)?.enabled && user(currentId).departmentId === 1 && effective(user(currentId)).has(key); },
    canManageUser(id, key) {
      const actor = user(currentId), selected = user(id);
      return !!(actor?.enabled && selected && effective(actor).has(key) && descendants(actor.departmentId).has(selected.departmentId)
        && targetWritable(actor, selected));
    },
    canManageDepartments() { const actor = user(currentId); return !!(actor?.enabled && authority(actor) >= 2 && effective(actor).has('departments.list')); },
    department(id) { const item = department(id); return item ? { ...item } : null; },
    tree() {
      const actor = authorize('tree');
      const visible = descendants(actor.departmentId);
      return departments.filter(item => visible.has(item.id)).map(item => ({
        ...item, parentId: visible.has(item.parentId) ? item.parentId : null,
      }));
    },
    list({ departmentId, q = '', enabled = '', page = 0, size = 10 } = {}) {
      const actor = authorize('users');
      const visible = descendants(actor.departmentId);
      const selectedId = Number(departmentId || actor.departmentId);
      if (!visible.has(selectedId)) fail(403, 'MANAGEMENT_SCOPE_DENIED', '该部门不在你的管理范围内');
      const selectedScope = descendants(selectedId);
      const search = q.trim().toLowerCase();
      if (search.length > 100) fail(400, 'INVALID_ARGUMENT', '搜索内容最多100个字符');
      const filtered = users.filter(person => visible.has(person.departmentId) && selectedScope.has(person.departmentId)
        && (!search || [person.name, person.username].some(value => value.toLowerCase().includes(search)))
        && (enabled === '' || person.enabled === (enabled === 'true')));
      return { items: filtered.slice(page * size, (page + 1) * size).map(person => {
        const result = publicUser(person);
        result.roles = result.roles.map(({ id, name, code, enabled }) => ({ id, name, code, enabled }));
        return { ...result, departmentName: department(person.departmentId).name };
      }), total: filtered.length, page, size };
    },
    currentRoles(id) {
      const { selected } = target(id, 'currentRoles');
      return publicUser(selected);
    },
    candidateRoles(id, { q = '', page = 0, size = 6 } = {}) {
      const { actor } = target(id, 'candidates');
      const items = candidates(actor).filter(item => [item.name, item.code].some(value => value.toLowerCase().includes(q.trim().toLowerCase())));
      return { items: structuredClone(items.slice(page * size, (page + 1) * size)), total: items.length, page, size };
    },
    assign(id, ids) {
      const { actor, selected } = target(id, 'assign', true);
      const allowed = candidates(actor).map(item => item.id);
      if (!Array.isArray(ids) || new Set(ids).size !== ids.length || ids.some(value => !Number.isSafeInteger(value) || !role(value))) {
        fail(400, 'INVALID_ARGUMENT', '角色集合存在无效或重复项目');
      }
      if (ids.some(value => !allowed.includes(value))) fail(403, 'MANAGEMENT_SCOPE_DENIED', '包含不可再次分配的角色，请移除后重试');
      preserveMaintenance(selected, () => { selected.roleIds = [...ids]; });
      return publicUser(selected);
    },
    disable(id) {
      const { selected } = target(id, 'disable', true);
      preserveMaintenance(selected, () => { selected.enabled = false; });
      return publicUser(selected);
    },
    move(id, departmentId) {
      const { actor, selected } = target(id, 'move', true);
      if (!department(Number(departmentId))) fail(400, 'INVALID_ARGUMENT', '请选择有效的目标部门');
      if (!descendants(actor.departmentId).has(Number(departmentId))) fail(403, 'MANAGEMENT_SCOPE_DENIED', '目标部门不在你的管理范围内');
      preserveMaintenance(selected, () => { selected.departmentId = Number(departmentId); });
      return publicUser(selected);
    },
    simulateRevocation(keepIds = []) {
      const selected = requireIdentity();
      if (keepIds.some(id => !selected.roleIds.includes(id))) fail(400, 'INVALID_ARGUMENT', '模拟撤权只能移除已有角色');
      preserveMaintenance(selected, () => { selected.roleIds = [...keepIds]; });
    },
    overview() {
      const actor = authorize('users');
      const visible = descendants(actor.departmentId);
      const people = users.filter(person => visible.has(person.departmentId));
      const branches = departments.filter(item => item.parentId === actor.departmentId).map(item => ({
        ...item, total: people.filter(person => descendants(item.id).has(person.departmentId)).length,
      }));
      return { total: people.length, enabled: people.filter(person => person.enabled).length,
        disabled: people.filter(person => !person.enabled).length, departments: visible.size,
        direct: people.filter(person => person.departmentId === actor.departmentId).length, branches,
        scope: { ...department(actor.departmentId) }, latestLogs: model.canGlobal('logs') ? structuredClone(logs.slice(-4).reverse()) : null };
    },
    departments() {
      const actor = authorize('departments.list');
      if (authority(actor) < 2) fail(403, 'MANAGEMENT_SCOPE_DENIED', '当前角色没有部门维护级别');
      const visible = descendants(actor.departmentId);
      const rows = departments.filter(item => visible.has(item.id)).map(item => {
        const direct = users.filter(person => person.departmentId === item.id).length;
        const children = departments.filter(node => node.parentId === item.id).length;
        return { ...item, parentId: visible.has(item.parentId) ? item.parentId : null, direct, children, isOwn: item.id === actor.departmentId,
          total: users.filter(person => descendants(item.id).has(person.departmentId)).length,
          allowedChildTypes: childTypes(item),
          canRename: departmentAccess(actor, item.id, 'departments.rename'), canCreate: departmentAccess(actor, item.id, 'departments.create', true) && childTypes(item).length > 0,
          canDelete: departmentAccess(actor, item.id, 'departments.delete') && !direct && !children,
          deleteReason: item.id === 1 ? '共同根不能删除' : !departmentAccess(actor, item.id, 'departments.delete') ? '本人、上级或平级部门不可删除' : direct || children ? '有人员或下级，不能删除' : '' };
      });
      // 先展示父部门，再紧接其整棵子树，不能沿初始化数组顺序平铺。
      const childrenByParent = new Map();
      rows.forEach(item => {
        if (!childrenByParent.has(item.parentId)) childrenByParent.set(item.parentId, []);
        childrenByParent.get(item.parentId).push(item);
      });
      const ordered = [];
      const visit = (parentId, depth) => {
        for (const item of childrenByParent.get(parentId) || []) {
          ordered.push({ ...item, depth });
          visit(item.id, depth + 1);
        }
      };
      visit(null, 0);
      return ordered;
    },
    renameDepartment(id, name) {
      const selected = departmentTarget(Number(id), 'departments.rename');
      const value = named(name, '部门名称');
      if (departments.some(item => item.id !== selected.id && item.parentId === selected.parentId && item.name === value)) fail(409, 'DATA_CONFLICT', '同一上级部门下已有该名称');
      selected.name = value;
      return { ...selected };
    },
    createDepartment(command) {
      const parent = departmentTarget(Number(command.parentId), 'departments.create', true);
      const name = named(command.name, '部门名称');
      const type = command.type || '部门';
      if (!['部门', '项目', '分公司'].includes(type)) fail(400, 'INVALID_ARGUMENT', '组织类型无效');
      if (!childTypes(parent).includes(type)) fail(400, 'INVALID_ARGUMENT', '当前上级不允许该组织类型：部门只能新增项目，项目不能新增下级，分公司只能由总公司创建');
      if (departments.some(item => item.parentId === parent.id && item.name === name)) fail(409, 'DATA_CONFLICT', '同一上级部门下已有该名称');
      const selected = { id: nextDepartmentId++, parentId: parent.id, name, type };
      departments.push(selected);
      return { ...selected };
    },
    deleteDepartment(id) {
      const selected = departmentTarget(Number(id), 'departments.delete');
      if (users.some(person => person.departmentId === selected.id) || departments.some(item => item.parentId === selected.id)) fail(409, 'DEPARTMENT_NOT_EMPTY', '部门仍有人员或下级，请先处理关联后再删除');
      departments.splice(departments.indexOf(selected), 1);
      return { id: selected.id, name: selected.name };
    },
    roles(query = {}) {
      globalAuthorize('roles.list');
      const q = String(query.q || '').trim().toLowerCase();
      return paged(roles.filter(item => [item.name, item.code].some(value => value.toLowerCase().includes(q))
        && (query.enabled === '' || query.enabled === undefined || item.enabled === (query.enabled === 'true'))), query);
    },
    createRole(command) {
      globalAuthorize('roles.create');
      const name = named(command.name, '角色名称');
      const code = named(command.code, '角色编码');
      if (roles.some(item => item.code === code)) fail(409, 'DATA_CONFLICT', '角色编码已存在');
      const result = { id: Math.max(...roles.map(item => item.id)) + 1, name, code, enabled: true, permissions: [], authorityLevel: 0, requiredGrantLevel: 5, description: '自定义角色，默认仅系统管理员可分配' };
      roles.push(result);
      return structuredClone(result);
    },
    patchRole(id, command) {
      globalAuthorize('roles.patch');
      const selected = requireRole(id);
      const update = {};
      if (command.name !== undefined) update.name = named(command.name, '角色名称');
      if (command.enabled !== undefined) {
        if (typeof command.enabled !== 'boolean') fail(400, 'INVALID_ARGUMENT', '角色状态无效');
        update.enabled = command.enabled;
      }
      if (!Object.keys(update).length || Object.keys(command).some(key => !['name', 'enabled'].includes(key))) fail(400, 'INVALID_ARGUMENT', '只能修改角色名称和启用状态');
      preserveMaintenance(selected, () => Object.assign(selected, update));
      return structuredClone(selected);
    },
    rolePermissions(id) {
      globalAuthorize('roles.permissions.read');
      const selected = requireRole(id);
      return rolePermissionResult(selected);
    },
    saveRolePermissions(id, ids) {
      globalAuthorize('roles.permissions.write');
      const selected = requireRole(id);
      if (!Array.isArray(ids) || new Set(ids).size !== ids.length || ids.some(value => !permission(value) || !systems.some(system => system.id === permission(value).systemId) || !permission(value).enabled)) fail(400, 'INVALID_ARGUMENT', '请选择已登记系统的启用权限，不能包含重复或未知项目');
      preserveMaintenance(selected, () => { selected.permissions = ids.map(value => permission(value).key); });
      return rolePermissionResult(selected);
    },
    resources({ systemId = 1 } = {}) {
      globalAuthorize('permissions.tree');
      if (!systems.some(item => item.id === Number(systemId))) fail(403, 'MANAGEMENT_SCOPE_DENIED', '该业务系统未登记');
      return { systems: structuredClone(systems), permissions: structuredClone(permissionCatalog.filter(item => item.systemId === Number(systemId))),
        matrix: structuredClone(applicationSource.features.filter(item => item.systemId === Number(systemId))),
        roleNames: [...applicationSource.roleNames], source: { file: applicationSource.sourceFile, sha256: applicationSource.sourceSha256, missingRights: structuredClone(applicationSource.missingRights), normalizations: structuredClone(applicationSource.normalizations) } };
    },
    permissionCatalog() {
      globalAuthorize('permissions.tree');
      return { systems: structuredClone(systems), permissions: structuredClone(permissionCatalog) };
    },
    apis(query = {}) {
      globalAuthorize('apis.list');
      const q = String(query.q || '').trim().toLowerCase();
      return paged(apiCatalog.filter(item => item.systemId === Number(query.systemId || 1) && [item.method, item.path].some(value => value.toLowerCase().includes(q))
        && (query.enabled === '' || query.enabled === undefined || item.enabled === (query.enabled === 'true'))), query);
    },
    patchPermission(id, command) {
      globalAuthorize('permissions.patch');
      const selected = registeredPermission(id);
      if (!Object.keys(command).length || Object.keys(command).some(key => !['parentId', 'type', 'enabled'].includes(key))) fail(400, 'INVALID_ARGUMENT', '仅能维护权限父级、类型与状态');
      const next = { ...selected, ...command };
      if (!['MENU', 'BUTTON'].includes(next.type) || typeof next.enabled !== 'boolean') fail(400, 'INVALID_ARGUMENT', '权限类型或状态无效');
      const parent = next.parentId === null ? null : permission(next.parentId);
      if (next.parentId !== null && (!parent || parent.systemId !== selected.systemId || parent.type === 'BUTTON')) fail(400, 'INVALID_ARGUMENT', '父级必须为本系统菜单节点');
      for (let current = parent, seen = new Set(); current; current = current.parentId === null ? null : permission(current.parentId)) {
        if (current.id === selected.id || seen.has(current.id)) fail(400, 'INVALID_ARGUMENT', '权限树不能形成环');
        seen.add(current.id);
      }
      if (next.type === 'BUTTON' && permissionCatalog.some(item => item.parentId === selected.id)) fail(400, 'INVALID_ARGUMENT', '按钮节点不能包含下级');
      preserveMaintenance(selected, () => Object.assign(selected, command));
      return structuredClone(selected);
    },
    patchApi(id, enabled) {
      globalAuthorize('apis.patch');
      const selected = apiCatalog.find(item => item.id === Number(id));
      if (!selected) fail(404, 'RESOURCE_NOT_FOUND', '该API资源不存在');
      if (!systems.some(item => item.id === selected.systemId)) fail(403, 'MANAGEMENT_SCOPE_DENIED', '该API所属系统未登记');
      if (typeof enabled !== 'boolean') fail(400, 'INVALID_ARGUMENT', 'API状态无效');
      preserveMaintenance(selected, () => { selected.enabled = enabled; });
      return { ...selected };
    },
    permissionApis(id) {
      globalAuthorize('permissions.apis.read');
      const selected = registeredPermission(id);
      return permissionApiResult(selected);
    },
    savePermissionApis(id, ids) {
      globalAuthorize('permissions.apis.write');
      const selected = registeredPermission(id);
      if (!Array.isArray(ids) || new Set(ids).size !== ids.length || ids.some(value => !apiCatalog.some(item => item.id === value && item.systemId === selected.systemId))) fail(400, 'INVALID_ARGUMENT', 'API映射必须为本系统存在且不重复的项目');
      preserveMaintenance(selected, () => { selected.apiIds = [...ids]; });
      return permissionApiResult(selected);
    },
    logs(query = {}) {
      globalAuthorize('logs');
      const { cursor = null, limit = 12, ...filters } = query;
      if (![6, 12, 24, 50].includes(limit)) fail(400, 'INVALID_ARGUMENT', '请选择有效批量');
      if (filters.userId && (!Number.isSafeInteger(Number(filters.userId)) || Number(filters.userId) <= 0)) fail(400, 'INVALID_ARGUMENT', '用户ID须为正整数');
      for (const key of ['from', 'to']) if (filters[key] && (!/Z$/.test(filters[key]) || !Number.isFinite(Date.parse(filters[key])))) fail(400, 'INVALID_ARGUMENT', '时间必须为有效UTC值');
      if (filters.from && filters.to && filters.from >= filters.to) fail(400, 'INVALID_ARGUMENT', '开始时间必须早于结束时间');
      const signature = JSON.stringify({ filters, limit });
      const position = cursor ? cursorIndex.get(cursor) : { snapshot: logs.length, before: Infinity, signature };
      if (!position || position.signature !== signature) fail(400, 'INVALID_CURSOR', '筛选条件或批量已改变，请重新查询');
      const matched = logs.filter(item => item.id <= position.snapshot
        && (!filters.userId || item.userId === Number(filters.userId))
        && (!filters.username || item.username === filters.username)
        && (!filters.appCode || item.appCode === filters.appCode)
        && (!filters.event || item.eventType === filters.event)
        && (filters.allowed === '' || filters.allowed === undefined || item.allowed === (filters.allowed === 'true'))
        && (!filters.from || item.occurredAt >= filters.from) && (!filters.to || item.occurredAt < filters.to));
      const remaining = matched.filter(item => item.id < position.before).sort((a, b) => b.id - a.id);
      const items = remaining.slice(0, limit);
      const hasMore = remaining.length > limit;
      let nextCursor = null;
      if (hasMore) {
        nextCursor = globalThis.crypto.randomUUID();
        cursorIndex.set(nextCursor, { snapshot: position.snapshot, before: items.at(-1).id, signature });
      }
      return { items: structuredClone(items), total: matched.length, snapshotId: position.snapshot, hasMore, nextCursor };
    },
  };
  /** 每个受控模拟方法记录一个鉴权事件；业务失败与鉴权失败分别记录。 */
  const audited = {
    tree: 'tree', list: 'users', currentRoles: 'currentRoles', candidateRoles: 'candidates', assign: 'assign', disable: 'disable', move: 'move',
    departments: 'departments.list', renameDepartment: 'departments.rename', createDepartment: 'departments.create', deleteDepartment: 'departments.delete',
    roles: 'roles.list', createRole: 'roles.create', patchRole: 'roles.patch', rolePermissions: 'roles.permissions.read', saveRolePermissions: 'roles.permissions.write',
    resources: 'permissions.tree', permissionCatalog: 'permissions.tree', apis: 'apis.list', patchPermission: 'permissions.patch', patchApi: 'apis.patch',
    permissionApis: 'permissions.apis.read', savePermissionApis: 'permissions.apis.write', logs: 'logs',
  };
  let auditDepth = 0;
  for (const [method, key] of Object.entries(audited)) {
    const operation = model[method];
    model[method] = function (...args) {
      if (auditDepth) return operation(...args);
      const actor = user(currentId);
      const api = apiCatalog.find(item => item.key === key);
      let error = null;
      auditDepth += 1;
      try { return operation(...args); }
      catch (caught) { error = caught; throw caught; }
      finally {
        auditDepth -= 1;
        const httpStatus = error?.status || (method === 'createRole' ? 201 : 200);
        record({ actor, eventType: httpStatus === 401 ? 'UNAUTHENTICATED' : 'AUTHORIZATION',
          allowed: ![401, 403].includes(httpStatus), reason: error?.code || 'OK', httpStatus,
          httpMethod: method === 'departments' ? 'GET' : api.method,
          requestPath: method === 'departments' ? '/api/departments' : api.path,
          permissionCode: permissionCatalog.find(item => item.key === key)?.code || null, requestId: error?.requestId });
      }
    };
  }
  const loginOperation = model.login;
  model.login = (account, password) => {
    try {
      const result = loginOperation(account, password);
      record({ actor: user(currentId), eventType: 'LOGIN_SUCCESS', httpMethod: 'POST', requestPath: '/api/auth/login' });
      return result;
    } catch (error) {
      record({ eventType: 'LOGIN_FAILED', allowed: false, reason: error.code, httpStatus: error.status, httpMethod: 'POST', requestPath: '/api/auth/login', requestId: error.requestId });
      throw error;
    }
  };
  return model;
}
const api = { createDemoModel, DemoError, DEMOS, DEMO_PASSWORD, PEOPLE, MAINTENANCE };
if (typeof module !== 'undefined' && module.exports) module.exports = api;
else globalThis.MunicipalPrototype = api;
})();
