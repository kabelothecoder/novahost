import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

/**
 * The News and Events wire for the Trading Symbols screen.
 *
 * ## What it replaces
 *
 * Nothing -- and that was the problem. Both sections shipped as finished
 * layouts over a permanent "Not connected" chip, because there was no news
 * table, no calendar table, and no function anywhere that returned either. The
 * screen promised a wire on your selected symbols and had no wire.
 *
 * ## Sources, and why these ones
 *
 * Both are free and keyless, which is the whole reason they were chosen: a
 * calendar behind a paid key is a calendar that is dark until somebody
 * remembers to renew a card, and this product has already been bitten by a
 * provider silently hitting its plan ceiling.
 *
 *   - Events   -- FairEconomy's weekly XML, the feed ForexFactory's own
 *                 calendar is published from. Impact, forecast and previous
 *                 travel with each row, which is what makes it usable for
 *                 "should I be in a trade right now".
 *   - News     -- public forex newswire RSS. Several are tried in order and
 *                 the first that answers wins, so one source going down is a
 *                 slower response rather than an empty screen.
 *
 * Neither needs a key, which is why there is no key to configure and no signup
 * to do. If a paid provider is ever wanted for better symbol tagging, it slots
 * in as one more entry in NEWS_FEEDS with its own parser -- the cache, the
 * filtering and the client contract do not change.
 *
 * ## Honest failure
 *
 * When a feed cannot be reached this returns `ok: false` for THAT feed with an
 * empty list -- never a stale-but-unlabelled payload and never invented
 * headlines. A plausible-looking headline on a trading screen is the one kind
 * of placeholder that can move real money, which is exactly why the screen was
 * built to say "not connected" rather than show samples. That principle
 * survives: the client renders age and source, so a user can always tell how
 * old the thing they are reading is.
 */

/** How long a cached feed is served before upstream is consulted again. */
const TTL_MS = {
  news:   10 * 60 * 1000,   // headlines go stale fast
  events: 30 * 60 * 1000,   // the week's calendar barely moves
}

/** Serve a cached payload this much past its TTL if upstream is down. A calendar
 *  from an hour ago beats an empty screen; one from yesterday does not. */
const STALE_GRACE_MS = 6 * 60 * 60 * 1000

const EVENTS_URL = 'https://nfs.faireconomy.media/ff_calendar_thisweek.xml'

/**
 * Tried in order. The first that parses to at least one item wins.
 *
 * All three were checked live on 2026-09-12: FXStreet answered 30 items,
 * InvestingLive 25, Investing.com 10. The old forexlive.com address 301s to
 * investinglive.com, so the destination is listed directly rather than leaning
 * on redirect-following that a future fetch policy might turn off.
 */
const NEWS_FEEDS = [
  { url: 'https://www.fxstreet.com/rss/news',        source: 'FXStreet'      },
  { url: 'https://investinglive.com/feed/news',      source: 'InvestingLive' },
  { url: 'https://www.investing.com/rss/news_1.rss', source: 'Investing'     },
]

/* ── Symbol → what a headline or an event has to mention ──────────────
   Keys are canonical symbols as the licence lists them. `ccy` drives the
   calendar filter (the feed tags every row with a currency); `words` drives
   the headline match.

   Kept explicit rather than derived from the symbol string. A regex over
   "NAS100" cannot know that a Federal Reserve decision moves it, and a gold
   trader wants "XAU", "gold" and "bullion" but not every USD headline. */
const SYMBOL_MAP: Record<string, { ccy: string[]; words: string[] }> = {
  XAUUSD: { ccy: ['USD'], words: ['gold', 'xau', 'bullion', 'precious metal'] },
  XAGUSD: { ccy: ['USD'], words: ['silver', 'xag', 'precious metal'] },
  NAS100: { ccy: ['USD'], words: ['nasdaq', 'nas100', 'tech stock', 'wall street', 'us stocks'] },
  US30:   { ccy: ['USD'], words: ['dow', 'us30', 'wall street', 'us stocks'] },
  US500:  { ccy: ['USD'], words: ['s&p', 'sp500', 'us500', 'wall street', 'us stocks'] },
  /* SPX500 and VIX are here because the live licences list them — the first
     draft of this map was written from the canonical names and missed both,
     which would have left two real instruments matching nothing. */
  SPX500: { ccy: ['USD'], words: ['s&p', 'sp500', 'spx', 'wall street', 'us stocks'] },
  GER40:  { ccy: ['EUR'], words: ['dax', 'ger40', 'german stocks'] },
  UK100:  { ccy: ['GBP'], words: ['ftse', 'uk100'] },
  USOIL:  { ccy: ['USD'], words: ['oil', 'crude', 'wti', 'brent', 'opec'] },
  BTCUSD: { ccy: ['USD'], words: ['bitcoin', 'btc', 'crypto'] },
  ETHUSD: { ccy: ['USD'], words: ['ethereum', 'eth', 'crypto'] },
  VIX:    { ccy: ['USD'], words: ['vix', 'volatility', 'risk off', 'risk-off'] },
}

