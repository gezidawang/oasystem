import { useUserStore } from '@/stores/user'

/**
 * 按钮/元素级权限指令（RBAC 第二道闸门）。
 *
 * 菜单可见性（menu.js + 路由守卫）控制“能不能进这个页面”；
 * 本指令控制“页面里的这个按钮/区块能不能出现”，用于同一页面内的角色差异，
 * 例如「部门筛选」「审核按钮」只对具备相应权限码的角色展示。
 *
 * 用法：
 *   v-perm="'employee:read:dept'"              单个权限码
 *   v-perm="['notice:create', 'notice:delete']" 任一命中即显示
 *   无权限时直接移除元素（不是隐藏，避免 DOM 里被手动改出来）
 *
 * 注意：前端指令只是体验层，真正的边界必须在后端接口做二次校验，
 * 前端移除按钮不等于接口安全。
 */
function check(value) {
  const store = useUserStore()
  if (value === undefined || value === null || value === '') return true
  const codes = Array.isArray(value) ? value : [value]
  return store.hasAnyPermission(codes)
}

export const permissionDirective = {
  mounted(el, binding) {
    if (!check(binding.value)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  },
}

export function setupPermissionDirective(app) {
  app.directive('perm', permissionDirective)
}
