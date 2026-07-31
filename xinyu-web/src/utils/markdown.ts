/**
 * Markdown 安全渲染管线（M1-6）: marked 解析 → hljs 代码高亮 → DOMPurify 消毒
 *
 * 只用于 ASSISTANT 消息; USER 消息保持纯文本插值, 不经过本模块。
 *
 * 安全约定（评审锁死）:
 * - 输出必须经 DOMPurify 消毒后才能交给 v-html, 任何绕过都视为 XSS 漏洞
 * - 链接统一强制 target=_blank + rel=noopener, 防 tab-nabbing
 *
 * 性能约定: 流式期间由 useSseChat 的 50ms 节流控制调用频率,
 * 每次对全文重新解析（marked 对聊天级文本 <10KB 为亚毫秒级, 可接受）;
 * 无语言标注的代码块不做 highlightAuto（避免流式高频调用时的探测开销）。
 */
import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import 'highlight.js/styles/github-dark.css'

/** 无语言代码块的 HTML 转义（hljs.highlight 自带转义, 这里补齐 else 分支） */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 独立 Marked 实例, 避免污染全局单例配置 */
const marked = new Marked({
  gfm: true,
  // 聊天场景单换行即分行, 与各大 AI 产品行为一致
  breaks: true,
})

marked.use({
  renderer: {
    /** 代码块: 语言可识别时用 hljs 高亮, 否则仅转义展示 */
    code({ text, lang }) {
      const language = lang && hljs.getLanguage(lang) ? lang : ''
      const body = language
        ? hljs.highlight(text, { language }).value
        : escapeHtml(text)
      const langLabel = language ? `<span class="md-code-lang">${language}</span>` : ''
      return `<div class="md-code-block">${langLabel}<pre><code class="hljs">${body}</code></pre></div>`
    },
  },
})

// 外链安全: 消毒阶段统一补 target/rel（放 hook 里保证 v-html 内容全覆盖）
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

/**
 * Markdown 原文 → 可安全交给 v-html 的 HTML
 *
 * 流式中的不完整语法（未闭合代码块/表格）由 marked 按纯文本降级, 不会抛异常
 */
export function renderMarkdown(content: string): string {
  if (!content) {
    return ''
  }
  const rawHtml = marked.parse(content, { async: false })
  return DOMPurify.sanitize(rawHtml, { USE_PROFILES: { html: true } })
}
