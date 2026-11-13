<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
defineProps({ title: String, busy: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
let previous
onMounted(() => { previous = document.activeElement; dialog.value.showModal() })
onUnmounted(() => { dialog.value?.close(); previous?.focus() })
</script>
<template>
  <dialog ref="dialog" class="management-dialog" aria-labelledby="dialog-title" @cancel.prevent="!busy && emit('close')">
    <header class="dialog-heading"><h2 id="dialog-title">{{ title }}</h2><button aria-label="关闭" :disabled="busy" class="text-button" @click="emit('close')">×</button></header>
    <slot />
  </dialog>
</template>
