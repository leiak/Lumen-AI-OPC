// W12.3 — app store spec. Sidebar toggle, device, size + cookie side effects.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import Cookies from 'js-cookie'
import useAppStore from '@/store/modules/app'

describe('app store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // Wipe any cookies left over from a previous spec.
    Cookies.remove('sidebarStatus')
    Cookies.remove('size')
  })

  // -----------------------------------------------------------------
  // Initial state
  // -----------------------------------------------------------------
  it('1. initial state: sidebar.opened=true, hide=false, device=desktop, size=default', () => {
    const app = useAppStore()
    expect(app.sidebar.opened).toBe(true)
    expect(app.sidebar.withoutAnimation).toBe(false)
    expect(app.sidebar.hide).toBe(false)
    expect(app.device).toBe('desktop')
    expect(app.size).toBe('default')
  })

  it('2. initial state reads cookie "sidebarStatus=0" -> opened=false', () => {
    Cookies.set('sidebarStatus', '0')
    setActivePinia(createPinia())
    const app = useAppStore()
    expect(app.sidebar.opened).toBe(false)
  })

  it('3. initial state reads cookie "size=large" -> size=large', () => {
    Cookies.set('size', 'large')
    setActivePinia(createPinia())
    const app = useAppStore()
    expect(app.size).toBe('large')
  })

  // -----------------------------------------------------------------
  // toggleSideBar
  // -----------------------------------------------------------------
  it('4. toggleSideBar() flips opened and writes "1" cookie when open', () => {
    const app = useAppStore()
    app.toggleSideBar()
    expect(app.sidebar.opened).toBe(false)
    expect(Cookies.get('sidebarStatus')).toBe('0')

    app.toggleSideBar()
    expect(app.sidebar.opened).toBe(true)
    expect(Cookies.get('sidebarStatus')).toBe('1')
  })

  it('5. toggleSideBar(true) (boolean arg) sets withoutAnimation=true', () => {
    // Signature is `toggleSideBar(withoutAnimation?: boolean)` — the param is
    // a boolean, not an object. Pin the actual API.
    const app = useAppStore()
    app.toggleSideBar(true)
    expect(app.sidebar.withoutAnimation).toBe(true)
  })

  it('6. toggleSideBar() when sidebar.hide=true is a no-op', () => {
    const app = useAppStore()
    app.sidebar.hide = true
    const before = app.sidebar.opened
    app.toggleSideBar()
    expect(app.sidebar.opened).toBe(before)
    // No cookie write either.
    expect(Cookies.get('sidebarStatus')).toBeUndefined()
  })

  // -----------------------------------------------------------------
  // closeSideBar
  // -----------------------------------------------------------------
  it('7. closeSideBar({withoutAnimation:true}) sets opened=false + writes "0"', () => {
    const app = useAppStore()
    app.closeSideBar({ withoutAnimation: true })
    expect(app.sidebar.opened).toBe(false)
    expect(app.sidebar.withoutAnimation).toBe(true)
    expect(Cookies.get('sidebarStatus')).toBe('0')
  })

  // -----------------------------------------------------------------
  // toggleDevice
  // -----------------------------------------------------------------
  it('8. toggleDevice("mobile") updates device to mobile', () => {
    const app = useAppStore()
    app.toggleDevice('mobile')
    expect(app.device).toBe('mobile')
  })

  // -----------------------------------------------------------------
  // setSize
  // -----------------------------------------------------------------
  it('9. setSize("large") updates size + writes cookie', () => {
    const app = useAppStore()
    app.setSize('large')
    expect(app.size).toBe('large')
    expect(Cookies.get('size')).toBe('large')
  })

  // -----------------------------------------------------------------
  // toggleSideBarHide
  // -----------------------------------------------------------------
  it('10. toggleSideBarHide(true) flips sidebar.hide=true', () => {
    const app = useAppStore()
    app.toggleSideBarHide(true)
    expect(app.sidebar.hide).toBe(true)
    app.toggleSideBarHide(false)
    expect(app.sidebar.hide).toBe(false)
  })

  // -----------------------------------------------------------------
  // After toggle: Cookies removed by beforeEach so re-init resets
  // -----------------------------------------------------------------
  it('11. cookie "sidebarStatus"="0" survives a Pinia reset (state factory re-reads)', () => {
    Cookies.set('sidebarStatus', '0')
    setActivePinia(createPinia())
    const app = useAppStore()
    expect(app.sidebar.opened).toBe(false)
    Cookies.remove('sidebarStatus')
  })

  it('12. cookie with value "1" parses to opened=true (truthy numeric coercion)', () => {
    Cookies.set('sidebarStatus', '1')
    setActivePinia(createPinia())
    const app = useAppStore()
    expect(app.sidebar.opened).toBe(true)
  })

  it('13. cookie "sidebarStatus"="garbage" -> opened=true (!!+NaN === false, BUT !!NaN === false too)', () => {
    // Source: `Cookies.get('sidebarStatus') ? !!+Cookies.get(...) : true`
    // - "garbage" is truthy (non-empty string)
    // - +"garbage" === NaN, !!NaN === false → opened=false
    Cookies.set('sidebarStatus', 'garbage')
    setActivePinia(createPinia())
    const app = useAppStore()
    expect(app.sidebar.opened).toBe(false)
  })

  it('14. setSize cookie + state are independent of sidebar cookies', () => {
    Cookies.set('sidebarStatus', '0')
    const app = useAppStore()
    app.setSize('medium')
    expect(app.size).toBe('medium')
    expect(Cookies.get('size')).toBe('medium')
    // sidebar still respects its cookie
    expect(app.sidebar.opened).toBe(false)
  })
})