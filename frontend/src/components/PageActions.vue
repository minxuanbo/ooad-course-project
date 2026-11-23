<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ busy: Boolean })
defineEmits(['refresh'])
</script>
<template>
  <!-- 标题栏与页面在登录切换时同批挂载，待本轮DOM更新完成后解析目标。 -->
  <Teleport defer to="#page-actions">
    <slot />
    <button type="button" class="secondary page-refresh" :disabled="busy" @click="$emit('refresh')">
      <AppIcon name="refresh" />{{ busy ? '刷新中…' : '刷新' }}
    </button>
  </Teleport>
</template>
<style scoped>
.page-refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-size: 12px;
  min-height: 38px;
  white-space: nowrap;
}
</style>
