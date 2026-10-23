<template>
  <div class="page-container">
    <div class="toolbar">
      <h3 class="page-title">编制申请</h3>
      <el-button v-if="canSubmit" type="primary" @click="openDialog()">提交申请</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon class="rule-alert">
      编制变更三段式治理：部门负责人/主管提交申请 → 总经理审批 → 人事执行建制。审批通过前不改动组织架构。
    </el-alert>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.type)" effect="plain">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="内容" min-width="180">
        <template #default="{ row }">
          <template v-if="row.type === 'create'">增设「{{ row.name }} / {{ row.code }}」</template>
          <template v-else>{{ typeLabel(row.type) }}「{{ row.deptName || row.targetDeptId }}」<template v-if="row.type === 'update'"> → {{ row.name }} / {{ row.code }}</template></template>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="申请理由" min-width="180" show-overflow-tooltip />
      <el-table-column prop="applicantName" label="申请人" width="100" align="center" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusColor(row.status)">{{ statusLabel(row.status) }}</el-tag>
          <div v-if="row.status === 'rejected' && row.rejectReason" class="reject-tip">原因：{{ row.rejectReason }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="approverName" label="审批人" width="100" align="center">
        <template #default="{ row }">{{ row.approverName || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" align="center" class-name="action-col">
        <template #default="{ row }">
          <div class="action-btns">
            <el-button v-if="canApprove && row.status === 'pending'" link type="success" size="small" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="canApprove && row.status === 'pending'" link type="danger" size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="canExecute && row.status === 'approved'" link type="primary" size="small" @click="handleExecute(row)">执行建制</el-button>
            <span v-if="!((canApprove && row.status === 'pending') || (canExecute && row.status === 'approved'))" class="no-action">—</span>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="total, prev, pager, next" @current-change="loadList" />
    </div>

    <!-- 提交申请弹窗 -->
    <el-dialog v-model="dialogVisible" title="提交编制变更申请" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="申请类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio value="create">增设部门</el-radio>
            <el-radio value="disable">撤编停用</el-radio>
            <el-radio value="update">调整部门</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type !== 'create'" label="目标部门" prop="targetDeptId">
          <el-select v-model="form.targetDeptId" placeholder="选择要调整/撤编的部门" style="width:100%">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <template v-if="form.type !== 'disable'">
          <el-form-item label="部门名称" prop="name">
            <el-input v-model="form.name" placeholder="如 研发一组" />
          </el-form-item>
          <el-form-item label="字母代码" prop="code">
            <el-input v-model="form.code" maxlength="1" placeholder="A-Z（工号首字母）" style="width:100px" />
          </el-form-item>
          <el-form-item label="上级部门">
            <el-select v-model="form.parentId" clearable :placeholder="canScopeAll ? '无（一级部门）' : '自己管辖范围内'" style="width:100%">
              <el-option v-for="d in deptOptions" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
            <div v-if="!canScopeAll" class="scope-tip">只能申请自己管辖范围内的部门变更；设立一级部门请提报总经理/人事</div>
          </el-form-item>
        </template>
        <el-form-item label="申请理由" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="必填：说明业务需要（审批人据此判断）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deptRequestApi } from '@/api/deptRequests'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
// 提交权：持 dept:manage:request（角色授权）+ 后端 W2 职级双闸门
const canSubmit = computed(() => store.hasPermission('dept:manage:request'))
// 审批/执行能力由服务端列表接口下发（后端权威，前端仅控按钮显示）
const canApprove = ref(false)
const canExecute = ref(false)

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const list = ref([])
const total = ref(0)
const page = ref(1)
// 可申请部门（服务端按岗位数据范围下发）：只能申请自己管辖范围内的编制变更，防越权提案
const deptOptions = ref([])
// 能否申请一级部门（仅总经理/人事，公司级事项）
const canScopeAll = ref(false)

const form = ref({ type: 'create', targetDeptId: null, name: '', code: '', parentId: null, reason: '' })
const rules = {
  type: [{ required: true, message: '请选择申请类型', trigger: 'change' }],
  reason: [{ required: true, message: '请填写申请理由', trigger: 'blur' }],
}

onMounted(() => { loadList() })

async function loadList() {
  loading.value = true
  try {
    const res = await deptRequestApi.list({ page: page.value, size: 10 })
    if (res.success) {
      list.value = res.data.records
      total.value = res.data.total
      canApprove.value = !!res.data.canApprove
      canExecute.value = !!res.data.canExecute
      deptOptions.value = res.data.deptOptions || []
      canScopeAll.value = !!res.data.canScopeAll
    }
  } finally {
    loading.value = false
  }
}

function openDialog() {
  form.value = { type: 'create', targetDeptId: null, name: '', code: '', parentId: null, reason: '' }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // 类型相关字段的必填校验（表单动态字段，统一在此兜底提示）
  const f = form.value
  if (f.type === 'create' && (!f.name || !f.code)) { ElMessage.warning('增设部门需填写名称与字母代码'); return }
  if (f.type === 'disable' && !f.targetDeptId) { ElMessage.warning('撤编需选择目标部门'); return }
  if (f.type === 'update' && (!f.targetDeptId || !f.name || !f.code)) { ElMessage.warning('调整需选择目标部门并填写新名称与字母'); return }
  submitting.value = true
  try {
    const res = await deptRequestApi.submit({ ...f, parentId: f.parentId ?? null })
    if (res.success) {
      ElMessage.success(res.message)
      dialogVisible.value = false
      loadList()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}

async function handleApprove(row) {
  await ElMessageBox.confirm(`确认通过该编制申请？通过后由人事执行建制。`, '审批')
  const res = await deptRequestApi.approve(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleReject(row) {
  let value
  try {
    ({ value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      inputPlaceholder: '原因（必填）',
      inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空'),
      customClass: 'nm-msg',
    }))
  } catch {
    return // 取消
  }
  const res = await deptRequestApi.reject(row.id, value.trim())
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleExecute(row) {
  await ElMessageBox.confirm(`确认执行建制？将按申请内容真实变更组织架构。`, '执行建制', { type: 'warning' })
  const res = await deptRequestApi.execute(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
  else ElMessage.error(res.message)
}

function typeLabel(t) { return { create: '增设', disable: '撤编', update: '调整' }[t] || t }
function typeTag(t) { return { create: 'success', disable: 'danger', update: 'warning' }[t] || 'info' }
function statusLabel(s) { return { pending: '待审批', approved: '待执行', rejected: '未通过', done: '已完成' }[s] || s }
function statusColor(s) { return { pending: 'warning', approved: 'primary', rejected: 'danger', done: 'success' }[s] || 'info' }
</script>

<style scoped>
.page-container { padding: 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-title { margin: 0; font-size: 18px; line-height: 28px; color: #303133; font-weight: 600; }
.rule-alert { margin-bottom: 14px; }
.reject-tip { margin-top: 4px; font-size: 12px; color: #c45656; }
.scope-tip { margin-top: 4px; font-size: 12px; color: #8896a7; line-height: 1.5; }
.no-action { color: #c0c4cc; }
.action-btns { display: flex; align-items: center; justify-content: center; gap: 8px; white-space: nowrap; }
.action-btns .el-button { margin-left: 0; min-width: 48px; justify-content: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
:deep(.action-col .cell) { display: flex; align-items: center; justify-content: center; padding-left: 8px; padding-right: 8px; }
</style>
