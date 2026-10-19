<template>
  <div class="checkin-page">
    <!-- 兑底：无打卡权限的角色 -->
    <el-card v-if="!canPunch || punchable === false" shadow="never">
      <el-empty :description="punchMessage || '该角色无需打卡'">
        <template #image>
          <el-icon :size="60" color="#2b6cb0"><Postcard /></el-icon>
        </template>
      </el-empty>
    </el-card>

    <template v-else>
      <!-- 实时时钟 + 排班信息 -->
      <el-card shadow="never" class="clock-card">
        <div class="clock-time">{{ clock }}</div>
        <div class="clock-meta">
          <div class="meta-row">
            <span class="meta-label">日期</span>
            <span class="meta-value">{{ todayInfo.workDate || '加载中...' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">部门</span>
            <span class="meta-value">{{ todayInfo.departmentName || '' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">排班</span>
            <span class="meta-value">
              {{ todayInfo.workStartTime || '--:--' }} — {{ todayInfo.workEndTime || '--:--' }}
              <el-tag v-if="todayInfo.hasSchedule === false" size="small" type="info" effect="plain">
                默认
              </el-tag>
            </span>
          </div>
        </div>
      </el-card>

      <!-- 打卡记录 + 操作按钮 -->
      <el-card shadow="never" class="action-card">
        <div class="punch-title">今日打卡记录</div>
        <div class="punch-table">
          <!-- 上班行 -->
          <div class="punch-row">
            <span class="row-label">上班</span>
            <span class="row-time">
              <template v-if="record.checkInTime">{{ record.checkInTime }}</template>
              <span v-else class="muted">尚未打卡</span>
            </span>
            <span class="row-status">
              <el-tag v-if="record.checkInTime" size="small" :type="statusTagType(record.status)" effect="light">
                {{ record.statusText }}
              </el-tag>
            </span>
          </div>
          <!-- 下班行 -->
          <div class="punch-row">
            <span class="row-label">下班</span>
            <span class="row-time">
              <template v-if="record.checkOutTime">{{ record.checkOutTime }}</template>
              <span v-else class="muted">{{ record.checkInTime ? '尚未打卡' : '—' }}</span>
            </span>
            <span class="row-status">
              <el-tag v-if="record.checkOutTime" size="small" type="success" effect="light">
                已完成
              </el-tag>
            </span>
          </div>
        </div>
        <!-- 统一打卡按钮：居中 -->
        <div class="punch-action">
          <el-button
            v-perm="'attendance:check:own'"
            :type="punchButtonType"
            size="large"
            :loading="loading"
            :disabled="allDone"
            class="punch-btn"
            @click="onPunch"
          >
            {{ punchLabel }}
          </el-button>
        </div>
      </el-card>

      <!-- 打卡结果提示 -->
      <el-alert v-if="lastMessage" :title="lastMessage" :type="lastMessageType" show-icon :closable="false" class="result-alert" />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { attendanceApi } from '@/api/attendance'
import { useUserStore } from '@/stores/user'

const store = useUserStore()

const canPunch = computed(() => store.hasPermission('attendance:check:own'))
const punchable = ref(true)
const punchMessage = ref('')

const todayInfo = reactive({
  workDate: '',
  departmentName: '',
  workStartTime: '',
  workEndTime: '',
  hasSchedule: true,
})
const record = reactive({ checkInTime: null, checkOutTime: null, status: '', statusText: '' })
const loading = ref(false)
const lastMessage = ref('')
const lastMessageType = ref('success')

const now = ref(new Date())
let timer = null
const clock = computed(() =>
  now.value.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' }))

onMounted(() => {
  if (canPunch.value) loadToday()
  timer = setInterval(() => { now.value = new Date() }, 1000)
})
onUnmounted(() => clearInterval(timer))

async function loadToday() {
  const res = await attendanceApi.today()
  if (!res.success) {
    ElMessage.error(res.message || '获取打卡信息失败')
    return
  }
  Object.assign(todayInfo, {
    workDate: res.data.workDate,
    departmentName: res.data.departmentName,
    workStartTime: res.data.workStartTime,
    workEndTime: res.data.workEndTime,
    hasSchedule: res.data.hasSchedule,
  })
  if (res.data.punchable === false) {
    punchable.value = false
    punchMessage.value = res.data.punchMessage || '该角色无需打卡'
    return
  }
  Object.assign(record, res.data.record || { checkInTime: null, checkOutTime: null, status: '', statusText: '' })
}

const allDone = computed(() => !!record.checkOutTime)
const punchLabel = computed(() => {
  if (record.checkOutTime) return '今日打卡已完成'
  return record.checkInTime ? '下班打卡' : '上班打卡'
})
const punchButtonType = computed(() => {
  if (record.checkOutTime) return 'info'
  return record.checkInTime ? 'success' : 'primary'
})

async function onPunch() {
  loading.value = true
  try {
    const res = await attendanceApi.punch()
    if (res.success) {
      const warn = res.data.late || res.data.status === 'early_leave'
      lastMessageType.value = warn ? 'warning' : 'success'
      lastMessage.value = res.data.message
      ElMessage({ message: res.data.message, type: warn ? 'warning' : 'success' })
      await loadToday()
    } else {
      ElMessage.error(res.message || '打卡失败')
    }
  } finally {
    loading.value = false
  }
}

function statusTagType(status) {
  return { normal: 'success', late: 'danger', early_leave: 'warning', absent: 'info' }[status] || 'info'
}
</script>

<style scoped>
.checkin-page {
  max-width: 680px;
  margin: 0 auto;
  padding: 20px 16px;
}

/* ===== 时钟卡片 ===== */
.clock-card {
  text-align: center;
  background: linear-gradient(135deg, #ffffff, #eef6ff);
  border: 1px solid #e6eef7;
  margin-bottom: 16px;
}
.clock-time {
  font-size: 48px;
  font-weight: 600;
  color: #1f2d3d;
  font-variant-numeric: tabular-nums;
  letter-spacing: 1px;
}
.clock-meta {
  margin-top: 12px;
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.meta-label {
  color: #8896a7;
  width: 36px;
  text-align: right;
  flex-shrink: 0;
}
.meta-value {
  color: #4b5563;
  text-align: left;
}

/* ===== 打卡记录卡片 ===== */
.action-card {
  border: 1px solid #e6eef7;
}
.punch-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f4f8;
}
.punch-table {
  margin-bottom: 20px;
}
.punch-row {
  display: grid;
  grid-template-columns: 50px 1fr 80px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f4f8;
  font-size: 14px;
}
.punch-row:last-child {
  border-bottom: none;
}
.row-label {
  color: #6b7a90;
  font-weight: 500;
}
.row-time {
  color: #1f2d3d;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.row-status {
  text-align: right;
}
.muted {
  color: #9aa5b1;
  font-weight: 400;
}

/* ===== 打卡按钮 ===== */
.punch-action {
  text-align: center;
  padding-top: 6px;
}
.punch-btn {
  width: 220px;
}

/* ===== 结果提示 ===== */
.result-alert {
  margin-top: 16px;
}
</style>
