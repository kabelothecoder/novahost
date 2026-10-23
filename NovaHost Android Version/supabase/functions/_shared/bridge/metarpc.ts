import type {
  AccountSummary, BridgeErrorCode, BridgeFailure, BridgeResult, BrokerBridge, ConnectInput, ConnectResult,
  Deal, HealthState, OrderRefusal, OrderRequest, OrderResult, OrderType, Position, Quote, SymbolSpec,
} from './types.ts'

/**
 * The MetaRPC adapter (mrpc.pro).
 *
 * MetaRPC runs one real MT4/MT5 terminal per linked account and puts a
 * GET-only REST API in front of it. Two headers: `APIKey` (ours, every call)
 * and `id` (the terminal, every call after connect).
 *
 * ## The account id IS the terminal id
 *
 * `bridge_account_id` holds `terminalInstanceGuid` as MetaRPC returns it,
 * e.g. `mt5_live_6ee0...`. The prefix names the platform, which picks the host
 * (`mt4.mrpc.pro` / `mt5.mrpc.pro`), so nothing else has to be stored.
 *
 * ## Things that are different from MetaCopier, on purpose
 *
 *  - Every reply is HTTP 200 with `data` or `error`. Never branch on status.
 *  - Refusals are MetaTrader retcodes as data, so `classifyRetcode` maps
 *    numbers, not prose.
 *  - **A timed-out order can still execute.** `COMMAND_TIMEOUT_EXCEEDED` is
 *    reconciled against open positions before anything is reported, and if
 *    it cannot be found it is `ORDER_UNCONFIRMED` with `definitive: false`.
 *  - **The broker password travels in the ConnectEx query string.** No URL
 *    that carries one is ever logged.
 *  - There is no parked state: `disconnect()` stops billing, and coming back
 *    needs the password again.
 *
 * Reference: https://mrpc.pro/metarpc-api-for-ai-agents.md and the OpenAPI
 * files at https://mrpc.pro/openapi/mt5.json / mt4.json.
 */

type Platform = 'MT4' | 'MT5'

const HOST: Record<Platform, string> = {
  MT4: 'https://mt4.mrpc.pro',
  MT5: 'https://mt5.mrpc.pro',
}

const num = (v: unknown) => (v == null || v === '' || !Number.isFinite(Number(v)) ? null : Number(v))
const str = (v: unknown) => (v == null || v === '' ? null : String(v))

/** MT reports "no stop" as 0; we report it as null. */
const price = (v: unknown) => {
  const n = num(v)
  return n == null || n === 0 ? null : n
}

/** MetaRPC's own reply envelope. */
interface MrpcError {
  errorCode?: string
  errorMessage?: string
  mqlErrorTradeIntCode?: number
  mqlErrorTradeCode?: string
  mqlErrorTradeDescription?: string
  mqlErrorIntCode?: number
  mqlErrorDescription?: string
}

type Call<T = Record<string, unknown>> =
  | { ok: true; data: T }
  | { ok: false; error: MrpcError; status: number; detail: string }

const platformOfTerminal = (id: string | null): Platform =>
  /^mt4/i.test(String(id ?? '')) ? 'MT4' : 'MT5'

const asPlatform = (p: unknown): Platform =>
  String(p ?? '').toUpperCase().includes('4') ? 'MT4' : 'MT5'

/**
 * MT5 trade retcodes into our vocabulary.
 * https://www.mql5.com/en/docs/constants/errorswarnings/enum_trade_return_codes
 */
const MT5_RETCODE: Record<number, BridgeErrorCode> = {
  10006: 'BROKER_REJECTED',
  10013: 'REQUEST_INVALID',
  10014: 'INVALID_VOLUME',
  10015: 'INVALID_STOPS',     // invalid price
  10016: 'INVALID_STOPS',
  10017: 'ACCOUNT_READ_ONLY', // trade disabled for the account
  10018: 'MARKET_CLOSED',
  10019: 'INSUFFICIENT_MARGIN',
  10022: 'REQUEST_INVALID',   // invalid expiration
  10026: 'AUTOTRADING_DISABLED',
  10027: 'AUTOTRADING_DISABLED',
  10030: 'REQUEST_INVALID',   // unsupported filling mode
  10031: 'ACCOUNT_DISCONNECTED',
  10032: 'TRADING_PROHIBITED',
  10033: 'TRADING_PROHIBITED',
  10034: 'TRADING_PROHIBITED',
  10040: 'TRADING_PROHIBITED',
  10042: 'TRADING_PROHIBITED',
  10043: 'TRADING_PROHIBITED',
  10044: 'TRADING_PROHIBITED',
}

