import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'

// 用户状态与权限（权限实时来自后端，仅存内存，刷新后由 fetchUser 重新拉取）
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: null,
    roleCode: '',
    permissions: [],
    loaded: false,
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.username || '',
    realName: (state) => state.userInfo?.realName || '',
  },

  actions: {
    async login(username, password) {
      const res = await authApi.login({ username, password })
      if (!res.success) {
        throw new Error(res.message || '登录失败')
      }
      const { token, user, permissions } = res.data
      this.token = token
      this.userInfo = user
      this.roleCode = user?.roleCode || ''
      this.permissions = permissions || []
      this.loaded = true
      localStorage.setItem('token', token)
      return res.data
    },

    async fetchUser() {
      const res = await authApi.me()
      if (!res.success) {
        throw new Error(res.message || '获取用户信息失败')
      }
      this.userInfo = res.data.user
      this.roleCode = res.data.user?.roleCode || ''
      this.permissions = res.data.permissions || []
      this.loaded = true
      return res.data
    },

    logout() {
      this.token = ''
      this.userInfo = null
      this.roleCode = ''
      this.permissions = []
      this.loaded = false
      localStorage.removeItem('token')
    },

    // 单个权限码判断
    hasPermission(code) {
      return this.permissions.includes(code)
    },

    // 任一权限满足即可见（空数组表示所有登录用户可见）
    hasAnyPermission(codes) {
      if (!codes || codes.length === 0) return true
      return codes.some((c) => this.permissions.includes(c))
    },
  },
})
