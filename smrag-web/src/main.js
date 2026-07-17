import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElButton,
  ElIcon,
  ElInput,
  ElLoading,
  ElTable,
  ElTableColumn,
  ElTooltip,
  ElUpload
} from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-icon.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-loading.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-overlay.css'
import 'element-plus/theme-chalk/el-popper.css'
import 'element-plus/theme-chalk/el-scrollbar.css'
import 'element-plus/theme-chalk/el-table.css'
import 'element-plus/theme-chalk/el-tooltip.css'
import 'element-plus/theme-chalk/el-upload.css'
import './style.css'
import router from './router'
import App from './App.vue'

const app = createApp(App)
const pinia = createPinia()

const elementComponents = [
  ElButton,
  ElIcon,
  ElInput,
  ElTable,
  ElTableColumn,
  ElTooltip,
  ElUpload
]

elementComponents.forEach(component => app.use(component))

app.use(pinia)
app.use(router)
app.use(ElLoading)

app.mount('#app')