/** MT5 "it went through" codes: done, placed (pending), partial. */
const MT5_SUCCESS = new Set([10008, 10009, 10010])

/** MT4 trade error codes (`GetLastError`) into our vocabulary. */
const MT4_ERRCODE: Record<number, BridgeErrorCode> = {
  3: 'REQUEST_INVALID',
  4: 'BROKER_UNREACHABLE',
  6: 'ACCOUNT_DISCONNECTED',
  64: 'ACCOUNT_READ_ONLY',
  65: 'ACCOUNT_READ_ONLY',
  129: 'INVALID_STOPS',
  130: 'INVALID_STOPS',
  131: 'INVALID_VOLUME',
  132: 'MARKET_CLOSED',
  133: 'TRADING_PROHIBITED',
  134: 'INSUFFICIENT_MARGIN',
  148: 'TRADING_PROHIBITED',
  4109: 'AUTOTRADING_DISABLED',
  4110: 'TRADING_PROHIBITED',
  4111: 'TRADING_PROHIBITED',
  4106: 'UNKNOWN_SYMBOL',
}

const UNKNOWN_SYMBOL = /SYMBOL.*(NOT_FOUND|NOT_EXIST|UNKNOWN|INVALID)|UNKNOWN_SYMBOL|unknown\s*symbol|symbol\s+(is\s+)?(not\s+found|unknown|does\s+not\s+exist)/i

/** A read failure, in our vocabulary. */
function readFailure(c: { error: MrpcError; status: number; detail: string }): BridgeFailure {
  const code = String(c.error.errorCode ?? '')
  const text = `${code} ${c.error.errorMessage ?? ''}`
  let out: BridgeErrorCode = 'BROKER_UNREACHABLE'
  if (UNKNOWN_SYMBOL.test(text)) out = 'UNKNOWN_SYMBOL'
  // A terminal that no longer exists is reported as disconnected, not gone:
  // reads must never be the thing that unlinks an account. `health()` and the
  // order path are where "gone" is decided.
  else if (/TERMINAL_INSTANCE_NOT_FOUND|NOT_CONNECTED|DISCONNECT|CONNECTION/i.test(text)) out = 'ACCOUNT_DISCONNECTED'
  return { ok: false, code: out, status: c.status, detail: c.detail }
}

const MT5_ORDER_TYPE: Record<OrderType, string> = {
  Buy: 'TMT5_ORDER_TYPE_BUY',
  Sell: 'TMT5_ORDER_TYPE_SELL',
  BuyLimit: 'TMT5_ORDER_TYPE_BUY_LIMIT',
  SellLimit: 'TMT5_ORDER_TYPE_SELL_LIMIT',
  BuyStop: 'TMT5_ORDER_TYPE_BUY_STOP',
  SellStop: 'TMT5_ORDER_TYPE_SELL_STOP',
}

const MT4_ORDER_TYPE: Record<OrderType, string> = {
  Buy: 'OC_OP_BUY',
  Sell: 'OC_OP_SELL',
  BuyLimit: 'OC_OP_BUYLIMIT',
  SellLimit: 'OC_OP_SELLLIMIT',
  BuyStop: 'OC_OP_BUYSTOP',
  SellStop: 'OC_OP_SELLSTOP',
}

/** `TMT5_ORDER_TYPE_BUY_LIMIT` / `POSITION_TYPE_SELL` / `OC_OP_BUYSTOP` -> `BuyLimit` / `Sell` / `BuyStop`. */
function orderTypeName(raw: unknown): string {
  const s = String(raw ?? '').toUpperCase()
  const side = s.includes('SELL') ? 'Sell' : s.includes('BUY') ? 'Buy' : ''
  if (!side) return String(raw ?? '')
  if (s.includes('STOP_LIMIT') || s.includes('STOPLIMIT')) return `${side}StopLimit`
  if (s.includes('LIMIT')) return `${side}Limit`
  if (s.includes('STOP')) return `${side}Stop`
  return side
}

export class MetaRpcBridge implements BrokerBridge {
  readonly provider = 'metarpc' as const
  private readonly platform: Platform

  constructor(
    private readonly apiKey: string,
    private readonly accountId: string | null,
    platform?: string | null,
  ) {
    this.platform = accountId ? platformOfTerminal(accountId) : asPlatform(platform)
  }

  private get base() {
    return HOST[this.platform]
  }

