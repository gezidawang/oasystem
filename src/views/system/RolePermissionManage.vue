<template>
  <div class="page-container">
    <div class="toolbar">
      <h3 class="page-title">角色权限管理</h3>
      <el-button type="primary" @click="openRoleDialog()">新增角色</el-button>
    </div>
    <el-alert type="info" :closable="false" show-icon class="rule-alert">
      角色与权限矩阵仅总经理（W4）可维护；权限保存后即时生效（自动清缓存），并留改前/改后快照审计。
    </el-alert>

    <el-table :data="roles" v-loading="loading" border>
      <el-table-column prop="roleName" label="角色名称" min-width="110">
        <template #default="{ row }">
          {{ row.roleName }}
          <el-tag v-if="row.isSystem === 1" size="small" type="info" effect="plain" style="margin-left:4px">内置</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="roleCode" label="编码" width="130" />
      <el-table-column prop="userCount" label="账号数" width="80" align="center" />
      <el-table-column prop="permCount" label="权限数" width="80" align="center" />
      <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openMatrix(row)">权限矩阵</el-button>
          <el-button link type="primary" size="small" @click="openRoleDialog(row)">编辑</el-button>
          <el-button v-if="row.isSystem !== 1" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 角色新增/编辑 -->
    <el-dialog v-model="roleDialogVisible" :title="roleForm.id ? '编辑角色' : '新增角色'" width="440px" destroy-on-close>
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="roleForm.roleCode" :disabled="!!roleForm.id" placeholder="小写字母开头，如 auditor" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="如 审计员" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleRoleSubmit">确认</el-button>
      </template>
    </el-dialog>

    <!-- 权限矩阵 -->
    <el-dialog v-model="matrixVisible" :title="`权限矩阵 - ${matrixRole?.roleName || ''}`" width="720px" destroy-on-close top="6vh">
      <div class="matrix-toolbar">
        <el-input v-model="matrixSearch" placeholder="搜索权限码/名称" clearable style="width:220px" />
        <div>
          <el-button size="small" @click="checkAll(true)">全选</el-button>
          <el-button size="small" @click="checkAll(false)">清空</el-button>
        </div>
      </div>
      <div class="matrix-body" v-loading="matrixLoading">
        <div v-for="group in filteredGroups" :key="group.resource" class="perm-group">
          <div class="group-head">
            <el-checkbox
              :model-value="groupAllChecked(group)"
              :indeterminate="groupIndeterminate(group)"
              @change="(v) => toggleGroup(group, v)"
            >
              <b>{{ group.resource }}</b>（{{ group.items.length }}）
            </el-checkbox>
          </div>
          <div class="group-items">
            <el-checkbox
              v-for="p in group.items"
              :key="p.id"
              :model-value="checked.has(p.id)"
              @change="(v) => toggleOne(p.id, v)"
            >
              {{ p.permName }}
              <span class="code">{{ p.permCode }}</span>
            </el-checkbox>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="count-tip">已选 {{ checked.size }} 项</span>
        <el-button @click="matrixVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleMatrixSave">保存并即时生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roleApi } from '@/api/roles'

const loading = ref(false)
const submitting = ref(false)
const roles = ref([])

const roleDialogVisible = ref(false)
const roleFormRef = ref(null)
const roleForm = ref({ id: null, roleCode: '', roleName: '', description: '' })
const roleRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

const matrixVisible = ref(false)
const matrixLoading = ref(false)
const matrixRole = ref(null)
const allPerms = ref([])
const checked = ref(new Set())
const matrixSearch = ref('')

// 按 resource 分组
const groups = computed(() => {
  const map = new Map()
  for (const p of allPerms.value) {
    if (!map.has(p.resource)) map.set(p.resource, { resource: p.resource, items: [] })
    map.get(p.resource).items.push(p)
  }
  return [...map.values()]
})
const filteredGroups = computed(() => {
  const kw = matrixSearch.value.trim().toLowerCase()
  if (!kw) return groups.value
  return groups.value
    .map(g => ({ ...g, items: g.items.filter(p => (p.permCode + p.permName).toLowerCase().includes(kw)) }))
    .filter(g => g.items.length)
})

onMounted(() => loadRoles())

async function loadRoles() {
  loading.value = true
  try {
    const res = await roleApi.list()
    if (res.success) roles.value = res.data
    else ElMessage.error(res.message)
  } finally {
    loading.value = false
  }
}

function openRoleDialog(row) {
  roleForm.value = row
    ? { id: row.id, roleCode: row.roleCode, roleName: row.roleName, description: row.description }
    : { id: null, roleCode: '', roleName: '', description: '' }
  roleDialogVisible.value = true
}

async function handleRoleSubmit() {
  const valid = await roleFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const res = roleForm.value.id
      ? await roleApi.update(roleForm.value.id, roleForm.value)
      : await roleApi.create(roleForm.value)
    if (res.success) {
      ElMessage.success(res.message)
      roleDialogVisible.value = false
      loadRoles()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」？`, '危险操作', { type: 'error' })
  const res = await roleApi.delete(row.id)
  if (res.success) { ElMessage.success(res.message); loadRoles() }
  else ElMessage.error(res.message)
}

async function openMatrix(row) {
  matrixRole.value = row
  matrixSearch.value = ''
  matrixVisible.value = true
  matrixLoading.value = true
  try {
    const [permsRes, ownRes] = await Promise.all([roleApi.allPermissions(), roleApi.rolePermissions(row.id)])
    if (permsRes.success && ownRes.success) {
      allPerms.value = permsRes.data
      checked.value = new Set(ownRes.data)
    }
  } finally {
    matrixLoading.value = false
  }
}

function toggleOne(id, val) {
  const s = new Set(checked.value)
  val ? s.add(id) : s.delete(id)
  checked.value = s
}

function toggleGroup(group, val) {
  const s = new Set(checked.value)
  for (const p of group.items) val ? s.add(p.id) : s.delete(p.id)
  checked.value = s
}

function groupAllChecked(group) {
  return group.items.every(p => checked.value.has(p.id))
}
function groupIndeterminate(group) {
  const hit = group.items.filter(p => checked.value.has(p.id)).length
  return hit > 0 && hit < group.items.length
}

function checkAll(val) {
  checked.value = val ? new Set(allPerms.value.map(p => p.id)) : new Set()
}

async function handleMatrixSave() {
  await ElMessageBox.confirm(
    `确认保存「${matrixRole.value.roleName}」的权限矩阵？保存后该角色所有账号权限即时生效。`,
    '敏感操作确认', { type: 'warning' })
  submitting.value = true
  try {
    const res = await roleApi.savePermissions(matrixRole.value.id, [...checked.value])
    if (res.success) {
      ElMessage.success(res.message)
      matrixVisible.value = false
      loadRoles()
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-container { padding: 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-title { margin: 0; font-size: 18px; color: #303133; }
.rule-alert { margin-bottom: 14px; }
.matrix-toolbar { display: flex; justify-content: space-between; margin-bottom: 10px; }
.matrix-body { max-height: 55vh; overflow-y: auto; }
.perm-group { margin-bottom: 12px; }
.group-head { background: #f5f9ff; padding: 6px 10px; border-radius: 4px; margin-bottom: 6px; }
.group-items { display: flex; flex-wrap: wrap; gap: 4px 18px; padding-left: 22px; }
.code { color: #a5b3c4; font-size: 12px; }
.count-tip { margin-right: 12px; font-size: 13px; color: #8896a7; }
</style>