/** A six-letter FX pair is its own mapping: both currencies, both names. */
function mappingFor(symbol: string): { ccy: string[]; words: string[] } {
  const s = symbol.toUpperCase().replace(/[^A-Z0-9]/g, '')
  if (SYMBOL_MAP[s]) return SYMBOL_MAP[s]
  if (/^[A-Z]{6}$/.test(s)) {
    const a = s.slice(0, 3), b = s.slice(3)
    return { ccy: [a, b], words: [a.toLowerCase(), b.toLowerCase(), `${a}/${b}`.toLowerCase()] }
  }
  return { ccy: [], words: [s.toLowerCase()] }
}

/* ── Tiny XML/RSS reading ────────────────────────────────────────────
   No parser dependency. Both feeds are flat lists of one repeated element
   with text-only children, which `matchAll` handles in a few lines; pulling
   a DOM implementation into an edge function to read <title> would cost more
   cold-start than the fetch it is parsing. */

function decodeEntities(s: string): string {
  return s
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#0?39;|&apos;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, d) => String.fromCharCode(Number(d)))
    .replace(/&amp;/g, '&')          // last, or the others double-decode
    .trim()
}

/** Strips tags, collapses whitespace. Descriptions arrive as HTML fragments. */
function plainText(s: string): string {
  return decodeEntities(s).replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
}

function blocks(xml: string, tag: string): string[] {
  return [...xml.matchAll(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`, 'gi'))].map((m) => m[1])
}

function tagText(block: string, tag: string): string {
  const m = block.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`, 'i'))
  return m ? decodeEntities(m[1]) : ''
}

/* ── Events ──────────────────────────────────────────────────────────*/

type WireEvent = {
  id: string
  title: string
  currency: string
  impact: 'high' | 'medium' | 'low' | 'none'
  at: string | null
  forecast: string
  previous: string
}

/**
 * FairEconomy rows carry `<date>` (MM-DD-YYYY) and `<time>` ("12:30pm")
 * separately. The time may also be "All Day", "Tentative" or empty; anything
 * that does not resolve to a real instant keeps a null `at` and the client
 * renders it as undated rather than defaulting to midnight -- an all-day CPI
 * release shown as 00:00 is a lie about when to be flat.
 *
 * ## The times are GMT, not US Eastern
 *
 * Worth stating plainly because the feed does not say so anywhere, the source
 * is ForexFactory (whose own site renders in the viewer's local zone), and the
 * obvious assumption is Eastern. It is wrong by four hours. Checked against
 * three independent anchors in the 2026-09-07 feed:
 *
 *   US CPI      12:30pm  -- released 08:30 ET, which is 12:30 UTC
 *   ECB rate    12:15pm  -- announced 14:15 CET, which is 12:15 UTC
 *   UK GDP       6:00am  -- released 07:00 London, which is 06:00 UTC
 *
 * All three land exactly on UTC and none on Eastern. An Eastern reading shifts
 * every high-impact release four to five hours late, which on this screen means
 * telling a user the market is quiet during NFP.
 */
function parseEventTime(dateStr: string, timeStr: string): string | null {
  const d = dateStr.match(/^(\d{2})-(\d{2})-(\d{4})$/)
  if (!d) return null
  const [, mm, dd, yyyy] = d
  const t = timeStr.match(/^(\d{1,2}):(\d{2})\s*(am|pm)$/i)
  if (!t) return null
  let hour = Number(t[1]) % 12
  if (t[3].toLowerCase() === 'pm') hour += 12

  return new Date(Date.UTC(Number(yyyy), Number(mm) - 1, Number(dd), hour, Number(t[2]))).toISOString()
}

const IMPACT: Record<string, WireEvent['impact']> = {
  high: 'high', medium: 'medium', low: 'low', holiday: 'none', none: 'none',
}

