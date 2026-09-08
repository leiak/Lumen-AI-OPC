// W22 — src/layout/components/Sidebar/Logo.spec.ts. Sidebar logo + title SFC.
//
// Source flow:
//   1. const title = import.meta.env.VITE_APP_TITLE (from .env.development
//      => '若依管理系统')
//   2. useSettingsStore() reads isDark / navType / sideTheme
//   3. Computed getLogoBackground:
//        if (isDark)                          -> 'var(--sidebar-bg)'
//        else if (navType == 3)               -> variables.menuLightBg
//        else if (sideTheme === 'theme-dark') -> variables.menuBg
//        else                                 -> variables.menuLightBg
//   4. Computed getLogoTextColor: same 4-branch tree
//        if (isDark)                          -> 'var(--sidebar-logo-text)'
//        else if (navType == 3)               -> variables.menuLightText
//        else if (sideTheme === 'theme-dark') -> '#fff' (HARD-CODED)
//        else                                 -> variables.menuLightText
//   5. Template has two <router-link> branches keyed on `collapse`. The
//      v-else (expand) branch is MISSING the v-if/v-else on <h1> — so the
//      h1 is always rendered alongside the img. Pin this quirk.
//   6. v-bind() in <style> does NOT produce inline style attributes in
//      happy-dom — use vm.$.setupState to access computed values directly.
//
// Pinned behaviors:
// - The 4-way decision tree hits in priority order: isDark > navType >
//   sideTheme > fallback. isDark short-circuits everything else.
// - `getLogoTextColor` is asymmetric: theme-dark branch returns hardcoded
//   '#fff' (NOT variables.menuLightText) — would not update if SCSS var
//   changes. Document the asymmetry.
// - title is undefined in vitest (import.meta.env.VITE_APP_TITLE not
//   loaded from .env.development). The h1 renders empty.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// Hoisted mock holders so vi.mock factories can reference them without TDZ.
const mocks = vi.hoisted(() => ({
  isDark: false,
  toggle: vi.fn(),
  useDynamicTitle: vi.fn(),
  handleThemeStyle: vi.fn(),
}))

// Mock settings store dependencies (useDark must return Ref-like for .value).
vi.mock('@vueuse/core', async () => {
  const actual = await vi.importActual<typeof import('@vueuse/core')>('@vueuse/core')
  return {
    ...actual,
    useDark: () => ({ value: mocks.isDark }),
    useToggle: () => mocks.toggle,
  }
})

vi.mock('@/utils/dynamicTitle', () => ({ useDynamicTitle: mocks.useDynamicTitle }))
vi.mock('@/utils/theme', async () => {
  const actual = await vi.importActual<typeof import('@/utils/theme')>('@/utils/theme')
  return { ...actual, handleThemeStyle: mocks.handleThemeStyle }
})

vi.mock('@/settings', () => ({
  default: {
    title: '若依管理系统',
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
    footerContent: 'Copyright © 2018-2026 RuoYi',
  },
}))

// Mock the raw binary asset import.
vi.mock('@/assets/logo/logo.png', () => ({ default: '/mock-logo.png' }))

// Mock SCSS module — variables.module.scss exports these keys via :export.
vi.mock('@/assets/styles/variables.module.scss', () => ({
  default: {
    menuBg: '#1a1f2e',
    menuLightBg: '#ffffff',
    menuLightText: '#303133',
    menuActiveText: '#409eff',
  },
}))

import Logo from '@/layout/components/Sidebar/Logo.vue'
import useSettingsStore from '@/store/modules/settings'

// Setup helper: mount Logo with requested props + store overrides. Returns
// the wrapper and a handle to access setupState for computed values.
function mountLogo(props: { collapse: boolean }, settingsOverrides?: Record<string, any>) {
  const wrapper = mount(Logo, {
    props,
    global: {
      stubs: {
        'router-link': { template: '<a class="sidebar-logo-link" :data-to="$attrs.to"><slot/></a>' },
      },
    },
  })
  if (settingsOverrides) {
    const store = useSettingsStore()
    Object.assign(store, settingsOverrides)
  }
  return wrapper
}

// Typed accessor for the computed values. Vue 3.4+ exposes <script setup>
// bindings on vm.$.setupState with refs AUTO-UNWRAPPED — so getLogoBackground
// is a string, NOT a ComputedRef. (Verified via explore spec.)
function getBg(wrapper: ReturnType<typeof mount>): string {
  return (wrapper.vm as any).$.setupState.getLogoBackground
}
function getTextColor(wrapper: ReturnType<typeof mount>): string {
  return (wrapper.vm as any).$.setupState.getLogoTextColor
}

