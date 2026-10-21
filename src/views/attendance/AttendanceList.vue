<template>
  <div class="attendance-page">
    <!-- 筛选栏：部门 / 姓名 / 日期范围 / 考勤状态 -->
    <el-card shadow="never" class="filter-card">
      <el-form inline class="filter-form">
        <!-- 部门筛选：页内按钮级权限（v-perm）——无 read:dept/read:all 的角色直接不渲染此控件 -->
        <el-form-item v-if="showDeptFilter" v-perm="['attendance:read:dept', 'attendance:read:all']" label="部门">
          <el-select v-model="query.deptId" placeholder="全部部门" clearable style="width: 150px" @change="search">
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <!-- 姓名搜索：仅对能看他人记录的角色开放；普通员工只能看自己的，搜姓名无意义 -->
        <el-form-item v-if="showDeptFilter" label="姓名">
          <el-input v-model="query.keyword" placeholder="输入姓名" clearable style="width: 140px" @keyup.enter="search" @clear="search" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="开始日期" end-placeholder="结束日期"
                          :clearable="true" style="width: 240px" />
        </el-form-item>
        <el-form-item label="考勤情况">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="search">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 考勤记录表格：部门（一级部门口径） - 姓名 - 职位 - 打卡日期时间 - 考勤情况 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="records" v-loading="loading" stripe border empty-text="暂无考勤记录">
        <el-table-column prop="departmentName" label="部门" min-width="100" />
        <el-table-column prop="realName" label="姓名" min-width="90" />
        <el-table-column prop="positionName" label="职位" min-width="80">
          <template #default="{ row }">{{ row.positionName || '员工' }}</template>
        </el-table-column>
        <el-table-column prop="workDate" label="打卡日期" width="110" />
        <el-table-column prop="checkInTime" label="上班打卡" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'late-time': row.status === 'late' }">{{ row.checkInTime || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="checkOutTime" label="下班打卡" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'early-time': row.status === 'early_leave' }">{{ row.checkOutTime || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="考勤情况" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)" effect="light">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <!-- 分页 -->
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next, sizes"
                       :total="total" v-model:current-page="query.page" v-model:page-size="query.size"
                       :page-sizes="[10, 20, 50]"
                       @current-change="load" @size-change="onSizeChange" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { attendanceApi } from '@/api/attendance'
import { useUserStore } from '@/stores/user'

const store = useUserStore()

// 考勤状态选项（value 与后端 status 码一致；pending=未打卡，仅当天未到下班时间出现）
const STATUS_OPTIONS = [
  { label: '正常', value: 'normal' },
  { label: '迟到', value: 'late' },
  { label: '早退', value: 'early_leave' },
  { label: '缺勤', value: 'absent' },
  { label: '未打卡', value: 'pending' },
]

const loading = ref(false)
const records = ref([])
const total = ref(0)
const departments = ref([])
const dateRange = ref(null)

// 查询条件（page/size 双向绑定到分页组件）
const query = reactive({
  deptId: null,
  keyword: '',
  status: '',
  startDate: '',
  endDate: '',
  page: 1,
  size: 10,
})

// 部门筛选框仅对「可看全公司/本部门」的账号展示；普通员工只看自己的，无需部门筛选
// （控件渲染由模板里的 v-perm 指令控制，本计标仅用于决定是否预加载部门列表）
const showDeptFilter = computed(() =>
  store.hasPermission('attendance:read:all') || store.hasPermission('attendance:read:dept'))

onMounted(() => {
  if (showDeptFilter.value) loadDepartments()
  load()
})

async function loadDepartments() {
  const res = await attendanceApi.departments()
  if (res.success) departments.value = res.data || []
}

async function load() {
  loading.value = true
  try {
    // 日期范围转为 startDate/endDate；未选默认后端按「今天」查询
    query.startDate = dateRange.value?.[0] || ''
    query.endDate = dateRange.value?.[1] || ''
    const res = await attendanceApi.records(query)
    if (res.success) {
      records.value = res.data.records
      total.value = res.data.total
    } else {
      // 业务失败（如越权部门）时清空旧结果，
      // 避免「筛选未生效、列表仍显示所有部门」的假象
      records.value = []
      total.value = 0
    }
  } catch (e) {
    // 网络/HTTP 异常同样清空旧数据（403 等错误提示由 axios 拦截器统一弹出）
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.deptId = null
  query.keyword = ''
  query.status = ''
  dateRange.value = null
  search()
}

function onSizeChange() {
  query.page = 1
  load()
}

function statusTagType(status) {
  return { normal: 'success', late: 'danger', early_leave: 'warning', absent: 'info', pending: 'info' }[status] || 'info'
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 14px;
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 4px;
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 4px;
  margin-right: 12px;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
.late-time {
  color: #f56c6c;
}
.early-time {
  color: #e6a23c;
}
</style>
