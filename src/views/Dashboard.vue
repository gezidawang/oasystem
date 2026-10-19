<template>
  <div class="dash">
    <el-card class="dash-hero" shadow="never">
      <div class="hero-text">
        <h2>你好，{{ store.realName || store.username }}</h2>
        <p>{{ roleLabel }} · 当前拥有 {{ store.permissions.length }} 项功能权限</p>
      </div>
      <el-icon class="hero-icon" size="42"><Sunny /></el-icon>
    </el-card>

    <el-row :gutter="16" class="dash-stats">
      <el-col :xs="12" :sm="12" :md="6" v-for="s in stats" :key="s.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-num">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="dash-modules" shadow="never">
      <div class="block-title">我可访问的模块</div>
      <div class="tags">
        <el-tag v-for="m in visibleModuleTitles" :key="m" type="primary" effect="light" class="mod-tag">
          {{ m }}
        </el-tag>
        <span v-if="visibleModuleTitles.length <= 1" class="muted">（更多业务模块将在后续阶段开放）</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { MENU_GROUPS } from '@/config/menu'

const store = useUserStore()

const ROLE_LABELS = { super_admin: '超级管理员', hr: '人事', manager: '部门领导', employee: '普通员工' }
const roleLabel = computed(() => ROLE_LABELS[store.roleCode] || '未知角色')

// 工作台数据在 Phase 12 接入聚合接口，先展示占位数字
const stats = [
  { label: '待办事项', value: '—' },
  { label: '消息通知', value: '—' },
  { label: '待审批', value: '—' },
  { label: '本月考勤', value: '—' },
]

const visibleModuleTitles = computed(() =>
  MENU_GROUPS.flatMap((g) => g.items)
    .filter((i) => store.hasAnyPermission(i.permission))
    .map((i) => i.title)
)
</script>

<style scoped>
/*
  缺陷修复说明：
  el-card 的插槽内容实际渲染在 .el-card__body 内部，
  如果把 display:flex 写在 .dash-hero（el-card 根节点）上，
  真正的子元素（hero-text / hero-icon）并不是它的直接子节点，
  margin-left:auto 会失效，太阳图标会掉到文字下方居中。
  正确做法：用 :deep() 把 flex 布局应用到内部的 .el-card__body 上。
*/
.dash-hero {
  background: linear-gradient(90deg, #ffffff, #eef6ff);
  border: 1px solid #e6eef7;
}
.dash-hero :deep(.el-card__body) {
  display: flex;
  align-items: center;
}
.hero-text h2 {
  margin: 0 0 6px;
  color: #1f2d3d;
}
.hero-text p {
  margin: 0;
  color: #6b7a90;
}
.hero-icon {
  margin-left: auto;
  color: #2b6cb0;
}
.dash-stats {
  margin: 16px 0;
}
.stat-card {
  text-align: center;
  border: 1px solid #e6eef7;
}
.stat-num {
  font-size: 24px;
  font-weight: 600;
  color: #2b6cb0;
}
.stat-label {
  margin-top: 6px;
  color: #6b7a90;
  font-size: 13px;
}
.block-title {
  font-weight: 600;
  margin-bottom: 12px;
  color: #1f2d3d;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.muted {
  color: #9aa5b1;
  font-size: 12px;
}
</style>
