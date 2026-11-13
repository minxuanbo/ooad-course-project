<script setup>
defineProps({ page: Number, size: Number, total: Number, busy: Boolean })
const emit = defineEmits(['change'])
</script>
<template>
  <div class="pagination">
    <span>共 {{ total }} 条 · 第 {{ page + 1 }} 页</span>
    <button class="secondary" :disabled="busy || page === 0" @click="emit('change', page - 1)">上一页</button>
    <button class="secondary" :disabled="busy || (page + 1) * size >= total" @click="emit('change', page + 1)">下一页</button>
  </div>
</template>
