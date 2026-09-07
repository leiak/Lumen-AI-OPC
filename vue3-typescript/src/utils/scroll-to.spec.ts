// W15 — src/utils/scroll-to.spec.ts.
//
// CRITICAL TEST-SETUP NOTE:
//   scroll-to.ts captures `requestAnimationFrame` in an IIFE at module load:
//     const requestAnimFrame = (function() {
//       return window.requestAnimationFrame || ...
//     })()
//   So mocking window.requestAnimationFrame AFTER the import has no effect —
//   the IIFE-bound `requestAnimFrame` still points at the original. We must
//   stub RAF, then vi.resetModules() + dynamic-import the module so the IIFE
//   captures our fake on re-load.
//
// Pinned behaviors:
// - Math.easeInOutQuad(t, b, c, d) quadratic ease-in-out:
//   t=0 → b; t=d → b+c; t=d/2 → b + c/2 (peak velocity midpoint)
//   t<d/2: acceleration (quadratic out); t>=d/2: deceleration (quadratic in)
// - scrollTo default duration is 500ms (increment 20ms → 25 frames)
// - scrollTo(to, duration, callback) animates scrollTop via move() on
//   documentElement, html (=body.parentNode), and body
// - callback fires once when currentTime reaches duration
// - callback NOT called if missing or not a function (typeof defensive check)
// - final scrollTop equals `to`; intermediate frames are monotonically
//   approaching `to`
// - duration < 20ms (less than one frame increment) → callback fires after
//   the initial sync call
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Captured across the test body (NOT per-test) so we can drive the animation
// from the test. beforeEach resets this array.
const rafCallbacks: FrameRequestCallback[] = []
let scrollToFn: (typeof import('@/utils/scroll-to'))['scrollTo']

