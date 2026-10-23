import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { openBridge, resolveLink } from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * One push-to-talk turn: the user holds the mic, speaks, lets go.
 *
 * audio (or typed text) -> Fish ASR -> Gemini (strict JSON, fixed action list)
 * -> Fish TTS in the right voice -> { transcript, reply, action, audio_b64 }.
 *
 * ## Two speakers
 *
 * `mode: "robot"` is the trading robot on Home: the deep trading voice (or the
 * robot's own `expert_advisors.voice_id`), and it may propose actions.
 * `mode: "assist"` is Help & Support's teacher: its own voice, explains the
 * app and fixes problems, and can only navigate. It never proposes a trade.
 *
 * ## Nothing here trades
 *
 * A `trade_idea` is written to `voice_trade_ideas` with a 30 s expiry and
 * returned for the screen to show. Only a tap, through `voice-action`, places
 * it -- and that goes through `metacopier-execute`'s guardrails like any
 * other order. `kill_switch` likewise only asks the screen for a confirm.
 *
 * ## Positioning
 *
 * The robot speaks as itself. It never names a mentor or says "signals from":
 * NovaHost hosts the user's robot, it does not relay someone's trades.
 */

const FISH = 'https://api.fish.audio/v1'
const GEMINI_BASE = 'https://generativelanguage.googleapis.com/v1beta/models'
const MODEL = Deno.env.get('GEMINI_MODEL') || 'gemini-3.6-flash'
const TTS_MODEL = Deno.env.get('FISH_TTS_MODEL') || 's2.1-pro-free'

// Fish model ids, from the owner's saved voices.
const ROBOT_VOICE = Deno.env.get('FISH_ROBOT_VOICE_ID') || '7a18a1851d2649108c48ec9f2c80eb2c'
const ASSIST_VOICE = Deno.env.get('FISH_ASSIST_VOICE_ID') || '2624ab358cb6420989c343ba91d708a8'

const DAILY_TURNS = Number(Deno.env.get('VOICE_DAILY_TURNS') || 40)
const MAX_AUDIO_BYTES = 4 * 1024 * 1024 // ~60 s of phone audio is far below this
const SCREENS = ['home', 'pairs', 'symbols', 'accounts', 'settings', 'support', 'scanner', 'trades', 'terminal']

const fishKey = () => Deno.env.get('FISHAUDIO_API_KEY') || Deno.env.get('FISH_AUDIO_API_KEY') || ''

const APP_GUIDE = `
How the NovaHost app works (use this to teach and to fix problems):
- NovaHost hosts the user's trading robot in the cloud: no PC or VPS needs to stay on.
- Home: the robot. START/STOP runs or pauses it. Hold the mic to talk to it.
- Accounts: connect a MetaTrader 4/5 broker login (server, account number, password). Use the server name exactly as the broker spells it in MetaTrader. Wrong password or wrong server is the most common connect failure.
- Symbols: pick which of the robot's symbols to trade, the lot size and max trades at once. "No size configured" means a lot has not been set for that symbol.
- Trades: open and closed trades.
- Scanner: upload a chart screenshot for an AI read (a separate purchase).
- Settings: layout, look, notifications, robot name.
- Support: this help screen, plus device reset and contact options.
- Trades refused with "Autotrading disabled by server" usually mean the broker uses a different symbol name (like XAUUSD.ifx); the Symbols screen fixes that.
- Notifications on iPhone need the app added to the Home Screen first, then switched on in Settings.
`.trim()

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS_HEADERS })

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } })

  try {
    const db = createClient(Deno.env.get('SUPABASE_URL') ?? '', Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '')

    // ---- Input: multipart (audio) or JSON (typed text) ----------------------
    let fields: Record<string, string> = {}
    let audio: File | null = null
    const ctype = req.headers.get('content-type') || ''
    if (ctype.includes('multipart/form-data')) {
      const form = await req.formData()
      for (const [k, v] of form.entries()) {
        if (v instanceof File) { if (k === 'audio') audio = v } else fields[k] = String(v)
      }
    } else {
      fields = await req.json().catch(() => ({}))
    }

    const key = String(fields.license_key ?? '').trim().toUpperCase()
    if (!key) return json({ success: false, code: 'NO_LICENCE', error: 'A licence key is required.' }, 400)
    const mode = fields.mode === 'assist' ? 'assist' : 'robot'
    const screen = String(fields.screen ?? '').slice(0, 40)
    let history: { role: string; text: string }[] = []
    try { history = JSON.parse(String(fields.history ?? '[]')).slice(-6) } catch { /* none */ }

    if (!fishKey()) {
      console.error('[voice-turn] FISHAUDIO_API_KEY is not set')
      return json({ success: false, code: 'NOT_CONFIGURED', error: 'Voice is not set up on the server yet.' }, 503)
    }
    const geminiKey = Deno.env.get('GEMINI_API_KEY') || Deno.env.get('GOOGLE_API_KEY')
    if (!geminiKey) return json({ success: false, code: 'NOT_CONFIGURED', error: 'Voice is not set up on the server yet.' }, 503)

    // ---- Licence --------------------------------------------------------------
    const { data: lic, error: licErr } = await db
      .from('licenses')
      .select('id, status, expires_at, metadata, allowed_symbols, ea_id, expert_advisors!licenses_ea_id_fkey(name, display_name, symbols, voice_id)')
      .eq('license_key', key)
      .maybeSingle()
    if (licErr) throw licErr
    if (!lic) return json({ success: false, code: 'LICENCE_UNKNOWN', error: 'This licence key is not recognised.' }, 404)
    if (lic.status !== 'active' || (lic.expires_at && new Date(lic.expires_at) < new Date())) {
      return json({ success: false, code: 'LICENCE_INACTIVE', error: 'This licence is not active.' }, 403)
    }

    // ---- Daily cap (checked before spending anything) -------------------------
    const today = new Date().toISOString().slice(0, 10)
    const { data: use } = await db.from('voice_usage').select('turns').eq('license_id', lic.id).eq('day', today).maybeSingle()
    if ((use?.turns ?? 0) >= DAILY_TURNS) {
      return json({ success: false, code: 'DAILY_LIMIT', error: `That is today's ${DAILY_TURNS} voice chats used. Voice resets at midnight UTC.` }, 429)
    }

    // ---- Hear -----------------------------------------------------------------
    let transcript = String(fields.text ?? '').trim().slice(0, 600)
    let asrSeconds = 0
    if (!transcript && audio) {
      if (audio.size > MAX_AUDIO_BYTES) return json({ success: false, code: 'TOO_LONG', error: 'That recording is too long. Keep it under a minute.' }, 413)
      const fd = new FormData()
      fd.append('audio', audio, audio.name || 'speech')
      fd.append('language', 'en')
      const r = await fetch(`${FISH}/asr`, {
        method: 'POST',
        // Pro is the one that takes Safari's MP4/AAC and Chrome's WebM.
        headers: { Authorization: `Bearer ${fishKey()}`, model: 'transcribe-1-pro' },
        body: fd,
      })
      if (!r.ok) {
        console.error('[voice-turn] ASR', r.status, (await r.text()).slice(0, 300))
        return json({ success: false, code: 'ASR_FAILED', error: "I couldn't hear that. Try again." }, 502)
      }
      const a = await r.json()
      transcript = String(a.text ?? '').replace(/<\|[^|]*\|>/g, '').trim()
      asrSeconds = Number(a.duration ?? 0)
    }
    if (!transcript) return json({ success: false, code: 'NOTHING_HEARD', error: "I didn't catch anything. Hold the mic and speak." }, 422)

    // ---- Context ----------------------------------------------------------------
    const ea = (lic as any).expert_advisors ?? {}
    const robotName = String(ea.display_name || ea.name || 'your robot')
    const symbols: string[] = (lic.allowed_symbols?.length ? lic.allowed_symbols : ea.symbols) ?? []
    let account = 'No broker account connected.'
    let positions = ''
    const link = resolveLink(lic.metadata as Record<string, unknown>)
    if (link) {
      const bridge = openBridge(link, db)
      if (bridge) {
        const [s, p] = await Promise.all([bridge.accountSummary(5000), bridge.positions(5000)])
        if (s.ok) account = `Balance ${s.data.balance} ${s.data.currency ?? ''}, equity ${s.data.equity ?? '?'}.`
        else account = 'Broker account connected but not answering right now.'
        if (p.ok) positions = p.data.length
          ? `Open positions: ${p.data.slice(0, 8).map((x) => `${x.symbol} ${x.orderType} ${x.volume} lots P/L ${x.netProfit ?? x.profit ?? "?"}`).join('; ')}.`
          : 'No open positions.'
      }
    }

    const persona = mode === 'robot'
      ? `You are ${robotName}, the user's trading robot, hosted by NovaHost. You speak in first person as the robot: calm, confident, brief.
Never name a mentor, provider or person, and never say "signals from" anyone. You are the user's own robot.
You do not give financial advice; you can describe your own state, the account, and propose a trade idea only when the user asks for one, which the user must confirm on screen.
Symbols you may trade: ${symbols.join(', ') || 'none set'}.`
      : `You are the NovaHost assistant inside Help & Support. You teach how the app and the robots work and you walk users through fixing problems, step by step, in plain words.
You never propose trades or give financial advice. If asked for a trade, say the robot on Home handles that.`

    const actions = mode === 'robot'
      ? `"action" is null or one of:
{"type":"navigate","screen":one of ${JSON.stringify(SCREENS)}}
{"type":"trade_idea","pair":one of the allowed symbols,"side":"BUY"|"SELL","volume":number|null,"sl":number|null,"tp":number|null,"reason":short string}  (only when the user explicitly asks you to take or set up a trade)
{"type":"kill_switch"}  (only when the user asks to close everything / stop all trades)
{"type":"morning_briefing"}  (when asked for a briefing or summary; put the briefing in reply)
For trade_idea and kill_switch you have NOT done anything yet: say what you propose and ask the user to tap confirm on screen.`
      : `"action" is null or {"type":"navigate","screen":one of ${JSON.stringify(SCREENS)}} when sending the user to the right screen helps. The app then shows an Open button under your reply, so never say you will take them there yourself.`

    const prompt = `${persona}

${APP_GUIDE}

Live state: ${account} ${positions} Robot is ${lic.status}. User is on the "${screen || 'unknown'}" screen. UTC time ${new Date().toISOString().slice(0, 16)}.

Reply in JSON only: {"reply": string, "action": ...}
"reply" is what you say out loud: 1 to 3 short spoken sentences, no markdown, no lists, no emoji, numbers written so they read well aloud.
${actions}`

    const contents = [
      ...history.map((h) => ({ role: h.role === 'user' ? 'user' : 'model', parts: [{ text: String(h.text).slice(0, 500) }] })),
      { role: 'user', parts: [{ text: transcript }] },
    ]

    const g = await fetch(`${GEMINI_BASE}/${MODEL}:generateContent`, {
      method: 'POST',
      headers: { 'x-goog-api-key': geminiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: prompt }] },
        contents,
        generationConfig: { responseMimeType: 'application/json', temperature: 0.4, maxOutputTokens: 2048 },
      }),
    })
    if (!g.ok) {
      const t = await g.text()
      console.error('[voice-turn] Gemini', g.status, t.slice(0, 300))
      const busy = g.status === 429
      return json({
        success: false, transcript,
        code: busy ? 'BRAIN_BUSY' : 'BRAIN_FAILED',
        error: busy ? "I've hit today's thinking limit. Try again later." : 'Something went wrong on my side. Try again.',
      }, 503)
    }
    const gj = await g.json()
    let out: { reply?: string; action?: any } = {}
    const raw = (gj?.candidates?.[0]?.content?.parts ?? []).map((p: any) => p.text ?? '').join('')
    try { out = JSON.parse(raw.replace(/^```(json)?|```$/g, '').trim()) } catch { console.error('[voice-turn] unparsed', gj?.candidates?.[0]?.finishReason, raw.slice(0, 300)) }
    let reply = String(out.reply ?? '').trim() || "Sorry, I didn't get that. Say it again?"
    let action: any = null

    // ---- Validate the action: the model proposes, this file decides ----------
    const a = out.action
    if (a && typeof a === 'object') {
      if (a.type === 'navigate' && SCREENS.includes(a.screen)) action = { type: 'navigate', screen: a.screen }
      else if (mode === 'robot' && a.type === 'kill_switch') action = { type: 'kill_switch' }
      else if (mode === 'robot' && a.type === 'morning_briefing') action = { type: 'morning_briefing' }
      else if (mode === 'robot' && a.type === 'trade_idea') {
        const pair = String(a.pair ?? '').toUpperCase()
        const side = String(a.side ?? '').toUpperCase()
        const allowed = symbols.map((s) => s.toUpperCase())
        if (!link) {
          reply = 'Connect your trading account first, then I can set that up.'
        } else if (!allowed.includes(pair) || !['BUY', 'SELL'].includes(side)) {
          reply = `I can only trade ${symbols.join(', ') || 'my own symbols'}.`
        } else {
          const num = (v: unknown) => (typeof v === 'number' && isFinite(v) && v > 0 ? v : null)
          const { data: idea, error: ideaErr } = await db.from('voice_trade_ideas').insert({
            license_id: lic.id, pair, side,
            volume: num(a.volume), sl: num(a.sl), tp: num(a.tp),
            reason: String(a.reason ?? '').slice(0, 200),
          }).select('id, pair, side, volume, sl, tp, reason, expires_at').single()
          if (ideaErr) throw ideaErr
          action = { type: 'trade_idea', idea }
        }
      }
    }

    // ---- Speak ------------------------------------------------------------------
    const voice = mode === 'assist' ? ASSIST_VOICE : (ea.voice_id || ROBOT_VOICE)
    let audioB64: string | null = null
    let ttsBytes = 0
    const t = await fetch(`${FISH}/tts`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${fishKey()}`, 'Content-Type': 'application/json', model: TTS_MODEL },
      body: JSON.stringify({ text: reply, reference_id: voice, format: 'mp3', latency: 'balanced', normalize: true }),
    })
    if (t.ok) {
      const buf = new Uint8Array(await t.arrayBuffer())
      ttsBytes = new TextEncoder().encode(reply).length
      let bin = ''
      for (let i = 0; i < buf.length; i += 0x8000) bin += String.fromCharCode(...buf.subarray(i, i + 0x8000))
      audioB64 = btoa(bin)
    } else {
      // Text still goes back; the screen shows the caption without sound.
      console.error('[voice-turn] TTS', t.status, (await t.text()).slice(0, 300))
    }

    await db.rpc('voice_usage_bump', { p_license: lic.id, p_asr: asrSeconds, p_tts: ttsBytes })

    return json({ success: true, mode, transcript, reply, action, audio_b64: audioB64, audio_mime: 'audio/mpeg' })
  } catch (e) {
    console.error('[voice-turn]', e)
    return json({ success: false, code: 'SERVER_ERROR', error: 'Something went wrong on my side. Try again.' }, 500)
  }
})
