import { useUserStore } from '@/stores/user'
import { permissionOf } from '@/config/menu'

// 路由守卫：登录态 + 菜单权限双重校验
export function setupRouterGuard(router) {
  router.beforeEach(async (to) => {
    document.title = to.meta.title ? `${to.meta.title} · OA 办公系统` : 'OA 办公系统'

    const userStore = useUserStore()

    // 公开页（登录）直接放行
    if (to.meta.public) return true

    // 未登录 → 去登录页，带 redirect
    if (!userStore.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    // 刷新后权限未加载：从后端实时拉取
    if (!userStore.loaded) {
      try {
        await userStore.fetchUser()
      } catch (e) {
        userStore.logout()
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }

    // 菜单级权限：目标路由若绑定 menuKey，校验可见性（越权则回工作台）
    if (to.meta.menuKey) {
      const required = permissionOf(to.meta.menuKey)
      if (!userStore.hasAnyPermission(required)) {
        // softPermission 路由（打卡页）放行：页面内展示「该角色无需打卡」兑底提示，
        // 不重定向、不发出打卡请求
        if (to.meta.softPermission) return true
        return { path: '/dashboard' }
      }
    }

    return true
  })
}
