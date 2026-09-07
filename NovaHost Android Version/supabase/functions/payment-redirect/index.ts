import { serve } from "https://deno.land/std@0.208.0/http/server.ts";

/**
 * Where Payfast sends the BROWSER once a payment finishes or is abandoned.
 *
 * Payfast will only accept an https return_url -- it rejects custom schemes
 * outright -- so this function is the bridge between it and whichever client
 * started the checkout. Two clients share it and they need opposite things:
 *
 *   android   The native app registers the `metahost://` scheme, so a 302 to
 *             the deep link drops the user back inside the running activity.
 *             Unchanged.
 *
 *   ios/web   A PWA. There is no scheme to hand off to, and `metahost://` in
 *             Safari is an "invalid address" error page -- which is exactly
 *             what every iOS payer was landing on after paying.
 *
 * `platform` is appended to return_url/cancel_url by generate-payfast-checkout.
 * ABSENT MEANS ANDROID: that is what every build shipped before the parameter
 * existed sends, and their deep link has to keep working. Nothing about the
 * android path changes here.
 *
 * -- Why this function does not just render the page -----------------------
 *
 * It tried to, and the platform will not allow it. A function on
 * `*.supabase.co` that answers with `Content-Type: text/html` has the header
 * rewritten to `text/plain` by the edge gateway and is served with
 * `X-Content-Type-Options: nosniff` and `Content-Security-Policy:
 * default-src 'none'; sandbox`. Verified against this project: the same
 * function returning JSON keeps `application/json` and gets neither header.
 * That is Supabase refusing to host arbitrary browser-rendered HTML, and it
 * means a styled page served from here reaches the user as its own raw
 * markup. Undocumented, so the observed behaviour is the authority.
 *
 * So the page belongs on the PWA's own origin, which serves real HTML:
 * PWA_URL points at it and this function only ever redirects there.
 *
 * -- Why the redirect target is a dead-end route ---------------------------
 *
 * `#/payment-return` deliberately does NOT check entitlement, and the router
 * guard lets it through unverified. A standalone iOS PWA breaks out to Safari
 * for a cross-origin navigation, and since iOS 16.4 a Home Screen web app
 * keeps its own storage bucket. So the copy of the app that Safari loads here
 * holds a DIFFERENT DEVICE_ID from the installed one the purchase was bound
 * to -- and any entitlement check on this screen would read that as
 * `device_mismatch` and tell someone who just paid R599 that their licence
 * lives on another device. The page states the outcome and points home; the
 * installed app re-checks for itself when it comes back to the front.
 *
 * -- With PWA_URL unset ----------------------------------------------------
 *
 * Plain text, which is the one thing this domain will render faithfully. Not
 * pretty, but it is legible and true, and it beats both an "invalid address"
 * error and a screenful of raw markup.
 */

/** The PWA's public origin, e.g. https://app.novahost.co.za. Optional. */
const PWA_URL = Deno.env.get("PWA_URL") || "";

/**
 * The origin to send a web payer home to, or "" if none is usable.
 *
 * PWA_URL is operator-set rather than user-supplied, so this is a
 * misconfiguration guard rather than an injection one -- but a value that is
 * not an absolute https origin would be a broken redirect on the one screen a
 * paying customer sees, so it is dropped rather than followed.
 */
function safeAppOrigin(): string {
  if (!PWA_URL) return "";
  try {
    const parsed = new URL(PWA_URL);
    return parsed.protocol === "https:" ? parsed.origin : "";
  } catch {
    return "";
  }
}

/** The fallback, for when there is nowhere to send them. */
function plainText(status: string): string {
  const cancelled = status === "cancel";

  const heading = cancelled ? "PAYMENT CANCELLED" : "PAYMENT RECEIVED";

  const body = cancelled
    ? "Nothing was charged. You can try again from the app whenever\nyou are ready."
    : "Thank you. Your purchase is being activated on the device you\nstarted from - it usually takes a few seconds.";

  const next = cancelled
    ? "Switch back to the NovaHost app to pick up where you left off."
    : "Switch back to the NovaHost app. It checks again the moment it\ncomes to the front, and unlocks itself.";

  return [
    "NovaHost",
    "",
    heading,
    "",
    body,
    "",
    "WHAT TO DO NOW",
    "",
    next,
    "",
    "",
    "Payments are handled by Payfast. NovaHost never sees your card",
    "details.",
    "",
  ].join("\n");
}

serve((req) => {
  const url = new URL(req.url);

  const rawStatus = url.searchParams.get("status") || "success";
  const status = rawStatus === "cancel" ? "cancel" : "success";

  // Absent = android. Every build that predates this parameter relies on it.
  const platform = (url.searchParams.get("platform") || "android").toLowerCase();

  if (platform === "android") {
    return new Response(null, {
      status: 302,
      headers: { Location: `metahost://payment/${status}` },
    });
  }

  const origin = safeAppOrigin();

  if (origin) {
    return new Response(null, {
      status: 302,
      headers: {
        // Hash history: everything after `#` is the route, query included.
        Location: `${origin}/#/payment-return?status=${status}`,
        "Cache-Control": "no-store",
      },
    });
  }

  return new Response(plainText(status), {
    status: 200,
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      // The outcome is per-transaction; a cached "PAYMENT RECEIVED" shown to
      // the next person to abandon a checkout would be a lie.
      "Cache-Control": "no-store",
    },
  });
});
