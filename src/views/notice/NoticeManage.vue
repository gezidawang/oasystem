<template>
  <div class="manage-page">
    <div class="toolbar">
      <h3 class="page-title">{{ isApplicantOnly ? '公告申请' : '公告管理' }}</h3>
      <el-button type="primary" @click="$router.push('/notice/create')">{{ isApplicantOnly ? '申请发布公告' : '发布公告' }}</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="loadList">
      <el-tab-pane v-if="!isApplicantOnly" label="已发布" name="published" />
      <!-- 治理 Tab（读管分离）：行政治理层（manage:all+W3）的跨部门治理入口，宽范围只列可操作行；
           阅读仍按岗位范围收窄，既消除信息干扰又不削弱删/撤/置顶三层口径（数据源 status=published&managed=1） -->
      <el-tab-pane v-if="canManageAll" label="治理" name="governance" />
      <el-tab-pane label="我的草稿" name="draft" />
      <!-- 高权限角色隐藏"我提交的申请"Tab（定案）：能直发全员公告者（publish:company+W3）部门/全员均直发，
           永远不会产生待审批申请，展示空 Tab 徒增困惑；能力以服务端 canDirectCompany 为准 -->
      <el-tab-pane v-if="!canDirectCompany" label="我提交的申请" name="pending" />
      <!-- 待审批 Tab：行政管理层（审全员申请）+ 部门领导层（审本部门申请）分工协作，
           避免部门内审堆积到行政/老板兜底；能力以服务端 publishPermission() 下发为准 -->
      <el-tab-pane v-if="canReview" label="待审批" name="pending_approval" />
      <el-tab-pane label="未通过" name="rejected" />
    </el-tabs>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
      <el-table-column prop="publisherName" label="发布人" width="140" align="center">
        <template #default="{ row }">
          {{ row.publisherName ? (row.type === 'all' && row.publisherDeptName ? `${row.publisherName} / ${row.publisherDeptName}` : row.publisherName) : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusColor(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">{{ row.type === 'all' ? '全员' : '部门' }}</template>
      </el-table-column>
      <el-table-column label="时间" width="150" align="center">
        <template #default="{ row }">{{ formatTime(rowTime(row)) }}</template>
      </el-table-column>
      <!-- 操作列：固定槽位排布——同名按钮跨行上下成列对齐；
           行级显隐用 slot-off（visibility:hidden 保留占位不塌陷），Tab 级用 v-if 整槽增减 -->
      <el-table-column label="操作" width="310" align="center" class-name="action-col">
        <template #default="{ row }">
          <div class="action-btns">
            <el-button link type="primary" size="small" @click="$router.push(`/notice/${row.id}`)"
              :class="{ 'slot-off': activeTab === 'rejected' && row.publisherId !== store.userId }">查看</el-button>
            <!-- 编辑：草稿/驳回/申请中仅本人（二次编辑提交）；已发布态受 4 小时窗口+本人限制（隐藏时保留槽位） -->
            <el-button v-if="activeTab !== 'pending_approval'" :class="{ 'slot-off': !canEdit(row) }"
              link type="primary" size="small" @click="$router.push(`/notice/edit/${row.id}`)">编辑</el-button>
            <!-- 置顶：已发布/治理视图，行级判定（隐藏时保留槽位；槽位加宽兼容'取消置顶'4字） -->
            <el-button v-if="isPublishedView" :class="{ 'slot-off': !canPinRow(row), 'slot-wide': true }"
              link size="small" @click="handleTop(row, row.top ? 0 : 1)">
              {{ row.top ? '取消置顶' : '置顶' }}
            </el-button>
            <el-button v-if="activeTab === 'pending_approval'" link type="success" size="small" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="activeTab === 'pending_approval'" link type="danger" size="small" @click="handleReject(row)">驳回</el-button>
            <!-- 撤回：已发布/治理视图，行级判定（隐藏时保留槽位） -->
            <el-button v-if="isPublishedView" :class="{ 'slot-off': !(row.status === 'published' && canRevoke(row)) }"
              link type="warning" size="small" @click="handleRevoke(row)">撤回</el-button>
            <!-- 原因：仅未通过 Tab 的"我驳回下级"记录显示（本人的详情页内可见，不需要此入口）；只读展示驳回原因 -->
            <el-button v-if="activeTab === 'rejected'" :class="{ 'slot-off': row.publisherId === store.userId }"
              link type="warning" size="small" @click="handleShowReason(row)">原因</el-button>
            <!-- 删除：草稿/驳回/已发布 Tab，行级判定（申请中不可删）；隐藏时保留槽位 -->
            <el-button v-if="activeTab !== 'pending_approval' && activeTab !== 'pending'" :class="{ 'slot-off': !canDelete(row) }"
              link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="total, prev, pager, next" @current-change="loadList" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noticeApi } from '@/api/notices'
import { departmentApi } from '@/api/departments'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
// 待审批 Tab：以服务端 publishPermission() 下发为准（W1 专员即使持公司发布码也不能审批）
const canApprove = ref(false)
// 部门内审审批能力（W3 专属）：主管 W2 无拍板权，只有负责人/行政管理层有审批入口
const canApproveDept = ref(false)
// 审批入口=行政管理层（canApproveCompany，审全员）∪ 部门负责人（canApproveDept，审本部门），分工协作
const canReview = computed(() => canApprove.value || canApproveDept.value)
// 管理越权（删除/撤回他人公告、置顶全员）：行政 W3+；老板无此权。前端仅控按钮显示，实际拦截以后端为准
const canManageAll = computed(() => store.hasPermission('notice:manage:all'))

// 置顶行级判定（与后端 canPin 镜像）：全员置顶仅行政；部门置顶仅本部门领导层（服务端下发）；保密部门行政不可置顶
function canPinRow(row) {
  if (row.status !== 'published') return false
  if (row.type === 'all') return canManageAll.value
  const conf = confDeptIds.value.has(row.departmentId)
  // 链内一体（2026-10-23）：保密子组公告归本链领导层可顶（链外死隔离由子树结构保证）
  if (conf) return canDeptScope.value && mySubtreeIds.value.has(row.departmentId)
  return (canManageAll.value && !conf) || deptHeadScope(row)
}

// 部门负责人管理域（服务端权威下发：publish:dept + 实时职级≥2，防角色码泄漏给部门内普通成员）
const canDeptScope = ref(false)
// 全员公告直发能力（服务端 canDirectCompany=publish:company+W3 双闸门结果）：true 时隐藏"我提交的申请"Tab
const canDirectCompany = ref(false)
const myDeptId = computed(() => store.userInfo?.departmentId ?? null)
const mySubtreeIds = ref(new Set())
const confDeptIds = ref(new Set())

function deptHeadScope(row) {
  if (!canDeptScope.value) return false
  if (row.type !== 'department' || row.departmentId == null) return false
  if (confDeptIds.value.has(row.departmentId)) {
    // 保密链内一体（2026-10-23）：本链领导层可操作（含下属组）；链外（行政/兄弟组/他链）仍死隔离，不外溢口径不变
    return mySubtreeIds.value.has(row.departmentId)
  }
  return mySubtreeIds.value.has(row.departmentId) // 非保密：本部门或子部门
}

async function loadDeptScope() {
  const perm = await noticeApi.publishPermission()
  if (perm.success) {
    canDeptScope.value = !!perm.data.canManageDeptScope
    canApprove.value = !!perm.data.canApproveCompany
    canApproveDept.value = !!perm.data.canApproveDept
    // 全员直发能力（服务端双闸门结果）：决定"我提交的申请"Tab 是否有意义
    canDirectCompany.value = !!perm.data.canDirectCompany
  }
  // 保密集对行政行级判定也需要（保密部门公告行政不可置顶），故行政管理层同样加载
  if (!canDeptScope.value && !canManageAll.value) return
  const res = await departmentApi.tree()
  if (!res.success) return
  const conf = new Set()
  const collect = (nodes) => { for (const n of nodes) { if (n.confidential === 1) conf.add(n.id); if (n.children?.length) collect(n.children) } }
  collect(res.data)
  confDeptIds.value = conf
  const findNode = (nodes, id) => { for (const n of nodes) { if (n.id === id) return n; const hit = n.children?.length && findNode(n.children, id); if (hit) return hit } return null }
  const mine = myDeptId.value != null ? findNode(res.data, myDeptId.value) : null
  const sub = new Set()
  if (mine) { const walk = (n) => { sub.add(n.id); (n.children || []).forEach(walk) }; walk(mine) }
  mySubtreeIds.value = sub
}
// 纯申请人（只有 notice:submit:dept）：只能看到自己的草稿/申请/未通过，无已发布管理视角
const isApplicantOnly = computed(() =>
  store.hasPermission('notice:submit:dept')
  && !store.hasPermission('notice:create')
  && !store.hasPermission('notice:publish:dept'))
const activeTab = ref(isApplicantOnly.value ? 'draft' : 'published')
// 已发布类视图（已发布 Tab + 治理 Tab）：置顶/撤回等已发布态按钮的显示集合
const isPublishedView = computed(() => activeTab.value === 'published' || activeTab.value === 'governance')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)

onMounted(() => { loadList(); loadDeptScope() })

async function loadList() {
  loading.value = true
  try {
    // 治理 Tab=跨部门治理入口（status=published&managed=1，治理权由后端判定，无权者回落普通范围）
    const params = { status: activeTab.value === 'governance' ? 'published' : activeTab.value, page: page.value, size: 10 }
    if (activeTab.value === 'governance') params.managed = 1
    // 已发布 Tab=管理工作台口径（方案 A）：只看自己发布的+管辖链内部门公告；他人全员公告去公告中心看（后端谓词为准）
    if (activeTab.value === 'published') params.manage = 1
    const res = await noticeApi.list(params)
    if (res.success) {
      list.value = res.data.records
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

function canEdit(row) {
  // 草稿/驳回/申请中：二次编辑提交仅限本人（后端 update() 同口径拦截，非本人一律拒绝）
  if (row.status === 'draft' || row.status === 'rejected' || row.status === 'pending') return row.publisherId === store.userId
  if (row.status === 'published') {
    const base = new Date(row.approvedAt || row.publishAt)
    base.setHours(base.getHours() + 4)
    return new Date() < base && row.publisherId === store.userId
  }
  return false
}

function canRevoke(row) {
  // 三层口径：本人 / 行政（非保密） / 部门负责人本部门链
  if (row.publisherId === store.userId) return true
  if (canManageAll.value && !confDeptIds.value.has(row.departmentId)) return true
  return deptHeadScope(row)
}

function canDelete(row) {
  // 本人限草稿/未通过；行政可直删非保密；部门负责人可删本部门链
  if (row.publisherId === store.userId) return row.status === 'draft' || row.status === 'rejected'
  if (canManageAll.value && !confDeptIds.value.has(row.departmentId)) return true
  return deptHeadScope(row)
}

async function handleApprove(row) {
  await ElMessageBox.confirm(`确认通过「${row.title}」？`, '审批', { customClass: 'nm-msg' })
  const res = await noticeApi.approve(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleReject(row) {
  // 驳回=二次确认弹窗：驳回原因必填（空值不允许提交，前端校验提示；后端同样校验，防止绕过前端拦截）
  let value
  try {
    ({ value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      inputPlaceholder: '原因（必填）',
      //必填校验：为空/纯空格时提示并不允许提交
      inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空'),
      customClass: 'nm-msg',
    }))
  } catch {
    return // 用户点击取消，不执行驳回
  }
  const res = await noticeApi.reject(row.id, value.trim())
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleRevoke(row) {
  await ElMessageBox.confirm(`确认撤回「${row.title}」？撤回后将退回发布人的草稿箱。`, '撤回', { type: 'warning', customClass: 'nm-msg' })
  const res = await noticeApi.revoke(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除「${row.title}」？删除后不可恢复。`, '删除', { type: 'error', customClass: 'nm-msg' })
  const res = await noticeApi.delete(row.id)
  if (res.success) { ElMessage.success(res.message); loadList() }
}

// 只读展示驳回原因（仅相关双方可见，后端已对其他人脱敏置空）
async function handleShowReason(row) {
  await ElMessageBox.alert(row.rejectReason || '（未填写原因）', '驳回原因', { customClass: 'nm-msg' })
}

async function handleTop(row, top) {
  const action = top === 1 ? '置顶' : '取消置顶'
  await ElMessageBox.confirm(`确认${action}「${row.title}」？`, action, { customClass: 'nm-msg' })
  const res = await noticeApi.setTop(row.id, top)
  if (res.success) { ElMessage.success(res.message); loadList() }
  else { ElMessage.error(res.message) }
}

function statusLabel(s) { return { draft:'草稿', pending:'审批中', published:'已发布', rejected:'未通过', archived:'已撤回' }[s]||s }
function statusColor(s) { return { draft:'info', pending:'warning', published:'success', rejected:'danger', archived:'info' }[s]||'' }
// 时间列口径：未通过显示驳回时间（历史行无该字段时退回创建时间）；已发布显示发布时间；其余显示创建/申请时间
function rowTime(row) {
  if (row.status === 'rejected') return row.rejectedAt || row.createdAt
  return row.publishAt || row.createdAt
}
function formatTime(t) { return t ? t.replace('T',' ').slice(0,16) : '—' }
</script>

<style scoped>
/* ===== 页面骨架：白底+淡蓝企业简约风（与 CheckIn.vue 一致），统一间距节奏 ===== */
.manage-page { padding: 20px; }
/* 工具栏：标题与主按钮两端对齐、垂直居中，统一上下留白 */
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-title { margin: 0; font-size: 18px; line-height: 28px; color: #303133; font-weight: 600; }
/* Tab 区与上/下间距统一（12px 节奏），窄屏可横滑不溢出 */
:deep(.el-tabs__header) { margin-bottom: 12px; }

/* ===== 操作列：固定槽位、上下成列对齐、按钮间距统一 8px，禁止换行/高低参差 ===== */
.action-btns {
  display: flex;            /* 单行横排 */
  align-items: center;      /* 上下垂直居中 */
  justify-content: center;  /* 水平居中 */
  flex-wrap: nowrap;        /* 禁止换行 */
  gap: 8px;                 /* 按钮间距统一 */
  white-space: nowrap;
}
/* 取消 el-button 相邻默认 12px 外边距，统一由 gap 控制间距 */
.action-btns .el-button { margin-left: 0; }
/* 固定槽位宽度：同名按钮跨行上下成列对齐（2字按钮 48px 槽位） */
.action-btns .el-button { min-width: 48px; justify-content: center; }
/* 加宽槽位：兼容'取消置顶'4字文案，避免按钮变宽时列错位 */
.action-btns .slot-wide { min-width: 64px; }
/* 行级隐藏：visibility 保留占位（display:none 会塌陷槽位），同名列保持上下对齐；并禁点 */
.action-btns .slot-off { visibility: hidden; pointer-events: none; }
/* 操作列表体/表头单元格均居中，内边距对称（行高与其他列一致） */
:deep(.action-col .cell) {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 8px;
  padding-right: 8px;
}

/* 分页与表格间距统一 */
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }

/* ===== 移动端适配（<992px，遵循《10-横切》§四通用规则） ===== */
@media (max-width: 991px) {
  /* 页面内边距收紧 */
  .manage-page { padding: 12px; }
  .page-title { font-size: 16px; line-height: 24px; }
  /* 表格允许横向滚动（el-table 容器内自适应），行内元素不挤压换行 */
  .action-btns { gap: 8px; }
  /* 触控目标 ≥40px（规范 §四），保证点得准 */
  .action-btns .el-button { min-height: 40px; padding: 8px 6px; }
  /* 表格行高适度加大，避免拥挤 */
  :deep(.el-table .cell) { line-height: 24px; }
}
</style>

<!--
  消息确认/输入弹框小屏自适应（规范 §四：弹窗 width: 92%）。
  ElMessageBox 被 teleport 到 body，scoped 样式够不到，故单独放非 scoped 块；
  仅作用于本页面标记了 nm-msg 的弹框（5 处操作确认），不影响全局其他弹框。
-->
<style>
@media (max-width: 991px) {
  .nm-msg { width: 92% !important; }
}
</style>
