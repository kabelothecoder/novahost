import type { SupabaseClient } from 'https://esm.sh/@supabase/supabase-js@2'
import { MetaCopierBridge } from './metacopier.ts'
import { MetaRpcBridge } from './metarpc.ts'
import type { BridgeProvider, BrokerBridge } from './types.ts'

export * from './types.ts'

/**
 * Which provider holds a licence's broker account, and under what id.
 *
 * ## Where it is read from
 *
 * `licenses.metadata.bridge_provider` and `metadata.bridge_account_id` are the
 * record. Every licence linked before the bridge existed has neither, only
 * `metadata.metacopier_account_id` -- so a missing provider means MetaCopier,
 * and a missing bridge id falls back to that older key. Nobody has to
 * reconnect for this change.
 *
 * The fallback only applies to MetaCopier. A licence that says it is on
 * another provider and carries no id for it is NOT linked; reading a stale
 * MetaCopier id for it would send its orders to an account it has left.
 */
export interface BridgeLink {
  provider: BridgeProvider
  accountId: string
}

const PROVIDERS: BridgeProvider[] = ['metacopier', 'metarpc']

/**
 * The provider a licence's CURRENT link is on.
 *
 * MetaRPC is the default since 2026-10-23. A licence with no provider named
 * but an old `metacopier_account_id` is a MetaCopier link from before the
 * bridge, and stays readable as one until it re-links.
 */
export function providerOf(metadata: Record<string, unknown> | null | undefined): BridgeProvider {
  const named = String(metadata?.bridge_provider ?? '').trim().toLowerCase()
  if ((PROVIDERS as string[]).includes(named)) return named as BridgeProvider
  return String(metadata?.metacopier_account_id ?? '').trim() ? 'metacopier' : 'metarpc'
}

/** Where every NEW link goes. MetaCopier takes no new accounts. */
export const NEW_LINK_PROVIDER: BridgeProvider = 'metarpc'

/** The licence's linked account, or null when it has none. */
export function resolveLink(metadata: Record<string, unknown> | null | undefined): BridgeLink | null {
  const provider = providerOf(metadata)
  const id = String(
    metadata?.bridge_account_id ??
    (provider === 'metacopier' ? metadata?.metacopier_account_id : '') ??
    ''
  ).trim()
  return id ? { provider, accountId: id } : null
}

/**
 * The link's last known session state: `connected`, `connecting` or
 * `disconnected`. Falls back to the MetaCopier-era key for the same reason
 * `resolveLink` does.
 */
export function linkStatusOf(metadata: Record<string, unknown> | null | undefined): string | null {
  const s = metadata?.bridge_status ?? metadata?.metacopier_status
  return s == null ? null : String(s)
}

/**
 * Metadata recording a fresh link.
 *
 * `metacopier_account_id` and `metacopier_status` are still written for a
 * MetaCopier link because functions outside the bridge -- `license-status`,
 * `set-auto-execute`, `claim-signals`, the admin console -- read them. They
 * are a compatibility copy, not the record.
 */
export function withLink(
  metadata: Record<string, unknown>,
  provider: BridgeProvider,
  accountId: string,
  status: string,
): Record<string, unknown> {
  const out: Record<string, unknown> = { ...metadata, bridge_provider: provider, bridge_account_id: accountId, bridge_status: status }
  if (provider === 'metacopier') {
    out.metacopier_account_id = accountId
    out.metacopier_status = status
  } else {
    // An id for one provider must never be read as an id for another.
    delete out.metacopier_account_id
    delete out.metacopier_status
  }
  return out
}

/**
 * Metadata with the link removed, for an account the provider has deleted.
 * Both keys go, or `resolveLink`'s fallback would resurrect the dead id.
 */
export function withoutLink(metadata: Record<string, unknown>, reason: string): Record<string, unknown> {
  const out = { ...metadata }
  delete out.bridge_account_id
  delete out.metacopier_account_id
  out.bridge_status = 'disconnected'
  out.metacopier_status = 'disconnected'
  out.disconnected_at = new Date().toISOString()
  out.disconnected_reason = reason
  return out
}

/**
 * The adapter for a provider, scoped to one account (or none, for `connect`
 * and `servers`).
 *
 * Returns null when the provider's credentials are not configured. Callers
 * answer that as SERVER_MISCONFIGURED and never fall through to another
 * provider: a licence on MetaRPC must not be traded through MetaCopier
 * because one key was missing.
 */
export function bridgeFor(
  provider: BridgeProvider,
  accountId: string | null,
  db: SupabaseClient,
): BrokerBridge | null {
  switch (provider) {
    case 'metacopier': {
      const key = Deno.env.get('METACOPIER_API_KEY') ?? ''
      if (!key) {
        console.error('[bridge] METACOPIER_API_KEY is not configured')
        return null
      }
      return new MetaCopierBridge(key, accountId, { db })
    }
    case 'metarpc': {
      const key = Deno.env.get('METARPC_API_KEY') ?? ''
      if (!key) {
        console.error('[bridge] METARPC_API_KEY is not configured')
        return null
      }
      // A terminal id carries its platform; with no id (connect) the caller's
      // platform is passed through connect() itself.
      return new MetaRpcBridge(key, accountId)
    }
  }
}

/** Shorthand: the adapter for a licence's linked account. */
export function openBridge(link: BridgeLink, db: SupabaseClient): BrokerBridge | null {
  return bridgeFor(link.provider, link.accountId, db)
}
