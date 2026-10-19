<template>
  <div class="login-wrap">
    <div class="login-box">
      <div class="login-brand">
        <el-icon size="26"><Grid /></el-icon>
        <span>OA 办公系统</span>
      </div>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent="submit">
        <el-form-item label="工号" prop="username">
          <el-input v-model="form.username" placeholder="请输入工号，如 A001 / YE10005" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="login-tip">演示账号：A001（总经理）/ RG10001（人事·部门负责人）/ YG10004（研发·主管）/ YE10005（研发·员工），密码均为 123456</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await store.login(form.username.trim(), form.password)
      ElMessage.success('登录成功')
      const redirect = route.query.redirect
      router.push(redirect && redirect !== '/login' ? redirect : '/dashboard')
    } catch (e) {
      ElMessage.error(e.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eaf3fc 0%, #d7e9fb 100%);
}
.login-box {
  width: 380px;
  max-width: 92vw;
  padding: 36px 34px;
  background: #ffffff;
  border: 1px solid #e6eef7;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(43, 108, 176, 0.1);
}
.login-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: #2b6cb0;
  margin-bottom: 24px;
}
.login-btn {
  width: 100%;
  margin-top: 6px;
}
.login-tip {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #9aa5b1;
}
</style>
