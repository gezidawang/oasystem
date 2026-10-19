<template>
  <div class="notice-detail" v-loading="loading">
    <el-page-header @back="$router.back()" content="公告详情" class="detail-header" />

    <el-card v-if="notice" shadow="never" class="detail-card">
      <h1 class="notice-title">
        <el-tag v-if="notice.top" type="warning" size="small" effect="plain" class="top-tag">置顶</el-tag>
        {{ notice.title }}
      </h1>
      <div class="notice-meta">
        <span>发布人：{{ notice.publisherName || '—' }}</span>
        <el-divider direction="vertical" />
        <span>{{ formatTime(notice.publishAt) }}</span>
        <el-divider direction="vertical" />
        <el-tag size="small" :type="notice.type === 'all' ? '' : 'info'">
          {{ notice.type === 'all' ? '全员公告' : '部门公告' }}
        </el-tag>
      </div>
      <el-divider />
      <div class="notice-content" v-html="notice.content"></div>
    </el-card>

    <el-empty v-else-if="!loading" description="公告不存在或已删除" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { noticeApi } from '@/api/notices'

const route = useRoute()
const loading = ref(false)
const notice = ref(null)

onMounted(() => loadDetail())

async function loadDetail() {
  loading.value = true
  try {
    const res = await noticeApi.detail(route.params.id)
    if (res.success) {
      notice.value = res.data
    }
  } finally {
    loading.value = false
  }
}

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<style scoped>
.notice-detail { max-width: 800px; margin: 0 auto; padding: 20px; }
.detail-header { margin-bottom: 16px; }
.detail-card { border: 1px solid #e6eef7; }
.notice-title { font-size: 20px; color: #1f2d3d; margin: 0 0 12px; }
.top-tag { margin-right: 8px; vertical-align: middle; }
.notice-meta { font-size: 13px; color: #8896a7; }
.notice-content { line-height: 1.8; color: #374151; font-size: 15px; }
.notice-content :deep(p) { margin: 8px 0; }
</style>
