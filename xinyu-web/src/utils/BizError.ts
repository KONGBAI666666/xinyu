/**
 * 业务错误: axios 拦截器把 code !== 0 的响应统一转成 BizError 抛出,
 * 页面层只写成功分支, 需要区分错误时 catch 后读 code/message
 */
export class BizError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}