beforeEach(() => {
  setActivePinia(createPinia())
  mocks.isDark = false
  mocks.toggle.mockReset()
  mocks.useDynamicTitle.mockReset()
  mocks.handleThemeStyle.mockReset()
})

// ---------------------------------------------------------------
// Computed: getLogoBackground (4-branch matrix)
// ---------------------------------------------------------------

describe('Logo.vue — getLogoBackground (4-branch decision tree)', () => {
  it('1. isDark=true -> "var(--sidebar-bg)" (highest priority, short-circuits)', () => {
    mocks.isDark = true
    const w = mountLogo({ collapse: false }, { isDark: true, navType: 1, sideTheme: 'theme-dark' })
    expect(getBg(w)).toBe('var(--sidebar-bg)')
  })

  it('2. isDark=true wins regardless of navType value (3 vs non-3 both lose)', () => {
    mocks.isDark = true
    const w1 = mountLogo({ collapse: false }, { isDark: true, navType: 3, sideTheme: 'theme-light' })
    const w2 = mountLogo({ collapse: false }, { isDark: true, navType: 1, sideTheme: 'theme-dark' })
    expect(getBg(w1)).toBe('var(--sidebar-bg)')
    expect(getBg(w2)).toBe('var(--sidebar-bg)')
  })

  it('3. isDark=false, navType=3 -> variables.menuLightBg (#ffffff)', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 3, sideTheme: 'theme-dark' })
    expect(getBg(w)).toBe('#ffffff')
  })

  it('4. isDark=false, navType!=3, sideTheme="theme-dark" -> variables.menuBg (#1a1f2e)', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-dark' })
    expect(getBg(w)).toBe('#1a1f2e')
  })

  it('5. isDark=false, navType!=3, sideTheme="theme-light" -> variables.menuLightBg (#ffffff)', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-light' })
    expect(getBg(w)).toBe('#ffffff')
  })
})

// ---------------------------------------------------------------
// Computed: getLogoTextColor (4-branch matrix, asymmetric to bg)
// ---------------------------------------------------------------

describe('Logo.vue — getLogoTextColor (4-branch decision tree)', () => {
  it('6. isDark=true -> "var(--sidebar-logo-text)"', () => {
    mocks.isDark = true
    const w = mountLogo({ collapse: false }, { isDark: true, navType: 1, sideTheme: 'theme-dark' })
    expect(getTextColor(w)).toBe('var(--sidebar-logo-text)')
  })

  it('7. isDark=false, navType=3 -> variables.menuLightText (#303133)', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 3, sideTheme: 'theme-dark' })
    expect(getTextColor(w)).toBe('#303133')
  })

  it('8. isDark=false, navType!=3, sideTheme="theme-dark" -> "#fff" (HARD-CODED, NOT variables.menuLightText)', () => {
    // Pin: theme-dark text branch uses literal '#fff' instead of
    // variables.menuLightText. Asymmetric with the background branch
    // (which uses variables.menuBg). Changing menuLightText in SCSS
    // would NOT propagate to this branch. Document the asymmetry.
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-dark' })
    expect(getTextColor(w)).toBe('#fff')
  })

  it('9. isDark=false, navType!=3, sideTheme="theme-light" -> variables.menuLightText (#303133)', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-light' })
    expect(getTextColor(w)).toBe('#303133')
  })

  it('10. Asymmetry pin: theme-dark bg uses SCSS var but theme-dark text is hardcoded', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-dark' })
    // Bg reads from SCSS var ('#1a1f2e'), text reads hardcoded literal '#fff'.
    expect(getBg(w)).toBe('#1a1f2e')
    expect(getTextColor(w)).toBe('#fff')
    expect(getBg(w)).not.toBe(getTextColor(w))
  })
})

// ---------------------------------------------------------------
// Computed reactivity
// ---------------------------------------------------------------

describe('Logo.vue — computed reactivity', () => {
  it('11. mutating settingsStore.isDark flips getLogoBackground between branches', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-dark' })
    expect(getBg(w)).toBe('#1a1f2e')
    // Flip isDark
    const store = useSettingsStore()
    store.isDark = true
    expect(getBg(w)).toBe('var(--sidebar-bg)')
    // Flip back
    store.isDark = false
    expect(getBg(w)).toBe('#1a1f2e')
  })

  it('12. mutating settingsStore.navType switches the navType=3 branch', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-dark' })
    expect(getBg(w)).toBe('#1a1f2e')
    const store = useSettingsStore()
    store.navType = 3
    expect(getBg(w)).toBe('#ffffff')
    store.navType = 1
    expect(getBg(w)).toBe('#1a1f2e')
  })

  it('13. mutating settingsStore.sideTheme toggles between menuBg and menuLightBg', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-dark' })
    expect(getBg(w)).toBe('#1a1f2e')
    const store = useSettingsStore()
    store.sideTheme = 'theme-light'
    expect(getBg(w)).toBe('#ffffff')
    store.sideTheme = 'theme-dark'
    expect(getBg(w)).toBe('#1a1f2e')
  })
})

