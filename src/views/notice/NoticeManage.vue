<template>
  <div class="manage-page">
    <div class="toolbar">
      <h3 class="page-title">{{ isApplicantOnly ? '公告申请' : '公告管理' }}</h3>
      <el-button type="primary" @click="$router.push('/notice/create')">{{ isApplicantOnly ? '申请发布公告' : '发布公告' }}</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="loadList">
      <el-tab-pane v-if="!isApplicantOnly" label="已发布" name="published" />
      <el-tab-pane label="我的草稿" name="draft" />
      <el-tab-pane v-if="!canApprove" label="我提交的申请" name="pending" />
      <el-tab-pane v-if="canApprove" label="待审批" name="pending_approval" />
      <el-tab-pane label="未通过" name="rejected" />
    </el-tabs>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
      <el-table-column prop="publisherName" label="发布人" width="140" align="center">
        <template #default="{ row }">
          {{ row.publisherName ? (row.type === 'all' && row.publisherDeptName ? `${row.publisherName} / ${row.publisherDeptName}` : row.publisherName) : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">{{ row.type === 'all' ? '全员' : '部门' }}</template>
      </el-table-column>
      <el-table-column label="时间" width="150" align="center">
        <template #default="{ row }">{{ formatTime(row.publishAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="290" align="center" class-name="action-col">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="$router.push(`/notice/${row.id}`)">查看</el-button>
          <el-button v-if="canEdit(row)" link type="primary" size="small" @click="$router.push(`/notice/edit/${row.id}`)">编辑</el-button>
          <el-button v-if="canPin && row.status === 'published'" link size="small" @click="handleTop(row, row.top ? 0 : 1)">
            {{ row.top ? '取消置顶' : '置顶' }}
          </el-button>
          <el-button v-if="activeTab === 'pending_approval'" link type="success" size="small" @click="handleApprove(row)">通过</el-button>
          <el-button v-if="activeTab === 'pending_approval'" link type="danger" size="small" @click="handleReject(row)">驳回</el-button>
          <el-button v-if="row.status === 'published' && canRevoke(row)" link type="warning" size="small" @click="handleRevoke(row)">撤回</el-button>
          <el-button v-if="canDelete(row)" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="total, prev, pager, next" @current-change="loadList" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noticeApi } from '@/api/notices'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const canApprove = computed(() => store.hasPermission('notice:publish:company'))
// 管理越权（删除/撤回他人公告、置顶全员）：行政 W3+；老板无此权。前端仅控按钮显示，后端兜底
const canManageAll = computed(() => store.hasPermission('notice:manage:all'))
const canPin = computed(() => store.hasPermission('notice:manage:all') || store.hasPermission('notice:publish:dept'))
// 纯申请人（只有 notice:submit:dept）：只能看到自己的草稿/申请/未通过，无已发布管理视角
const isApplicantOnly = computed(() =>
  store.hasPermission('notice:submit:dept')
  && !store.hasPermission('notice:create')
  && !store.hasPermission('notice:publish:dept'))
const activeTab = ref(isApplicantOnly.value ? 'draft' : 'published')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)

onMounted(() => loadList())

async function loadList() {
  loading.value = true
  try {
    const res = await noticeApi.list({ status: activeTab.value, page: page.value, size: 10 })
    if (res.success) {
      list.value = res.data.records
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

function canEdit(row) {
  if (row.status === 'draft' || row.status === 'rejected' || row.status === 'pending') return true
  if (row.status === 'published') {
    const base = new Date(row.approvedAt || row.publishAt)
    base.setHours(base.getHours() + 4)
    return new Date() < base && row.publisherId === store.userId
  }
  return false
}

function canRevoke(row) {
  return row.publisherId === store.userId || canManageAll.value
}

function canDelete(row) {
  if (canManageAll.value) return true // 行政管理层可直删任何公告
  return (row.status === 'draft' || row.status === 'rejected') && row.publisherId === store.userId
}

async function handleApprove(row) {
  await ElMessageBox.confirm(`确认通过「${row.title}」？`, '审批')
  const res = await noticeApi.approve(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleReject(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', { inputPlaceholder: '原因（必填）' })
  const res = await noticeApi.reject(row.id, value)
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleRevoke(row) {
  await ElMessageBox.confirm(`确认撤回「${row.title}」？撤回后将退回发布人的草稿箱。`, '撤回', { type: 'warning' })
  const res = await noticeApi.revoke(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？删除后不可恢复。`, '删除', { type: 'error' })
  const res = await noticeApi.delete(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleTop(row, top) {
  const action = top === 1 ? '置顶' : '取消置顶'
  await ElMessageBox.confirm(`确认${action}「${row.title}」？`, action)
  const res = await noticeApi.setTop(row.id, top)
  if (res.success) { ElMessage.success(res.message); loadList() }
  else { ElMessage.error(res.message) }
}

function statusLabel(s) { return { draft:'草稿', pending:'审批中', published:'已发布', rejected:'未通过', archived:'已撤回' }[s]||s }
function statusColor(s) { return { draft:'info', pending:'warning', published:'success', rejected:'danger', archived:'info' }[s]||'' }
function formatTime(t) { return t ? t.replace('T',' ').slice(0,16) : '—' }
</script>

<style scoped>
.manage-page { padding: 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-title { margin: 0; font-size: 18px; color: #303133; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
:deep(.action-col .cell) { white-space: nowrap; overflow: visible; }
</style>
