<script setup>
import Overview from './Overview.vue'
import { canRoute } from '../api.js'
</script>
<template>
  <Overview v-if="canRoute('GET', '/overview')" />
  <section v-else class="card panel">
    <h2>已进入权限管理工作区</h2>
    <p class="muted">请从获准的菜单进入相应功能。当前账号没有范围总览权限。</p>
  </section>
</template>
