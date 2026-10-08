<script setup lang="ts">
/**
 * 聊天区顶栏: 模型切换器 + 当前会话标题 + 统计入口 + 用户昵称 + 修改密码 + 退出登录
 */
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useStatsStore } from '@/stores/stats'
import { useModelsStore } from '@/stores/models'
import { useConversationStore } from '@/stores/conversation'
import { modelsApi } from '@/api/modules/models'
import { authApi } from '@/api/modules/auth'
import { BizError } from '@/utils/BizError'
import type { AiModelVO } from '@/types/api'

const router = useRouter()
const authStore = useAuthStore()
const statsStore = useStatsStore()
const modelsStore = useModelsStore()
const conversationStore = useConversationStore()

defineProps<{
  title: string
}>()

defineEmits<{
  logout: []
}>()

/** 模型选择下拉是否展开 */
const modelDropdownOpen = ref(false)

/** 当前会话生效的模型: 会话级覆盖 > 用户默认 */
const activeModel = computed<AiModelVO | null>(() => {
  const conv = conversationStore.active
  if (conv?.modelId) {
    return modelsStore.getById(conv.modelId)
  }
  return modelsStore.defaultModel()
})

/** 会话激活时预加载模型列表（用于切换器显示） */
watch(
  () => conversationStore.activeId,
  async (id) => {
    if (id) {
      await modelsStore.load().catch(() => {})
    }
  },
  { immediate: true },
)

/** 可切换的模型: 已停用的不在切换器中出现 (后端解析同样拒绝) */
const switchableModels = computed(() => modelsStore.list.filter((m) => m.enabled === 1))

/** 是否管理员 (控制「管理后台」入口) */
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')

async function switchModel(model: AiModelVO | null): Promise<void> {
  const convId = conversationStore.activeId
  if (!convId) return
  const targetId = model?.id ?? null
  // 与当前一致则不调用
  const currentId = conversationStore.active?.modelId ?? null
  if (targetId === currentId) {
    modelDropdownOpen.value = false
    return
  }
  try {
    await modelsApi.switchConversationModel(convId, targetId)
    // 本地更新会话的 modelId, 避免重新拉列表
    if (conversationStore.active) {
      conversationStore.active.modelId = targetId
    }
  } catch (e) {
    // 失败给明确提示 (原先静默, 用户误以为切换成功, 下次进入才从后端同步回旧值)
    pwTip.value = e instanceof BizError ? e.message : '模型切换失败, 请稍后重试'
    setTimeout(() => (pwTip.value = ''), 2000)
  }
  modelDropdownOpen.value = false
}

function toggleDropdown(): void {
  modelDropdownOpen.value = !modelDropdownOpen.value
}

function closeDropdown(): void {
  modelDropdownOpen.value = false
}

/** 跳转设置页并收起下拉 (多语句收敛为方法, 模板内联表达式不支持换行多语句) */
function goToSettings(path: string): void {
  router.push(path)
  closeDropdown()
}

// ---------- 修改密码弹窗 ----------

const pwModalOpen = ref(false)
const pwSubmitting = ref(false)
const pwError = ref('')
/** 成功提示（2 秒自动消失） */
const pwTip = ref('')
const pwForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

function openPwModal(): void {
  pwForm.oldPassword = ''
  pwForm.newPassword = ''
  pwForm.confirm = ''
  pwError.value = ''
  pwModalOpen.value = true
}

function closePwModal(): void {
  pwModalOpen.value = false
}

/** 前端校验与后端 UpdatePasswordDTO 对齐: 新密码 6-20 位 + 两次一致 */
function validatePwForm(): string {
  if (!pwForm.oldPassword) return '请输入原密码'
  if (pwForm.newPassword.length < 6 || pwForm.newPassword.length > 20) {
    return '新密码长度需为 6-20 位'
  }
  if (pwForm.newPassword !== pwForm.confirm) return '两次输入的新密码不一致'
  return ''
}

async function submitPassword(): Promise<void> {
  pwError.value = validatePwForm()
  if (pwError.value) return
  pwSubmitting.value = true
  try {
    await authApi.updatePassword({
      oldPassword: pwForm.oldPassword,
      newPassword: pwForm.newPassword,
    })
    pwModalOpen.value = false
    pwTip.value = '密码已修改'
    setTimeout(() => (pwTip.value = ''), 2000)
  } catch (e) {
    // 42200 "原密码错误" 等业务错误在弹窗内提示, 不触发全局登出
    pwError.value = e instanceof BizError ? e.message : '修改失败，请稍后重试'
  } finally {
    pwSubmitting.value = false
  }
}

