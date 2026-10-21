<template>
  <el-container class="oa-layout">
    <!-- 桌面：固定左侧栏 -->
    <el-aside v-if="!isMobile" width="230px" class="oa-aside">
      <div class="oa-logo">
        <el-icon><Grid /></el-icon>
        <span>OA 办公系统</span>
      </div>
      <el-scrollbar class="oa-nav">
        <el-menu :default-active="$route.path" router class="oa-menu">
          <template v-for="group in visibleGroups" :key="group.group">
            <div class="oa-group-title">{{ group.group }}</div>
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="oa-body">
      <el-header class="oa-header" :height="isMobile ? '52px' : '60px'">
        <div class="oa-header-left">
          <el-button v-if="isMobile" class="oa-hamburger" text @click="drawer = true">
            <el-icon size="20"><Fold /></el-icon>
          </el-button>
          <span class="oa-crumb">{{ $route.meta.title }}</span>
        </div>
        <el-dropdown class="oa-user" @command="onCommand">
          <span class="oa-user-name">
            <UserAvatar :name="displayName" :seed="store.userInfo?.id || displayName" :size="30" />
            <span v-if="!isMobile" class="oa-user-meta">
              {{ displayName }}<el-tag size="small" effect="plain" class="oa-role-tag">{{ roleLabel }}</el-tag>
            </span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="oa-main" :class="{ 'oa-main--mobile': isMobile }">
        <router-view />
      </el-main>
    </el-container>

    <!-- 移动端：侧栏抽屉（复用同一份权限菜单数据） -->
    <el-drawer v-if="isMobile" v-model="drawer" direction="ltr" size="72%" :with-header="false">
      <div class="oa-logo oa-logo--drawer">
        <el-icon><Grid /></el-icon><span>OA 办公系统</span>
      </div>
      <el-menu :default-active="$route.path" router class="oa-menu" @select="drawer = false">
        <template v-for="group in visibleGroups" :key="group.group">
          <div class="oa-group-title">{{ group.group }}</div>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-drawer>

    <!-- 移动端：底部高频 Tab -->
    <nav v-if="isMobile" class="oa-tabbar">
      <div
        v-for="tab in tabs"
        :key="tab.path"
        class="oa-tab"
        :class="{ 'oa-tab--active': isTabActive(tab.path) }"
        @click="router.push(tab.path)"
      >
        <el-icon size="20"><component :is="tab.icon" /></el-icon>
        <span>{{ tab.title }}</span>
      </div>
    </nav>
  </el-container>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { MENU_GROUPS } from '@/config/menu'
import { useIsMobile } from '@/composables/useBreakpoint'
import UserAvatar from '@/components/UserAvatar.vue'

const store = useUserStore()
const router = useRouter()
const route = useRoute()
const isMobile = useIsMobile()
const drawer = ref(false)

const ROLE_LABELS = { super_admin: '超级管理员', hr: '人事', manager: '部门领导', employee: '普通员工' }
const roleLabel = computed(() => ROLE_LABELS[store.roleCode] || store.roleCode || '未知角色')
const displayName = computed(() => store.realName || store.username || '用户')

// 按当前用户权限过滤；某分组无可见项则整组隐藏
const visibleGroups = computed(() =>
  MENU_GROUPS
    .map((g) => ({ ...g, items: g.items.filter((i) => store.hasAnyPermission(i.permission)) }))
    .filter((g) => g.items.length > 0)
)

// 底部 Tab：工作台 / 审批 / 考勤 / 我的
const tabs = [
  { path: '/dashboard', title: '工作台', icon: 'HomeFilled' },
  { path: '/approvals', title: '审批', icon: 'Tickets' },
  { path: '/attendance', title: '考勤', icon: 'Clock' },
  { path: '/profile', title: '我的', icon: 'User' },
]
function isTabActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

// 路由变化后关闭抽屉
watch(() => route.fullPath, () => { drawer.value = false })

function onCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
      .then(() => { store.logout(); router.push('/login') })
      .catch(() => {})
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.oa-layout { height: 100vh; }
.oa-body { min-width: 0; }
.oa-aside { background: #fff; border-right: 1px solid #eef2f7; display: flex; flex-direction: column; }
.oa-logo { height: 60px; display: flex; align-items: center; gap: 8px; padding: 0 20px; font-size: 17px; font-weight: 600; color: #2b6cb0; border-bottom: 1px solid #eef2f7; }
.oa-logo--drawer { height: 52px; }
.oa-nav { flex: 1; }
.oa-menu { border-right: none; }
.oa-group-title { padding: 14px 20px 6px; font-size: 12px; color: #9aa5b1; letter-spacing: 1px; }
.oa-header { background: #fff; border-bottom: 1px solid #eef2f7; display: flex; align-items: center; justify-content: space-between; }
.oa-header-left { display: flex; align-items: center; gap: 6px; min-width: 0; }
.oa-hamburger { padding: 4px; }
.oa-crumb { font-size: 15px; font-weight: 500; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.oa-user-name { display: flex; align-items: center; gap: 8px; cursor: pointer; color: #4b5563; }
/* 去除浏览器给下拉触发器加的默认聚焦轮廓线（黑色圆角边框） */
.oa-user :deep(*) { outline: none; }
.oa-user-meta { display: flex; align-items: center; gap: 6px; }
.oa-role-tag { color: #2b6cb0; border-color: #cfe3f7; background: #eaf3fc; }
.oa-main { background: #f5f9ff; padding: 18px; }
.oa-main--mobile { padding: 12px 12px 64px; }

/* 移动端底部 Tab */
.oa-tabbar {
  position: fixed; left: 0; right: 0; bottom: 0; height: 56px;
  background: #fff; border-top: 1px solid #eef2f7;
  display: flex; z-index: 100;
  padding-bottom: env(safe-area-inset-bottom);
}
.oa-tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; font-size: 12px; color: #9aa5b1; cursor: pointer; }
.oa-tab--active { color: #2b6cb0; }
</style>
