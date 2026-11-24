<script setup>
defineProps({ name: { type: String, required: true } })
/** 本地线性图标与原型保持一致，名称由页面固定提供，不依赖远端字体或资源。 */
const paths = {
  overview: ['M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z'],
  building: ['M6 21V3h12v18M3 21h18M9 7h1m4 0h1M9 11h1m4 0h1M10 21v-5h4v5'],
  folder: ['M3 7V5a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z'],
  button: ['M5 7h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2ZM9 12h6'],
  lock: ['M5 10h14v11H5zM8 10V7a4 4 0 0 1 8 0v3M12 14v3'],
  eye: ['M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z', 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z'],
  eyeOff: ['m3 3 18 18M10.6 5.1A12 12 0 0 1 12 5c6 0 10 7 10 7a21 21 0 0 1-4 4M6 6a25 25 0 0 0-4 6s4 7 10 7a13 13 0 0 0 5-1M10 10a3 3 0 0 0 4 4'],
  arrow: ['M5 12h14m-6-6 6 6-6 6'],
  user: ['M20 21v-2a8 8 0 0 0-16 0v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z'],
  ban: ['m5 5 14 14M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z'],
  project: ['M4 21V8h16v13M8 8V3h8v5M2 21h20M8 12h2m4 0h2M8 16h2m4 0h2'],
  users: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M17 4a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87',
    'M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  ],
  shield: ['m12 3 8 4v5c0 5-8 9-8 9s-8-4-8-9V7l8-4Z', 'm8.5 12 2.5 2.5 4.5-5'],
  resource: ['m12 3 10 5-10 5L2 8l10-5Zm-10 9 10 5 10-5M2 16l10 5 10-5'],
  history: ['M3 4v5h5M3.5 9A9 9 0 1 1 3 16M12 7v5l3 2'],
  chevron: ['m9 5 7 7-7 7'],
  plus: ['M12 5v14M5 12h14'],
  refresh: ['M20 4v5h-5M4 20v-5h5M4.8 8A8 8 0 0 1 18 5l2 4M4 15l2 4a8 8 0 0 0 13.2-3'],
  search: ['M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z'],
  info: ['M12 11v6M12 7h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z'],
}
</script>
<template>
  <svg
    class="ui-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="(path, index) in paths[name] || paths.building" :key="index" :d="path" />
  </svg>
</template>