  /**
   * One GET. Failure comes back as data, never thrown.
   *
   * `secret` marks a call whose query carries the broker password: its URL is
   * never put in a log line or a `detail`.
   */
  private async call<T = Record<string, unknown>>(
    path: string,
    params: Record<string, string | number | boolean | undefined | null> = {},
    opts: { timeoutMs?: number; terminal?: string | null; secret?: boolean; platform?: Platform; retried?: boolean } = {},
  ): Promise<Call<T>> {
    const qs = new URLSearchParams()
    for (const [k, v] of Object.entries(params)) if (v != null && v !== '') qs.set(k, String(v))
    const base = opts.platform ? HOST[opts.platform] : this.base
    const url = `${base}/${path}${qs.size ? `?${qs}` : ''}`
    const terminal = opts.terminal === undefined ? this.accountId : opts.terminal
    const headers: Record<string, string> = { APIKey: this.apiKey, Accept: 'application/json' }
    if (terminal) headers.id = terminal

    let res: Response
    try {
      res = await fetch(url, { headers, ...(opts.timeoutMs ? { signal: AbortSignal.timeout(opts.timeoutMs) } : {}) })
    } catch (e) {
      const msg = (e as Error).name === 'TimeoutError' ? 'COMMAND_TIMEOUT_EXCEEDED' : (e as Error).message
      return { ok: false, status: 0, error: { errorCode: msg }, detail: `${path}: ${msg}` }
    }
    const text = await res.text().catch(() => '')
    let body: { data?: T; error?: MrpcError } | null = null
    try { body = text ? JSON.parse(text) : null } catch { /* not JSON */ }

    if (body?.error || !res.ok || !body || body.data === undefined) {
      const error: MrpcError = body?.error ?? { errorCode: res.status === 401 ? 'API_KEY_INVALID' : `HTTP_${res.status}` }
      // MT5 4301 / MT4 4106: the broker lists the symbol but it is not in this
      // terminal's Market Watch yet, so ticks and orders call it "unknown".
      // Seen live on RCGMarkets-Demo: XAUUSD in SymbolList, 4301 on the tick.
      // Select it and try once more; a name the broker truly lacks fails again.
      const unselected = error.mqlErrorIntCode === 4301 || error.mqlErrorIntCode === 4106
      const symbol = params.symbol ?? params.symbolName
      if (unselected && symbol && path !== 'SymbolSelect' && !opts.retried) {
        const sel = await this.call('SymbolSelect', { symbol, select: true }, { timeoutMs: 15_000 })
        if (sel.ok) return this.call<T>(path, params, { ...opts, retried: true })
      }
      if (error.errorCode === 'API_KEY_INVALID' || error.errorCode === 'API_KEY_EXPIRED') {
        console.error(`[MetaRPC] ${error.errorCode} -- METARPC_API_KEY is wrong or expired`)
      }
      return { ok: false, status: res.status, error, detail: `${path}: ${text.slice(0, 500)}` }
    }
    return { ok: true, data: body.data as T }
  }

  private async read<T, R = Record<string, unknown>>(
    path: string,
    params: Record<string, string | number | undefined>,
    map: (raw: R) => T | null,
    timeoutMs?: number,
  ): Promise<BridgeResult<T>> {
    const r = await this.call<R>(path, params, { timeoutMs })
    if (!r.ok) return readFailure(r)
    const data = map(r.data)
    return data === null
      ? { ok: false, code: 'BROKER_UNREACHABLE', status: 200, detail: `${path}: unexpected response shape` }
      : { ok: true, data }
  }

  // ---- Reads ---------------------------------------------------------------

  /**
   * MT5's `AccountSummary` has no free margin and no trade permissions, so
   * those come from `AccountInfoDouble` / `AccountInfoInteger` alongside it.
   * A failed side-read leaves its field null; only the summary itself is
   * required.
   */
  async accountSummary(timeoutMs?: number): Promise<BridgeResult<AccountSummary>> {
    if (this.platform === 'MT4') {
      return this.read('AccountSummary', {}, (a) => ({
        balance: num(a.accountBalance),
        equity: num(a.accountEquity),
        credit: num(a.accountCredit),
        freeMargin: null,
        currency: a.accountCurrency ? String(a.accountCurrency).toUpperCase().slice(0, 3) : null,
        leverage: num(a.accountLeverage),
        connected: true,
        wrongCredentials: false,
        investorPassword: a.isInvestor === true,
        tradingDisabled: false,
      }), timeoutMs)
    }

    const [summary, free, allowed, expert] = await Promise.all([
      this.call('AccountSummary', {}, { timeoutMs }),
      this.call('AccountInfoDouble', { propertyId: 'ACCOUNT_MARGIN_FREE' }, { timeoutMs }),
      this.call('AccountInfoInteger', { propertyId: 'ACCOUNT_TRADE_ALLOWED' }, { timeoutMs }),
      this.call('AccountInfoInteger', { propertyId: 'ACCOUNT_TRADE_EXPERT' }, { timeoutMs }),
    ])
    if (!summary.ok) return readFailure(summary)
    const a = summary.data
    const flag = (r: Call) => (r.ok ? num(r.data.requestedValue) : null)
    return {
      ok: true,
      data: {
        balance: num(a.accountBalance),
        equity: num(a.accountEquity),
        credit: num(a.accountCredit),
        freeMargin: free.ok ? num(free.data.requestedValue) : null,
        currency: a.accountCurrency ? String(a.accountCurrency).toUpperCase().slice(0, 3) : null,
        leverage: num(a.accountLeverage),
        connected: true,
        wrongCredentials: false,
        // ACCOUNT_TRADE_ALLOWED is false for an investor login and for an
        // account the broker has frozen. Either way no order will open.
        investorPassword: flag(allowed) === 0,
        tradingDisabled: flag(expert) === 0,
      },
    }
  }