async function fetchEvents(): Promise<{ items: WireEvent[]; source: string }> {
  const res = await fetch(EVENTS_URL, {
    headers: { 'User-Agent': 'NovaHost/1.0 (+https://novahost-ea.app)', Accept: 'application/xml, text/xml' },
  })
  if (!res.ok) throw new Error(`calendar ${res.status}`)
  const xml = await res.text()

  const items = blocks(xml, 'event').map((b, i) => {
    const title = plainText(tagText(b, 'title'))
    const currency = tagText(b, 'country').toUpperCase()
    const at = parseEventTime(tagText(b, 'date'), tagText(b, 'time'))
    return {
      // Stable within a week's feed, which is all a list key needs. Title and
      // date together, so two releases for the same currency on one day do not
      // collapse into one row.
      id: `${currency}-${tagText(b, 'date')}-${title}`.replace(/\s+/g, '_').slice(0, 120) || `ev-${i}`,
      title,
      currency,
      impact: IMPACT[tagText(b, 'impact').toLowerCase()] ?? 'none',
      at,
      forecast: plainText(tagText(b, 'forecast')),
      previous: plainText(tagText(b, 'previous')),
    }
  }).filter((e) => e.title)

  return { items, source: 'FairEconomy' }
}

/* ── News ────────────────────────────────────────────────────────────*/

type WireNews = {
  id: string
  title: string
  summary: string
  url: string
  source: string
  at: string | null
}

function parseRss(xml: string, source: string): WireNews[] {
  return blocks(xml, 'item').map((b, i) => {
    const title = plainText(tagText(b, 'title'))
    const link = decodeEntities(tagText(b, 'link'))
    const date = tagText(b, 'pubDate') || tagText(b, 'dc:date')
    const when = date ? new Date(date) : null
    return {
      id: link || `${source}-${i}`,
      title,
      // Two sentences at most. The card shows a headline and a line of
      // context, and an RSS description is frequently the entire article.
      summary: plainText(tagText(b, 'description')).split(/(?<=[.!?])\s/).slice(0, 2).join(' ').slice(0, 240),
      url: link,
      source,
      at: when && !Number.isNaN(when.getTime()) ? when.toISOString() : null,
    }
  }).filter((n) => n.title)
}

async function fetchNews(): Promise<{ items: WireNews[]; source: string }> {
  const errors: string[] = []
  for (const feed of NEWS_FEEDS) {
    try {
      const res = await fetch(feed.url, {
        headers: { 'User-Agent': 'NovaHost/1.0 (+https://novahost-ea.app)', Accept: 'application/rss+xml, application/xml, text/xml' },
      })
      if (!res.ok) { errors.push(`${feed.source} ${res.status}`); continue }
      const items = parseRss(await res.text(), feed.source)
      if (items.length) return { items, source: feed.source }
      errors.push(`${feed.source} empty`)
    } catch (e) {
      errors.push(`${feed.source} ${(e as Error).message}`)
    }
  }
  throw new Error(errors.join('; ') || 'no news source answered')
}

/* ── Cache ───────────────────────────────────────────────────────────*/

