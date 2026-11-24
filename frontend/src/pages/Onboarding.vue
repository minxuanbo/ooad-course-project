<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { api, canRoute, session, refreshSession } from '../api.js'
import { errorText } from '../lib/presentation.js'
import PageActions from '../components/PageActions.vue'
import AppIcon from '../components/AppIcon.vue'
const route = useRoute(), router = useRouter(), prefix = '/systems/' + route.params.id + '/onboarding'
let existingPermissions = new WeakSet()
const steps = ['登记应用', '导入接口', '配置权限', '配置角色', '检查与确认', '完成接入']
const step = ref(0), busy = ref(false), snapshot = ref(null), draft = ref(null), error = ref('')
const document = ref(''), preview = ref([]), importSelection = ref([]), overwrite = ref(false)
const selectedPermission = ref(0), selectedRole = ref(''), report = ref(null), checkedSignature = ref('')
const testAccount = ref(''), testPassword = ref(''), businessConfirmed = ref(false), revocationConfirmed = ref(false)
const finished = ref(false), denied = ref(false), initialDraft = ref('')
const copy = value => JSON.parse(JSON.stringify(value))
const may = suffix => session.me?.user.managementLevel === 5 && canRoute('POST', '/systems/{id}/onboarding' + suffix, true)
const editable = computed(() => may('/complete') && !denied.value && !finished.value)
const permission = computed(() => draft.value?.permissions[selectedPermission.value])
const grant = computed(() => draft.value?.roleGrants.find(item => item.roleId === Number(selectedRole.value)))
const apiKey = item => item.method + ' ' + item.path
const baseline = computed(() => snapshot.value?.plan)
const dirty = computed(() => draft.value && baseline.value && JSON.stringify(draft.value) !== initialDraft.value)
const checked = computed(() => report.value?.configurationValid && checkedSignature.value === JSON.stringify(draft.value))
const changes = computed(() => {
  if (!draft.value || !baseline.value) return []
  const result = []
  const old = new Map(baseline.value.roleGrants.map(item => [item.roleId, item.permissionCodes]))
  for (const item of draft.value.roleGrants) {
    const before = old.get(item.roleId) || [], added = item.permissionCodes.filter(code => !before.includes(code)), removed = before.filter(code => !item.permissionCodes.includes(code))
    if (added.length || removed.length) result.push({ ...item, added, removed, name: snapshot.value.roles.find(role => role.id === item.roleId)?.name || item.roleId })
  }
  return result
})

/** 草稿和测试口令只在当前页面内存中，重新读取须先确认丢弃未发布内容。 */
async function load() {
  if (dirty.value && !finished.value && !window.confirm('重新读取会丢弃未发布草稿，是否继续？')) return
  busy.value = true; error.value = ''
  try {
    snapshot.value = await api(prefix)
    draft.value = copy(snapshot.value.plan)
    existingPermissions = new WeakSet(draft.value.permissions)
    if (!draft.value.loginPermissionCode) {
      const existing = draft.value.permissions.find(item => item.code === 'LOGIN')
      if (!existing) draft.value.permissions.push({ code: 'LOGIN', name: '登录应用', type: 'BUTTON', parentCode: null, enabled: true, apiKeys: [] })
      draft.value.loginPermissionCode = 'LOGIN'
    }
    initialDraft.value = JSON.stringify(draft.value)
    selectedRole.value = String(snapshot.value.roles[0]?.id || '')
    selectedPermission.value = 0; step.value = 0; report.value = null; finished.value = false; denied.value = false
    businessConfirmed.value = false; revocationConfirmed.value = false
  } catch (e) { error.value = errorText(e) } finally { busy.value = false }
}

/** 导入先预览，保留现有操作例外与停用状态，同名覆盖必须单独选择。 */
async function parse() {
  if (!may('/import') || busy.value) return
  busy.value = true; error.value = ''
  try { preview.value = (await api(prefix + '/import', 'POST', { document: document.value })).operations; importSelection.value = preview.value.map(item => item.operationId) }
  catch (e) { error.value = errorText(e) } finally { busy.value = false }
}

