import { fetchEventSource } from '@microsoft/fetch-event-source'
import { handleUnauthorized } from '@/api/request'
import { useMessageStore } from '@/stores/message'
import { tokenStorage } from '@/utils/storage'
import { BizError } from '@/utils/BizError'
import type { SseDeltaEvent, SseDoneEvent, SseErrorEvent, SseMetaEvent } from '@/types/api'

/** delta 渲染节流间隔: 先入缓冲, 批量 flush, 避免每个 token 都触发 DOM 更新 */
const FLUSH_INTERVAL_MS = 50

/**
 * SSE 流式聊天 composable（契约二, SSE 连接的唯一存在处）
 *
 * 职责边界: 建连 → 四事件分发 → 节流写入 message store → 结束清理;
 * store 只存状态, 不碰连接; 组件只调 send/stop, 不碰事件。
 *
 * 关键约定（评审锁死）:
 * - onerror 必须 throw: fetch-event-source 默认断线重连会再次 POST, 导致 USER 消息重复落库
 * - done/error 后服务端 complete, 这里主动 abort 防库默认的"连接关闭即重连"
 * - 停止生成 = abort(), 后端检测断连置 STOPPED 并保留已生成文本, 两端最终一致
 */
export function useSseChat() {
  const messageStore = useMessageStore()

  let controller: AbortController | null = null
  let flushTimer: number | null = null
  let deltaBuffer = ''
  /** 已收到终态事件（done/error）, 用于区分「主动/收尾 abort」与「异常中断」 */
  let finished = false

  /** 缓冲的 delta 批量写入 store */
  function flushDelta(): void {
    if (deltaBuffer) {
      messageStore.appendDelta(deltaBuffer)
      deltaBuffer = ''
    }
  }

  function startFlushTimer(): void {
    if (flushTimer === null) {
      flushTimer = window.setInterval(flushDelta, FLUSH_INTERVAL_MS)
    }
  }

  /** 连接收尾: 停节流器 + flush 残留 buffer */
  function cleanup(): void {
    if (flushTimer !== null) {
      window.clearInterval(flushTimer)
      flushTimer = null
    }
    flushDelta()
    controller = null
  }

  /**
   * 发送消息并建立 SSE 连接
   * @throws BizError 业务失败（40100/40400/42200 等 JSON 响应）时上抛给调用方展示
   */
  async function send(conversationId: string, content: string): Promise<void> {
    if (messageStore.streaming) return

    messageStore.appendUserMessage(conversationId, content)
    finished = false
    controller = new AbortController()
    const signal = controller.signal

    try {
      await fetchEventSource(`/api/conversations/${conversationId}/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenStorage.get() ?? ''}`,
        },
        body: JSON.stringify({ content, clientMessageId: crypto.randomUUID() }),
        signal,
        // 后台标签页不断开, 避免切 tab 触发意外重连（契约二 2.3）
        openWhenHidden: true,

        async onopen(response) {
          const type = response.headers.get('content-type') ?? ''
          if (response.ok && type.includes('text/event-stream')) return
          // 业务异常（40400/42200/40100…）走普通 JSON: 解析后按 BizError 上抛
          const result = await response.json().catch(() => null)
          const code = result?.code ?? 50000
          if (code === 40100) {
            handleUnauthorized()
          }
          throw new BizError(code, result?.message ?? '发送失败，请稍后重试')
        },

        onmessage(event) {
          switch (event.event) {
            case 'meta': {
              const meta = JSON.parse(event.data) as SseMetaEvent
              messageStore.confirmMeta(meta.userMessageId, meta.assistantMessageId)
              startFlushTimer()
              break
            }
            case 'delta': {
              const delta = JSON.parse(event.data) as SseDeltaEvent
              deltaBuffer += delta.content
              break
            }
            case 'done': {
              const done = JSON.parse(event.data) as SseDoneEvent
              finished = true
              flushDelta()
              messageStore.finishAssistant(done)
              // 服务端已 complete, 主动断开防止库默认重连
              controller?.abort()
              break
            }
            case 'error': {
              // LLM 异常（51001~51004）: 气泡置 FAILED, 连接由服务端收尾
              JSON.parse(event.data) as SseErrorEvent
              finished = true
              flushDelta()
              messageStore.failAssistant()
              controller?.abort()
              break
            }
          }
        },

        onerror(err) {
          // 锁死: 必须 throw 阻止自动重连（重连 = 再次 POST = USER 消息重复落库）
          throw err
        },
      })
    } catch (e) {
      // 主动 abort（停止/收尾）时 fetch 会以 AbortError 结束, 属正常路径
      if (!finished && !signal.aborted) {
        flushDelta()
        // meta 未到达时（如 40400/42200 业务拒绝）服务端未落库, 需回滚本地乐观插入的 USER 消息
        messageStore.failOrRollback()
        cleanup()
        throw e
      }
    }
    cleanup()
  }

  /** 停止生成: 断连 → 本地置 STOPPED（后端检测断连后同样置 STOPPED） */
  function stop(): void {
    if (!messageStore.streaming || finished) return
    finished = true
    controller?.abort()
    cleanup()
    messageStore.stopAssistant()
  }

  return { send, stop }
}
