<script setup>
import { ref, onMounted } from 'vue'
import { api, notify } from '../api.js'
const props = defineProps({ target: Object }), emit = defineEmits(['close', 'saved'])
const dialog = ref(null), scopes = ref([]), roles = ref([]), selected = ref([]), org = ref({ companies: [], departments: [] })
const busy = ref(true), error = ref(''), loaded = ref(false)
function add() { scopes.value.push({ roleId: null, scope: 'DEPARTMENT', companyId: null, departmentId: null }) }
function roleChanged(g) { g.scope = roles.value.find(r => r.id === g.roleId)?.code === '系统管理员' ? 'GLOBAL' : 'DEPARTMENT'; g.companyId = null; g.departmentId = null }
function rolesChanged() { scopes.value = scopes.value.filter(g => selected.value.includes(g.roleId)) }
async function save() {
  busy.value = true; error.value = ''
  try { await api(`/users/${props.target.id}/role-assignments`, 'PUT', { roleIds: selected.value, scopes: scopes.value }); notify('角色与管理范围已保存'); emit('saved') }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(async () => {
  dialog.value.showModal()
  try {
    const [assignments, organizations] = await Promise.all([api(`/users/${props.target.id}/role-assignments`), api('/organizations')])
    roles.value = assignments.roles; selected.value = assignments.roles.filter(r => r.assigned).map(r => r.id)
    scopes.value = assignments.scopes; org.value = organizations; loaded.value = true
  } catch (e) { error.value = e.message } finally { busy.value = false }
})
</script>
<template>
  <dialog ref="dialog" @cancel.prevent="emit('close')"><form @submit.prevent="save">
    <header><h2>{{ target.name }} · 分配角色与管理范围</h2><button type="button" class="secondary" @click="emit('close')">关闭</button></header>
    <p>角色决定能执行哪些操作；此处单独设置这名员工各角色的管理范围。修改角色的操作权限，请前往“角色与权限”。</p>
    <section class="assignment-section"><h3>① 分配角色</h3><p>决定这名员工能执行哪些操作。</p><fieldset :disabled="busy"><legend>选择角色</legend><label v-for="r in roles" :key="r.id" class="choice"><input type="checkbox" v-model="selected" :value="r.id" :disabled="!r.enabled" @change="rolesChanged">{{ r.name }}</label></fieldset></section>
    <section class="assignment-section"><h3>② 设置管理范围</h3><p>为上方已选角色设置可管理的公司或部门。同一角色可以添加多个范围。</p><p class="muted">角色没有范围时，不获得该角色的后台管理权限；业务功能权限仍按所选角色生效。公司范围覆盖所属全部部门，部门范围仅覆盖所选部门／项目部。</p>
    <fieldset v-for="(g,i) in scopes" :key="i" :disabled="busy" class="panel"><legend>范围 {{ i + 1 }}</legend>
      <label>应用于哪个角色<select v-model="g.roleId" required @change="roleChanged(g)"><option :value="null" disabled>选择已分配角色</option><option v-for="r in roles.filter(r => selected.includes(r.id))" :key="r.id" :value="r.id">{{ r.name }}</option></select></label>
      <label>范围类型<select v-model="g.scope" @change="g.companyId = null; g.departmentId = null"><option v-if="roles.find(r => r.id === g.roleId)?.code === '系统管理员'" value="GLOBAL">全平台</option><template v-else><option value="COMPANY">指定公司</option><option value="DEPARTMENT">指定部门／项目部</option></template></select></label>
      <label v-if="g.scope !== 'GLOBAL'">公司<select v-model="g.companyId" required @change="g.departmentId = null"><option :value="null" disabled>选择公司</option><option v-for="c in org.companies" :key="c.id" :value="c.id">{{ c.name }}</option></select></label>
      <label v-if="g.scope === 'DEPARTMENT'">部门／项目部<select v-model="g.departmentId" required><option :value="null" disabled>选择部门</option><option v-for="d in org.departments.filter(d => d.companyId === g.companyId)" :key="d.id" :value="d.id">{{ d.name }}</option></select></label>
      <button type="button" class="text-button" @click="scopes.splice(i,1)">移除此范围</button>
    </fieldset>
    <button type="button" class="secondary" :disabled="busy || !loaded || !selected.length" @click="add">＋ 添加范围</button><p class="muted">添加、移除和角色调整均在保存后生效；取消不会修改现有分配。</p></section>
    <p class="error" role="alert">{{ error }}</p><footer><small>撤销角色会一并撤销其范围；同一角色可兼管多个范围。</small><button class="primary" :disabled="busy || !loaded">保存全部更改</button></footer>
  </form></dialog>
</template>

<style scoped>
.assignment-section { margin: 22px 0; padding: 18px; border: 1px solid #dfe5ef; border-radius: 10px; background: #f8faff; }
.assignment-section h3 { margin: 0; color: #294c86; font-size: 17px; }
.assignment-section fieldset { min-width: 0; border: 1px solid #dce2eb; border-radius: 8px; background: white; margin: 16px 0; }
.assignment-section legend { padding: 0 6px; font-weight: 600; }
.assignment-section label:not(.choice) { display: block; margin: 10px 0; }
.assignment-section select { display: block; width: 100%; margin-top: 5px; padding: 10px; border: 1px solid #dce2eb; border-radius: 7px; background: white; color: inherit; font: inherit; }
</style>
