/** 普通脚本兼容file://双击打开；封装内存模型，不污染其他原型脚本。 */
(function () {
'use strict';
/** 仅用于原型的内存业务模型；数据全部虚构，不调用后端或持久化浏览器存储。 */
const PEOPLE = ['tree', 'users', 'currentRoles', 'candidates', 'assign', 'disable', 'move'];
/** 与现行17项维护恢复操作对应；日志、停用和调动不属于完整K。 */
const MAINTENANCE = [
  'tree', 'users', 'currentRoles', 'candidates', 'assign',
  'roles.list', 'roles.create', 'roles.patch', 'roles.permissions.read', 'roles.permissions.write',
  'systems', 'permissions.tree', 'apis.list', 'permissions.patch', 'apis.patch',
  'permissions.apis.read', 'permissions.apis.write',
];
const DEMO_PASSWORD = 'Demo@2026';
const DEMOS = [
  { id: 1, label: '系统管理员', account: 'admin', note: '全公司人员管理' },
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
    { id: 1, name: '系统管理员', code: 'SYSTEM_ADMIN', enabled: true, permissions: [...MAINTENANCE, 'disable', 'move', 'logs'], description: '完整系统维护与日志查询' },
    { id: 2, name: '公司领导', code: 'COMPANY_LEADER', enabled: true, permissions: [...PEOPLE], description: '本人部门及下级的人员管理' },
    { id: 3, name: '部门经理', code: 'DEPARTMENT_MANAGER', enabled: true, permissions: [...PEOPLE], description: '本人部门及下级的人员管理' },
    { id: 4, name: '项目经理', code: 'PROJECT_MANAGER', enabled: true, permissions: [...PEOPLE], description: '本人部门及下级的人员管理' },
    { id: 5, name: '普通员工', code: 'EMPLOYEE', enabled: true, permissions: [], description: '基础员工角色，无本管理端操作权限' },
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
  roles.push({ id: 91, name: '外系统业务审核', code: 'EXTERNAL_REVIEW', enabled: true, permissions: ['external.review'], description: '含外系统权限，不属于本管理端可授予集合' });
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
  let currentId = null;
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
  const effective = person => new Set(
    person.roleIds.flatMap(id => role(id)?.enabled ? role(id).permissions : []),
  );
  const complete = person => person.enabled && person.departmentId === 1 && MAINTENANCE.every(key => effective(person).has(key));
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
    if (write && actor.departmentId !== 1 && (actor.id === selected.id || complete(selected))) {
      fail(403, 'MANAGEMENT_SCOPE_DENIED', '不能调整本人的授权或部门，也不能修改有效全局管理员');
    }
    return { actor, selected };
  };
  const candidates = actor => roles.filter(item => item.enabled && item.permissions.every(key => effective(actor).has(key)));
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
    enabled: person.enabled, roles: person.roleIds.map(id => structuredClone(role(id))).filter(Boolean),
  });
  return {
    login(account, password) {
      const matches = users.filter(person => person.username === String(account).trim() || person.phone === String(account).trim());
      if (matches.length !== 1 || !matches[0].enabled || password !== DEMO_PASSWORD) fail(401, 'LOGIN_FAILED', '账号或密码错误');
      if (![...effective(matches[0])].some(key => [...MAINTENANCE, 'disable', 'move', 'logs'].includes(key))) {
        fail(403, 'MANAGEMENT_ACCESS_DENIED', '该账号没有本权限系统的有效管理权限');
      }
      currentId = matches[0].id;
      return publicUser(matches[0]);
    },
    logout() { currentId = null; },
    me() { return publicUser(requireIdentity()); },
    can(key) { return currentId !== null && user(currentId)?.enabled && effective(user(currentId)).has(key); },
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
        delete result.roles;
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
    simulateRevocation() {
      const selected = requireIdentity();
      preserveMaintenance(selected, () => { selected.roleIds = []; });
    },
  };
}
const api = { createDemoModel, DemoError, DEMOS, DEMO_PASSWORD, PEOPLE, MAINTENANCE };
if (typeof module !== 'undefined' && module.exports) module.exports = api;
else globalThis.MunicipalPrototype = api;
})();
