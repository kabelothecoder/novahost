import { signFor } from '../../../services/fxRates.js'

/** "R1,000" / "$12.5". Rounded the way the design reads: whole above 10. */
export function money(account, v) {
  const n = v >= 10 ? Math.round(v) : Math.round(v * 10) / 10
  return `${signFor(account?.currency || 'USD')}${n.toLocaleString('en-US')}`
}

export const tradesWord = (n) => (n === 1 ? '1 trade' : `${n} trades`)

/** Masked login when we have one, the server otherwise (the linked account's
 *  login is not reported back by `license-status`). */
export const accountTag = (a) => (a?.login ? `···${a.login}` : a?.server || '')
