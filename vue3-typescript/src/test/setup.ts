// W11.2 + W12.1 setup: Pinia isolation + global ElMessageBox stub +
// sessionStorage/localStorage clear (so request.ts isRelogin.show reset &
// cache.session.getJSON('sessionObj') returns null between specs).
import { beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  // Reset storage so the request.ts repeat-submit guard (cache.session)
  // and the tagsView loadPersistedViews (cache.local) start clean.
  if (typeof sessionStorage !== 'undefined') sessionStorage.clear()
  if (typeof localStorage !== 'undefined') localStorage.clear()
  // Reset the request.ts mutable module state (mutates isRelogin.show = true
  // when a 401 is observed; reset to default between specs).
  // We import lazily to avoid circular module init during setup phase.
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
