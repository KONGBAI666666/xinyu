import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/base.css'

// 深色主题为品牌默认 (设计文档 4.1); useTheme composable 在后续里程碑接管切换
document.documentElement.classList.add('dark')

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
