/**
 * Fits the 390×844 Home v4 design onto any screen.
 *
 * The design is drawn in fixed coordinates, so rather than re-flowing it
 * this scales one stage uniformly (k) and hands the layouts the slack:
 *
 *   W, H  stage size in design units: at least 390×844, larger on one axis
 *         when the screen's aspect differs from the design's.
 *   dx    left edge of the centred 390-wide column. Backgrounds and the
 *         Full-Bleed video span the whole stage; controls stay in the column.
 *   dy    extra height. Layouts push bottom controls down by dy and the hero
 *         by dy/2, so a tall phone gets air instead of a gap at the bottom.
 *   st    real status-bar clearance beyond the 44 units the design reserves.
 *
 * In `mini` mode (the layout-picker thumbnail) it measures its own box
 * instead of the viewport and ignores the safe area.
 *
 * Usage: bind `root` to the outer element, `probe` to an element with
 * `padding-top: env(safe-area-inset-top)`, and `stage` to the scaled stage.
 */

import { ref, computed, onMounted, onUnmounted } from 'vue'

export function useStage({ mini = false } = {}) {
  const root = ref(null)
  const probe = ref(null)
  const stage = ref(null)
  const box = ref({ w: 390, h: 844 })
  const safeTop = ref(0)
  const ready = ref(false)

  const k = computed(() => Math.min(box.value.w / 390, box.value.h / 844) || 1)
  const W = computed(() => box.value.w / k.value)
  const H = computed(() => box.value.h / k.value)
  const dx = computed(() => (W.value - 390) / 2)
  const dy = computed(() => H.value - 844)
  const st = computed(() => mini ? 0 : Math.max(0, safeTop.value / k.value - 44))

  const stageStyle = computed(() => ({
    width: W.value + 'px',
    height: H.value + 'px',
    transform: `scale(${k.value})`,
  }))

  /** A design y in one of three anchor groups, as a px string. */
  function y(v, group = 'top') {
    const shift = group === 'mid' ? dy.value / 2 : group === 'bot' ? dy.value : st.value
    return (v + shift) + 'px'
  }

  /** Pointer position in design units, relative to the 390-wide column. */
  function local(e) {
    const s = stage.value
    if (!s) return [0, 0]
    /* The on-screen scale, measured rather than assumed, so a pointer read
       during any ancestor transform still maps onto design units. */
    const r = s.getBoundingClientRect()
    const kk = r.width / W.value || k.value
    return [(e.clientX - r.left) / kk - dx.value, (e.clientY - r.top) / kk]
  }

  let ro = null
  function measure() {
    const el = root.value
    if (!el) return
    /* clientWidth/Height, not getBoundingClientRect: Home mounts inside the
       route transition's scale, and a rect read then is the shrunken size.
       ResizeObserver does not fire when only an ancestor transform changes,
       so that wrong size would stick. Layout size ignores transforms. */
    const w = el.clientWidth, h = el.clientHeight
    if (w && h) box.value = { w, h }
    if (probe.value) safeTop.value = parseFloat(getComputedStyle(probe.value).paddingTop) || 0
    ready.value = true
  }

  onMounted(() => {
    measure()
    if (window.ResizeObserver) {
      ro = new ResizeObserver(measure)
      ro.observe(root.value)
    } else {
      window.addEventListener('resize', measure)
    }
  })
  onUnmounted(() => {
    if (ro) ro.disconnect()
    else window.removeEventListener('resize', measure)
  })

  return { root, probe, stage, ready, k, W, H, dx, dy, st, stageStyle, y, local }
}
