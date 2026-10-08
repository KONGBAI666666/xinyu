/**
 * OpenAPI 生成契约的桥接导出 (手写文件, 与 codegen 产物分离)
 *
 * 用法:
 *   import type { ApiSchemas } from './api.bridge'
 *   type MessageVO = ApiSchemas['MessageVO']
 *
 * 注意: api.generated.ts 由 `npm run codegen` 从 openapi.json 生成, 禁止手改
 * (手改内容会在 CI 重新生成时被抹掉, 触发 codegen:check 失败);
 * 业务代码需要后端真源类型时统一从本文件取。
 */
import type { components, operations } from './api.generated'

export type ApiSchemas = components['schemas']
export type ApiOperations = operations
