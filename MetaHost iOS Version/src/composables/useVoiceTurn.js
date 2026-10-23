/**
 * Push-to-talk, end to end: record while held, send on release, speak the
 * answer, and surface any action the robot proposed.
 *
 * `mode` is 'robot' (Home: the trading voice, may propose a trade or the kill
 * switch) or 'assist' (Help & Support: the teaching voice, navigate only).
 * Server side is `voice-turn`; confirms go to `voice-action`.
 *
 * ## iOS audio unlock
 *
 * Safari only lets an <audio> element play if it was first played inside a
 * user gesture. The answer arrives seconds after the finger lifted, outside
 * any gesture, so one shared element is primed with a silent clip on press
 * and the real answer is swapped into that same element later.
 *
 * ## Nothing here places a trade
 *
 * A proposed trade comes back as `pending` with a 30 s expiry. Only
 * `confirm()`, which a tap calls, sends it to `voice-action`.
 */
import { ref, onUnmounted } from 'vue'
import { supabase } from '../utils/supabaseClient.ts'
import { secStorage } from '../utils/secStorage.js'

const SILENT = 'data:audio/mpeg;base64,SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4LjI5LjEwMAAAAAAAAAAAAAAA//tQxAADB8AhSmxhIIEVCSiJrDCQBTcu3UrAIwUdkRgQbFAZC1CQEwTJ9mjRvBA4UOLD8nKVOWfh+UlK3z/177OXrfOdKl7pyn3Xf//WreyTRUoAWgBgkOAGbZHBgG1OF6zM82DWbZaUmMBptgQhGjsyYqc9ae9XFz280948NMBWInljyzsNRFLPWdnZGWrddDsjK1unuSrVN9jJsK8KuQtQCtMBjCEtImISdNKJOopIpBFpNSMbIHCSRpRR5iakjTiyzLhchUUBwCgyKiweBv/7UsQbg8isVNoMPMjAAAA0gAAABEVFGmgqK////9bP/6XCykxBTUUzLjEwMKqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq'

let player = null
function primePlayer() {
  if (!player) player = new Audio()
  try { player.src = SILENT; player.play().catch(() => {}) } catch { /* no audio */ }
}

async function call(fn, body) {
  let { data, error } = await supabase.functions.invoke(fn, { body })
  // Unwrap a non-2xx so its own message reaches the user.
  if (error && !data && error.context && typeof error.context.json === 'function') {
    try { data = await error.context.json(); error = null } catch { /* no body */ }
  }
  if (error || !data) return { success: false, error: 'No connection. Check your internet and try again.' }
  return data
}

export function useVoiceTurn(mode = 'robot', { screen = '', onNavigate } = {}) {
  const state = ref('idle') // idle | listen | think | answer
  const caption = ref('')
  const transcript = ref('')
  const pending = ref(null) // { type: 'trade_idea', idea } | { type: 'kill_switch' }
  const busy = ref(false)
  const suggest = ref(null) // a screen the answer points to, when the caller opens it by tap
  const history = []

  let rec = null
  let chunks = []
  let stream = null
  let cancelled = false
  let clearT = 0

  function releaseMic() {
    stream?.getTracks().forEach((t) => t.stop())
    stream = null
  }

  async function start() {
    if (state.value === 'listen' || state.value === 'think') return
    clearTimeout(clearT)
    primePlayer()
    caption.value = ''; transcript.value = ''; cancelled = false
    state.value = 'listen'
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      state.value = 'answer'
      caption.value = 'I need the microphone. Allow it in Settings, then hold the button again.'
      clearT = setTimeout(reset, 5000)
      return
    }
    // The finger may have lifted while the permission prompt was up.
    if (state.value !== 'listen') { releaseMic(); return }
    chunks = []
    rec = new MediaRecorder(stream)
    rec.ondataavailable = (e) => { if (e.data?.size) chunks.push(e.data) }
    rec.onstop = () => { releaseMic(); if (!cancelled) send() }
    rec.start()
  }

  function stop(cancel = false) {
    cancelled = cancel
    if (rec && rec.state !== 'inactive') {
      if (!cancel) state.value = 'think'
      rec.stop()
    } else {
      releaseMic()
    }
    rec = null
    if (cancel) state.value = 'idle'
  }

  async function send(text) {
    state.value = 'think'
    const fd = new FormData()
    fd.append('license_key', secStorage.getItem('LICENSE_KEY') || '')
    fd.append('mode', mode)
    fd.append('screen', screen)
    fd.append('history', JSON.stringify(history.slice(-6)))
    if (text) fd.append('text', text)
    else {
      const type = chunks[0]?.type || 'audio/mp4'
      const blob = new Blob(chunks, { type })
      if (blob.size < 1500) { // a tap, not speech
        state.value = 'idle'
        return
      }
      fd.append('audio', blob, type.includes('webm') ? 'speech.webm' : 'speech.m4a')
    }
    const res = await call('voice-turn', fd)
    if (!res.success) {
      answer(res.error || 'Something went wrong. Try again.', null)
      return
    }
    transcript.value = res.transcript || ''
    history.push({ role: 'user', text: res.transcript }, { role: 'model', text: res.reply })
    suggest.value = res.action?.type === 'navigate' ? '/' + res.action.screen : null
    if (suggest.value && onNavigate) setTimeout(() => onNavigate(suggest.value), 1200)
    if (res.action?.type === 'trade_idea' || res.action?.type === 'kill_switch') pending.value = res.action
    answer(res.reply, res.audio_b64 ? `data:${res.audio_mime || 'audio/mpeg'};base64,${res.audio_b64}` : null)
  }

  function answer(text, src) {
    state.value = 'answer'
    caption.value = text
    const done = () => { clearT = setTimeout(reset, 2500) }
    if (src && player) {
      player.onended = done
      player.src = src
      player.play().catch(done)
    } else {
      clearT = setTimeout(reset, Math.max(4000, text.length * 60))
    }
  }

  function reset() { if (state.value === 'answer') { state.value = 'idle'; caption.value = '' } }

  async function confirm() {
    const p = pending.value
    if (!p || busy.value) return
    busy.value = true
    const body = { license_key: secStorage.getItem('LICENSE_KEY') || '' }
    const res = p.type === 'trade_idea'
      ? await call('voice-action', { ...body, action: 'confirm_trade', idea_id: p.idea.id })
      : await call('voice-action', { ...body, action: 'kill_switch' })
    busy.value = false
    pending.value = null
    return res
  }

  function dismiss() { pending.value = null }

  onUnmounted(() => { stop(true); clearTimeout(clearT); player?.pause() })

  return { state, caption, transcript, pending, busy, suggest, start, stop, send, confirm, dismiss }
}
