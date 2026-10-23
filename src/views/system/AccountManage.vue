<template>
  <div class="account-manage">
    <!-- 筛选栏：工号/姓名搜索 + 部门 + 账号状态 -->
    <div class="toolbar">
      <el-input v-model="filters.keyword" placeholder="工号 / 姓名" clearable style="width: 180px" @keyup.enter="reload" />
      <el-select v-model="filters.departmentId" placeholder="部门" clearable style="width: 150px">
        <el-option v-for="d in deptOptions" :key="d.id" :label="d.name" :value="d.id" />
      </el-select>
      <el-select v-model="filters.status" placeholder="账号状态" clearable style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="reload">查询</el-button>
      <div class="spacer" />
      <el-button type="success" @click="openCreate">员工建档</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="employeeNo" label="工号" width="110" />
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column prop="departmentName" label="部门" width="120" />
      <el-table-column label="岗位" width="140">
        <template #default="{ row }">
          {{ row.positionName }}<span v-if="row.positionLevel" class="lv">（W{{ row.positionLevel }}）</span>
        </template>
      </el-table-column>
      <el-table-column prop="roleName" label="角色" width="110" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="在职状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.employeeStatus === '离职' ? 'danger' : row.employeeStatus === '在职' ? 'success' : 'warning'">
            {{ row.employeeStatus || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最近登录" min-width="150" :formatter="fmtTime" />
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 1" link type="warning" @click="toggleStatus(row, 0)">停用</el-button>
          <el-button v-else link type="success" @click="toggleStatus(row, 1)">启用</el-button>
          <el-button link type="primary" @click="resetPwd(row)">重置密码</el-button>
          <el-button link type="info" @click="unlock(row)">解锁</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, sizes, prev, pager, next" :total="total"
      v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]"
      @current-change="loadList" @size-change="reload" />

    <!-- 建档/编辑弹窗（编辑态锁定部门/岗位/角色：调岗归区域四异动流程，防绕过工号规则） -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑员工信息' : '员工建档（工号自动生成）'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="form.realName" maxlength="50" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio><el-radio value="女">女</el-radio><el-radio value="未设置">未设置</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄"><el-input-number v-model="form.age" :min="16" :max="80" /></el-form-item>
        <el-form-item label="手机号" :required="!form.id">
          <el-input v-model="form.phone" maxlength="11" placeholder="11位数字（加密存储，列表掩码展示）" />
        </el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="部门" required>
          <el-select v-model="form.departmentId" :disabled="!!form.id" style="width: 100%" @change="form.positionId = null">
            <el-option v-for="d in deptOptions" :key="d.id" :label="`${d.name}（${d.code}）`" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位" required>
          <el-select v-model="form.positionId" :disabled="!!form.id" style="width: 100%">
            <el-option v-for="p in formPositions" :key="p.id" :label="`${p.name}（W${p.level}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.roleId" :disabled="!!form.id" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.id" label="在职状态">
          <el-select v-model="form.employeeStatus" style="width: 100%">
            <el-option label="试用" value="试用" /><el-option label="在职" value="在职" /><el-option label="离职（联动停用账号）" value="离职" />
          </el-select>
        </el-form-item>
        <el-form-item label="工号预览">
          <span class="no-preview">{{ employeeNoPreview }}</span>
          <span class="tip">（部门字母 + 权重字母 + 5位全局流水，服务端发号为准）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 一次性初始密码展示（库内为 BCrypt 密文，此处仅本次可见） -->
    <el-dialog v-model="pwdVisible" title="初始密码（仅展示一次，请当面告知本人）" width="420px">
      <el-alert type="warning" :closable="false" show-icon
        :title="`账号 ${pwdInfo.employeeNo || ''} 已创建`" description="员工首次登录后应自行修改密码" />
      <div class="pwd-box">{{ pwdInfo.initialPassword }}</div>
      <template #footer>
        <el-button @click="copyPwd">复制密码</el-button>
        <el-button type="primary" @click="pwdVisible = false">我已告知本人</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api/users'
import { departmentApi } from '@/api/departments'
import { positionApi } from '@/api/positions'
import { roleApi } from '@/api/roles'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const filters = reactive({ keyword: '', departmentId: null, status: null })

const deptOptions = ref([])
const allPositions = ref([])
const roleOptions = ref([])

const formVisible = ref(false)
const form = reactive({ id: null, realName: '', gender: '未设置', age: null, phone: '', email: '', departmentId: null, positionId: null, roleId: null, employeeStatus: '试用' })
const pwdVisible = ref(false)
const pwdInfo = reactive({ employeeNo: '', initialPassword: '' })

// 岗位随部门联动：全局模板岗位（department_id 空，如主管/员工）恒可选；绑定部门的岗位仅本部门可选（后端同口径校验）
const formPositions = computed(() => allPositions.value.filter(p => p.departmentId == null || p.departmentId === form.departmentId))

// 工号预览（仅辅助录入，权威在服务端发号器）：部门字母 + 岗位 weight_code（A=老板专属禁发/G=管理/E=员工）+ 全局流水
const employeeNoPreview = computed(() => {
  const dept = deptOptions.value.find(d => d.id === form.departmentId)
  const pos = allPositions.value.find(p => p.id === form.positionId)
  if (!dept?.code || !pos) return '选择部门与岗位后生成'
  const weight = (pos.weightCode || (pos.level >= 2 ? 'G' : 'E')).toUpperCase()
  if (weight === 'A' || pos.level >= 99) return '不可建档（A 字头为总经理专属）'
  return `${dept.code.toUpperCase()}${weight}#####`
})

function fmtTime(_row, _col, val) {
  return val ? String(val).replace('T', ' ').slice(0, 19) : '—'
}

async function loadList() {
  loading.value = true
  try {
    const res = await userApi.page({
      keyword: filters.keyword || undefined,
      departmentId: filters.departmentId ?? undefined,
      status: filters.status ?? undefined,
      page: page.value,
      size: size.value,
    })
    if (res.success) { list.value = res.data.records; total.value = res.data.total }
  } finally {
    loading.value = false
  }
}

function reload() { page.value = 1; loadList() }

async function loadOptions() {
  const [d, p, r] = await Promise.all([departmentApi.tree(), positionApi.list(), roleApi.list()])
  if (d.success) deptOptions.value = d.data.filter(x => x.status === 1)
  if (p.success) allPositions.value = p.data
  if (r.success) roleOptions.value = r.data
}

function openCreate() {
  Object.assign(form, { id: null, realName: '', gender: '未设置', age: null, phone: '', email: '', departmentId: null, positionId: null, roleId: null, employeeStatus: '试用' })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, realName: row.realName, gender: '未设置', age: null,
    phone: '', email: '', departmentId: row.departmentId, positionId: null, roleId: null,
    employeeStatus: row.employeeStatus || '在职',
  })
  // 编辑态部门/岗位/角色/手机号锁定：调岗走异动流程；手机号留空表示不修改（后端同口径）
  formVisible.value = true
}

