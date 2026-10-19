import { createRouter, createWebHistory } from 'vue-router'
import { MENU_FLAT } from '@/config/menu'
import { setupRouterGuard } from '@/config/permission'

const Placeholder = () => import('@/views/_Placeholder.vue')

// 已落地的真实页面：menuKey 对应菜单路径 -> 组件
// 未列出的菜单项仍指向占位页（各业务页面在后续 Phase 落地）
const COMPONENT_MAP = {
  '/attendance/checkin': () => import('@/views/attendance/CheckIn.vue'),
  '/attendance': () => import('@/views/attendance/AttendanceList.vue'),
  '/attendance/schedule': () => import('@/views/attendance/ScheduleSettings.vue'),
  '/system': () => import('@/views/system/DepartmentManage.vue'),
  '/notices': () => import('@/views/notice/NoticeList.vue'),
  '/notices/manage': () => import('@/views/notice/NoticeManage.vue'),
}

const menuChildren = MENU_FLAT
  .filter((m) => m.path !== '/dashboard')
  .map((m) => ({
    path: m.path.replace(/^\//, ''),
    name: `menu-${m.menuKey}`,
    component: COMPONENT_MAP[m.path] || Placeholder,
    // softPermission：越权时不强制重定向，由页面内展示兑底提示。
    // 仅打卡页使用：超管（老板）无打卡权限，手动输 URL 时应看到「该角色无需打卡」，
    // 而不是被弹回工作台或发出打卡请求后报错。
    meta: { title: m.title, menuKey: m.menuKey, softPermission: m.menuKey === 'attendance-checkin' },
  }))

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '首页工作台', menuKey: 'dashboard' } },
      { path: 'profile', name: 'Profile', component: Placeholder, meta: { title: '个人资料' } },
      { path: 'notice/:id', name: 'NoticeDetail', component: () => import('@/views/notice/NoticeDetail.vue'), meta: { title: '公告详情', menuKey: 'notices' } },
      { path: 'notice/create', name: 'NoticeCreate', component: () => import('@/views/notice/NoticeEdit.vue'), meta: { title: '新建公告', menuKey: 'notices' } },
      { path: 'notice/edit/:id', name: 'NoticeEdit', component: () => import('@/views/notice/NoticeEdit.vue'), meta: { title: '编辑公告', menuKey: 'notices' } },
      ...menuChildren,
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

setupRouterGuard(router)

export default router
