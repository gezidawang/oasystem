<template>
  <div class="schedule-page">
    <!-- 说明 -->
    <el-alert type="info" show-icon :closable="false" class="tip"
              title="只为一级部门设置上下班时间：系统不按技术栈划分二级组，若存在子部门也不单独排班，其员工打卡自动沿用所属一级部门的时间；未配置排班的部门无法打卡，会收到明确提示。" />

    <!-- 排班列表 -->
    <el-card shadow="never">
      <el-table :data="schedules" v-loading="loading" border empty-text="暂无部门数据">
        <el-table-column prop="departmentName" label="部门" min-width="140" />
        <el-table-column label="上班时间" min-width="180">
          <template #default="{ row }">
            <el-time-select v-model="row.workStartTime" start="06:00" step="00:15" end="12:00"
                            placeholder="选择上班时间" style="width: 150px" />
          </template>
        </el-table-column>
        <el-table-column label="下班时间" min-width="180">
          <template #default="{ row }">
            <el-time-select v-model="row.workEndTime" start="15:00" step="00:15" end="23:30"
                            placeholder="选择下班时间" style="width: 150px" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hasSchedule ? 'success' : 'info'" size="small" effect="light">
              {{ row.hasSchedule ? '已配置' : '默认时间' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button type="primary" link :loading="row._saving" @click="save(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { attendanceApi } from '@/api/attendance'

const loading = ref(false)
// 排班行：{ departmentId, departmentName, workStartTime:'HH:mm', workEndTime:'HH:mm', hasSchedule }
const schedules = ref([])

onMounted(() => load())

async function load() {
  loading.value = true
  try {
    const res = await attendanceApi.schedules()
    if (res.success) {
      schedules.value = (res.data || []).map((s) => ({
        ...s,
        // 未配置排班的一级部门，编辑框里预填建议时间，方便管理员微调后保存
        workStartTime: s.workStartTime || '09:00',
        workEndTime: s.workEndTime || '18:00',
        _saving: false,
      }))
    } else {
      ElMessage.error(res.message || '获取排班失败')
    }
  } finally {
    loading.value = false
  }
}

async function save(row) {
  if (!row.workStartTime || !row.workEndTime) {
    ElMessage.warning('请先选择上班和下班时间')
    return
  }
  if (row.workStartTime >= row.workEndTime) {
    ElMessage.warning('上班时间必须早于下班时间')
    return
  }
  row._saving = true
  try {
    const res = await attendanceApi.saveSchedule({
      departmentId: row.departmentId,
      workStartTime: row.workStartTime,
      workEndTime: row.workEndTime,
    })
    if (res.success) {
      row.hasSchedule = true
      ElMessage.success(`${row.departmentName} 排班已保存：${row.workStartTime} - ${row.workEndTime}`)
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } finally {
    row._saving = false
  }
}
</script>

<style scoped>
.tip {
  margin-bottom: 14px;
}
</style>
