<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { api, notify, employmentLabel } from '../api.js'
const props = defineProps({ target: Object })
const emit = defineEmits(['close', 'saved'])
const dialog = ref(null), data = ref(null), error = ref(''), busy = ref(true)
const form = reactive({ receiverNo: '', ownerNo: '', expectedDate: '', reason: '', oaReference: '', note: '', confirmed: false })
const state = computed(function() { return data.value?.handover.state || 'EMPLOYED' })
async function save() {
  busy.value = true; error.value = ''
  const body = { ...form, confirmed: String(form.confirmed) }
  try {
    await api('/users/' + props.target.id + '/handover' + (state.value === 'HANDOVER' ? '/complete' : ''), 'POST', body)
    notify(state.value === 'HANDOVER' ? '离职处置完成：角色、账号及会话已停用。' : '已开始离职交接，现有权限保持不变。')
    emit('saved')
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
onMounted(async function() {
  dialog.value.showModal()
  try { data.value = await api('/users/' + props.target.id + '/handover') }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
})
</script>
<template>
  <dialog id="handover-dialog" ref="dialog" @cancel.prevent="emit('close')"><form @submit.prevent="save">
    <header><h2>{{ target.name }} · 离职交接</h2><button class="secondary" type="button" @click="emit('close')">关闭</button></header>
    <template v-if="data">
      <p>人员状态：<strong>{{ employmentLabel(state) }}</strong>　账号：{{ data.user.status === 'ACTIVE' ? '启用' : '停用' }}</p>
      <p>当前角色：<span v-for="r in data.roles" :key="r.id" class="badge">{{ r.name }}</span><span v-if="!data.roles.length">无角色</span></p>
      <details><summary>当前可用系统（{{ data.systems.length }} 个）</summary><ul><li v-for="s in data.systems" :key="s.name">{{ s.name }}</li></ul></details>
      <template v-if="state === 'EMPLOYED'">
        <p class="muted">开始交接保留现有授权，可按需手动回收。接收人不会自动获得原员工权限。</p>
        <label>交接接收人工号<input v-model="form.receiverNo" required maxlength="100"></label>
        <label>交接负责人工号<input v-model="form.ownerNo" required maxlength="100"></label>
        <label>预计完成日期<input v-model="form.expectedDate" type="date" required></label>
        <label>离职交接说明<textarea v-model="form.reason" required maxlength="1000"></textarea></label>
        <label>OA 单号（可选）<input v-model="form.oaReference" maxlength="200"></label>
      </template>
      <template v-else>
        <div class="handover-summary"><p>接收人：{{ data.handover.receiver_name }}（{{ data.handover.receiver_no }}）</p><p>负责人：{{ data.handover.owner_name }}（{{ data.handover.owner_no }}）</p><p>预计完成：{{ data.handover.expected_date }}</p><p>交接说明：{{ data.handover.reason }}</p><p>OA 单号：{{ data.handover.oa_reference || '未填写' }}</p></div>
        <template v-if="state === 'HANDOVER'"><p class="error">完成处置将回收全部角色、停用账号并撤销所有登录会话。</p><label>完成交接说明<textarea v-model="form.note" required maxlength="1000"></textarea></label><label class="choice"><input v-model="form.confirmed" type="checkbox" required><span>确认交接已完成，执行集中撤权和停用账号</span></label></template>
        <template v-else><p>实际完成：{{ data.handover.completed_at }} UTC</p><p>完成说明：{{ data.handover.completion_note }}</p><p>账号已停用。用户和历史记录保留，不物理删除。</p></template>
      </template>
      <details><summary>最近变更记录</summary><details v-for="(h, i) in data.history" :key="i"><summary>{{ h.occurred_at }} · {{ h.action }} · 操作人 ID {{ h.operator_id }}</summary><pre>{{ h.detail }}</pre></details></details>
    </template>
    <p class="error" role="alert">{{ error }}</p><footer><small>记录时间采用 UTC</small><button v-if="data && state !== 'DEPARTED'" class="primary" :disabled="busy">{{ state === 'EMPLOYED' ? '开始离职交接' : '完成离职处置' }}</button></footer>
  </form></dialog>
</template>
