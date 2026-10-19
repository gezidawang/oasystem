<template>
  <el-avatar :size="size" :src="url || undefined" :style="url ? {} : fallbackStyle">
    <span v-if="!url">{{ initial }}</span>
  </el-avatar>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 统一头像组件（10 号横切 §六）：
 *  - 传入 url（后端按权限返回的实名照片/头像，可空）→ 显示图片；
 *  - 无 url → 姓名首字母 + 按 seed 稳定的默认色块兜底。
 * 前端不做权限判断：url 有值即代表后端已授权可见。
 */
const props = defineProps({
  name: { type: String, default: '' },
  url: { type: String, default: '' },
  seed: { type: [String, Number], default: '' },
  size: { type: [String, Number], default: 32 },
})

const initial = computed(() => (props.name || '?').trim().charAt(0).toUpperCase())

const PALETTE = ['#4e8cc9', '#5aa469', '#c98b4e', '#8a6fb0', '#4fb0a5', '#b06f7a', '#6f7ab0', '#a98f3f']
const fallbackStyle = computed(() => {
  const key = String(props.seed || props.name || '?')
  let h = 0
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0
  return { background: PALETTE[h % PALETTE.length], color: '#fff', fontWeight: '600' }
})
</script>
