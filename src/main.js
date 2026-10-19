import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setupPermissionDirective } from './directives/permission'
import './style.css'

const app = createApp(App)

// 注册 Element Plus 全部图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

// 按钮级权限指令 v-perm（菜单之外的页内第二道闸门）
setupPermissionDirective(app)

app.mount('#app')
