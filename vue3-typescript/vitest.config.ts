import { defineConfig } from 'vitest/config'
import path from 'path'
import vue from '@vitejs/plugin-vue'
import autoImport from 'unplugin-auto-import/vite'

// Mirror the subset of vite.config.ts that tests need: alias resolution,
// Vue SFC compilation, and auto-import (so `defineStore` / `setActivePinia`
// / etc. are available without explicit imports — matches src/store/*).
//
// If you change vite.config.ts aliases or the auto-import list, update this
// file in the same commit.
export default defineConfig({
  plugins: [
    vue(),
    autoImport({
      imports: [
        'vue',
        'vue-router',
        'pinia',
        {
          '@/utils/dict': ['useDict'],
          '@/utils/ruoyi': ['selectDictLabel'],
        },
      ],
      // Disable dts generation for tests (write fails inside vitest temp dir)
      dts: false,
    }),
  ],
  resolve: {
    alias: {
      '~': path.resolve(__dirname, './'),
      '@': path.resolve(__dirname, './src'),
    },
    extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue'],
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{spec,test}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,vue}'],
      exclude: [
        'src/**/*.{spec,test}.ts',
        'src/main.ts',
        'src/App.vue',
        'src/permission.ts',
        // Barrel re-export files (no logic to cover). Per-file exceptions
        // (e.g. src/utils/index.ts) intentionally NOT excluded — they have
        // real functions and are covered by W14.
        'src/directive/index.ts',
        'src/layout/components/index.ts',
        'src/plugins/index.ts',
        'src/router/index.ts',
        'src/store/index.ts',
        'src/views/**',         // view templates not in scope for W12
        'src/types/**',
        // W12 scope focuses on utils + stores + api/opc + request. Vue SFC
        // templates (components/layout) need @vue/test-utils harness tests
        // and are deferred to W13+ — exclude to avoid dragging aggregate %.
        'src/layout/**',
        'src/components/**',
        'src/directive/**',
        // RuoYi built-in admin endpoints — outside OPC business scope.
        'src/api/login.ts',
        'src/api/menu.ts',
        'src/api/monitor/**',
        'src/api/system/**',
        'src/api/tool/**',
        'src/router/**',
        // Plugins exercised transitively; full coverage deferred.
        'src/plugins/auth.ts',
        'src/plugins/download.ts',
        'src/plugins/modal.ts',
        'src/plugins/tab.ts',
        // Store modules intentionally outside W12 scope (W13 adds permission).
        'src/store/modules/dict.ts',
        'src/store/modules/lock.ts',
        // Utils deferred to W15+.
        'src/utils/dict.ts',
        'src/utils/scroll-to.ts',
        'src/utils/dynamicTitle.ts',
      ],
      thresholds: {
        // After exclusions, every surviving file is >=80% covered. The
        // aggregate floor is set just below the actual rate so a regression
        // fails the gate; bumping requires more tests, not just config.
        lines: 80,
        functions: 75,
        branches: 70,
        statements: 80,
      },
    },
  },
})
