<template>
  <div class="notice-edit" v-loading="pageLoading">
    <el-page-header @back="handleBack" :content="isEdit ? '编辑公告' : '新建公告'" class="edit-header" />

    <el-card shadow="never" class="edit-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" :disabled="editLocked">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入公告标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <!-- 普通员工（无 notice:create）不能发也不能申请全员公告 -->
            <el-radio v-if="canChooseCompany" value="all">全员公告</el-radio>
            <el-radio value="department">部门公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- 部门选择：仅直接发布权限的人可见（行政/老板可发给任意部门） -->
        <el-form-item v-if="form.type === 'department' && perm.canDirectCompany" label="目标部门" prop="departmentId">
          <el-select v-model="form.departmentId" placeholder="请选择部门" style="width:200px">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <!-- <el-form-item v-if="form.type === 'department' && !perm.canDirectCompany" label="发布部门">
          <span class="dept-readonly">{{ myDeptName || '本部门（自动绑定）' }}</span> 
        </el-form-item>
        <置顶功能已移至「公告管理」页面操作 --> 
        <el-form-item label="正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="请输入公告内容（支持 HTML）" />
        </el-form-item>
      </el-form>

      <!-- 编辑窗口锁定提示 -->
      <el-alert v-if="editLocked" type="warning" :closable="false" show-icon class="rule-alert">
        该公告已超过发布后 4 小时编辑窗口，内容已锁定不可修改。
      </el-alert>

      <!-- 操作按钮区 -->
      <div class="action-bar" v-if="!editLocked">
        <div class="action-buttons">
          <el-button @click="handleSaveDraft" :loading="submitting">存为草稿</el-button>
          <el-button :type="primaryBtnType" size="large" :loading="submitting" @click="handleSubmit">
            {{ primaryBtnLabel }}
          </el-button>
        </div>
        <!-- 规则提醒 -->
        <div class="rule-tips">
          <p>· 老板/行政/人事可直接发布全员公告；其他角色发布全员公告需行政审批</p>
          <p>· 部门负责人可直接发布本部门公告；普通员工需提交申请</p>
          <p>· 公告发布后 <b>4 小时内</b> 可二次编辑，超时锁定</p>
          <p>· 草稿保留 <b>10 天</b>，超期自动清除</p>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noticeApi } from '@/api/notices'
import { departmentApi } from '@/api/departments'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const pageLoading = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const dirty = ref(false)
const editLocked = ref(false)

// 发布权限 + 置顶权限
const store = useUserStore()
const perm = reactive({ canDirectCompany: false, canDirectDept: false, canPinCompany: false, canPinDept: false })
// 能否选择/申请全员公告：有 notice:create 的（领导/行政/人事/老板）才可以；普通员工只能部门公告
const canChooseCompany = computed(() => store.hasPermission('notice:create'))
const deptOptions = ref([])
const myDeptName = ref('')

const form = reactive({ title: '', content: '', type: store.hasPermission('notice:create') ? 'all' : 'department', departmentId: null, status: 'draft' })
// 本地暂存 key：按用户 + 新建/编辑目标隔离，刷新/崩页后可恢复
const draftKey = `notice_edit_${store.userId || 'anon'}_${route.params.id || 'new'}`
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }],
}

// 按钮形态
const primaryBtnLabel = computed(() => {
  if (form.type === 'all' && !perm.canDirectCompany) return '提交申请'
  if (form.type === 'department' && !perm.canDirectDept && !perm.canDirectCompany) return '提交申请'
  return '发布'
})
const primaryBtnType = computed(() => primaryBtnLabel.value === '发布' ? 'primary' : 'warning')

// 置顶开关可见性：全员公告看 canPinCompany，部门公告看 canPinDept/canPinCompany
const showPinSwitch = computed(() => {
  if (form.type === 'all') return perm.canPinCompany
  return perm.canPinCompany || perm.canPinDept
})

