/**
 * The provider-neutral broker bridge.
 *
 * ## Why this exists
 *
 * Nine functions talked to MetaCopier directly, each with its own copy of the
 * base URL, the API key, the account-id lookup and its own guess at what a
 * refusal meant. Moving to MetaRPC (see design-briefs/2026-10-backend-build-plan.md,
 * Phase 2) would have meant rewriting all nine at once, on a live product, with
 * every user's trading going through them.
 *
 * Instead every function now speaks this interface, and each provider is an
 * adapter behind it. The functions keep their names and response shapes -- no
 * app release -- and moving a licence between providers is a matter of which
 * adapter `bridgeFor()` returns, not of which code path a function takes.
 *
 * ## The rules for an adapter
 *
 *  - **Neutral shapes out.** Nothing a function reads is a provider payload.
 *    Positions, deals, quotes and account figures are mapped into the types
 *    below, and a field the provider does not report is `null`, never guessed.
 *  - **Our error vocabulary out.** Refusals are translated into the codes the
 *    app already renders (`INSUFFICIENT_MARGIN`, `INVALID_STOPS`, ...). How a
 *    provider spells a refusal -- bracketed tokens, retcodes, prose -- stays
 *    inside its adapter.
 *  - **Reads degrade, writes do not guess.** A failed read reports itself as a
 *    failure and callers treat it as "unknown". A write whose outcome is not
 *    known (a timed-out order) must say so with `definitive: false`, and is
 *    never reported as refused: a refused order is safe to retry under another
 *    name, an unconfirmed one may already be open.
 */

/** Which provider a licence's account lives on. */
export type BridgeProvider = 'metacopier' | 'metarpc'

/**
 * Codes a bridge reports. These are the codes the apps already understand --
 * adapters translate INTO them, they do not add their own.
 */
export type BridgeErrorCode =
  | 'ACCOUNT_DISCONNECTED'
  | 'ACCOUNT_GONE'
  | 'ACCOUNT_WRONG_CREDENTIALS'
  | 'ACCOUNT_READ_ONLY'
  | 'AUTOTRADING_DISABLED'
  | 'INSUFFICIENT_MARGIN'
  | 'INVALID_STOPS'
  | 'INVALID_VOLUME'
  | 'MARKET_CLOSED'
  | 'TRADING_PROHIBITED'
  | 'UNKNOWN_SYMBOL'
  | 'REQUEST_INVALID'
  | 'SERVER_NOT_SUPPORTED'
  | 'BROKER_REJECTED'
  | 'BROKER_UNREACHABLE'
  | 'ORDER_UNCONFIRMED'

/** A read that did not produce an answer. */
export interface BridgeFailure {
  ok: false
  /** `ACCOUNT_DISCONNECTED`, `ACCOUNT_GONE`, `UNKNOWN_SYMBOL` or `BROKER_UNREACHABLE`. */
  code: BridgeErrorCode
  /** The provider's HTTP status, 0 when the request never completed. */
  status: number
  /** The provider's raw reply, for logs and `details` fields. Never shown as copy. */
  detail: string
}

export type BridgeResult<T> = { ok: true; data: T } | BridgeFailure

/** Balance, equity, free margin and currency, always together. */
export interface AccountSummary {
  balance: number | null
  equity: number | null
  /** Bonus credit. Reported for display only -- never sized against. */
  credit: number | null
  freeMargin: number | null
  /** ISO code, upper case. Null when the provider did not say. */
  currency: string | null
  leverage: number | null
  /** Null means "could not tell", which callers treat as "proceed". */
  connected: boolean | null
  wrongCredentials: boolean
  /** Linked with an investor (read-only) password. */
  investorPassword: boolean
  /** The broker refuses orders on this account regardless of what is sent. */
  tradingDisabled: boolean
}

/** One instrument's contract, as the user's own broker defines it. */
export interface SymbolSpec {
  digits: number | null
  point: number | null
  baseCurrency: string | null
  quoteCurrency: string | null
  contractSize: number | null
  minVolume: number | null
  stepVolume: number | null
  maxVolume: number | null
  /** True when the broker has the instrument closed for trading. */
  disabled: boolean
}

export interface Quote {
  bid: number | null
  ask: number | null
  timestamp: string | null
  /** Null when the provider does not send the contract with the price. */
  spec: SymbolSpec | null
}

/**
 * `Buy`/`Sell` for a market position, or one of the four pending variants.
 * MetaTrader's own vocabulary, so no provider has to invent a mapping for it.
 */
export type OrderType = 'Buy' | 'Sell' | 'BuyLimit' | 'SellLimit' | 'BuyStop' | 'SellStop'

/** An open position, or a pending order still waiting to fill. */
export interface Position {
  id: string
  /** Exactly as the broker spells it. */
  symbol: string
  orderType: string
  volume: number | null
  openPrice: number | null
  stopLoss: number | null
  takeProfit: number | null
  /** Before swap and commission. */
  profit: number | null
  /** After swap and commission. */
  netProfit: number | null
  swap: number | null
  commission: number | null
  openTime: string | null
  /** The magic number, as a string -- what tells our orders from manual ones. */
  magic: string | null
  comment: string | null
}

