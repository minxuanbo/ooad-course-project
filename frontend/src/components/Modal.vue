<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
defineProps({ title: String, busy: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
let previous
/** 在首尾循环Tab焦点；原生模态仍负责背景不可交互和Escape事件。 */
function containTab(event) {
  if (event.key !== 'Tab') return
  const controls = [
    ...dialog.value.querySelectorAll(
      'button, input, select, textarea, a[href], [tabindex]',
    ),
  ].filter(
    (element) =>
      element.tabIndex >= 0 &&
      !element.matches(':disabled') &&
      element.getClientRects().length,
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
    aria-labelledby="dialog-title"
    tabindex="-1"
    @keydown="containTab"
    @cancel.prevent="!busy && emit('close')"
  >
    <header class="dialog-heading">
      <h2 id="dialog-title">{{ title }}</h2>
      <button
        aria-label="关闭"
        :disabled="busy"
        class="text-button"
        @click="emit('close')"
      >
        ×
      </button>
    </header>
    <slot />
  </dialog>
</template>
