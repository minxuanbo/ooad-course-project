<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, setToken } from '../api.js'
import AppIcon from '../components/AppIcon.vue'
import organizationImage from '../assets/login-organization.svg'
const router = useRouter()
const account = ref(''),
  password = ref(''),
  reveal = ref(false),
  busy = ref(false),
  error = ref('')
/** 真实口令登录后交由路由重读身份，不从演示数据填充或推断权限。 */
async function login() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const result = await api('/auth/login', 'POST', {
      account: account.value.trim(),
      password: password.value,
    })
    setToken(result.token)
    await router.replace('/')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

</script>
<template>
  <main class="login-page">
    <section class="login-story" aria-label="市政权限">
      <div class="login-brand">
        <span class="login-logo" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        <div><strong>市政权限</strong><small>MUNICIPAL ACCESS</small></div>
      </div>
      <div class="story-copy">
        <span class="eyebrow">Connected people. Clear access.</span>
        <h1>管理有界，<br />协作有序。</h1>
        <p>让组织、人员与权限各归其位，<br />为每一次协作建立清晰的边界。</p>
      </div>
      <img class="story-illustration" :src="organizationImage" alt="" />
      <div class="story-chips" aria-hidden="true">
        <span><AppIcon name="building" />统一组织</span>
        <span><AppIcon name="users" />高效协作</span>
        <span><AppIcon name="shield" />权限可控</span>
      </div>
    </section>
    <section class="login-main">
      <form class="login-form" :aria-busy="busy" @submit.prevent="login">
        <span class="eyebrow">Municipal workspace</span>
        <h2>欢迎登录</h2>
        <p>登录市政公司统一权限管理系统</p>
        <div class="field">
          <label for="account">工号或手机号</label>
          <div class="input-shell">
            <AppIcon name="user" />
            <input id="account" v-model="account" autocomplete="username" maxlength="100"
              placeholder="请输入工号或手机号" :disabled="busy" required />
          </div>
        </div>
        <div class="field">
          <label for="password">登录密码</label>
          <div class="input-shell">
            <AppIcon name="lock" />
            <input id="password" v-model="password" :type="reveal ? 'text' : 'password'"
              autocomplete="current-password" placeholder="请输入密码" :disabled="busy" required />
            <button class="password-toggle" type="button" :aria-label="reveal ? '隐藏密码' : '显示密码'"
              :aria-pressed="reveal" @click="reveal = !reveal">
              <AppIcon :name="reveal ? 'eyeOff' : 'eye'" />
            </button>
          </div>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button class="primary login-submit" :disabled="busy" aria-label="登录">
          <template v-if="busy">正在验证身份…</template>
          <template v-else>登 录<AppIcon name="arrow" /></template>
        </button>
        <div class="login-note"><AppIcon name="shield" />仅向已获管理权限的账号开放</div>
      </form>
      <footer class="login-footer">市政公司 · 统一权限管理系统</footer>
    </section>
  </main>
