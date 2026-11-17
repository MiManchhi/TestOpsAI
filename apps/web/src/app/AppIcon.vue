<script setup lang="ts">
withDefaults(
  defineProps<{
    name:
      | 'grid'
      | 'book'
      | 'check'
      | 'arrow'
      | 'logout'
      | 'menu'
      | 'close'
      | 'eye'
      | 'eye-off'
      | 'shield'
      | 'activity'
      | 'refresh'
      | 'user'
      | 'chevron';
  }>(),
  {},
);
const paths = {
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  book: 'M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1Z M12 5v15',
  check: 'M9 5H5v16h14V5h-4 M9 3h6v4H9z M8 14l3 3 5-6',
  arrow: 'M4 12h16 M14 6l6 6-6 6',
  logout: 'M9 4H4v16h5 M9 12h12 M16 7l5 5-5 5',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
  close: 'm6 6 12 12 M6 18 18 6',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  'eye-off':
    'm3 3 18 18 M10 5h2c6 0 10 7 10 7a20 20 0 0 1-3 4 M6 6a22 22 0 0 0-4 6s4 7 10 7c2 0 4-1 5-2 M10 10a3 3 0 0 0 4 4',
  shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z m-4 9 3 3 5-6',
  activity: 'M3 12h4l3-8 4 16 3-8h4',
  refresh: 'M20 7v5h-5 M4 17v-5h5 M6 6a8 8 0 0 1 13 2 M18 18a8 8 0 0 1-13-2',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-3a8 8 0 0 1 16 0v3',
  chevron: 'm9 5 7 7-7 7',
};
</script>
<template>
  <svg
    class="icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name]" />
  </svg>
</template>