  symbols(): Promise<BridgeResult<string[]>> {
    if (this.platform === 'MT4') {
      return this.read<string[], { SymbolNameInfos?: { symbolName?: string }[] }>('Symbols', {}, (d) =>
        Array.isArray(d.SymbolNameInfos)
          ? [...new Set(d.SymbolNameInfos.map((s) => String(s?.symbolName ?? '').trim()).filter(Boolean))]
          : null)
    }
    return this.read<string[], { Symbols?: string[] }>('SymbolList', {}, (d) =>
      Array.isArray(d.Symbols) ? [...new Set(d.Symbols.map((s) => String(s).trim()).filter(Boolean))] : null)
  }

  async symbolSpec(symbol: string, timeoutMs?: number): Promise<BridgeResult<SymbolSpec>> {
    const r = await this.read<SymbolSpec, { symbolInfos?: Record<string, unknown>[] }>(
      'SymbolParamsMany',
      { symbolName: symbol },
      (d) => {
        const list = Array.isArray(d.symbolInfos) ? d.symbolInfos : null
        if (!list) return null
        // The filter is a search, not an exact match: `XAUUSD` also returns
        // `XAUUSD.m`. Only the exact name is the instrument we were asked for.
        const s = list.find((x) => String(x.name ?? x.SymbolName ?? '') === symbol)
        if (!s) return null
        const g = (k: string) => s[k] ?? s[k[0].toUpperCase() + k.slice(1)]
        return {
          digits: num(g('digits')),
          point: num(g('point')),
          baseCurrency: str(g('currencyBase')),
          quoteCurrency: str(g('currencyProfit')),
          contractSize: num(g('tradeContractSize')),
          minVolume: num(g('volumeMin')),
          stepVolume: num(g('volumeStep')),
          maxVolume: num(g('volumeMax')),
          disabled: /DISABLED/i.test(String(g('tradeMode') ?? '')),
        }
      },
      timeoutMs,
    )
    // "Not in the list" is the one read failure where another spelling helps.
    return !r.ok && r.detail.endsWith('unexpected response shape') ? { ...r, code: 'UNKNOWN_SYMBOL' } : r
  }

  async quote(symbol: string): Promise<BridgeResult<Quote>> {
    const tick = this.platform === 'MT4'
      ? this.read<Omit<Quote, 'spec'>>('Quote', { symbol }, (q) => ({
          bid: price(q.bid), ask: price(q.ask), timestamp: str(q.dateTime),
        }))
      : this.read<Omit<Quote, 'spec'>>('SymbolInfoTick', { symbol }, (q) => ({
          bid: price(q.bid),
          ask: price(q.ask),
          timestamp: num(q.time) ? new Date(Number(q.time) * 1000).toISOString() : null,
        }))
    // MetaCopier sends the contract with the price and callers size lots off
    // it, so the spec is fetched alongside rather than leaving it null.
    const [t, spec] = await Promise.all([tick, this.symbolSpec(symbol)])
    if (!t.ok) return t
    if (t.data.bid == null && t.data.ask == null && !spec.ok && spec.code === 'UNKNOWN_SYMBOL') return spec
    return { ok: true, data: { ...t.data, spec: spec.ok ? spec.data : null } }
  }