type Cached = { payload: unknown[]; fetched_at: string; source: string | null }

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS_HEADERS })

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
    })

  try {
    const db = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
    )

    const body = await req.json().catch(() => ({}))
    const key = String(body.license_key ?? '').trim().toUpperCase()
    const symbols: string[] = Array.isArray(body.symbols)
      ? body.symbols.map((s: unknown) => String(s).toUpperCase()).filter(Boolean).slice(0, 12)
      : []

    /* Licence-scoped like every other read in this project. The wire itself is
       public information, but an unauthenticated function on this project is
       one more endpoint anyone can run up a bill on, and `broadcast-signal`
       already taught that lesson. The check is deliberately cheap: existence
       and not-revoked, no device binding -- reading a headline is not trading. */
    if (!key) return json({ success: false, code: 'NO_LICENCE', error: 'A licence key is required.' }, 401)
    const { data: lic } = await db
      .from('licenses').select('id, status').eq('license_key', key).maybeSingle()
    if (!lic || lic.status === 'revoked') {
      return json({ success: false, code: 'LICENCE_UNKNOWN', error: 'This licence is not recognised.' }, 401)
    }

    const { data: rows } = await db
      .from('market_wire_cache').select('feed, payload, fetched_at, source')
    const cache = new Map<string, Cached>(
      (rows ?? []).map((r) => [r.feed as string, r as unknown as Cached]),
    )

    /**
     * Serve cache while fresh, refresh when not, fall back to stale on failure.
     *
     * The upstream fetch is awaited rather than backgrounded. A calendar that
     * arrives on the NEXT screen open is a screen that is empty on the first
     * one, and these feeds answer in well under a second.
     */
    const serve = async <T>(
      feed: 'news' | 'events',
      fetcher: () => Promise<{ items: T[]; source: string }>,
    ) => {
      const hit = cache.get(feed)
      const age = hit ? Date.now() - new Date(hit.fetched_at).getTime() : Infinity
      if (hit && age < TTL_MS[feed]) {
        return { ok: true, items: hit.payload as T[], source: hit.source, fetched_at: hit.fetched_at }
      }

      try {
        const { items, source } = await fetcher()
        const fetched_at = new Date().toISOString()
        await db.from('market_wire_cache')
          .upsert({ feed, payload: items, fetched_at, source, ok: true }, { onConflict: 'feed' })
        return { ok: true, items, source, fetched_at }
      } catch (e) {
        console.error(`[market-wire] ${feed} fetch failed: ${(e as Error).message}`)
        if (hit && age < STALE_GRACE_MS) {
          return { ok: true, items: hit.payload as T[], source: hit.source, fetched_at: hit.fetched_at, stale: true }
        }
        return { ok: false, items: [] as T[], source: null, fetched_at: null, error: 'Feed unavailable.' }
      }
    }

    const [news, events] = await Promise.all([
      serve<WireNews>('news', fetchNews),
      serve<WireEvent>('events', fetchEvents),
    ])

    /* ── Filter to the licence's symbols ───────────────────────────────
       Only when symbols were sent. An empty list means "I have not chosen
       yet", and the right answer to that is the whole wire rather than
       nothing -- a user who has enabled no symbols still wants to see that
       NFP is in forty minutes. */
    const maps = symbols.map(mappingFor)
    const wantCcy = new Set(maps.flatMap((m) => m.ccy))
    const words = maps.flatMap((m) => m.words)

    const eventItems = symbols.length
      ? events.items.filter((e) => wantCcy.has(e.currency))
      : events.items

    /**
     * News is RANKED, not filtered.
     *
     * A hard filter measured 2 hits out of 25 on a live feed for a
     * XAUUSD/NAS100/US30 licence -- a section headed "wire on your selected
     * symbols" that shows two rows while the wire is carrying twenty-five is
     * not focus, it is a screen that looks broken. The ones that mention the
     * user's instruments go first and carry `matched` so the client can badge
     * them; the rest follow as general market context rather than being thrown
     * away.
     */
    const newsItems = news.items
      .map((n) => {
        const hay = `${n.title} ${n.summary}`.toLowerCase()
        return { ...n, matched: symbols.length > 0 && words.some((w) => hay.includes(w)) }
      })
      .sort((a, b) => (Number(b.matched) - Number(a.matched)) || String(b.at).localeCompare(String(a.at)))

    /**
     * The whole week, in order, with each row saying whether it has printed.
     *
     * Returning only what is still upcoming reads correctly on a Wednesday and
     * empties the screen completely on a Saturday -- the feed is a single
     * this-week file (nextweek/thismonth/today are all 404), so from Friday's
     * close until the new week publishes there is nothing ahead. An empty
     * Events card on a weekend looks like the feature is broken rather than
     * like the market is shut.
     *
     * So the week is kept whole. What already printed carries its actual
     * against the forecast, which is the most useful thing on the card, and
     * `past` lets the client render the two states differently. When the list
     * has to be trimmed, high impact survives first -- losing a low-impact
     * housing print to keep CPI is the right trade every time.
     */
    const now = Date.now()
    const RANK = { high: 0, medium: 1, low: 2, none: 3 }
    const dated = eventItems
      .filter((e) => e.at)
      .map((e) => ({ ...e, past: new Date(e.at!).getTime() < now }))

    const keep = new Set(
      [...dated]
        .sort((a, b) =>
          (RANK[a.impact] - RANK[b.impact]) ||
          // Within an impact band, what is still ahead beats what has gone.
          (Number(a.past) - Number(b.past)) ||
          a.at!.localeCompare(b.at!))
        .slice(0, 12)
        .map((e) => e.id),
    )
    const weekEvents = dated
      .filter((e) => keep.has(e.id))
      .sort((a, b) => a.at!.localeCompare(b.at!))

    return json({
      success: true,
      news: {
        ok: news.ok,
        stale: news.stale === true,
        source: news.source,
        fetched_at: news.fetched_at,
        error: news.error ?? null,
        items: newsItems.slice(0, 12),
      },
      events: {
        ok: events.ok,
        stale: events.stale === true,
        source: events.source,
        fetched_at: events.fetched_at,
        error: events.error ?? null,
        items: weekEvents,
      },
    })
  } catch (err) {
    console.error('[market-wire]', err)
    return json({ success: false, error: (err as Error)?.message ?? 'Unexpected error.' }, 500)
  }
})
