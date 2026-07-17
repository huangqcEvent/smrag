<template>
  <div class="skeleton-table">
    <div class="skeleton-row" v-for="i in rows" :key="i">
      <div class="skeleton-cell" style="width: 40%"></div>
      <div class="skeleton-cell" style="width: 15%"></div>
      <div class="skeleton-cell" style="width: 20%"></div>
      <div class="skeleton-cell" style="width: 10%"></div>
      <div class="skeleton-cell" style="width: 15%"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  rows: {
    type: Number,
    default: 5
  }
})
</script>

<style scoped>
.skeleton-table {
  padding: 12px 0;
}

.skeleton-row {
  display: flex;
  gap: 16px;
  padding: 14px 16px;
}

.skeleton-row + .skeleton-row {
  border-top: 1px solid var(--border-subtle);
}

.skeleton-cell {
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(
    90deg,
    var(--surface-hover) 0%,
    var(--surface-raised) 50%,
    var(--surface-hover) 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-cell {
    animation: none;
    background: var(--surface-hover);
  }
}
</style>