/**
 * One row of account history.
 *
 * History is the account's LEDGER, not its trade list: deposits, withdrawals,
 * credits and cancelled orders arrive here too. `kind` is how an adapter says
 * which is which when the provider tells it; `null` means the provider did not
 * say, and callers then fall back to "a trade has a symbol and a volume".
 */
export interface Deal extends Position {
  kind: 'trade' | 'ledger' | null
  closePrice: number | null
  closeTime: string | null
}

export interface OrderRequest {
  /** The broker's own name for the instrument. */
  symbol: string
  orderType: OrderType
  volume: number
  /** 0 for market. A price only on the pending variants. */
  openPrice: number
  /** Broker-side expiry for a pending order; 0 for none. */
  pendingExpirySeconds: number
  /** 0 for none. */
  stopLoss: number
  /** 0 for none. */
  takeProfit: number
  /**
   * Our idempotency key for this order. Adapters that support one pass it on;
   * the caller keeps it stable across retries of the SAME order.
   */
  clientId: number
  comment: string
  magic: string
}

/**
 * Why an order did not open.
 *
 * `code` is the most specific code the adapter could read from the refusal
 * itself. `explain` means the refusal named no reason and the caller should
 * look at the account and the instrument to find one.
 */
export interface OrderRefusal {
  ok: false
  code: BridgeErrorCode | string
  /** False when the outcome is unknown -- the order may be open. Never retry these. */
  definitive: boolean
  /** The broker does not list that name; another spelling is safe to try. */
  wrongName: boolean
  /** The account no longer exists at the provider and will never come back. */
  accountGone: boolean
  /** The refusal carried no reason; look at the account and instrument. */
  explain: boolean
  /** The broker's own sentence, when it gave one. Shown to the user as-is. */
  brokerMessage: string | null
  /** The provider's own token for the refusal, for logs and audit rows. */
  providerCode: string | null
  status: number
  detail: string
}

export type OrderResult = { ok: true } | OrderRefusal

export type Platform = 'MT4' | 'MT5'

export interface ConnectInput {
  licenseKey: string
  login: string
  password: string
  server: string
  platform: Platform | string
  /** Provider-specific placement hint (MetaCopier region); ignored where it means nothing. */
  region?: string | null
  /** The account this licence is already linked to on THIS provider, if any. */
  priorAccountId: string | null
}

export type LinkState = 'connected' | 'connecting'

export interface ConnectSuccess {
  ok: true
  accountId: string
  state: LinkState
  /** An existing provider account was reused rather than a new one made. */
  reused: boolean
  /** The server as the provider spells it, which may differ from what was typed. */
  server: string
  platform: string
}

/**
 * A connect that did not link. `code`, `error` and `extra` are exactly what
 * goes back to the app, so the response shape is the adapter's to keep stable.
 */
export interface ConnectFailure {
  ok: false
  status: number
  code?: string
  error: string
  extra?: Record<string, unknown>
}

export type ConnectResult = ConnectSuccess | ConnectFailure

export type HealthState = 'connected' | 'connecting' | 'wrong_credentials' | 'gone' | 'unknown'

/**
 * What every provider must do.
 *
 * Every method is scoped to the one account the bridge was opened for, except
 * `connect` (which makes that account) and `servers` (which has none yet).
 */
export interface BrokerBridge {
  readonly provider: BridgeProvider

  /** Links a broker login and waits briefly for the session to come up. */
  connect(input: ConnectInput): Promise<ConnectResult>
  /** Ends the session and stops the provider billing for it. */
  disconnect(): Promise<boolean>
  health(): Promise<HealthState>
  /** Restarts the session with the stored credentials. */
  reconnect(): Promise<boolean>

  accountSummary(timeoutMs?: number): Promise<BridgeResult<AccountSummary>>
  /** Every name the broker lists, exactly as spelled. */
  symbols(): Promise<BridgeResult<string[]>>
  symbolSpec(symbol: string, timeoutMs?: number): Promise<BridgeResult<SymbolSpec>>
  quote(symbol: string): Promise<BridgeResult<Quote>>
  positions(timeoutMs?: number): Promise<BridgeResult<Position[]>>
  history(since: Date, until?: Date, timeoutMs?: number): Promise<BridgeResult<Deal[]>>

  placeOrder(order: OrderRequest): Promise<OrderResult>
  /**
   * Closes every open position on the account -- including ones the user
   * opened by hand. This is the kill switch, so it is deliberately not
   * filtered by magic number.
   */
  closeAll(): Promise<BridgeResult<{ requested: true }>>

  /** Server names this provider can dial, or null when it cannot say. */
  servers(platform: string): Promise<string[] | null>
  /** Raw provider payloads for a support session. Shape is the provider's. */
  diagnostics(symbol: string | null): Promise<Record<string, unknown>>
}
