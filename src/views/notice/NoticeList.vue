<template>
  <div class="notice-page">
    <!-- 视图切换 -->
    <el-tabs v-model="query.type" class="status-tabs" @tab-change="loadList">
      <el-tab-pane label="全部公告" name="" />
      <el-tab-pane label="部门公告" name="department" />
    </el-tabs>

    <!-- 工具栏 -->
    <div class="toolbar">
      <h3 class="page-title">公告通知</h3>
      <div class="toolbar-actions">
        <el-input v-model="query.keyword" placeholder="搜索标题" clearable style="width:180px" @keyup.enter="loadList" />
        <el-select v-model="query.type" placeholder="类型" clearable style="width:120px" @change="loadList">
          <el-option label="全员公告" value="all" />
          <el-option label="部门公告" value="department" />
        </el-select>
        <el-select v-model="query.deptId" placeholder="部门" clearable style="width:140px" @change="loadList">
          <el-option v-for="d in deptOptions" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-button v-if="canManage" type="primary" @click="$router.push('/notices/manage')">公告管理</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <el-table :data="list" v-loading="loading" border class="notice-table" @row-click="goDetail">
      <el-table-column label="标题" min-width="260">
        <template #default="{ row }">
          <span class="title-cell">
            <el-icon v-if="row.top" color="#e6a23c" :size="14"><Top /></el-icon>
            <span :class="{ unread: !readSet.has(row.id) }">{{ row.title }}</span>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.type === 'all' ? '' : 'info'" size="small">
            {{ row.type === 'all' ? '全员' : '部门' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="160" align="center">
        <template #default="{ row }">{{ formatTime(row.publishAt) }}</template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { noticeApi } from '@/api/notices'
import { departmentApi } from '@/api/departments'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const store = useUserStore()
const canCreate = computed(() => store.hasPermission('notice:create'))
const canManage = computed(() => store.hasPermission('notice:create') || store.hasPermission('notice:publish:dept'))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const readSet = ref(new Set())
const query = reactive({ keyword: '', type: '', deptId: null, page: 1, size: 10 })
const deptOptions = ref([])

onMounted(() => {
  loadList()
  loadDepts()
})

async function loadDepts() {
  const res = await departmentApi.tree()
  if (res.success) {
    // 只取一级部门作为筛选项
    deptOptions.value = res.data.filter(d => d.status === 1).map(d => ({ id: d.id, name: d.name }))
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await noticeApi.list(query)
    if (res.success) {
      list.value = res.data.records
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

function goDetail(row) {
  router.push(`/notice/${row.id}`)
}

function statusLabel(s) {
  return { draft: '草稿', pending: '审批中', published: '已发布', rejected: '未通过', archived: '已撤回' }[s] || s
}
function statusColor(s) {
  return { draft: 'info', pending: 'warning', published: 'success', rejected: 'danger', archived: 'info' }[s] || ''
}
function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<style scoped>
.notice-page { padding: 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 10px; }
.page-title { margin: 0; font-size: 18px; color: #303133; }
.toolbar-actions { display: flex; gap: 10px; align-items: center; }
.notice-table { background: #fff; cursor: pointer; }
.title-cell { display: flex; align-items: center; gap: 4px; }
.unread { font-weight: 600; color: #1f2d3d; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.status-tabs { margin-bottom: 12px; }
</style>
