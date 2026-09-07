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
  },
})
