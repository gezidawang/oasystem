<template>
  <div class="system-home">
    <el-tabs v-model="activeTab" class="sys-tabs">
      <el-tab-pane label="部门管理" name="dept" lazy>
        <DepartmentManage />
      </el-tab-pane>
      <el-tab-pane label="岗位管理" name="position" lazy>
        <PositionManage />
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
import RolePermissionManage from './RolePermissionManage.vue'
import { useUserStore } from '@/stores/user'

const store = useUserStore()
// 角色权限矩阵：role:manage 仅总经理持有（铁律）
const canManageRoles = computed(() => store.hasPermission('role:manage'))

const activeTab = ref('dept')
</script>

<style scoped>
.sys-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
</style>
