<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, setToken, refreshSession, can } from '../api.js'
const router = useRouter()
const account = ref(''), password = ref(''), reveal = ref(false), busy = ref(false), error = ref('')
async function login() {
  busy.value = true; error.value = ''
  try {
    const result = await api('/auth/login', 'POST', { account: account.value.trim(), password: password.value })
    setToken(result.token)
    await refreshSession()
    await router.replace(can('rbac.logs.read') ? '/logs' : '/')
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <div class="login-layout">
    <section class="login-story"><div class="brand"><span class="mark">市</span> 市政集团 <small>数字化管理平台</small></div><div class="story"><div class="eyebrow">IDENTITY & ACCESS MANAGEMENT</div><h1>统一身份，<br>有序授权。</h1><p>连接人员、角色与业务系统，<br>让每一次访问都有据可依。</p><div class="diagram"><span>用户</span><i>──</i><span>角色</span><i>──</i><span>权限</span></div><small>统一认证 / 按角色授权 / 访问留痕</small></div><footer>市政公司统一权限管理系统</footer></section>
    <section class="login-panel"><div class="login-card"><span class="pill">统一登录</span><h2>欢迎回来</h2><p class="muted">工号或手机号任一入口登录，共用同一账号口令。</p>
      <form @submit.prevent="login"><label for="account">账号</label><input id="account" v-model="account" autocomplete="username" placeholder="输入工号或手机号" required><label for="password">密码</label><div class="password-field"><input id="password" v-model="password" :type="reveal ? 'text' : 'password'" autocomplete="current-password" required><button type="button" class="text-button" @click="reveal = !reveal">{{ reveal ? '隐藏' : '显示' }}</button></div><p class="error" role="alert">{{ error }}</p><button class="primary login-submit" :disabled="busy">{{ busy ? '正在验证身份…' : '登录 →' }}</button></form>
      <div class="demo"><strong>说明</strong><p>登录账号由部署初始化提供；无有效管理权限或已停用的账号将被拒绝登录。</p></div>
    </div><footer>统一权限管理 · RBAC0</footer></section>
  </div>
</template>
