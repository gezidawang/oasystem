<template>
  <div class="page-container">
    <div class="toolbar">
      <h3 class="page-title">岗位管理</h3>
      <el-button v-if="canManage" type="primary" @click="openDialog()">新增岗位</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="name" label="岗位名称" min-width="140" />
      <el-table-column prop="level" label="等级(level)" width="110" align="center" />
      <el-table-column label="权重字母" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="weightTag(row.weightCode)" effect="plain">{{ row.weightCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="departmentName" label="所属部门" width="120" />
      <el-table-column prop="userCount" label="在岗人数" width="90" align="center" />
      <el-table-column v-if="canManage" label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button v-if="isBoss" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑岗位' : '新增岗位'" width="460px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="form.name" placeholder="如 财务主管" />
        </el-form-item>
        <el-form-item label="等级" prop="level">
          <el-input-number v-model="form.level" :min="1" :max="99" />
          <span class="tip">1员工 / 2主管 / 3负责人 / 99总经理</span>
        </el-form-item>
        <el-form-item label="权重字母" prop="weightCode">
          <el-select v-model="form.weightCode" style="width:100px" :disabled="!canEditWeight">
            <el-option label="E（员工）" value="E" />
            <el-option label="G（主管/负责人）" value="G" />
            <el-option label="A（总经理）" value="A" />
          </el-select>
          <span class="tip">{{ canEditWeight ? '工号第二字母来源' : '仅总经理可修改' }}</span>
        </el-form-item>
        <el-form-item label="所属部门">
          <el-select v-model="form.departmentId" clearable placeholder="全公司（通用岗位）">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { positionApi } from '@/api/positions'
import { departmentApi } from '@/api/departments'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
const canManage = computed(() => store.hasPermission('org:manage'))
const isBoss = computed(() => store.roleCode === 'super_admin')
// weight_code 编辑权：仅总经理（后端 W4 铁律兜底）
const canEditWeight = computed(() => isBoss.value)

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const list = ref([])
const deptOptions = ref([])
const form = ref({ id: null, name: '', level: 1, weightCode: 'E', departmentId: null })
const rules = {
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
  level: [{ required: true, message: '请输入等级', trigger: 'blur' }],
  weightCode: [{ required: true, message: '请选择权重字母', trigger: 'change' }],
}

onMounted(() => {
  loadList()
  if (canManage.value) loadDepts()
})

async function loadList() {
  loading.value = true
  try {
    const res = await positionApi.list()
    if (res.success) list.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadDepts() {
  const res = await departmentApi.tree()
  if (res.success) deptOptions.value = res.data.filter(d => d.status === 1).map(d => ({ id: d.id, name: d.name }))
}

function openDialog(row) {
  form.value = row
    ? { id: row.id, name: row.name, level: row.level, weightCode: row.weightCode, departmentId: row.departmentId }
    : { id: null, name: '', level: 1, weightCode: 'E', departmentId: null }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = { ...form.value, departmentId: form.value.departmentId ?? null }
    const res = form.value.id
      ? await positionApi.update(form.value.id, payload)
      : await positionApi.create(payload)
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

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除岗位「${row.name}」？`, '危险操作', { type: 'error' })
  const res = await positionApi.delete(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
  else ElMessage.error(res.message)
}

function weightTag(w) {
  return { A: 'danger', G: 'warning', E: 'info' }[w] || 'info'
}
</script>

<style scoped>
.page-container { padding: 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 18px; color: #303133; }
.tip { margin-left: 10px; font-size: 12px; color: #8896a7; }
</style>
