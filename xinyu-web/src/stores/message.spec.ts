import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useMessageStore, PAGE_SIZE } from '@/stores/message'
import type { MessageVO } from '@/types/api'

/** 构造一条历史消息 */
function msg(over: Partial<MessageVO> = {}): MessageVO {
  return {
    id: '1',
    conversationId: 'c1',
    sequenceNo: 1,
    messageType: 'ASSISTANT',
    content: 'hi',
    status: 'COMPLETED',
    promptTokens: null,
    completionTokens: null,
    modelCode: null,
    createdAt: '2026-01-01T00:00:00',
    ...over,
  }
}

describe('message store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('乐观更新与回滚', () => {
    it('appendUserMessage 插入带 local- 前缀的临时消息并进入 streaming', () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', '你好')

      expect(store.items).toHaveLength(1)
      expect(store.items[0].id.startsWith('local-')).toBe(true)
      expect(store.items[0].messageType).toBe('USER')
      expect(store.streaming).toBe(true)
    })

    it('confirmMeta 把临时 USER id 换成真实 id 并追加 GENERATING 占位', () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', '你好')
      store.confirmMeta('1001', '1002')

      expect(store.items).toHaveLength(2)
      expect(store.items[0].id).toBe('1001')
      expect(store.items[1].id).toBe('1002')
      expect(store.items[1].status).toBe('GENERATING')
    })

    it('failOrRollback: meta 未到达时回滚乐观插入的 USER 消息', () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', '你好')
      store.failOrRollback('网络连接中断，请稍后重试')

      // 服务端未落库 → 孤儿气泡必须被移除
      expect(store.items).toHaveLength(0)
      expect(store.streaming).toBe(false)
    })

    it('failOrRollback: 已有 GENERATING 占位时置 FAILED 并保留已生成文本', () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', '你好')
      store.confirmMeta('1001', '1002')
      store.appendDelta('部分内容')
      store.failOrRollback('LLM 请求超时')

      expect(store.items).toHaveLength(2)
      expect(store.items[1].status).toBe('FAILED')
      expect(store.items[1].content).toBe('部分内容')
      expect(store.items[1].errorMessage).toBe('LLM 请求超时')
      expect(store.streaming).toBe(false)
    })
  })

  describe('错误原因透传', () => {
    it('failAssistant 记录 errorMessage, 供气泡展示真实原因', () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', 'hi')
      store.confirmMeta('1', '2')
      store.failAssistant('LLM 认证失败, 请检查 API Key')

      expect(store.items[1].status).toBe('FAILED')
      expect(store.items[1].errorMessage).toBe('LLM 认证失败, 请检查 API Key')
    })

    it('未传 errorMessage 时置 null (气泡回落默认文案)', () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', 'hi')
      store.confirmMeta('1', '2')
      store.failAssistant()

      expect(store.items[1].errorMessage).toBeNull()
    })
  })

  describe('终态与停止', () => {
    it('finishAssistant 置 COMPLETED 并写入 token', () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', 'hi')
      store.confirmMeta('1', '2')
      store.appendDelta('回复')
      store.finishAssistant({
        messageId: '2',
        promptTokens: 10,
        completionTokens: 20,
        status: 'COMPLETED',
      })

      expect(store.items[1].status).toBe('COMPLETED')
      expect(store.items[1].promptTokens).toBe(10)
      expect(store.items[1].completionTokens).toBe(20)
      expect(store.streaming).toBe(false)
    })

    it('stopAssistant 置 STOPPED 并保留已生成文本', () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', 'hi')
      store.confirmMeta('1', '2')
      store.appendDelta('半截话')
      store.stopAssistant()

      expect(store.items[1].status).toBe('STOPPED')
      expect(store.items[1].content).toBe('半截话')
      expect(store.streaming).toBe(false)
    })

    it('appendDelta 只写入末尾的 GENERATING 项', () => {
      const store = useMessageStore()
      store.items = [msg({ id: 'x', status: 'COMPLETED', content: '历史' })]
      store.appendDelta('不应写入')

      expect(store.items[0].content).toBe('历史')
    })
  })

  describe('历史加载竞态', () => {
    it('load: 先发后至的过期响应被丢弃 (loadSeq 守卫)', async () => {
      const store = useMessageStore()

      // 第一次请求慢, 第二次快
      let resolveSlow!: (v: MessageVO[]) => void
      const slow = new Promise<MessageVO[]>((r) => (resolveSlow = r))

      const api = await import('@/api/modules/conversation')
      const spy = vi.spyOn(api.conversationApi, 'fetchMessages')
      spy.mockReturnValueOnce(slow as never)
      spy.mockResolvedValueOnce([msg({ id: 'new', content: '新会话消息' })] as never)

      const p1 = store.load('c-old')
      const p2 = store.load('c-new')
      await p2

      // 旧请求此刻才返回 —— 必须被丢弃
      resolveSlow([msg({ id: 'stale', content: '旧会话消息' })])
      await p1

      expect(store.items).toHaveLength(1)
      expect(store.items[0].id).toBe('new')
    })

    it('clear: 使在途的历史加载响应作废', async () => {
      const store = useMessageStore()
      let resolveReq!: (v: MessageVO[]) => void
      const pending = new Promise<MessageVO[]>((r) => (resolveReq = r))

      const api = await import('@/api/modules/conversation')
      const spy = vi.spyOn(api.conversationApi, 'fetchMessages')
      spy.mockReturnValueOnce(pending as never)

      const p = store.load('c1')
      store.clear()
      resolveReq([msg({ id: 'late' })])
      await p

      expect(store.items).toHaveLength(0)
    })

    it('load: 残留的 GENERATING 历史消息按 STOPPED 展示, 不恢复连接', async () => {
      const store = useMessageStore()
      const api = await import('@/api/modules/conversation')
      const spy = vi.spyOn(api.conversationApi, 'fetchMessages')
      spy.mockResolvedValueOnce([msg({ id: 'g', status: 'GENERATING' })] as never)

      await store.load('c1')

      expect(store.items[0].status).toBe('STOPPED')
    })

    it('load: 满页时 hasMore 为真', async () => {
      const store = useMessageStore()
      const api = await import('@/api/modules/conversation')
      const spy = vi.spyOn(api.conversationApi, 'fetchMessages')
      const full = Array.from({ length: PAGE_SIZE }, (_, i) => msg({ id: String(i) }))
      spy.mockResolvedValueOnce(full as never)

      await store.load('c1')

      expect(store.hasMore).toBe(true)
    })

    it('loadMore: 本地临时消息不能作为游标', async () => {
      const store = useMessageStore()
      store.appendUserMessage('c1', 'hi')
      store.hasMore = true

      const api = await import('@/api/modules/conversation')
      const spy = vi.spyOn(api.conversationApi, 'fetchMessages')

      await store.loadMore('c1')

      expect(spy).not.toHaveBeenCalled()
    })
  })
})