/** 上传只是读文本，不抓取OpenAPI中的服务器地址或远程引用。 */
async function fileSelected(event) {
  const file = event.target.files[0]
  if (!file) return
  if (file.size > 1048576) { error.value = '规范文件最大1MiB。'; return }
  document.value = await file.text()
}

/** 应用到草稿仍不写服务器，也不隐式给角色授予接口权限。 */
function applyImport() {
  if (!editable.value) return
  for (const operation of preview.value.filter(item => importSelection.value.includes(item.operationId))) {
    const index = draft.value.operations.findIndex(item => item.operationId === operation.operationId)
    if (index >= 0 && !overwrite.value) continue
    if (index < 0) draft.value.operations.push(copy(operation))
    else if (overwrite.value) draft.value.operations[index] = copy(operation)
    const key = apiKey(operation)
    if (!draft.value.apis.some(item => apiKey(item) === key)) draft.value.apis.push({ method: operation.method, path: operation.path, enabled: true })
  }
  preview.value = []; importSelection.value = []
}

/** 只有本次新草稿项可移除，已有正式目录用停用保留编号和引用。 */
function addPermission() {
  const used = new Set(draft.value.permissions.map(item => item.code)); let index = 1
  while (used.has('permission-' + index)) index++
  draft.value.permissions.push({ code: 'permission-' + index, name: '新增权限', type: 'BUTTON', parentCode: null, enabled: true, apiKeys: [] })
  selectedPermission.value = draft.value.permissions.length - 1
}
function removePermission() {
  const selected = permission.value
  if (!selected || existingPermissions.has(selected)) return
  if (draft.value.permissions.some(item => item.parentCode === selected.code)) { error.value = '请先调整该权限的下级归属。'; return }
  draft.value.permissions.splice(selectedPermission.value, 1)
  for (const item of draft.value.roleGrants) item.permissionCodes = item.permissionCodes.filter(code => code !== selected.code)
  selectedPermission.value = 0
}

/** 每个复选框只改变当前权限，不按菜单层级自动选择其他按钮。 */
function toggle(list, value, selected) {
  const index = list.indexOf(value)
  if (selected && index < 0) list.push(value)
  else if (!selected && index >= 0) list.splice(index, 1)
}

/** 检查结果分别标明配置有效、协议成功/失败及待启用。 */
async function check() {
  if (!may('/check') || busy.value) return
  busy.value = true; error.value = ''
  try {
    draft.value.entryUrl = draft.value.entryUrl?.trim() || null
    const body = { plan: draft.value }
    if (testAccount.value.trim() || testPassword.value) Object.assign(body, { testAccount: testAccount.value.trim(), testPassword: testPassword.value })
    report.value = await api(prefix + '/check', 'POST', body)
    checkedSignature.value = JSON.stringify(draft.value)
  } catch (e) { error.value = errorText(e); report.value = null; if (e.status === 403) denied.value = true }
  finally { busy.value = false }
}

/** 完成是唯一保存入口，服务端重新验证，不相信页面的检查标记。 */
async function complete() {
  if (!editable.value || !checked.value || !businessConfirmed.value || !revocationConfirmed.value || busy.value) return
  busy.value = true; error.value = ''
  try {
    snapshot.value = await api(prefix + '/complete', 'POST', { plan: draft.value, businessConfirmed: true, revocationConfirmed: true })
    draft.value = copy(snapshot.value.plan); finished.value = true
  } catch (e) { error.value = errorText(e); if (e.status === 403) denied.value = true; return }
  finally { busy.value = false }
  try {
    if (testAccount.value.trim() && testPassword.value) await check()
  } finally {
    testAccount.value = ''; testPassword.value = ''
  }
  try { await refreshSession() }
  catch (e) { error.value = errorText(e) }
}

