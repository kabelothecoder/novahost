/**
 * Robot voice — ElevenLabs with a Web Speech fallback.
 *
 * Lifted verbatim in behaviour out of HomeView.vue, which is becoming a
 * host that swaps five layout components: a TTS instance owned by the
 * view would be torn down and rebuilt on every layout change, and any
 * phrase mid-flight would be orphaned.
 *
 * Errors are deliberately swallowed to the console rather than surfaced
 * — a robot that cannot speak is a degraded nicety, not a failure the
 * user needs a dialog about. The one exception is iOS autoplay, which
 * is reported through `onMuted` so the caller can say so in a toast.
 */

import { theme } from '../stores/theme.js'

const VOICE_FALLBACK = 'pFZP5JQG7iQjIQuC4Bku'

export function useSpeech() {
  /**
   * @param {string} text
   * @param {{ onMuted?: (text: string) => void }} [opts]
   */
  async function speak(text, opts = {}) {
    if (!text) return

    const apiKey  = localStorage.getItem('ELEVENLABS_API_KEY')
    const voiceId = localStorage.getItem('ELEVENLABS_VOICE_ID') || VOICE_FALLBACK

    if (apiKey) {
      try {
        const r = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${voiceId}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'xi-api-key': apiKey },
          body: JSON.stringify({
            text,
            model_id: 'eleven_monolingual_v1',
            voice_settings: { stability: 0.5, similarity_boost: 0.75 },
          }),
        })
        if (r.ok) {
          const url   = URL.createObjectURL(await r.blob())
          const audio = new Audio(url)
          // Release the blob once it has played, or the URL leaks for
          // the life of the session — one per phrase adds up fast on a
          // screen that speaks on every robot selection.
          audio.addEventListener('ended', () => URL.revokeObjectURL(url), { once: true })
          audio.play().catch((err) => {
            console.warn('Autoplay blocked by iOS:', err)
            URL.revokeObjectURL(url)
            opts.onMuted?.(text)
          })
          return
        }
      } catch (err) {
        console.warn('ElevenLabs unavailable, falling back:', err)
      }
    }

    if ('speechSynthesis' in window) {
      const u = new SpeechSynthesisUtterance(text)
      u.pitch = 0.8
      u.rate  = 0.9
      window.speechSynthesis.speak(u)
    }
  }

  return { speak }
}

/**
 * Short helper so widgets do not each re-check for the API — and the one
 * place the Haptic Feedback setting is enforced. That switch existed in
 * the old Settings screen as a local ref nothing read; routing every
 * vibrate through here is what makes it real.
 */
export function haptic(pattern = 30) {
  if (!theme.haptics) return
  if (navigator.vibrate) navigator.vibrate(pattern)
}
