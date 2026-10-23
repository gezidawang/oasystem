<template>
  <div class="notice-detail" v-loading="loading">
    <el-page-header @back="$router.back()" content="公告详情" class="detail-header" />

    <el-card v-if="notice" shadow="never" class="detail-card">
      <h1 class="notice-title">
        <el-tag v-if="notice.top" type="warning" size="small" effect="plain" class="top-tag">置顶</el-tag>
        {{ notice.title }}
      </h1>
      <div class="notice-meta">
        <span>发布人：{{ notice.publisherName || '—' }}{{ notice.type === 'all' && notice.publisherDeptName ? ' / ' + notice.publisherDeptName : '' }}</span>
        <el-divider direction="vertical" />
        <span>{{ formatTime(notice.publishAt) }}</span>
        <el-divider direction="vertical" />
        <el-tag size="small" :type="notice.type === 'all' ? '' : 'info'">
          {{ notice.type === 'all' ? '全员公告' : '部门公告' }}
        </el-tag>
      </div>
      <!-- 驳回原因：仅驳回人本人与申请/发布人本人可见（后端已对其他查看者置空脱敏，
           此处只在有值时展示；业务原因：驳回意见属敏感人事信息） -->
      <el-alert
        v-if="notice.status === 'rejected' && notice.rejectReason"
        type="error" :closable="false" show-icon class="reject-reason">
        <template #title>驳回原因：{{ notice.rejectReason }}</template>
      </el-alert>
      <el-divider />
      <div class="notice-content" v-html="safeContent"></div>
    </el-card>

    <el-empty v-else-if="!loading" description="公告不存在或已删除" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { noticeApi } from '@/api/notices'

const route = useRoute()
const loading = ref(false)
const notice = ref(null)

// 安全渲染：公告正文为富文本 HTML，v-html 前必须 DOMPurify 消毒（防存储型 XSS）
const safeContent = computed(() => notice.value ? DOMPurify.sanitize(notice.value.content || '') : '')

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
/* 驳回原因提示条：与详情卡片同风格，仅在授权双方视角出现 */
.reject-reason { margin-bottom: 12px; }
</style>
