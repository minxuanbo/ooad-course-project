<script setup>
import { ref, onMounted } from 'vue'
import { api, notify } from '../api.js'
const props = defineProps({ target: Object }), emit = defineEmits(['close', 'saved'])
const dialog = ref(null), permissions = ref({}), selected = ref([]), busy = ref(true), loaded = ref(false), error = ref('')
const platformOnly = ['audit.read', 'role.permissions', 'management.grant']
async function save() {
  busy.value = true; error.value = ''
  try { await api(`/roles/${props.target.id}/management-permissions`, 'PUT', { permissions: selected.value }); notify('角色管理权限已保存'); emit('saved') }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(async () => {
  dialog.value.showModal()
  try { [permissions.value, selected.value] = await Promise.all([api('/management/permissions'), api(`/roles/${props.target.id}/management-permissions`)]); loaded.value = true }
  catch (e) { error.value = e.message } finally { busy.value = false }
})
</script>
<template>
  <dialog ref="dialog" @cancel.prevent="emit('close')"><form @submit.prevent="save">
    <header><h2>{{ target.name }} · 管理操作权限</h2><button type="button" class="secondary" @click="emit('close')">关闭</button></header>
    <p>这里修改角色的权限，影响持有该角色且配置了管理范围的员工。每名员工的范围仍在人员页面分别设置。</p>
    <label v-for="(label, code) in permissions" :key="code" class="choice"><input type="checkbox" v-model="selected" :value="code" :disabled="busy || (target.code !== '系统管理员' && platformOnly.includes(code))">{{ label }}</label>
    <p class="muted">全部鉴权日志、角色权限配置、人员角色范围配置仅属于系统管理员角色。</p>
    <p class="error" role="alert">{{ error }}</p><footer><button class="primary" :disabled="busy || !loaded">保存角色管理权限</button></footer>
  </form></dialog>
</template>
