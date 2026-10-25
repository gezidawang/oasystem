<template>
  <div class="page-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <h3 class="page-title">部门管理</h3>
      <!-- 体验层收敛（2026-10-24 DOA）：写操作归 org:manage 持有者（人事）；老板已剥码只剩查看，服务端双闸门为唯一权威 -->
      <el-button v-if="canOperate" type="primary" @click="openDialog()">新增部门</el-button>
    </div>

    <!-- 树形表格 -->
    <el-table
      :data="treeData"
      row-key="id"
      :tree-props="{ children: 'children' }"
      border
      v-loading="loading"
      class="dept-table"
    >
      <el-table-column prop="name" label="部门名称" min-width="200">
        <template #default="{ row }">
          {{ row.name }}
          <el-tag v-if="row.confidential === 1" size="small" type="danger" effect="plain" style="margin-left:6px">保密隔离</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="字母代码" width="90" align="center" />
      <el-table-column prop="managerName" label="主管" width="100" />
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canOperate" label="操作" width="180" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button
            v-if="row.status === 1"
            link
            type="danger"
            size="small"
            @click="handleDisable(row)"
          >停用</el-button>
          <el-button
            v-if="row.status === 0"
            link
            type="success"
            size="small"
            @click="handleEnable(row)"
          >启用</el-button>
          <el-button
            v-if="row.status === 0"
            link
            type="danger"
            size="small"
            @click="handleDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑部门' : '新增部门'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" placeholder="如 技术部" />
        </el-form-item>
        <el-form-item label="字母代码" prop="code">
          <el-input v-model="form.code" maxlength="1" placeholder="A-Z（工号首字母）" style="width:100px" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="form.parentId" clearable placeholder="无（一级部门）">
            <el-option
              v-for="d in flatOptions"
              :key="d.id"
              :label="d.parentId ? '　└ ' + d.name : d.name"
              :value="d.id"
              :disabled="d.id === form.id"
            />
          </el-select>
        </el-form-item>
        <!-- 保密隔离：治理开关，仅老板/行政管理层可改 -->
        <el-form-item v-if="canSetConfidential" label="保密隔离">
          <el-switch v-model="form.confidential" :active-value="1" :inactive-value="0" />
          <span class="conf-tip">开启后：该部门内部公告仅本部门+老板可见可操作，行政不可见</span>
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
import { departmentApi } from '@/api/departments'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
// 建制写操作（新增/编辑/启停/删除）：归 org:manage 码持有者（人事总监 W3）；
// 2026-10-24 DOA 收敛后老板不再持码，此开关仅体验层，服务端码+职级双闸门为唯一权威
const canOperate = computed(() => store.hasPermission('org:manage'))
// 保密隔离开关仅老板/行政管理层可见可改（后端同步拦截）
const canSetConfidential = computed(() =>
  store.roleCode === 'super_admin' || store.hasPermission('notice:manage:all'))

const loading = ref(false)
const treeData = ref([])
const flatOptions = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = ref({ id: null, name: '', code: '', parentId: null, managerId: null, confidential: 0 })
const rules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入字母代码', trigger: 'blur' },
    { pattern: /^[A-Z]$/, message: '必须为单个大写字母', trigger: 'blur' },
  ],
}

onMounted(() => loadTree())

async function loadTree() {
  loading.value = true
  try {
    const res = await departmentApi.tree()
    if (res.success) {
      treeData.value = res.data
      flatOptions.value = flatten(res.data)
    }
  } finally {
    loading.value = false
  }
}

function flatten(nodes, list = []) {
  for (const n of nodes) {
    list.push({ id: n.id, name: n.name, parentId: n.parentId })
    if (n.children?.length) flatten(n.children, list)
  }
  return list
}

function openDialog(row) {
  if (row) {
    form.value = { id: row.id, name: row.name, code: row.code, parentId: row.parentId, managerId: row.managerId, confidential: row.confidential || 0 }
  } else {
    form.value = { id: null, name: '', code: '', parentId: null, managerId: null, confidential: 0 }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = { name: form.value.name, code: form.value.code, parentId: form.value.parentId, managerId: form.value.managerId, confidential: form.value.confidential }
    const res = form.value.id
      ? await departmentApi.update(form.value.id, payload)
      : await departmentApi.create(payload)
    if (res.success) {
      ElMessage.success(res.message)
      dialogVisible.value = false
      loadTree()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}

async function handleDisable(row) {
  await ElMessageBox.confirm(`确认停用「${row.name}」？`, '提示', { type: 'warning' })
  const res = await departmentApi.disable(row.id)
  if (res.success) {
    ElMessage.success(res.message)
    loadTree()
  } else {
    ElMessage.error(res.message)
  }
}

async function handleEnable(row) {
  const res = await departmentApi.enable(row.id)
  if (res.success) {
    ElMessage.success(res.message + '（删除计时已重置）')
    loadTree()
  } else {
    ElMessage.error(res.message)
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确认彻底删除「${row.name}」？\n注意：只有停用超过一个月的部门才允许删除，删除后不可恢复。`,
    '危险操作',
    { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  const res = await departmentApi.delete(row.id)
  if (res.success) {
    ElMessage.success(res.message)
    loadTree()
  } else {
    ElMessage.error(res.message)
  }
}
</script>

<style scoped>
.page-container { padding: 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 18px; color: #303133; }
.dept-table { background: #fff; }
.conf-tip { margin-left: 12px; font-size: 12px; color: #8896a7; }
</style>
