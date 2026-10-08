import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

export default defineConfigWithVueTs(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'src/types/api.generated.ts', // codegen 产物, 不参与 lint
    ],
  },

  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  skipFormatting,

  {
    languageOptions: {
      globals: { ...globals.browser },
    },
    rules: {
      // 项目现状: 部分交互用 @ts-ignore 桥接, 后续逐步清偿
      '@typescript-eslint/ban-ts-comment': 'off',
      // no-console 交给人工判断 (SSE 调试日志有用)
      'no-console': 'off',
    },
  },
)