  async positions(timeoutMs?: number): Promise<BridgeResult<Position[]>> {
    if (this.platform === 'MT4') {
      return this.read<Position[], { orderInfos?: Record<string, unknown>[] }>('OpenedOrders', {}, (d) =>
        Array.isArray(d.orderInfos)
          ? d.orderInfos.map((o) => {
              const profit = num(o.profit)
              const swap = num(o.swap)
              const commission = num(o.commision)
              return {
                id: String(o.ticket ?? ''),
                symbol: String(o.symbol ?? ''),
                orderType: orderTypeName(o.orderType),
                volume: num(o.lots),
                openPrice: num(o.openPrice),
                stopLoss: price(o.stopLoss),
                takeProfit: price(o.takeProfit),
                profit,
                netProfit: profit == null ? null : profit + (swap ?? 0) + (commission ?? 0),
                swap,
                commission,
                openTime: str(o.openTime),
                magic: str(o.magicNumber),
                comment: str(o.comment),
              }
            })
          : null, timeoutMs)
    }

    return this.read<Position[], { positionInfos?: Record<string, unknown>[]; openedOrders?: Record<string, unknown>[] }>(
      'OpenedOrders', {}, (d) => {
        if (!Array.isArray(d.positionInfos) && !Array.isArray(d.openedOrders)) return null
        const open = (d.positionInfos ?? []).map((p): Position => {
          const profit = num(p.profit)
          const swap = num(p.swap)
          const commission = num(p.positionCommission)
          return {
            id: String(p.ticket ?? p.identifier ?? ''),
            symbol: String(p.symbol ?? ''),
            orderType: orderTypeName(p.type),
            volume: num(p.volume),
            openPrice: num(p.priceOpen),
            stopLoss: price(p.stopLoss),
            takeProfit: price(p.takeProfit),
            profit,
            netProfit: profit == null ? null : profit + (swap ?? 0) + (commission ?? 0),
            swap,
            commission,
            openTime: str(p.openTime),
            magic: str(p.magicNumber),
            comment: str(p.comment),
          }
        })
        const pending = (d.openedOrders ?? []).map((o): Position => ({
          id: String(o.ticket ?? ''),
          symbol: String(o.symbol ?? ''),
          orderType: orderTypeName(o.type),
          volume: num(o.volumeCurrent),
          openPrice: num(o.priceOpen),
          stopLoss: price(o.stopLoss),
          takeProfit: price(o.takeProfit),
          profit: null,
          netProfit: null,
          swap: null,
          commission: null,
          openTime: str(o.timeSetup),
          magic: str(o.magicNumber),
          comment: str(o.comment),
        }))
        return [...open, ...pending]
      }, timeoutMs)
  }

  /**
   * Closed positions. MT5 `PositionsHistory` is paged and filtered by OPEN
   * time, so a position opened before `since` and closed after it would be
   * missed -- the window is widened by a week and trimmed by close time here.
   * Every row is a trade; MT5 keeps the ledger (deposits) out of this list.
   */
  async history(since: Date, until = new Date(), timeoutMs?: number): Promise<BridgeResult<Deal[]>> {
    if (this.platform === 'MT4') {
      return this.read<Deal[], { ordersInfo?: Record<string, unknown>[] }>(
        'OrdersHistory',
        { inputFrom: since.toISOString(), inputTo: until.toISOString(), itemsPerPage: 1000 },
        (d) => Array.isArray(d.ordersInfo)
          ? d.ordersInfo.map((o) => {
              const profit = num(o.profit)
              const swap = num(o.swap)
              const commission = num(o.commision)
              const type = String(o.orderType ?? '')
              return {
                id: String(o.ticket ?? ''),
                symbol: String(o.symbol ?? ''),
                orderType: orderTypeName(type),
                volume: num(o.lots),
                openPrice: num(o.openPrice),
                stopLoss: price(o.stopLoss),
                takeProfit: price(o.takeProfit),
                profit,
                netProfit: profit == null ? null : profit + (swap ?? 0) + (commission ?? 0),
                swap,
                commission,
                openTime: str(o.openTime),
                magic: str(o.magicNumber),
                comment: str(o.comment),
                // MT4 history mixes balance/credit rows in with trades.
                kind: /BALANCE|CREDIT/i.test(type) || !o.symbol ? 'ledger' as const : 'trade' as const,
                closePrice: num(o.closePrice),
                closeTime: str(o.closeTime),
              }
            })
          : null,
        timeoutMs,
      )
    }

    const from = new Date(since.getTime() - 7 * 86_400_000)
    const out: Deal[] = []
    for (let page = 0; page < 20; page++) {
      const r = await this.call<{ historyPositions?: Record<string, unknown>[] }>('PositionsHistory', {
        sortType: 'AH_POSITION_CLOSE_TIME_ASC',
        positionOpenTimeFrom: from.toISOString(),
        positionOpenTimeTo: until.toISOString(),
        pageNumber: page,
        itemsPerPage: 500,
      }, { timeoutMs })
      if (!r.ok) return readFailure(r)
      const rows = Array.isArray(r.data.historyPositions) ? r.data.historyPositions : []
      for (const p of rows) {
        const closeTime = str(p.closeTime)
        if (closeTime && new Date(closeTime) < since) continue
        const profit = num(p.profit)
        const swap = num(p.swap)
        const commission = (num(p.commission) ?? 0) + (num(p.fee) ?? 0)
        out.push({
          id: String(p.positionTicket ?? ''),
          symbol: String(p.symbol ?? ''),
          orderType: orderTypeName(p.orderType),
          volume: num(p.volume),
          openPrice: num(p.openPrice),
          stopLoss: price(p.stopLoss),
          takeProfit: price(p.takeProfit),
          profit,
          netProfit: profit == null ? null : profit + (swap ?? 0) + commission,
          swap,
          commission,
          openTime: str(p.openTime),
          magic: str(p.magic),
          comment: str(p.comment),
          kind: 'trade',
          closePrice: num(p.closePrice),
          closeTime,
        })
      }
      if (rows.length < 500) break
    }
    return { ok: true, data: out }
  }

