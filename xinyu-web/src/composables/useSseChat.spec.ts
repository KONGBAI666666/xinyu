import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useMessageStore } from '@/stores/message'
import { useSseChat } from '@/composables/useSseChat'
import { BizError } from '@/utils/BizError'

/**
 * mock @microsoft/fetch-event-source
 *
 * 把真实库替换为可控桩, 用于精确驱动 meta/delta/done/error 四条事件路径,
 * 并断言 onerror 的行为 (必须 throw, 否则库会自动重连 → 重复 POST → 消息重复落库)。
 */
const fetchEventSourceMock = vi.fn()
vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: (...args: unknown[]) => fetchEventSourceMock(...args),
}))

/** 简化的事件对象 */
type Handler = {
  onopen: (r: Response) => Promise<void> | void
  onmessage: (e: { event: string; data: string }) => void
  onerror: (e: unknown) => unknown
}

describe('useSseChat', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchEventSourceMock.mockReset()
  })

  describe('幂等与请求构造', () => {
    it('clientMessageId 使用 crypto.randomUUID 而非时间戳', async () => {
      fetchEventSourceMock.mockImplementation(async (_url: string, h: Handler) => {
        await h.onopen(
          new Response('', { status: 200, headers: { 'content-type': 'text/event-stream' } }),
        )
        h.onmessage({
          event: 'done',
          data: JSON.stringify({
            messageId: '2',
            promptTokens: 1,
            completionTokens: 1,
            status: 'COMPLETED',
          }),
        })
      })

      const { send } = useSseChat()
      await send('c1', '你好')

      const body = JSON.parse(fetchEventSourceMock.mock.calls[0][1].body)
      // UUID v4 形如 xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
      expect(body.clientMessageId).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
      )
    })

    it('streaming 中重复调用 send 直接返回, 不发第二个请求', async () => {
      fetchEventSourceMock.mockImplementation(() => new Promise<void>(() => {}))
      const store = useMessageStore()
      const { send } = useSseChat()

      void send('c1', '第一条')
      await send('c1', '第二条')

      expect(fetchEventSourceMock).toHaveBeenCalledTimes(1)
      expect(store.items.filter((m) => m.messageType === 'USER')).toHaveLength(1)
    })
  })

  describe('事件分发', () => {
    it('meta → delta → done 全链路写入 store', async () => {
      fetchEventSourceMock.mockImplementation(async (_url: string, h: Handler) => {
        await h.onopen(
          new Response('', { status: 200, headers: { 'content-type': 'text/event-stream' } }),
        )
        h.onmessage({
          event: 'meta',
          data: JSON.stringify({ userMessageId: '1001', assistantMessageId: '1002' }),
        })
        h.onmessage({ event: 'delta', data: JSON.stringify({ content: '你好' }) })
        h.onmessage({ event: 'delta', data: JSON.stringify({ content: '世界' }) })
        h.onmessage({
          event: 'done',
          data: JSON.stringify({
            messageId: '1002',
            promptTokens: 5,
            completionTokens: 8,
            status: 'COMPLETED',
          }),
        })
      })

      const store = useMessageStore()
      const { send } = useSseChat()
      await send('c1', '提问')

      expect(store.items).toHaveLength(2)
      expect(store.items[0].id).toBe('1001')
      expect(store.items[1].content).toBe('你好世界')
      expect(store.items[1].status).toBe('COMPLETED')
      expect(store.streaming).toBe(false)
    })

    it('error 事件把 LLM 真实原因透传到气泡 (回归: 旧实现丢弃了 message)', async () => {
      fetchEventSourceMock.mockImplementation(async (_url: string, h: Handler) => {
        await h.onopen(
          new Response('', { status: 200, headers: { 'content-type': 'text/event-stream' } }),
        )
        h.onmessage({
          event: 'meta',
          data: JSON.stringify({ userMessageId: '1', assistantMessageId: '2' }),
        })
        h.onmessage({
          event: 'error',
          data: JSON.stringify({ code: 51001, message: 'LLM 认证失败, 请检查 API Key' }),
        })
      })

      const store = useMessageStore()
      const { send } = useSseChat()
      await send('c1', '提问')

      expect(store.items[1].status).toBe('FAILED')
      // 关键: 用户必须能看到具体原因, 而不是笼统的"生成失败"
      expect(store.items[1].errorMessage).toBe('LLM 认证失败, 请检查 API Key')
    })
  })

  describe('onerror 必须阻止自动重连', () => {
    it('onerror 不吞异常: 抛出后 send 以 BizError 拒绝, 且不重试', async () => {
      fetchEventSourceMock.mockImplementation(async (_url: string, h: Handler) => {
        await h.onopen(
          new Response('', { status: 200, headers: { 'content-type': 'text/event-stream' } }),
        )
        // 模拟连接中断: 库会调用 onerror; onerror 必须 throw 而非返回,
        // 否则库会按默认策略指数退避重连 → 再次 POST → USER 消息重复落库。
        h.onerror(new Error('network down'))
        // 真实库在 onerror 抛出后不会继续, 这里用 reject 模拟该行为
        throw new Error('network down')
      })

      const store = useMessageStore()
      const { send } = useSseChat()

      await expect(send('c1', 'hi')).rejects.toBeInstanceOf(BizError)
      // 只发了一次请求 (未重试)
      expect(fetchEventSourceMock).toHaveBeenCalledTimes(1)
      // 断连时已有占位则置 FAILED, 无占位则回滚 —— 两种都不该留下 streaming 状态
      expect(store.streaming).toBe(false)
    })

    it('onerror 直接把异常抛回调用方 (返回值不是 thenable, 库无法继续重试)', async () => {
      let captured: unknown = 'not-called'
      fetchEventSourceMock.mockImplementation(async (_url: string, h: Handler) => {
        await h.onopen(
          new Response('', { status: 200, headers: { 'content-type': 'text/event-stream' } }),
        )
        const err = new Error('boom')
        try {
          h.onerror(err)
          captured = 'returned-normally'
        } catch (e) {
          captured = e
        }
      })

      const { send } = useSseChat()
      await send('c1', 'hi').catch(() => {})

      // onerror 必须向上抛, 而不是正常返回 —— 正常返回会让库按默认策略重连
      expect(captured).toBeInstanceOf(Error)
      expect((captured as Error).message).toBe('boom')
    })
  })

  describe('业务错误的两种通道', () => {
    it('onopen 收到 JSON 业务错误 (40400) → BizError 上抛并回滚本地消息', async () => {
      fetchEventSourceMock.mockImplementation(async (_url: string, h: Handler) => {
        await h.onopen(
          new Response(
            JSON.stringify({ code: 40400, message: '该会话的角色已被删除, 请新建会话' }),
            {
              status: 200,
              headers: { 'content-type': 'application/json' },
            },
          ),
        )
      })

      const store = useMessageStore()
      const { send } = useSseChat()

      await expect(send('c1', 'hi')).rejects.toMatchObject({ code: 40400 })
      // meta 未到达 → 服务端未落库 → 本地 USER 消息必须回滚
      expect(store.items).toHaveLength(0)
    })

    it('40100 触发登录跳转兜底', async () => {
      const onopenSpy = vi.fn()
      fetchEventSourceMock.mockImplementation(async (_url: string, h: Handler) => {
        await h.onopen(
          new Response(JSON.stringify({ code: 40100, message: '未登录或登录已过期' }), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          }),
        )
      })
      void onopenSpy

      const { send } = useSseChat()
      await expect(send('c1', 'hi')).rejects.toMatchObject({ code: 40100 })
    })
  })

  describe('停止生成', () => {
    it('stop 通知后端并置 STOPPED, 保留已生成文本', async () => {
      const stopSpy = vi.fn().mockResolvedValue(undefined)
      const api = await import('@/api/modules/conversation')
      vi.spyOn(api.conversationApi, 'stopGeneration').mockImplementation(stopSpy)

      fetchEventSourceMock.mockImplementation(async (_url: string, h: Handler) => {
        await h.onopen(
          new Response('', { status: 200, headers: { 'content-type': 'text/event-stream' } }),
        )
        h.onmessage({
          event: 'meta',
          data: JSON.stringify({ userMessageId: '1', assistantMessageId: '2' }),
        })
        h.onmessage({ event: 'delta', data: JSON.stringify({ content: '已经生成的部分' }) })
        // 之后不结束 —— 模拟用户中途停止
        await new Promise<void>(() => {})
      })

      const store = useMessageStore()
      const { send, stop } = useSseChat()

      const sending = send('c1', 'hi')
      // 等 meta/delta 被处理
      await vi.waitFor(() => expect(store.items.length).toBe(2))
      stop()

      expect(stopSpy).toHaveBeenCalledWith('c1')
      expect(store.items[1].status).toBe('STOPPED')
      expect(store.streaming).toBe(false)

      void sending
    })

    it('非 streaming 状态下 stop 是空操作', () => {
      const { stop } = useSseChat()
      expect(() => stop()).not.toThrow()
    })
  })
})
