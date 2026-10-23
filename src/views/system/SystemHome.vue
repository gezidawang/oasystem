<template>
  <div class="system-home">
    <el-tabs v-model="activeTab" class="sys-tabs">
      <el-tab-pane label="部门管理" name="dept" lazy>
        <DepartmentManage />
      </el-tab-pane>
      <el-tab-pane label="岗位管理" name="position" lazy>
        <PositionManage />
      </el-tab-pane>
      <!-- 8-3 员工建档：user:manage 已授 hr+super_admin，W3 职级闸门在后端接口拦截（人事专员可见入口但操作被 403） -->
      <el-tab-pane v-if="canManageAccounts" label="账号管理" name="account" lazy>
        <AccountManage />
      </el-tab-pane>
      <el-tab-pane v-if="canManageRoles" label="角色权限" name="role" lazy>
        <RolePermissionManage />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import DepartmentManage from './DepartmentManage.vue'
import PositionManage from './PositionManage.vue'
import AccountManage from './AccountManage.vue'
import RolePermissionManage from './RolePermissionManage.vue'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
// 角色权限矩阵：role:manage 仅总经理持有（铁律）
const canManageRoles = computed(() => store.hasPermission('role:manage'))
// 员工账号管理（建档/启停/重置密码/解锁）：人事总监与总经理可见入口
const canManageAccounts = computed(() => store.hasPermission('user:manage'))

const activeTab = ref('dept')
</script>

<style scoped>
.sys-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
</style>