  async health(): Promise<HealthState> {
    const r = await this.call<{ healthCheck?: Record<string, unknown> }>('CheckConnect')
    if (!r.ok) return r.error.errorCode === 'TERMINAL_INSTANCE_NOT_FOUND' ? 'gone' : 'unknown'
    const h = r.data.healthCheck ?? {}
    if (h.hasAuthorizationError === true) return 'wrong_credentials'
    if (h.terminalIsConnectedToMtServer === true) return 'connected'
    return 'connecting'
  }

  async diagnostics(symbol: string | null): Promise<Record<string, unknown>> {
    const raw = async (path: string, params: Record<string, string> = {}) => {
      const r = await this.call(path, params, { timeoutMs: 15_000 })
      return r.ok ? r.data : { _error: r.error.errorCode ?? r.status, _body: r.detail }
    }
    const mt5 = this.platform === 'MT5'
    const [connect_state, account, positions, journal, symbol_spec, quote] = await Promise.all([
      raw('ConnectState'),
      raw('AccountSummary'),
      raw('OpenedOrders'),
      raw('Logs/Journal'),
      symbol ? raw('SymbolParamsMany', { symbolName: symbol }) : Promise.resolve(null),
      symbol ? raw(mt5 ? 'SymbolInfoTick' : 'Quote', { symbol }) : Promise.resolve(null),
    ])
    return { provider: 'metarpc', platform: this.platform, connect_state, account, positions, journal, symbol_spec, quote }
  }

  // ---- Orders --------------------------------------------------------------

