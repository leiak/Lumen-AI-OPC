// W11.2 setup: Pinia isolation + global ElMessageBox stub.
import { beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

// Stub ElMessageBox so user store's "default password" / "expired password"
// confirm dialogs resolve silently without rendering UI. We preserve the rest
// of element-plus so component specs can render real components.
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue(undefined),
      alert: vi.fn().mockResolvedValue(undefined),
      prompt: vi.fn().mockResolvedValue({ value: '' }),
    },
  }
})

// Mock the default profile avatar so user store imports do not pull in a
// raw .jpg binary (Vite resolves the URL only at build time; vitest needs a
// explicit stub).
vi.mock('@/assets/images/profile.jpg', () => ({
  default: '/mock-profile.jpg',
}))