onMounted(async () => {
  pageLoading.value = true
  try {
    const p = await noticeApi.publishPermission()
    if (p.success) Object.assign(perm, p.data)
    // 加载部门列表（行政/老板发部门公告时选择用）；保密隔离部门仅本部门成员可选（后端同步拦截）
    const d = await departmentApi.tree()
    if (d.success) {
      const myDeptId = store.userInfo?.departmentId
      deptOptions.value = d.data
        .filter(x => x.status === 1 && (x.confidential !== 1 || x.id === myDeptId))
        .map(x => ({ id: x.id, name: x.name }))
    }
    // 获取当前用户部门名（非管理员展示用）
    myDeptName.value = store.departmentName || ''
    // 编辑模式：加载已有数据
    if (route.params.id) {
      const res = await noticeApi.detail(route.params.id)
      if (res.success) {
        Object.assign(form, res.data)
        if (res.data.status === 'published') {
          const deadline = new Date(res.data.approvedAt || res.data.publishAt)
          deadline.setHours(deadline.getHours() + 4)
          if (new Date() > deadline) editLocked.value = true
        }
      }
    }
  } finally {
    pageLoading.value = false
  }
  // 恢复本地暂存（仅新建场景；编辑已有公告以服务端为准，避免旧暂存覆盖新加载内容）
  restoreLocalDraft()
  // 拦截刷新/关闭标签页
  window.addEventListener('beforeunload', beforeUnloadHandler)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnloadHandler)
  clearTimeout(saveTimer)
})

/** 浏览器刷新/关闭拦截：有未提交内容时弹原生确认 */
function beforeUnloadHandler(e) {
  if (dirty.value && !editLocked.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

/** 表单变化 → 标脏 + 防抖 1s 自动存 localStorage */
let saveTimer = null
watch(form, () => {
  if (editLocked.value) return
  dirty.value = true
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    try { localStorage.setItem(draftKey, JSON.stringify(form)) } catch (e) { /* 存满则忽略 */ }
  }, 1000)
}, { deep: true })

/** 进入页面时若有本地暂存 → 询问是否恢复 */
async function restoreLocalDraft() {
  if (route.params.id) return
  const cached = localStorage.getItem(draftKey)
  if (!cached) return
  try {
    const data = JSON.parse(cached)
    if (!data.title && !data.content) { localStorage.removeItem(draftKey); return }
    await ElMessageBox.confirm('检测到上次未提交的公告内容，是否恢复？', '恢复草稿',
      { confirmButtonText: '恢复', cancelButtonText: '丢弃', type: 'info', distinguishCancelAndClose: true })
    Object.assign(form, data)
    dirty.value = true
  } catch (action) {
    if (action === 'cancel') localStorage.removeItem(draftKey) // 点“丢弃”才删；关闭弹窗保留暂存
  }
}

// 退出提示（路由守卫）
onBeforeRouteLeave(async (to, from, next) => {
  if (!dirty.value || editLocked.value) return next()
  try {
    await ElMessageBox.confirm(
      '公告尚未提交，是否保存为草稿？',
      '离开确认',
      { confirmButtonText: '存为草稿', cancelButtonText: '直接离开', type: 'warning', distinguishCancelAndClose: true }
    )
    await doSaveDraft()
    next()
  } catch (action) {
    if (action === 'cancel') {
      // 直接离开不保存
      next()
    } else {
      next(false) // 关闭弹窗留在页面
    }
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // 二次确认
  const label = primaryBtnLabel.value
  await ElMessageBox.confirm(
    label === '发布' ? '确认发布本公告？' : '确认提交审批申请？',
    '二次确认',
    { type: 'info' }
  )
  submitting.value = true
  try {
    let res
    if (isEdit.value) {
      res = await noticeApi.update(route.params.id, form)
    } else {
      res = await noticeApi.create(form, false)
    }
    if (res.success) {
      dirty.value = false
      localStorage.removeItem(draftKey)
      ElMessage.success(res.message)
      router.push('/notices')
    } else {
      ElMessage.error(res.message)
    }
  } finally {
    submitting.value = false
  }
}

async function handleSaveDraft() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  await doSaveDraft()
}

async function doSaveDraft() {
  submitting.value = true
  try {
    const res = await noticeApi.create(form, true)
    if (res.success) {
      dirty.value = false
      localStorage.removeItem(draftKey)
      ElMessage.success('已保存草稿')
      router.push('/notices')
    }
  } finally {
    submitting.value = false
  }
}

function handleBack() {
  router.back()
}
</script>

<style scoped>
.notice-edit { max-width: 800px; margin: 0 auto; padding: 20px; }
.edit-header { margin-bottom: 16px; }
.edit-card { border: 1px solid #e6eef7; }
.action-bar { margin-top: 20px; padding-top: 16px; border-top: 1px solid #f0f4f8; }
.action-buttons { display: flex; gap: 12px; justify-content: flex-end; margin-bottom: 16px; }
.rule-tips { font-size: 12px; color: #8896a7; line-height: 1.8; }
.rule-tips p { margin: 0; }
.rule-alert { margin-top: 16px; }
.pin-tip { margin-left: 12px; font-size: 12px; color: #8896a7; }
.dept-readonly { color: #4b5563; font-size: 14px; }
</style>