async function save() {
  if (!form.realName?.trim()) { ElMessage.warning('请填写姓名'); return }
  if (!form.id && !/^\d{11}$/.test(form.phone || '')) { ElMessage.warning('建档须填写11位手机号'); return }
  if (!form.id && (!form.departmentId || !form.positionId || !form.roleId)) { ElMessage.warning('请选择部门/岗位/角色'); return }
  saving.value = true
  try {
    const payload = {
      realName: form.realName, gender: form.gender, age: form.age,
      phone: form.phone || undefined, email: form.email || undefined,
      departmentId: form.departmentId, positionId: form.positionId, roleId: form.roleId,
      employeeStatus: form.employeeStatus,
    }
    const res = form.id ? await userApi.update(form.id, payload) : await userApi.create(payload)
    if (res.success) {
      formVisible.value = false
      if (!form.id) {
        // 建档成功：一次性展示初始密码（不落任何日志）
        pwdInfo.employeeNo = res.data.employeeNo
        pwdInfo.initialPassword = res.data.initialPassword
        pwdVisible.value = true
      } else {
        ElMessage.success(res.message || '已更新')
      }
      loadList()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } finally {
    saving.value = false
  }
}

function copyPwd() {
  navigator.clipboard?.writeText(pwdInfo.initialPassword)
    .then(() => ElMessage.success('已复制'))
    .catch(() => ElMessage.warning('浏览器不支持自动复制，请手抄'))
}

async function toggleStatus(row, status) {
  const word = status === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(
    status === 1
      ? `确认启用账号「${row.employeeNo} ${row.realName}」？`
      : `确认停用账号「${row.employeeNo} ${row.realName}」？停用后其在线登录态即刻失效。`,
    `${word}账号`)
  const res = await userApi.setStatus(row.id, status)
  if (res.success) { ElMessage.success(res.message); loadList() } else ElMessage.error(res.message)
}

async function resetPwd(row) {
  await ElMessageBox.confirm(`确认重置「${row.employeeNo} ${row.realName}」的密码？旧密码立即失效，同时清除该账号登录锁定。`, '重置密码', { type: 'warning' })
  const res = await userApi.resetPassword(row.id)
  if (res.success) {
    pwdInfo.employeeNo = row.employeeNo
    pwdInfo.initialPassword = res.data.initialPassword
    pwdVisible.value = true
  } else ElMessage.error(res.message)
}

async function unlock(row) {
  const res = await userApi.unlock(row.id)
  if (res.success) ElMessage.success(res.message || '账号已解锁')
  else ElMessage.error(res.message)
}

onMounted(() => { loadOptions(); loadList() })
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; flex-wrap: wrap; }
.spacer { flex: 1; }
.pager { margin-top: 14px; justify-content: flex-end; }
.lv { color: #909399; font-size: 12px; }
.no-preview { font-weight: 600; color: #409eff; letter-spacing: 1px; }
.tip { color: #909399; font-size: 12px; margin-left: 6px; }
.pwd-box { margin: 14px 0 4px; padding: 12px; text-align: center; font-size: 20px; font-weight: 700; letter-spacing: 2px; background: #f5f7fa; border-radius: 6px; user-select: all; }
</style>