// ---------- 绑定邮箱弹窗 ----------

const emailModalOpen = ref(false)
const emailSubmitting = ref(false)
const emailError = ref('')
const emailValue = ref('')

function openEmailModal(): void {
  emailValue.value = authStore.user?.email ?? ''
  emailError.value = ''
  emailModalOpen.value = true
}

function closeEmailModal(): void {
  emailModalOpen.value = false
}

/** 与后端 UpdateEmailDTO 校验对齐: 非空时须为合法邮箱格式 */
function validateEmailForm(): string {
  const v = emailValue.value.trim()
  if (!v) return '' // 空 = 清除绑定, 合法
  if (v.length > 64 || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v)) return '邮箱格式不正确'
  return ''
}

async function submitEmail(): Promise<void> {
  emailError.value = validateEmailForm()
  if (emailError.value) return
  emailSubmitting.value = true
  try {
    const next = emailValue.value.trim() || null
    await authApi.updateEmail(next)
    // 同步本地缓存, 让 /admin 守卫与展示即时生效
    if (authStore.user) {
      const updated = { ...authStore.user, email: next }
      authStore.user = updated
    }
    emailModalOpen.value = false
    pwTip.value = '邮箱已更新'
    setTimeout(() => (pwTip.value = ''), 2000)
  } catch (e) {
    emailError.value = e instanceof BizError ? e.message : '保存失败，请稍后重试'
  } finally {
    emailSubmitting.value = false
  }
}
</script>