function next() { error.value = ''; if (step.value < 5) step.value++ }
function leaving() { return !dirty.value || finished.value || window.confirm('向导草稿尚未发布，确定离开？') }
function beforeUnload(event) { if (dirty.value && !finished.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(leaving)
onMounted(() => { load(); window.addEventListener('beforeunload', beforeUnload) })
onUnmounted(() => { testAccount.value = ''; testPassword.value = ''; window.removeEventListener('beforeunload', beforeUnload) })
</script>
<template>
  <PageActions :busy="busy" @refresh="load"><button class="secondary" :disabled="busy" @click="router.push('/applications')">返回应用管理</button></PageActions>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <section v-if="draft" class="card panel onboarding">
    <div class="wizard-intro"><div><span class="badge blue">{{ snapshot.system.code }}</span><h2>{{ snapshot.system.name }}</h2></div><p class="muted">{{ finished ? '本次配置已发布。' : '草稿仅保留在当前向导；完成接入前不修改正式授权。' }}</p></div>
    <ol class="wizard-steps" aria-label="接入步骤"><li v-for="(label, index) in steps" :key="label" :class="{ current: step === index, done: step > index }"><span>{{ index + 1 }}</span>{{ label }}</li></ol>
    <div class="wizard-stage">
      <template v-if="step === 0">
        <h3>登记应用</h3><p class="muted">复用当前应用编号与原有授权，名称和稳定编码可返回应用管理查看。</p>
        <div class="wizard-fields"><label>系统编码<input :value="snapshot.system.code" disabled /></label><label>系统名称<input :value="snapshot.system.name" disabled /></label></div>
        <label>访问入口（可选）<input v-model="draft.entryUrl" type="url" maxlength="512" placeholder="https://example.com" :disabled="!editable || busy" /></label>
        <p class="muted">请填该系统前端的浏览器访问网址；完成接入后允许其Origin调用身份接口。服务器不会抓取这个地址。业务系统仍需实现RBAC鉴权代码。</p>
      </template>
      <template v-else-if="step === 1">
        <h3>导入接口</h3><p class="muted">支持OpenAPI3 JSON/YAML。选择需纳管的接口；新导入操作默认要求权限，原有配置不会自动覆盖。</p>
        <label>上传规范<input type="file" accept=".json,.yaml,.yml" :disabled="!editable || busy" @change="fileSelected" /></label>
        <label>OpenAPI内容<textarea v-model="document" rows="7" maxlength="1048576" placeholder="粘贴JSON或YAML规范" :disabled="!editable || busy" /></label>
        <button class="secondary" :disabled="!editable || !may('/import') || !document.trim() || busy" @click="parse">预览导入</button>
        <div v-if="preview.length" class="wizard-preview"><h4>导入预览</h4><label v-for="item in preview" :key="item.operationId" class="check"><input v-model="importSelection" type="checkbox" :value="item.operationId" />{{ item.method }} {{ item.path }} · {{ item.operationId }}</label><label class="check"><input v-model="overwrite" type="checkbox" />用导入内容更新同名操作（含原条件门禁配置）</label><button class="primary" :disabled="!editable || !importSelection.length" @click="applyImport">应用到草稿</button></div>
        <div class="table-scroll"><table><thead><tr><th>operationId</th><th>接口</th><th>全局门禁</th><th>条件角色</th><th>操作</th></tr></thead><tbody><tr v-for="(item, index) in draft.operations" :key="item.operationId"><td class="mono">{{ item.operationId }}</td><td class="mono">{{ item.method }} {{ item.path }}</td><td><label class="check"><input v-model="item.requiresPermission" type="checkbox" :disabled="!editable || busy" />需要权限</label></td><td><input :value="item.permissionRoles.join(',')" aria-label="条件角色编码" :disabled="!editable || busy" placeholder="留空表示所有角色" @change="item.permissionRoles = $event.target.value.split(',').map(value => value.trim()).filter(Boolean)" /></td><td><button class="text-button danger" :disabled="!editable || busy" @click="draft.operations.splice(index, 1)">移除操作标识</button></td></tr><tr v-if="!draft.operations.length"><td colspan="5" class="empty">尚未登记操作标识</td></tr></tbody></table></div>
        <details><summary>接口可用性（{{ draft.apis.length }}项）</summary><label v-for="item in draft.apis" :key="apiKey(item)" class="check"><input v-model="item.enabled" type="checkbox" :disabled="!editable || busy" />{{ apiKey(item) }}</label></details>
        <p class="muted">关闭全局门禁或设置条件角色时，业务系统仍须执行自己的对象权限判断；完成页会要求人工确认。</p>
      </template>
      <template v-else-if="step === 2">
        <h3>配置菜单与按钮</h3><p class="muted">按实际界面建立权限树，每项映射独立选择。选择菜单不会自动授予其下全部按钮。</p>
        <button class="secondary" :disabled="!editable || busy" @click="addPermission">新增权限</button>
        <div class="permission-editor"><div class="permission-list"><button v-for="(item, index) in draft.permissions" :key="index" class="permission-choice" :class="{ selected: selectedPermission === index }" @click="selectedPermission = index">{{ item.name }}<small>{{ item.type }} · {{ item.code }}</small></button></div>
          <div v-if="permission" class="permission-form"><label>权限编码<input v-model="permission.code" maxlength="100" :disabled="!editable || busy || existingPermissions.has(permission)" /></label><label>权限名称<input v-model="permission.name" maxlength="191" :disabled="!editable || busy" /></label><div class="wizard-fields"><label>权限类型<select v-model="permission.type" :disabled="!editable || busy"><option>MENU</option><option>BUTTON</option><option>API</option></select></label><label>上级菜单<select v-model="permission.parentCode" :disabled="!editable || busy"><option :value="null">无上级</option><option v-for="item in draft.permissions.filter(item => item.type === 'MENU' && item.code !== permission.code)" :key="item.code" :value="item.code">{{ item.name }} · {{ item.code }}</option></select></label></div><label class="check"><input v-model="permission.enabled" type="checkbox" :disabled="!editable || busy" />启用权限</label><fieldset><legend>关联API</legend><label v-for="item in draft.apis" :key="apiKey(item)" class="check"><input :checked="permission.apiKeys.includes(apiKey(item))" type="checkbox" :disabled="!editable || busy" @change="toggle(permission.apiKeys, apiKey(item), $event.target.checked)" />{{ apiKey(item) }}</label><p v-if="!draft.apis.length" class="muted">可先返回导入接口。</p></fieldset><button class="text-button danger" :disabled="!editable || busy || existingPermissions.has(permission)" @click="removePermission">移除草稿权限</button></div></div>
        <label>独立登录资格<select v-model="draft.loginPermissionCode" :disabled="!editable || busy"><option v-for="item in draft.permissions.filter(item => item.enabled && item.type !== 'API' && !item.apiKeys.length)" :key="item.code" :value="item.code">{{ item.name }} · {{ item.code }}</option></select></label><p class="muted">登录资格不映射业务API；持有它可以登录该应用，功能仍由其他权限控制。已有权限保留编号，可显式停用。</p>
      </template>
      <template v-else-if="step === 3">
        <h3>配置角色权限</h3><p class="muted">只调整当前应用的权限子集，保留这些角色在其他应用中的权限；不改变任何人员的角色或管理级别。</p>
        <label>当前配置角色<select v-model="selectedRole"><option v-for="role in snapshot.roles" :key="role.id" :value="String(role.id)">{{ role.name }} · {{ role.code }}{{ role.enabled ? '' : '（已停用）' }}</option></select></label>
        <fieldset v-if="grant"><legend>该角色在当前应用的权限</legend><div class="grant-grid"><label v-for="item in draft.permissions" :key="item.code" class="check"><input :checked="grant.permissionCodes.includes(item.code)" type="checkbox" :disabled="!editable || busy || !canRoute('PUT', '/roles/{id}/permissions', true)" @change="toggle(grant.permissionCodes, item.code, $event.target.checked)" />{{ item.name }}（{{ item.code }}）</label></div></fieldset>
        <p v-if="!canRoute('PUT', '/roles/{id}/permissions', true)" class="muted">当前账号没有配置角色权限的API资格，提交时不能改变角色子集。</p>
      </template>
      <template v-else-if="step === 4">
        <h3>自动检查与人工确认</h3><p class="muted">配置检查针对草稿；协议测试针对当前已发布配置。待接入应用预期拒绝登录，启用后自动复检。</p>
        <div class="wizard-fields"><label>专用测试账号（可选）<input v-model="testAccount" maxlength="100" autocomplete="off" :disabled="busy || !may('/check')" /></label><label>测试密码<input v-model="testPassword" type="password" maxlength="100" autocomplete="new-password" :disabled="busy || !may('/check')" /></label></div>
        <p class="muted">请用专用账号。测试会建立并退出一次应用会话，并产生真实鉴权日志；不撤回真实人员权限。测试密码不保存。</p>
        <button class="primary" :disabled="busy || !may('/check') || denied" @click="check">开始检查</button>
        <div v-if="report" class="check-results" role="status"><h4>{{ report.configurationValid ? '配置检查通过' : '配置需要修正' }}</h4><ul v-if="report.errors.length"><li v-for="item in report.errors" :key="item">{{ item }}</li></ul><p v-if="!checked && report.configurationValid" class="muted">草稿已变化，需要重新检查。</p><p v-for="item in report.protocolChecks" :key="item.code"><span class="badge" :class="item.status === 'PASS' ? 'green' : item.status === 'PENDING' ? 'blue' : 'red'">{{ { PASS: '通过', FAIL: '失败', PENDING: '待完成' }[item.status] }}</span> {{ item.message }}<small v-if="item.httpStatus"> · HTTP {{ item.httpStatus }}</small></p></div>
        <fieldset><legend>接入方实测确认</legend><label class="check"><input v-model="businessConfirmed" type="checkbox" :disabled="!editable || busy" />接入开发者已验证业务后端执行RBAC鉴权，并保留业务对象权限判断</label><label class="check"><input v-model="revocationConfirmed" type="checkbox" :disabled="!editable || busy" />已在专用测试环境验证授撤权立即生效、越权操作被拒绝</label></fieldset>
      </template>
      <template v-else>
        <h3>{{ finished ? '已完成接入' : '确认并完成接入' }}</h3><p>应用：{{ snapshot.system.name }} · {{ snapshot.system.code }}</p><p>{{ draft.apis.length }}项API，{{ draft.permissions.length }}项权限，{{ draft.operations.length }}项操作标识；独立登录资格为 {{ draft.loginPermissionCode }}。</p>
        <p v-if="finished && !testAccount && !testPassword" class="muted">本次测试凭据已清空。</p>
        <p class="muted">完成会以当前配置发布并标记已接入；已停用的应用需要先在应用管理恢复。业务系统的代码适配和部署由接入方完成。</p>
        <h4>角色权限变化</h4><p v-if="!changes.length" class="muted">本应用角色权限集合未变化。</p><div v-for="item in changes" :key="item.roleId" class="change-row"><strong>{{ item.name }}</strong><p v-if="item.added.length">新增：{{ item.added.join('、') }}</p><p v-if="item.removed.length">撤回：{{ item.removed.join('、') }}</p></div>
        <p v-if="draft.operations.some(item => !item.requiresPermission || item.permissionRoles.length)" class="muted">配置含由业务对象规则继续判断或仅对指定角色执行的全局门禁，已按上一步人工确认。</p>
        <p v-if="!checked && !finished" class="error">请先返回完成配置检查。</p>
        <button v-if="!finished" class="primary" :disabled="!editable || busy || !checked || !businessConfirmed || !revocationConfirmed" @click="complete">完成接入</button>
        <div v-else class="check-results" role="status"><p>配置已发布。原有人员角色及其他应用权限保留。</p><p v-for="item in report?.protocolChecks || []" :key="item.code"><span class="badge" :class="item.status === 'PASS' ? 'green' : item.status === 'PENDING' ? 'blue' : 'red'">{{ { PASS: '通过', FAIL: '失败', PENDING: '待完成' }[item.status] }}</span> {{ item.message }}</p></div>
        <details><summary>接入调用说明</summary><p>登录向RBAC的 /auth/login 提交username、password，以及systems: ["{{ snapshot.system.code }}"]。携带返回JWT调用 /auth/me 读取当前身份，调用 /auth/api-permissions/{operationId} 查询操作权限。</p><p>登录资格为 {{ draft.loginPermissionCode }}。业务后端每次操作实时查询，不能只依靠前端隐藏按钮或登录时缓存的权限；对象归属仍由业务系统判断。</p></details>
      </template>
    </div>
    <footer class="wizard-footer"><span class="muted">第{{ step + 1 }}步，共6步</span><div><button class="secondary" :disabled="busy || step === 0 || finished" @click="step--">上一步</button><button v-if="step < 5" class="primary" :disabled="busy || (step === 4 && (!checked || !businessConfirmed || !revocationConfirmed))" @click="next">下一步</button></div></footer>
  </section>
  <p v-else-if="busy" class="muted">正在读取当前接入配置…</p>
</template>
<style scoped>
.onboarding { padding: 26px 30px; }
.wizard-intro, .wizard-footer { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.wizard-intro h2 { margin: 12px 0; font-size: 21px; }
.wizard-steps { display: flex; list-style: none; padding: 18px 0 24px; margin: 0 0 24px; border-bottom: 1px solid #e6ecf5; gap: 12px; }
.wizard-steps li { flex: 1; display: flex; gap: 8px; align-items: center; color: #8b99ac; font-size: 13px; }
.wizard-steps li span { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: #edf1f8; flex-shrink: 0; }
.wizard-steps .current { color: #315ff2; font-weight: 600; }.wizard-steps .current span { color: white; background: #315ff2; }.wizard-steps .done span { color: #178b73; background: #e5f6ef; }
.wizard-stage { min-height: 330px; }.wizard-stage h3 { margin: 0 0 14px; font-size: 18px; }.wizard-stage h4 { margin: 20px 0 12px; }
.wizard-stage label:not(.check) { display: flex; flex-direction: column; gap: 8px; margin: 15px 0; font-size: 13px; color: #5b7090; }
.wizard-stage textarea { width: 100%; resize: vertical; border: 1px solid #d9e2ef; border-radius: 6px; padding: 12px; font-family: Consolas, monospace; font-size: 13px; }
.wizard-stage select, .wizard-stage input:not([type=checkbox]) { width: 100%; }
.wizard-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }.wizard-stage .check { display: flex; align-items: center; gap: 8px; margin: 10px 0; font-size: 13px; line-height: 1.6; }.wizard-stage input[type=checkbox] { width: 15px; height: 15px; flex-shrink: 0; }
.wizard-stage fieldset { padding: 16px 18px; border: 1px solid #e1e8f2; border-radius: 7px; margin: 18px 0; }.wizard-stage legend { font-size: 13px; color: #5b7090; padding: 0 6px; }.wizard-stage details { margin: 20px 0; font-size: 13px; }.wizard-stage summary { cursor: pointer; color: #4561a1; }.wizard-stage table { margin-top: 18px; }
.wizard-preview, .check-results { margin: 18px 0; padding: 18px; background: #f4f7fc; border-radius: 8px; }.check-results small { color: #8591a4; }
.permission-editor { display: grid; grid-template-columns: minmax(200px, 28%) 1fr; gap: 24px; margin: 20px 0; }.permission-list { max-height: 470px; overflow-y: auto; border: 1px solid #e5ebf4; border-radius: 8px; }.permission-choice { display: block; text-align: left; width: 100%; padding: 12px 14px; border: 0; border-bottom: 1px solid #edf1f6; background: white; color: #34506f; }.permission-choice small { display: block; font-size: 11px; margin-top: 4px; color: #8c9ab0; }.permission-choice.selected { background: #eef3ff; color: #315ff2; }.permission-form label:first-child { margin-top: 0; }
.grant-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }.change-row { border-bottom: 1px solid #e8eef6; padding: 12px 0; font-size: 13px; }
.wizard-footer { border-top: 1px solid #e6ecf5; padding-top: 22px; margin-top: 28px; }.wizard-footer>div { display: flex; gap: 10px; }
@media(max-width:900px) { .wizard-steps { flex-wrap: wrap; }.wizard-steps li { flex-basis: 28%; }.permission-editor, .wizard-fields, .grant-grid { grid-template-columns: 1fr; }.wizard-intro { align-items: flex-start; flex-direction: column; gap: 0; } }
</style>
