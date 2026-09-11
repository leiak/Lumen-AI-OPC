// W12.3 — settings store spec. 13 state fields, localStorage override,
// changeSetting, setTitle, toggleTheme, handleThemeStyle.
//
// IMPORTANT: the settings store reads `localStorage['layout-setting']` at
// MODULE LOAD time (`const storageSetting = JSON.parse(...)`). It does NOT
// re-read on subsequent state factory calls. So tests that depend on a
// particular localStorage value MUST reset the module + re-import AFTER
// setting localStorage. The `loadStore()` helper does this.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// Mock holders hoisted so vi.mock factories can reference them without TDZ.
const mocks = vi.hoisted(() => ({
  themeRef: { isDark: false, toggle: vi.fn() },
  setTitle: vi.fn(),
  handleThemeStyle: vi.fn(),
}))

// Mock @/settings so defaultSettings fields are deterministic.
// useDark must return an object with `.value` because the store does
// `isDark.value` on the returned object (vueuse's useDark returns a Ref).
vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  return {
    ...actual,
    useDark: () => ({ value: mocks.themeRef.isDark }),
    useToggle: () => mocks.themeRef.toggle,
  }
})

vi.mock('@/utils/dynamicTitle', () => ({
  useDynamicTitle: () => mocks.setTitle(),
}))

vi.mock('@/utils/theme', async () => {
  const actual = await vi.importActual<typeof import('@/utils/theme')>('@/utils/theme')
  return { ...actual, handleThemeStyle: mocks.handleThemeStyle }
})

vi.mock('@/settings', () => ({
  default: {
    title: 'OPC 管理后台',
    sideTheme: 'theme-dark',
    showSettings: true,
    navType: 1,
    tagsView: true,
    tagsViewPersist: false,
    tagsIcon: false,
    tagsViewStyle: 'card',
    fixedHeader: true,
    sidebarLogo: true,
    dynamicTitle: false,
    footerVisible: false,
    footerContent: 'Copyright © 2018-2026 OPC',
  },
}))

// Re-imports the settings store module so localStorage['layout-setting']
// (read at module-eval time) reflects current localStorage state.
async function loadStore() {
  vi.resetModules()
  return (await import('@/store/modules/settings')).default
}

describe('settings store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    mocks.themeRef.isDark = false
    mocks.themeRef.toggle.mockReset()
    mocks.setTitle.mockReset()
    mocks.handleThemeStyle.mockReset()
  })

  // -----------------------------------------------------------------
  // Initial state
  // -----------------------------------------------------------------
  it('1. initial state has 14 expected fields with default values', async () => {
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    expect(s.title).toBe('')
    expect(s.theme).toBe('#409EFF')
    expect(s.sideTheme).toBe('theme-dark')
    expect(s.showSettings).toBe(true)
    expect(s.navType).toBe(1)
    expect(s.tagsView).toBe(true)
    expect(s.tagsViewPersist).toBe(false)
    expect(s.tagsIcon).toBe(false)
    expect(s.tagsViewStyle).toBe('card')
    expect(s.fixedHeader).toBe(true)
    expect(s.sidebarLogo).toBe(true)
    expect(s.dynamicTitle).toBe(false)
    expect(s.footerVisible).toBe(false)
    expect(s.footerContent).toBe('Copyright © 2018-2026 OPC')
    expect(s.isDark).toBe(false)
  })

  it('2. localStorage["layout-setting"] override takes precedence over defaults', async () => {
    localStorage.setItem(
      'layout-setting',
      JSON.stringify({
        theme: '#ff0000',
        navType: 3,
        tagsView: false,
        sidebarLogo: false,
      }),
    )
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    expect(s.theme).toBe('#ff0000')
    expect(s.navType).toBe(3)
    expect(s.tagsView).toBe(false)
    expect(s.sidebarLogo).toBe(false)
    // Unspecified keys fall back to defaults
    expect(s.sideTheme).toBe('theme-dark')
  })

  it('3. corrupt localStorage["layout-setting"] JSON throws (no try/catch — KNOWN bug)', async () => {
    // KNOWN BUG: source `JSON.parse(localStorage.getItem('layout-setting') || '{}')`
    // has no try/catch. A corrupted JSON value crashes module load with
    // SyntaxError. Pin this so a future fix to wrap in try/catch is flagged.
    localStorage.setItem('layout-setting', '{not valid json')
    await expect(loadStore()).rejects.toThrow(SyntaxError)
  })

  // -----------------------------------------------------------------
  // changeSetting
  // -----------------------------------------------------------------
  it('4. changeSetting({key,value}) mutates state but does NOT write localStorage', async () => {
    // KNOWN: changeSetting only updates the reactive state. It does NOT
    // persist to localStorage. This means runtime changes to settings are
    // lost on reload — only values present in localStorage at module load
    // time take effect. Pin the actual contract.
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    s.changeSetting({ key: 'theme', value: '#abcdef' })
    expect(s.theme).toBe('#abcdef')
    const stored = JSON.parse(localStorage.getItem('layout-setting') || '{}')
    expect(stored.theme).toBeUndefined() // <- not persisted
  })

  it('5. changeSetting({key,value}) updates tagsView (Pinia state mutation)', async () => {
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    s.changeSetting({ key: 'tagsView', value: false } as any)
    expect(s.tagsView).toBe(false)
  })

  it('6. changeSetting with truly unknown key is a silent no-op (hasOwnProperty guard)', async () => {
    // Pinia's store proxy returns false for hasOwnProperty of non-state keys,
    // so changeSetting skips the assignment for unknown keys.
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    const before = JSON.stringify(s.$state)
    s.changeSetting({ key: 'nonExistentField', value: 'foo' } as any)
    expect(JSON.stringify(s.$state)).toBe(before)
  })

  // -----------------------------------------------------------------
  // setTitle
  // -----------------------------------------------------------------
  it('7. setTitle("Dashboard") updates title + calls useDynamicTitle()', async () => {
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    s.setTitle('Dashboard')
    expect(s.title).toBe('Dashboard')
    expect(mocks.setTitle).toHaveBeenCalledTimes(1)
  })

  // -----------------------------------------------------------------
  // toggleTheme
  // -----------------------------------------------------------------
  it('8. toggleTheme() flips isDark + calls useToggle().toggle + handleThemeStyle on next tick', async () => {
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    expect(s.isDark).toBe(false)
    s.toggleTheme()
    expect(s.isDark).toBe(true)
    expect(mocks.themeRef.toggle).toHaveBeenCalledTimes(1)
    // handleThemeStyle is called inside nextTick; wait for the microtask flush.
    await new Promise((r) => setTimeout(r, 0))
    expect(mocks.handleThemeStyle).toHaveBeenCalledWith(s.theme)
  })

  it('9. toggleTheme() twice returns to isDark=false (idempotent pairs)', async () => {
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    s.toggleTheme()
    expect(s.isDark).toBe(true)
    s.toggleTheme()
    expect(s.isDark).toBe(false)
  })

  // -----------------------------------------------------------------
  // setTitle called multiple times
  // -----------------------------------------------------------------
  it('10. setTitle called twice increments useDynamicTitle call count by 2', async () => {
    const useSettingsStore = await loadStore()
    const s = useSettingsStore()
    s.setTitle('A')
    s.setTitle('B')
    expect(mocks.setTitle).toHaveBeenCalledTimes(2)
    expect(s.title).toBe('B')
  })
})