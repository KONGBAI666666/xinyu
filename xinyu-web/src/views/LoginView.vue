<script setup lang="ts">
/**
 * 登录/注册页（契约三 3.3, M1-4.2 简化视觉版）
 * 同页 Tab 切换; 表单校验规则与后端 DTO 对齐（契约一 1.3）
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { BizError } from '@/utils/BizError'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type Mode = 'login' | 'register'
const mode = ref<Mode>('login')

const form = reactive({
  username: '',
  password: '',
  nickname: '',
})

const submitting = ref(false)
/** 提交级错误（后端 BizError message） */
const submitError = ref('')
/** 字段级错误 */
const fieldErrors = reactive({ username: '', password: '' })

const USERNAME_RULE = /^[a-zA-Z0-9_]{4,16}$/

function switchMode(next: Mode): void {
  mode.value = next
  submitError.value = ''
  fieldErrors.username = ''
  fieldErrors.password = ''
}

/** 前端先挡一层格式错误, 减少无效请求; 最终以后端校验为准 */
function validate(): boolean {
  fieldErrors.username = USERNAME_RULE.test(form.username)
    ? ''
    : '用户名需为 4-16 位字母、数字或下划线'
  fieldErrors.password =
    form.password.length >= 6 && form.password.length <= 20 ? '' : '密码长度需为 6-20 位'
  return !fieldErrors.username && !fieldErrors.password
}

async function handleSubmit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  submitError.value = ''
  try {
    if (mode.value === 'login') {
      await authStore.login({ username: form.username, password: form.password })
    } else {
      await authStore.register({
        username: form.username,
        password: form.password,
        nickname: form.nickname.trim() || undefined,
      })
    }
    // 守卫兜底: redirect 参数仅接受站内路径
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
    await router.push(redirect.startsWith('/') ? redirect : '/chat')
  } catch (e) {
    submitError.value = e instanceof BizError ? e.message : '出了点小状况，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex h-full items-center justify-center px-4">
    <div class="auth-card w-full max-w-sm p-8">
      <!-- 品牌区 -->
      <h1 class="brand-title text-center text-4xl font-bold">心屿</h1>
      <p class="mt-2 text-center text-sm" :style="{ color: 'var(--text-secondary)' }">
        每个人心里，都有一座岛。
      </p>

      <!-- Tab 切换 -->
      <div class="tab-bar mt-8 flex">
        <button
          v-for="item in [
            { key: 'login', label: '登录' },
            { key: 'register', label: '注册' },
          ]"
          :key="item.key"
          type="button"
          class="tab-item flex-1 py-2 text-sm"
          :class="{ active: mode === item.key }"
          @click="switchMode(item.key as Mode)"
        >
          {{ item.label }}
        </button>
      </div>

      <form class="mt-6 flex flex-col gap-4" @submit.prevent="handleSubmit">
        <div>
          <input
            v-model.trim="form.username"
            class="field w-full"
            type="text"
            placeholder="用户名（4-16 位字母、数字或下划线）"
            autocomplete="username"
          />
          <p v-if="fieldErrors.username" class="field-error">{{ fieldErrors.username }}</p>
        </div>

        <div>
          <input
            v-model="form.password"
            class="field w-full"
            type="password"
            placeholder="密码（6-20 位）"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          />
          <p v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</p>
        </div>

        <input
          v-if="mode === 'register'"
          v-model="form.nickname"
          class="field w-full"
          type="text"
          placeholder="昵称（选填，默认同用户名）"
          maxlength="30"
        />

        <p v-if="submitError" class="submit-error text-center text-sm">{{ submitError }}</p>

        <button class="submit-btn w-full py-2.5 font-medium" type="submit" :disabled="submitting">
          {{ submitting ? '请稍候…' : mode === 'login' ? '登 录' : '注 册' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.auth-card {
  background: var(--bg-elevated);
  border-radius: var(--radius-card);
  backdrop-filter: blur(var(--blur-glass));
}

.brand-title {
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
}

.tab-bar {
  background: var(--bg-elevated);
  border-radius: var(--radius-btn);
  padding: 4px;
}

.tab-item {
  border: none;
  background: transparent;
  color: var(--text-secondary);
  border-radius: calc(var(--radius-btn) - 4px);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}

.tab-item.active {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.field {
  padding: 10px 14px;
  border: 1px solid transparent;
  border-radius: var(--radius-btn);
  background: var(--bg-elevated);
  color: var(--text-primary);
  outline: none;
  transition: border-color var(--duration-base) var(--ease-base);
}

.field::placeholder {
  color: var(--text-muted);
}

.field:focus {
  border-color: var(--brand-from);
}

.field-error {
  margin: 4px 2px 0;
  font-size: 12px;
  color: var(--color-danger);
}

.submit-error {
  color: var(--color-danger);
}

.submit-btn {
  border: none;
  border-radius: var(--radius-btn);
  background: var(--brand-gradient);
  color: #fff;
  cursor: pointer;
  transition: opacity var(--duration-base) var(--ease-base);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