</template>
<style scoped>
.login-page { min-height: 100vh; min-height: 100dvh; background: #fff; display: grid; grid-template-columns: minmax(420px, 48%) 1fr; }
.login-story { position: relative; overflow: hidden; background: #f0f5fe; padding: 42px 48px 28px; display: flex; flex-direction: column; }
.login-story::before { content: ''; position: absolute; inset: 0; background-image: radial-gradient(#b1c7e940 .7px, transparent .7px); background-size: 22px 22px; mask-image: linear-gradient(to bottom, transparent, #000 70%); pointer-events: none; }
.login-brand { position: relative; display: flex; align-items: center; gap: 11px; }
.login-brand strong { font-size: 16px; letter-spacing: .5px; color: #182944; }
.login-brand small { font-size: 10px; letter-spacing: 1.2px; margin-top: 3px; color: #7b879a; }
.login-logo { display: grid; grid-template-columns: 1fr 1fr; gap: 3px; width: 34px; height: 34px; padding: 8px; background: #2563eb; border-radius: 9px; box-shadow: 0 3px 10px #2563eb20; }
.login-logo i { background: white; border-radius: 1px; }
.login-logo i:last-child { opacity: .6; }
.eyebrow { color: #2563eb; font-size: 11px; font-weight: 650; letter-spacing: 2px; text-transform: uppercase; }
.story-copy { margin: auto 0 0; padding-top: 72px; position: relative; }
.story-copy h1 { font-size: clamp(30px, 3.1vw, 46px); line-height: 1.45; letter-spacing: 1px; margin: 16px 0; color: #1d345a; font-weight: 650; }
.story-copy p { font-size: 14px; color: #7587a1; line-height: 1.9; margin: 0; }
.story-illustration { width: 100%; max-width: 480px; margin: 12px auto; position: relative; }
.story-chips { display: flex; gap: 21px; color: #68819f; font-size: 12px; position: relative; padding-bottom: 12px; margin-top: auto; }
.story-chips span { display: inline-flex; align-items: center; gap: 6px; }
.login-main { display: flex; align-items: center; justify-content: center; min-width: 0; padding: 60px 40px 80px; position: relative; }
.login-form { width: min(100%, 352px); }
.login-form h2 { font-size: 29px; margin: 12px 0; color: #182944; letter-spacing: 1px; font-weight: 650; }
.login-form > p:not(.error) { color: #7b879a; margin: 0 0 34px; line-height: 1.8; }
.field { display: flex; flex-direction: column; gap: 9px; margin-bottom: 22px; }
.field label { font-size: 13px; font-weight: 550; }
.input-shell { display: flex; align-items: center; gap: 10px; border: 1px solid #dee5ee; padding: 0 12px; border-radius: 7px; background: white; min-width: 0; }
.input-shell:focus-within { border-color: #80a8f7; box-shadow: 0 0 0 3px #2563eb0a; }
.input-shell > .ui-icon { color: #8e9ab0; width: 17px; height: 17px; }
.input-shell input { border: 0; outline: none; padding: 14px 0; width: 100%; min-width: 0; background: transparent; font-size: 13px; border-radius: 0; box-shadow: none; }
.input-shell input::placeholder { color: #a0aabc; opacity: 1; }
.input-shell:has(input:focus-visible) { outline: 2px solid #a6c1fa; outline-offset: 2px; }
.password-toggle { display: inline-flex; padding: 8px; background: transparent; color: #52617a; }
.password-toggle:hover { background: #edf1f8; }
.login-submit { display: flex; align-items: center; justify-content: center; gap: 8px; width: 100%; min-height: 46px; margin-top: 5px; padding: 8px 15px; background: #2563eb; border: 1px solid #2563eb; box-shadow: 0 2px 3px #2563eb12; }
.login-submit:hover:not(:disabled) { background: #1d4ed8; }
.login-note { font-size: 12px; color: #8c98aa; display: flex; align-items: center; justify-content: center; gap: 6px; margin-top: 18px; }
.login-note .ui-icon { width: 14px; height: 14px; }
.login-footer { position: absolute; bottom: 23px; left: 20px; right: 20px; text-align: center; font-size: 11px; color: #a2aaba; }
.error { font-size: 12px; margin: 0 0 16px; }
@media (max-width: 1100px) {
  .login-page { grid-template-columns: 45% 1fr; }
  .login-story { padding-left: 30px; padding-right: 30px; }
  .story-copy h1 { font-size: 31px; }
  .story-chips { gap: 12px; flex-wrap: wrap; }
}
@media (max-width: 760px) {
  .login-page { grid-template-columns: 1fr; }
  .login-story { display: none; }
  .login-main { min-height: 100dvh; padding: 80px 30px 70px; }
  .login-form::before { content: '市政权限 · MUNICIPAL ACCESS'; display: block; font-size: 12px; color: #7c93b6; letter-spacing: 1px; margin-bottom: 45px; }
}
</style>
