import { onMounted, onUnmounted } from 'vue'

/**
 * @description Publishes the on-screen keyboard's height as `--nv-kb` on the
 * given element.
 *
 * iOS shrinks the *visual* viewport when the keyboard opens but leaves the
 * layout viewport — and therefore `100dvh`, `vh` and `%` — untouched. A
 * bottom-anchored sheet is simply covered up, taking the CTA with it. The
 * only reliable measure is `visualViewport`, so read it and let CSS respond.
 *
 * Also pins the layout to the visual viewport's offsetTop, because iOS
 * scrolls the whole page up when a focused field would sit under the
 * keyboard, which otherwise drags the fixed shell out of view.
 *
 * @param {import('vue').Ref<HTMLElement|null>} target element to set the var on
 */
export function useKeyboardInset (target) {
  const vv = typeof window !== 'undefined' ? window.visualViewport : null

  function apply () {
    const el = target.value
    if (!el) return

    if (!vv) {
      el.style.setProperty('--nv-kb', '0px')
      return
    }

    // Everything the visual viewport lost off the bottom is keyboard
    // (plus, on older iOS, the accessory bar). Clamp: rubber-band scrolling
    // can briefly report a negative value.
    const hidden = window.innerHeight - vv.height - vv.offsetTop
    el.style.setProperty('--nv-kb', Math.max(0, Math.round(hidden)) + 'px')
  }

  onMounted(() => {
    apply()
    if (!vv) return
    vv.addEventListener('resize', apply)
    vv.addEventListener('scroll', apply)
  })

  onUnmounted(() => {
    if (!vv) return
    vv.removeEventListener('resize', apply)
    vv.removeEventListener('scroll', apply)
  })

  return { apply }
}