<template>
  <header class="header flex items-center justify-between px-6">
    <!-- 左侧: 标题 -->
    <div class="header-left">
      <!-- 模型切换器 -->
      <div class="model-switcher">
        <button
          type="button"
          class="model-btn"
          :disabled="!conversationStore.activeId"
          @click.stop="toggleDropdown"
        >
          <span class="model-dot" :class="{ active: !!activeModel }"></span>
          <span class="model-name">{{ activeModel ? activeModel.displayName : '默认模型' }}</span>
          <svg
            v-if="conversationStore.activeId"
            class="chevron"
            :class="{ open: modelDropdownOpen }"
            width="12"
            height="12"
            viewBox="0 0 12 12"
            fill="none"
          >
            <path
              d="M3 4.5L6 7.5L9 4.5"
              stroke="currentColor"
              stroke-width="1.4"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>

        <!-- 外部点击遮罩 -->
        <div v-if="modelDropdownOpen" class="outside-mask" @click="closeDropdown"></div>

        <!-- 下拉菜单 -->
        <Transition name="dropdown">
          <div v-if="modelDropdownOpen" class="dropdown" @click.stop>
            <!-- 默认模型选项（清除会话级覆盖） -->
            <div
              class="dropdown-item"
              :class="{ selected: !conversationStore.active?.modelId }"
              @click="switchModel(null)"
            >
              <div class="item-info">
                <span class="item-name">用户默认</span>
                <span class="item-desc">{{
                  modelsStore.defaultModel()?.displayName ?? '未设置'
                }}</span>
              </div>
              <svg
                v-if="!conversationStore.active?.modelId"
                class="check"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
              >
                <path
                  d="M2 7L6 11L12 3"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </div>

            <div class="dropdown-divider"></div>

            <!-- 模型列表 -->
            <div
              v-for="model in switchableModels"
              :key="model.id"
              class="dropdown-item"
              :class="{ selected: conversationStore.active?.modelId === model.id }"
              @click="switchModel(model)"
            >
              <div class="item-info">
                <span class="item-name">{{ model.displayName }}</span>
                <span class="item-desc">{{ model.provider }} · {{ model.modelCode }}</span>
              </div>
              <svg
                v-if="conversationStore.active?.modelId === model.id"
                class="check"
                width="14"
                height="14"
                viewBox="0 0 14 14"
                fill="none"
              >
                <path
                  d="M2 7L6 11L12 3"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </div>

            <!-- 空状态 / 管理 -->
            <div v-if="modelsStore.list.length === 0" class="dropdown-empty">还没有添加模型</div>

            <div class="dropdown-divider"></div>
            <div class="dropdown-item manage" @click="goToSettings('/settings/models')">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path
                  d="M7 2V12M2 7H12"
                  stroke="currentColor"
                  stroke-width="1.4"
                  stroke-linecap="round"
                />
              </svg>
              <span>管理模型</span>
            </div>
            <div class="dropdown-item manage" @click="goToSettings('/settings/memories')">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path
                  d="M7 2C4 2 2 4 2 6.5C2 8 2.8 9 3.8 9.8L3.5 12L5.6 11C6 11.1 6.5 11.2 7 11.2C10 11.2 12 9.2 12 6.6C12 4 10 2 7 2Z"
                  stroke="currentColor"
                  stroke-width="1.2"
                  stroke-linejoin="round"
                />
                <circle cx="5" cy="6.5" r="0.6" fill="currentColor" />
                <circle cx="7" cy="6.5" r="0.6" fill="currentColor" />
                <circle cx="9" cy="6.5" r="0.6" fill="currentColor" />
              </svg>
              <span>长期记忆</span>
            </div>
            <div class="dropdown-item manage" @click="goToSettings('/settings/knowledge-bases')">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path
                  d="M2 3.5C2 2.7 2.7 2 3.5 2H6V12H3.5C2.7 12 2 11.3 2 10.5V3.5Z"
                  stroke="currentColor"
                  stroke-width="1.2"
                />
                <path
                  d="M8 2H10.5C11.3 2 12 2.7 12 3.5V10.5C12 11.3 11.3 12 10.5 12H8V2Z"
                  stroke="currentColor"
                  stroke-width="1.2"
                />
              </svg>
              <span>知识库 (RAG)</span>
            </div>
            <div v-if="isAdmin" class="dropdown-item manage" @click="goToSettings('/admin')">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path
                  d="M2 12V7M7 12V2M12 12V9"
                  stroke="currentColor"
                  stroke-width="1.4"
                  stroke-linecap="round"
                />
              </svg>
              <span>管理后台</span>
            </div>
          </div>
        </Transition>
      </div>

      <span class="title-sep">/</span>
      <span class="title text-sm font-medium">{{ title || '选择或创建一个会话' }}</span>
    </div>

    <!-- 右侧: 统计 + 用户 -->
    <div class="flex items-center gap-3">
      <button type="button" class="stats-btn" title="用量统计" @click="statsStore.openPanel()">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path
            d="M2 13V3M2 13H14M5 13V8M8 13V5M11 13V10M14 13V6"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
      <span class="nickname text-sm">{{ authStore.user?.nickname }}</span>
      <button type="button" class="pw-btn text-xs" @click="openEmailModal">绑定邮箱</button>
      <button type="button" class="pw-btn text-xs" @click="openPwModal">修改密码</button>
      <button type="button" class="logout-btn text-xs" @click="$emit('logout')">退出登录</button>
    </div>

    <!-- 修改密码成功提示 -->
    <Transition name="dropdown">
      <div v-if="pwTip" class="pw-toast">{{ pwTip }}</div>
    </Transition>

    <!-- 修改密码弹窗 -->
    <div v-if="pwModalOpen" class="modal-mask" @click.self="closePwModal">
      <div class="pw-modal">
        <h3 class="pw-modal-title">修改密码</h3>
        <div class="pw-field">
          <label class="pw-label" for="pw-old">原密码</label>
          <input
            id="pw-old"
            v-model="pwForm.oldPassword"
            class="pw-input"
            type="password"
            autocomplete="current-password"
            placeholder="请输入当前密码"
          />
        </div>
        <div class="pw-field">
          <label class="pw-label" for="pw-new">新密码</label>
          <input
            id="pw-new"
            v-model="pwForm.newPassword"
            class="pw-input"
            type="password"
            maxlength="20"
            autocomplete="new-password"
            placeholder="6-20 位"
          />
        </div>
        <div class="pw-field">
          <label class="pw-label" for="pw-confirm">确认新密码</label>
          <input
            id="pw-confirm"
            v-model="pwForm.confirm"
            class="pw-input"
            type="password"
            maxlength="20"
            autocomplete="new-password"
            placeholder="再次输入新密码"
          />
        </div>
        <p v-if="pwError" class="pw-error">{{ pwError }}</p>
        <div class="pw-actions">
          <button type="button" class="pw-cancel" :disabled="pwSubmitting" @click="closePwModal">
            取消
          </button>
          <button type="button" class="pw-submit" :disabled="pwSubmitting" @click="submitPassword">
            {{ pwSubmitting ? '提交中…' : '确认修改' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 绑定邮箱弹窗 -->
    <div v-if="emailModalOpen" class="modal-mask" @click.self="closeEmailModal">
      <div class="pw-modal">
        <h3 class="pw-modal-title">绑定邮箱</h3>
        <div class="pw-field">
          <label class="pw-label" for="email-input">邮箱地址</label>
          <input
            id="email-input"
            v-model="emailValue"
            class="pw-input"
            type="email"
            maxlength="64"
            placeholder="用于后续找回密码等邮件服务"
          />
        </div>
        <p class="pw-hint">留空提交 = 清除绑定</p>
        <p v-if="emailError" class="pw-error">{{ emailError }}</p>
        <div class="pw-actions">
          <button
            type="button"
            class="pw-cancel"
            :disabled="emailSubmitting"
            @click="closeEmailModal"
          >
            取消
          </button>
          <button type="button" class="pw-submit" :disabled="emailSubmitting" @click="submitEmail">
            {{ emailSubmitting ? '提交中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  height: 56px;
  flex-shrink: 0;
  background: var(--bg-elevated);
  backdrop-filter: blur(var(--blur-glass));
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

/* ---------- 模型切换器 ---------- */
.model-switcher {
  position: relative;
}

.model-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
  white-space: nowrap;
}
.model-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  border-color: rgba(255, 255, 255, 0.12);
  color: var(--text-primary);
}
.model-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.model-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
}
.model-dot.active {
  background: var(--brand-to);
  box-shadow: 0 0 6px rgba(94, 234, 212, 0.5);
}

.model-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chevron {
  transition: transform var(--duration-base) var(--ease-base);
  color: var(--text-muted);
}
.chevron.open {
  transform: rotate(180deg);
}

/* ---------- 下拉 ---------- */
.outside-mask {
  position: fixed;
  inset: 0;
  z-index: 19;
}

.dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 20;
  width: 260px;
  padding: 6px;
  border-radius: 10px;
  background: rgba(20, 24, 40, 0.95);
  backdrop-filter: blur(var(--blur-glass));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
}

.dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-base);
}
.dropdown-item:hover {
  background: var(--bg-hover);
}
.dropdown-item.selected {
  background: rgba(94, 234, 212, 0.08);
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.item-name {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
}
.item-desc {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
}

.check {
  color: var(--brand-to);
  flex-shrink: 0;
}

.dropdown-divider {
  height: 1px;
  margin: 4px 6px;
  background: rgba(255, 255, 255, 0.06);
}

.dropdown-empty {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}

.dropdown-item.manage {
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12px;
}
.dropdown-item.manage:hover {
  color: var(--brand-to);
}

/* ---------- 其他 ---------- */
.title-sep {
  color: var(--text-muted);
  opacity: 0.4;
}

.title {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nickname {
  color: var(--text-secondary);
}

.stats-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}

.stats-btn:hover {
  background: var(--bg-hover);
  color: var(--brand-to);
}

.logout-btn {
  padding: 5px 12px;
  border: none;
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}

.logout-btn:hover {
  background: var(--bg-hover);
  color: var(--text-secondary);
}

/* ---------- 修改密码 ---------- */
.pw-btn {
  padding: 5px 12px;
  border: none;
  border-radius: var(--radius-btn);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.pw-btn:hover {
  background: var(--bg-hover);
  color: var(--brand-to);
}

.pw-toast {
  position: fixed;
  top: 68px;
  right: 24px;
  z-index: 60;
  padding: 8px 16px;
  border-radius: 8px;
  background: rgba(94, 234, 212, 0.12);
  border: 1px solid rgba(94, 234, 212, 0.3);
  color: var(--brand-to);
  font-size: 12px;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
}

.pw-modal {
  width: 320px;
  padding: 20px;
  border-radius: 14px;
  background: rgba(20, 24, 40, 0.98);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.5);
}

.pw-modal-title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.pw-field {
  margin-bottom: 12px;
}
.pw-label {
  display: block;
  margin-bottom: 5px;
  font-size: 12px;
  color: var(--text-secondary);
}
.pw-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.25);
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  transition: border-color var(--duration-base) var(--ease-base);
}
.pw-input:focus {
  border-color: rgba(94, 234, 212, 0.4);
}
.pw-input::placeholder {
  color: var(--text-muted);
}

.pw-error {
  margin: 0 0 12px;
  font-size: 12px;
  color: #fca5a5;
}

.pw-hint {
  margin: -6px 0 10px;
  font-size: 11px;
  color: var(--text-muted);
}

.pw-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}
.pw-cancel,
.pw-submit {
  padding: 7px 16px;
  border-radius: var(--radius-btn);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-base);
}
.pw-cancel {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: transparent;
  color: var(--text-secondary);
}
.pw-cancel:hover {
  background: var(--bg-hover);
}
.pw-submit {
  border: none;
  background: var(--brand-gradient);
  color: #fff;
  font-weight: 500;
}
.pw-submit:hover:not(:disabled) {
  filter: brightness(1.1);
}
.pw-cancel:disabled,
.pw-submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* ---------- 动画 ---------- */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all var(--duration-base) var(--ease-base);
}
.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