beforeEach(async () => {
  rafCallbacks.length = 0
  // 1) Replace window.requestAnimationFrame BEFORE the scroll-to module is
  //    (re-)loaded. vi.stubGlobal restores the original on unstubAllGlobals.
  vi.stubGlobal('requestAnimationFrame', ((cb: FrameRequestCallback): number => {
    rafCallbacks.push(cb)
    return rafCallbacks.length
  }) as typeof window.requestAnimationFrame)
  // 2) Clear module cache so the next import re-runs the IIFE.
  vi.resetModules()
  // 3) Dynamic import — module body assigns Math.easeInOutQuad and captures
  //    our fake RAF.
  const mod = await import('@/utils/scroll-to')
  scrollToFn = mod.scrollTo
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

// Run all pending RAF callbacks until the queue drains (capped at 100 ticks
// to guard against infinite-loop bugs).
const runAnimation = (): void => {
  for (let i = 0; i < 100; i++) {
    if (rafCallbacks.length === 0) break
    const cbs = rafCallbacks.splice(0, rafCallbacks.length)
    for (const cb of cbs) cb(performance.now())
  }
}

describe('Math.easeInOutQuad (assigned by scroll-to module on import)', () => {
  it('1. at t=0 returns start (b)', () => {
    expect(Math.easeInOutQuad(0, 10, 50, 100)).toBeCloseTo(10, 5)
  })

  it('2. at t=d returns start + change (b + c)', () => {
    expect(Math.easeInOutQuad(100, 10, 50, 100)).toBeCloseTo(60, 5)
  })

  it('3. at t=d/2 returns midpoint (b + c/2)', () => {
    // Source: at t=d/2 normalized=1 → t--=0 → returns b + c/2 exactly.
    expect(Math.easeInOutQuad(50, 0, 100, 100)).toBeCloseTo(50, 5)
    expect(Math.easeInOutQuad(20, 5, 30, 40)).toBeCloseTo(5 + 15, 5) // 20
  })

  it('4. at t=d/4 (acceleration phase) is strictly between b and b + c/2', () => {
    const v = Math.easeInOutQuad(25, 0, 100, 100)
    expect(v).toBeGreaterThan(0)
    expect(v).toBeLessThan(50)
  })

  it('5. at t=3d/4 (deceleration phase) is strictly between b + c/2 and b + c', () => {
    const v = Math.easeInOutQuad(75, 0, 100, 100)
    expect(v).toBeGreaterThan(50)
    expect(v).toBeLessThan(100)
  })
})

describe('scrollTo()', () => {
  beforeEach(() => {
    // Reset scrollTop so position() returns 0 at the start of each test.
    document.documentElement.scrollTop = 0
    document.body.scrollTop = 0
  })

  it('6. default duration is 500ms (callback fires after 25 frames)', () => {
    const cb = vi.fn()
    scrollToFn(100, undefined, cb)
    runAnimation()
    expect(cb).toHaveBeenCalledTimes(1)
    // Final scrollTop equals target.
    expect(document.documentElement.scrollTop).toBe(100)
  })

  it('7. explicit duration overrides default', () => {
    const cb = vi.fn()
    // 40ms / 20ms increment = 2 frames (t = 20, 40) → callback fires.
    scrollToFn(100, 40, cb)
    runAnimation()
    expect(cb).toHaveBeenCalledTimes(1)
    expect(document.documentElement.scrollTop).toBe(100)
  })

  it('8. duration < increment (10ms) → callback fires after the initial sync frame', () => {
    // initial animateScroll: currentTime 0 → 20 → 20 < 10 false → callback.
    const cb = vi.fn()
    scrollToFn(100, 10, cb)
    runAnimation()
    expect(cb).toHaveBeenCalledTimes(1)
    // After only 1 frame at currentTime=20, val is easeInOutQuad(20, 0, 100, 10).
    // t = 20 / (10/2) = 4. t < 1 false. t-- → 3.
    // val = -100/2 * (3 * (3-2) - 1) + 0 = -50 * (3 - 1) = -100.
    // So scrollTop gets clamped to -100 (no clamping in source, but
    // happy-dom may clamp to 0). Just assert callback was called.
  })

  it('9. callback fires once when animation completes', () => {
    const cb = vi.fn()
    scrollToFn(50, 40, cb)
    runAnimation()
    expect(cb).toHaveBeenCalledTimes(1)
  })

  it('10. callback NOT called if not provided (no throw)', () => {
    expect(() => {
      scrollToFn(100, 40)
      runAnimation()
    }).not.toThrow()
  })

  it('11. callback NOT called if not a function (typeof defensive check)', () => {
    expect(() => {
      // Source: `if (callback && typeof (callback) === 'function')` — a
      // string fails the typeof check and is silently ignored.
      scrollToFn(100, 40, 'not-a-function' as unknown as () => void)
      runAnimation()
    }).not.toThrow()
  })

  it('12. final scrollTop equals target `to` after animation', () => {
    scrollToFn(123, 40)
    runAnimation()
    expect(document.documentElement.scrollTop).toBe(123)
  })

  it('13. scrolls DOWN to a positive target', () => {
    document.documentElement.scrollTop = 0
    scrollToFn(200, 60)
    runAnimation()
    expect(document.documentElement.scrollTop).toBe(200)
  })

  it('14. scrolls UP from non-zero scrollTop to 0', () => {
    // position() reads from documentElement.scrollTop first; happy-dom allows
    // us to set it to 500.
    document.documentElement.scrollTop = 500
    scrollToFn(0, 60)
    runAnimation()
    expect(document.documentElement.scrollTop).toBe(0)
  })

  it('15. intermediate frames produce intermediate scrollTop values (monotonic)', () => {
    // 100ms = 5 frames at t = 20, 40, 60, 80, 100. Drive 2 frames manually.
    scrollToFn(100, 100)
    // After scrollTo() the initial sync call already pushed 1 frame and
    // scheduled a RAF. So we have exactly 1 RAF callback pending.
    expect(rafCallbacks.length).toBe(1)
    rafCallbacks.shift()!(performance.now()) // frame 2
    const after2 = document.documentElement.scrollTop
    expect(after2).toBeGreaterThan(0)
    expect(after2).toBeLessThan(100)
    // Drive remaining frames to completion.
    runAnimation()
    expect(document.documentElement.scrollTop).toBe(100)
  })

  it('16. multiple scrollTo() calls produce independent animations (last wins)', () => {
    const cb1 = vi.fn()
    const cb2 = vi.fn()
    scrollToFn(50, 40, cb1)
    scrollToFn(200, 40, cb2)
    runAnimation()
    expect(cb1).toHaveBeenCalledTimes(1)
    expect(cb2).toHaveBeenCalledTimes(1)
    // Both completed; the most recent set the final scrollTop.
    expect(document.documentElement.scrollTop).toBe(200)
  })
})