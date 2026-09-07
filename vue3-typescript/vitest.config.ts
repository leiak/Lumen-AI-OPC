import { defineConfig } from 'vitest/config'
import path from 'path'

// Re-declare the subset of vite.config.ts that we need so mergeConfig can
// consume it. The full vite.config.ts is a callback (defineConfig(({mode,...}) => ...))
// which mergeConfig rejects, so we lift the alias/server/css settings here.
//
// If you change vite.config.ts aliases, update this file in the same commit.
export default defineConfig({
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
