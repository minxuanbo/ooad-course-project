<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, notify } from '../api.js'
const props = defineProps({ kind: String, target: Object })
const emit = defineEmits(['close', 'saved'])
const dialog = ref(null), choices = ref([]), selected = ref([]), query = ref(''), error = ref(''), busy = ref(true)
const loaded = ref(false)
let alive = true
const endpoint = '/' + props.kind + '/' + props.target.id + (props.kind === 'users' ? '/roles' : '/permissions')
function label(item) { return props.kind === 'users' ? item.name : item.appName + ' / ' + item.module + ' / ' + item.feature + ' / ' + item.action }
const visible = computed(function() {
  const result = []
  for (const item of choices.value) if (label(item).toLowerCase().includes(query.value.toLowerCase())) result.push(item)
  return result
})
async function save() {
  busy.value = true; error.value = ''
  try { await api(endpoint, 'PUT', { ids: selected.value }); notify('授权已保存'); emit('saved') }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
onMounted(async function() {
  dialog.value.showModal()
  try {
    const data = await api(endpoint)
    if (!alive) return
    choices.value = data
    for (const item of data) if (item.assigned) selected.value.push(item.id)
    loaded.value = true
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
})
onUnmounted(function() { alive = false })
</script>
<template>
  <dialog ref="dialog" @cancel.prevent="emit('close')"><form @submit.prevent="save">
    <header><div><small>授权配置</small><h2>{{ target.name }}</h2></div><button type="button" class="secondary" @click="emit('close')">关闭</button></header>
    <p class="muted">搜索仅筛选显示项，保存时保留其他已选权限。取消全部选项可回收全部授权。</p>
    <input v-model="query" aria-label="筛选授权选项" placeholder="筛选选项…">
    <div class="choices"><p v-if="busy">正在处理…</p><label v-for="item in visible" :key="item.id" class="choice"><input v-model="selected" type="checkbox" :value="item.id" :disabled="busy"><span>{{ label(item) }}</span></label></div>
    <p class="error" role="alert">{{ error }}</p>
    <footer><span>已选择 {{ selected.length }} 项</span><button type="button" class="secondary" @click="emit('close')">取消</button><button class="primary" :disabled="busy || !loaded">保存授权</button></footer>
  </form></dialog>
</template>