  async placeOrder(order: OrderRequest): Promise<OrderResult> {
    const pending = order.orderType !== 'Buy' && order.orderType !== 'Sell'
    const expiry = pending && order.pendingExpirySeconds > 0
      ? new Date(Date.now() + order.pendingExpirySeconds * 1000).toISOString()
      : null
    const sentAt = Date.now()

    const r = this.platform === 'MT4'
      ? await this.call<Record<string, unknown>>('OrderSend', {
          symbol: order.symbol,
          operationType: MT4_ORDER_TYPE[order.orderType],
          volume: order.volume,
          price: pending ? order.openPrice : undefined,
          stoploss: order.stopLoss || undefined,
          takeprofit: order.takeProfit || undefined,
          slippage: 20,
          comment: order.comment,
          magicNumber: order.magic,
          expiration: expiry ?? undefined,
        }, { timeoutMs: 35_000 })
      : await this.call<Record<string, unknown>>('OrderSend', {
          symbol: order.symbol,
          operation: MT5_ORDER_TYPE[order.orderType],
          volume: order.volume,
          price: pending ? order.openPrice : undefined,
          stopLoss: order.stopLoss || undefined,
          takeProfit: order.takeProfit || undefined,
          slippage: 20,
          comment: order.comment,
          expertId: order.magic,
          ...(expiry ? { expirationTimeType: 'TMT5_ORDER_TIME_SPECIFIED', expirationTime: expiry } : {}),
        }, { timeoutMs: 35_000 })

    if (r.ok) {
      if (this.platform === 'MT4') return { ok: true }
      const rc = Number(r.data.returnedCode)
      if (MT5_SUCCESS.has(rc)) return { ok: true }
      const code = MT5_RETCODE[rc] ?? 'BROKER_REJECTED'
      const brokerMessage = str(r.data.returnedCodeDescription) ?? str(r.data.comment)
      console.error(`[MetaRPC] order refused ${rc} ${r.data.returnedStringCode} for ${order.symbol}`)
      return {
        ok: false, code, definitive: true,
        wrongName: false, accountGone: false,
        explain: code === 'BROKER_REJECTED',
        brokerMessage,
        providerCode: str(r.data.returnedStringCode) ?? String(rc),
        status: 200, detail: JSON.stringify(r.data).slice(0, 500),
      }
    }

    const e = r.error
    const errorCode = String(e.errorCode ?? '')
    const base = {
      ok: false as const,
      wrongName: false, accountGone: false, explain: false,
      brokerMessage: str(e.mqlErrorTradeDescription) ?? str(e.mqlErrorDescription) ?? null,
      providerCode: errorCode || str(e.mqlErrorTradeCode),
      status: r.status, detail: r.detail,
    }

    // The outcome is unknown: the terminal may still execute it.
    if (/COMMAND_TIMEOUT_EXC?EEDED/.test(errorCode)) {
      if (await this.foundOpened(order, sentAt)) return { ok: true }
      return { ...base, code: 'ORDER_UNCONFIRMED', definitive: false }
    }

    // Never placed and never will be; safe to send again.
    if (errorCode === 'TOO_MANY_ORDERS_IN_QUEUE' || errorCode === 'TERMINAL_BUSY' || errorCode === 'TERMINAL_RATE_LIMIT_EXCEEDED') {
      return { ...base, code: 'BROKER_UNREACHABLE', definitive: true }
    }

    if (errorCode === 'TERMINAL_INSTANCE_NOT_FOUND') {
      return { ...base, code: 'ACCOUNT_GONE', definitive: true, accountGone: true }
    }

    const text = `${errorCode} ${e.errorMessage ?? ''} ${e.mqlErrorTradeDescription ?? ''}`
    if (UNKNOWN_SYMBOL.test(text)) return { ...base, code: 'UNKNOWN_SYMBOL', definitive: true, wrongName: true }

    const mql = num(e.mqlErrorTradeIntCode) ?? num(e.mqlErrorIntCode)
    const mapped = mql == null ? undefined : (this.platform === 'MT4' ? MT4_ERRCODE[mql] : MT5_RETCODE[mql])
    if (mapped) {
      return { ...base, code: mapped, definitive: true, wrongName: mapped === 'UNKNOWN_SYMBOL' }
    }
    if (/NOT_CONNECTED|DISCONNECT/i.test(text)) return { ...base, code: 'ACCOUNT_DISCONNECTED', definitive: true }

    console.error(`[MetaRPC] order failed ${errorCode} for ${order.symbol}: ${r.detail}`)
    return { ...base, code: errorCode || 'BROKER_REJECTED', definitive: true, explain: !errorCode }
  }

  /**
   * Did a timed-out order open after all? Matched on magic + symbol + side +
   * volume, opened no earlier than a little before we sent it. Our magic is
   * per robot, not per order, so the time window is what keeps an older
   * position on the same robot from being mistaken for this one.
   */
  private async foundOpened(order: OrderRequest, sentAt: number): Promise<boolean> {
    await new Promise((r) => setTimeout(r, 2_000))
    const p = await this.positions(15_000)
    if (!p.ok) return false
    const side = order.orderType.startsWith('Buy') ? 'Buy' : 'Sell'
    return p.data.some((x) =>
      x.magic === String(order.magic) &&
      x.symbol === order.symbol &&
      x.orderType.startsWith(side) &&
      Math.abs((x.volume ?? 0) - order.volume) < 1e-9 &&
      (!x.openTime || new Date(x.openTime).getTime() >= sentAt - 120_000))
  }

  /**
   * The kill switch. MetaRPC has no close-all, and a terminal queues at most
   * 5 orders, so this closes in batches of 5 and re-reads until nothing is
   * left (or 8 rounds pass). Includes manual positions and pending orders.
   */
  async closeAll(): Promise<BridgeResult<{ requested: true }>> {
    for (let round = 0; round < 8; round++) {
      const open = await this.positions(15_000)
      if (!open.ok) return open
      if (open.data.length === 0) return { ok: true, data: { requested: true } }
      const tickets = open.data.map((p) => p.id).filter(Boolean)
      for (let i = 0; i < tickets.length; i += 5) {
        await Promise.all(tickets.slice(i, i + 5).map((t) =>
          this.platform === 'MT4'
            ? this.call('OrderCloseDelete', { orderTicket: t, slippage: 30 }, { timeoutMs: 35_000 })
            : this.call('OrderClose', { ticket: t, slippage: 30 }, { timeoutMs: 35_000 })))
      }
    }
    const left = await this.positions(15_000)
    if (left.ok && left.data.length === 0) return { ok: true, data: { requested: true } }
    return { ok: false, code: 'BROKER_REJECTED', status: 200, detail: 'positions still open after 8 close rounds' }
  }

  // ---- Account lifecycle ---------------------------------------------------

