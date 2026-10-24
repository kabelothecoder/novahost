/**
 * Whether this is an iPhone/iPad in a Safari TAB rather than the Home Screen
 * app.
 *
 * NovaHost is not usable from a tab: the Home Screen app keeps its own storage
 * (so anything done in the tab is lost the moment they install), push only
 * works installed, and the device id is per-origin-per-container. So the tab
 * is a dead end on purpose — onboarding shows the install steps and nothing
 * past them, and the router sends every other route back there. Above all,
 * nobody pays from a tab, because the purchase would bind to a device id the
 * installed app will never present.
 *
 * Desktop and Android browsers are never gated: they are where the app is
 * built and reviewed.
 */
export function needsHomeScreenInstall () {
  if (typeof navigator === 'undefined' || typeof window === 'undefined') return false
  const ua = navigator.userAgent || ''
  // iPadOS 13+ reports as a Mac, so check for touch as well.
  const isIOS = /iphone|ipad|ipod/i.test(ua) ||
    (/Macintosh/i.test(ua) && navigator.maxTouchPoints > 1)
  const standalone = window.matchMedia('(display-mode: standalone)').matches ||
    window.navigator.standalone === true
  return isIOS && !standalone
}
