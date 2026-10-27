<script setup>
import { ref, onMounted } from 'vue'
import { api, can, notify, session } from '../api.js'
const data = ref({ companies: [], departments: [] }), error = ref(''), busy = ref(false), editing = ref(null)
const form = ref({ code: '', name: '', companyId: null, parentId: null })
async function load() { try { data.value = await api('/organizations') } catch (e) { error.value = e.message } }
function edit(kind, item) { editing.value = { kind, id: item?.id }; form.value = { code: item?.code || '', name: item?.name || '', companyId: null, parentId: null } }
function companyWritable(id) { return (session.me?.managementGrants || []).some(g => g.permission === 'org.write' && (g.scope === 'GLOBAL' || g.scope === 'COMPANY' && g.companyId === id)) }
async function save() {
  busy.value = true; error.value = ''
  try { const { kind, id } = editing.value; await api(`/organizations/${kind}${id ? '/' + id : ''}`, id ? 'PATCH' : 'POST', id ? { code: form.value.code, name: form.value.name } : form.value); editing.value = null; notify('组织已保存'); await load() }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
function departmentsFor(companyId) { const items = data.value.departments.filter(d => d.companyId === companyId); return items.length ? items : [null] }
onMounted(load)
</script>
<template>
  <section class="card panel"><p>仅显示您有权查看的组织。部门维护可修改编码和名称，组织迁移不在此界面开放。</p>
    <button v-if="can('org.write',true)" class="secondary" @click="edit('companies')">新增公司</button>
    <button v-if="data.companies.some(c => companyWritable(c.id))" class="secondary" @click="edit('departments')">新增部门</button>
    <form v-if="editing" @submit.prevent="save" class="panel"><h2>{{ editing.id ? '修改' : '新增' }}{{ editing.kind === 'companies' ? '公司' : '部门' }}</h2>
      <label>编码<input v-model="form.code" required maxlength="100"></label><label>名称<input v-model="form.name" required maxlength="191"></label>
      <template v-if="editing.kind === 'departments' && !editing.id"><label>公司<select v-model="form.companyId" required @change="form.parentId = null"><option :value="null" disabled>选择公司</option><option v-for="c in data.companies.filter(c => companyWritable(c.id))" :key="c.id" :value="c.id">{{ c.name }}</option></select></label></template>
      <button class="primary" :disabled="busy">保存</button><button type="button" class="secondary" @click="editing = null">取消</button>
    </form><p class="error" role="alert">{{ error }}</p>
    <div class="table-scroll"><table><thead><tr><th>公司</th><th>部门</th><th>操作</th></tr></thead>
      <tbody v-for="c in data.companies" :key="c.id">
        <tr v-for="(d,index) in departmentsFor(c.id)" :key="d?.id || 'empty'">
          <td v-if="index === 0" :rowspan="departmentsFor(c.id).length">{{ c.name }}<small>{{ c.code }}</small></td>
          <td>{{ d?.name || '暂无部门' }}<small v-if="d">{{ d.code }}</small></td>
          <td><button v-if="index === 0 && can('org.write',true)" class="text-button" @click="edit('companies',c)">修改公司</button><button v-if="d?.writable" class="text-button" @click="edit('departments',d)">修改部门</button></td>
        </tr>
      </tbody>
    </table></div>
  </section>
</template>
