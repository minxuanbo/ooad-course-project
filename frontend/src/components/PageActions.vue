<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ busy: Boolean })
defineEmits(['refresh'])
</script>
<template>
  <Teleport to="#page-actions">
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
