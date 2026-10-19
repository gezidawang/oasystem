<template>
  <el-card class="placeholder" shadow="never">
    <el-empty :description="`${title} · 该模块将在后续 Phase 实现`" />
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title || '功能模块')
</script>

<style scoped>
.placeholder {
  border: 1px solid #e6eef7;
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
