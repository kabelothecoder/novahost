/**
 * Home-level voice commands — "start", "stop", "pairs".
 *
 * Separate from the Voice Console *widget* on purpose. The widget is a
 * visualisation the user can reorder or hide; the commands are a
 * capability of the Home screen itself and must keep working when the
 * widget is hidden — which, on three of the five layouts, it is by
 * default.
 *
 * Extracted from HomeView.vue, where the recogniser was created at
 * module scope. That is a real leak once Home hosts swappable layouts:
 * a module-scope recogniser is shared across every mount and its
 * `onresult` handler is reassigned rather than replaced, so handlers
 * from previous mounts keep firing against stale closures.
 */

import { onMounted, onUnmounted } from 'vue'

const SR = window.SpeechRecognition || window.webkitSpeechRecognition

export function useVoiceCommands(handlers = {}) {
  /** Created per-caller, not per-module — see note above. */
  let recognition = null
  let stopped = false

  function start() {
    if (!SR || recognition) return
    recognition = new SR()
    recognition.continuous = true
    recognition.interimResults = false

    recognition.onresult = (e) => {
      const cmd = e.results[e.results.length - 1][0].transcript.trim().toUpperCase()
      if (cmd.includes('START'))      handlers.onStart?.()
      else if (cmd.includes('STOP'))  handlers.onStop?.()
      else if (cmd.includes('PAIRS')) handlers.onPairs?.()
      else handlers.onUnknown?.(cmd)
    }

    /* Continuous recognition ends itself on silence and on some errors.
       Without a restart the commands work once and then quietly stop —
       which reads as the feature being broken rather than idle. */
    recognition.onend = () => { if (!stopped) { try { recognition.start() } catch { /* already starting */ } } }
    recognition.onerror = (e) => {
      // 'not-allowed' means the user declined the mic; retrying that
      // forever would re-prompt on a loop.
      if (e.error === 'not-allowed' || e.error === 'service-not-allowed') stopped = true
    }

    try { recognition.start() } catch { /* fires if already started */ }
  }

  function stop() {
    stopped = true
    if (!recognition) return
    recognition.onend = null
    try { recognition.stop() } catch { /* not running */ }
    recognition = null
  }

  onMounted(start)
  onUnmounted(stop)

  return { start, stop, supported: Boolean(SR) }
}