  /** Recreates the terminal under the same id. No password needed. */
  async reconnect(): Promise<boolean> {
    const r = await this.call('Reconnect', { forceReconnection: false }, { timeoutMs: 120_000 })
    return r.ok
  }

  /** Stops the terminal, which stops the meter. Coming back needs the password. */
  async disconnect(): Promise<boolean> {
    if (!this.accountId) return false
    const r = await this.call('Disconnect', { reason: 'novahost-unlink' }, { timeoutMs: 30_000 })
    return r.ok || r.error.errorCode === 'TERMINAL_INSTANCE_NOT_FOUND'
  }

  /**
   * The server catalogue needs an already-connected terminal (`id` header on
   * `GetBrokerServersByBrokerName`). Until a helper terminal exists this says
   * "unknown", which callers treat as "don't block the connect".
   */
  servers(_platform: string): Promise<string[] | null> {
    return Promise.resolve(null)
  }

  async connect(input: ConnectInput): Promise<ConnectResult> {
    const platform = asPlatform(input.platform)
    const login = String(input.login ?? '').replace(/\s+/g, '')
    const password = String(input.password ?? '').trim()
    const server = String(input.server ?? '').trim()
    if (!login || !password || !server) {
      return { ok: false, status: 400, code: 'REQUEST_INVALID', error: 'Login, password and server are all required.' }
    }

    // A second terminal on the same account is billed separately, and the old
    // one would keep running. Stop the previous link first.
    if (input.priorAccountId) {
      await new MetaRpcBridge(this.apiKey, input.priorAccountId).disconnect().catch(() => false)
    }

    // The licence key is a credential; MetaRPC's dashboard only needs enough
    // to find the terminal.
    const name = `novahost-${String(input.licenseKey ?? '').slice(-6)}-${login}`
    const r = await this.call<{ terminalInstanceGuid?: string; terminalType?: string }>('ConnectEx', {
      user: login,
      password,
      mtClusterName: server,
      timeoutSeconds: 60,
      name,
      expiration: 0,
    }, { terminal: null, secret: true, platform, timeoutMs: 75_000 })

    if (!r.ok) {
      const errorCode = String(r.error.errorCode ?? '')
      const msg = `${errorCode} ${r.error.errorMessage ?? ''} ${r.error.mqlErrorDescription ?? ''}`
      // r.detail is the response body, not the URL, so it is safe to log.
      console.error(`[MetaRPC] ConnectEx failed for ${platform} ${server}: ${errorCode}`)
      if (errorCode === 'BROKER_SERVER_NOT_FOUND') {
        return {
          ok: false, status: 400, code: 'SERVER_NOT_SUPPORTED',
          error: `We couldn't find the server "${server}". Type it exactly as MetaTrader shows it.`,
          extra: { provider: 'metarpc' },
        }
      }
      if (/AUTH|PASSWORD|INVALID_ACCOUNT|CREDENTIAL|LOGIN/i.test(msg)) {
        return {
          ok: false, status: 400, code: 'WRONG_CREDENTIALS',
          error: 'The broker refused that login and password. Use your MASTER (trading) password, not the investor one.',
        }
      }
      if (/TIMEOUT/i.test(msg)) {
        return { ok: false, status: 504, code: 'CONNECT_FAILED', error: 'The broker did not answer in time. Try again in a minute.' }
      }
      return { ok: false, status: 502, code: 'CONNECT_FAILED', error: 'Could not connect to your broker.', extra: { details: errorCode } }
    }

    const id = String(r.data.terminalInstanceGuid ?? '')
    if (!id) return { ok: false, status: 502, code: 'NO_ACCOUNT_ID', error: 'The bridge did not return an account id.' }

    // Catch read-only logins and server-disabled autotrading NOW, instead of
    // on the first signal. A refused link is disconnected so it stops billing.
    const linked = new MetaRpcBridge(this.apiKey, id)
    const summary = await linked.accountSummary(15_000)
    if (summary.ok && (summary.data.investorPassword || summary.data.tradingDisabled)) {
      await linked.disconnect()
      return summary.data.investorPassword
        ? {
            ok: false, status: 400, code: 'ACCOUNT_READ_ONLY',
            error: 'That is a read-only (investor) login. Connect with your MASTER trading password.',
          }
        : {
            ok: false, status: 400, code: 'AUTOTRADING_DISABLED',
            error: 'Your broker has disabled automated trading on this account. Ask them to enable it, then reconnect.',
          }
    }

    return {
      ok: true,
      accountId: id,
      state: summary.ok ? 'connected' : 'connecting',
      reused: false,
      server,
      platform,
    }
  }
}
