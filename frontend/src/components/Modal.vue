<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
defineProps({ title: String, busy: Boolean, drawer: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
let previous
/** 在首尾循环Tab焦点；原生模态仍负责背景不可交互和Escape事件。 */
function containTab(event) {
  if (event.key !== 'Tab') return
  const controls = [
    ...dialog.value.querySelectorAll('button, input, select, textarea, a[href], [tabindex]'),
  ].filter(
    (element) =>
      element.tabIndex >= 0 && !element.matches(':disabled') && element.getClientRects().length,
  )
  const first = controls[0]
  const last = controls.at(-1)
  if (!first) {
    event.preventDefault()
    dialog.value.focus()
  } else if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
onMounted(() => {
  previous = document.activeElement
  dialog.value.showModal()
})
onUnmounted(() => {
  dialog.value?.close()
  previous?.focus()
})
</script>
<template>
  <dialog
    ref="dialog"
    class="management-dialog"
    :class="{ drawer }"
    aria-labelledby="dialog-title"
    tabindex="-1"
    @keydown="containTab"
    @cancel.prevent="!busy && emit('close')"
  >
    <header class="dialog-heading">
      <h2 id="dialog-title">{{ title }}</h2>
      <button aria-label="关闭" :disabled="busy" class="text-button" @click="emit('close')">
        ×
      </button>
    </header>
    <div class="modal-body"><slot /></div>
  </dialog>
</template>
<style scoped>
.management-dialog.drawer {
  position: fixed;
  inset: 0 0 0 auto;
  margin: 0;
  width: min(760px, 100vw);
  max-width: 100vw;
  height: 100dvh;
  max-height: 100dvh;
  border-radius: 0;
  padding: 0;
  overflow: hidden;
}
.drawer .dialog-heading {
  margin: 0;
  padding: 22px 24px;
}
.drawer .dialog-heading h2,
.drawer .dialog-heading button {
  margin-bottom: 0;
}
.drawer .modal-body {
  max-height: calc(100dvh - 86px);
  overflow-y: auto;
  padding: 24px;
}
</style>