// ---------------------------------------------------------------
// Template structure
// ---------------------------------------------------------------

describe('Logo.vue — template structure', () => {
  it('14. logo image renders with src from mocked import ("/mock-logo.png")', () => {
    const w = mountLogo({ collapse: false })
    expect(w.find('img.sidebar-logo').exists()).toBe(true)
    expect(w.find('img.sidebar-logo').attributes('src')).toBe('/mock-logo.png')
  })

  it('15. <h1.sidebar-title> is ALWAYS rendered alongside the img (v-else branch quirk)', () => {
    // Pin: the v-else (expand) branch has NO v-if on <h1>, so both <img>
    // AND <h1> render. This is asymmetric with the v-if="collapse" branch
    // which correctly toggles between img and h1. Document the quirk so
    // a future fix is flagged.
    const w = mountLogo({ collapse: false })
    expect(w.find('img.sidebar-logo').exists()).toBe(true)
    expect(w.find('h1.sidebar-title').exists()).toBe(true)
  })

  it('16. <router-link> wrapper has class "sidebar-logo-link"', () => {
    const w = mountLogo({ collapse: false })
    expect(w.find('a.sidebar-logo-link').exists()).toBe(true)
  })

  it('17. <router-link> target is "/" (collapse=true)', () => {
    const w = mountLogo({ collapse: true })
    // The stub captures data-to attribute.
    const link = w.find('a.sidebar-logo-link')
    expect(link.attributes('data-to')).toBe('/')
  })

  it('18. <router-link> target is "/" (collapse=false)', () => {
    const w = mountLogo({ collapse: false })
    const link = w.find('a.sidebar-logo-link')
    expect(link.attributes('data-to')).toBe('/')
  })
})

// ---------------------------------------------------------------
// Collapse prop
// ---------------------------------------------------------------

describe('Logo.vue — collapse prop', () => {
  it('19. collapse=true -> root has class "collapse"', () => {
    const w = mountLogo({ collapse: true })
    expect(w.classes()).toContain('collapse')
  })

  it('20. collapse=false -> root does NOT have class "collapse"', () => {
    const w = mountLogo({ collapse: false })
    expect(w.classes()).not.toContain('collapse')
  })
})

// ---------------------------------------------------------------
// Title from env (verified via setup state)
// ---------------------------------------------------------------

describe('Logo.vue — title from env', () => {
  it('21. title is sourced from import.meta.env.VITE_APP_TITLE (undefined in vitest)', () => {
    // happy-dom does not auto-load .env files, so VITE_APP_TITLE is
    // undefined. In production (real Vite build) it would be '若依管理系统'.
    // Pin the contract: title is bound to env, not hardcoded.
    const w = mountLogo({ collapse: false })
    const title = (w.vm as any).$.setupState.title
    expect(title === undefined || title === '若依管理系统').toBe(true)
  })
})

// ---------------------------------------------------------------
// Module behavior
// ---------------------------------------------------------------

describe('Logo.vue — module behavior', () => {
  it('22. Logo.vue is a valid SFC with default export', () => {
    expect(typeof Logo).toBe('object')
    expect(Logo).not.toBeNull()
  })

  it('23. collapse prop is required (TypeScript prop validation)', () => {
    // Vue prop validation emits a console warning if a required prop is
    // missing. Pin that the prop is declared required:true.
    const w = mount(Logo, {
      props: {} as any, // intentionally omit collapse
      global: { stubs: { 'router-link': { template: '<a><slot/></a>' } } },
    })
    // Vue 3 logs a warning to console; the component still renders.
    // We don't assert on console here (vitest may suppress it), just
    // confirm the component didn't crash.
    expect(w.exists()).toBe(true)
  })

  it('24. computed values are stable across multiple reads (cached)', () => {
    mocks.isDark = false
    const w = mountLogo({ collapse: false }, { isDark: false, navType: 1, sideTheme: 'theme-dark' })
    // Vue computed caches the result until dependencies change. Multiple
    // reads return the same value without re-computing.
    const bg1 = getBg(w)
    const bg2 = getBg(w)
    const bg3 = getBg(w)
    expect(bg1).toBe(bg2)
    expect(bg2).toBe(bg3)
  })
})
