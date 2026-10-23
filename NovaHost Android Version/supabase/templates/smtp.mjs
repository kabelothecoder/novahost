/**
 * Points the auth service's mailer at Resend, and lifts the send rate limit.
 *
 *   node smtp.mjs --check     what is live now (the password is never printed)
 *   node smtp.mjs --dry-run   print exactly what would be sent
 *   node smtp.mjs             apply
 *
 * WHY THIS EXISTS: on the built-in mailer, auth email is capped at a couple an
 * hour on every plan -- Pro raises the ceiling you are allowed to configure, it
 * does not raise the built-in sender's own throttle. Real signups hit
 * `429 over_email_send_rate_limit` and the customer sees "Email rate limit
 * exceeded" with no account created. Custom SMTP is the only fix, and it also
 * replaces the `noreply@mail.app.supabase.io` From line at the same time.
 *
 * Requires two secrets in the environment, neither of which is stored or logged:
 *
 *   export SUPABASE_ACCESS_TOKEN="sbp_..."   # dashboard/account/tokens
 *   export RESEND_API_KEY="re_..."           # the same key send-license-email uses
 *
 * Like deploy.mjs, this PATCHes only the fields named in CONFIG below, so
 * nothing else on the project can be touched by running it. `supabase config
 * push` would send the whole [auth] section and default everything absent from
 * config.toml -- do not use it here either.
 */

import { readFileSync } from 'node:fs';

const API = 'https://api.supabase.com/v1';

// Resend's SMTP relay takes the literal username "resend" and the API key as
// the password -- the key is not an account password, and there is no separate
// SMTP credential to create.
const SMTP_HOST = 'smtp.resend.com';
const SMTP_PORT = '587';
const SMTP_USER = 'resend';

// Must be on a domain that is VERIFIED in Resend, not merely owned. An
// unverified sender is accepted by the API and then silently delivered only to
// the Resend account owner, which looks identical to working when you test it
// on your own address. novahost.co is parked with no mail configured; the live
// domain is the one below.
const SENDER_DOMAIN = 'novahost-ea.app';
const SMTP_ADMIN_EMAIL = `no-reply@${SENDER_DOMAIN}`;
const SMTP_SENDER_NAME = 'NovaHost';

// Emails per hour across the project. The built-in mailer's effective ceiling
// is single digits; 100 is room to launch into without being an open relay if
// the signup form is ever scripted. Raise it when real volume justifies it.
const RATE_LIMIT_EMAIL_SENT = 100;

// Seconds a single address must wait between auth emails. Guards the resend
// button; unrelated to the hourly figure above.
const SMTP_MAX_FREQUENCY = 60;

const args = new Set(process.argv.slice(2));
const CHECK = args.has('--check');
const DRY = args.has('--dry-run');

const refArg = process.argv.find((a) => a.startsWith('--project-ref='));
const projectRef =
  refArg?.split('=')[1] ??
  readFileSync(new URL('../config.toml', import.meta.url), 'utf8').match(/project_id\s*=\s*"([^"]+)"/)?.[1];

if (!projectRef) {
  console.error('Could not determine the project ref. Pass --project-ref=<ref>.');
  process.exit(1);
}

const token = process.env.SUPABASE_ACCESS_TOKEN;
if (!token) {
  console.error(
    'SUPABASE_ACCESS_TOKEN is not set.\n\n' +
      'Create one at https://supabase.com/dashboard/account/tokens, then export it\n' +
      'in your shell and re-run. This script never stores or logs the token.'
  );
  process.exit(1);
}

// Only the apply path needs the Resend key -- --check and --dry-run describe
// the shape of the change without needing the secret to do it.
const resendKey = process.env.RESEND_API_KEY;
if (!resendKey && !CHECK && !DRY) {
  console.error(
    'RESEND_API_KEY is not set.\n\n' +
      'Use the same key send-license-email already has in its edge function\n' +
      'secrets, so both senders bill and authenticate as one account.'
  );
  process.exit(1);
}

const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

const CONFIG = {
  smtp_host: SMTP_HOST,
  smtp_port: SMTP_PORT,
  smtp_user: SMTP_USER,
  smtp_pass: resendKey,
  smtp_admin_email: SMTP_ADMIN_EMAIL,
  smtp_sender_name: SMTP_SENDER_NAME,
  smtp_max_frequency: SMTP_MAX_FREQUENCY,
  rate_limit_email_sent: RATE_LIMIT_EMAIL_SENT,
};

// Never let the key reach a terminal, a scrollback buffer or a CI log.
const redact = (k, v) => (k === 'smtp_pass' ? (v ? `set (${String(v).length} chars)` : 'unset') : v);

const res = await fetch(`${API}/projects/${projectRef}/config/auth`, { headers });
if (!res.ok) {
  console.error(`GET config/auth failed: ${res.status} ${res.statusText}`);
  console.error(await res.text());
  process.exit(1);
}
const remote = await res.json();

console.log(`project ${projectRef}\n`);
for (const key of Object.keys(CONFIG)) {
  const live = redact(key, remote[key]);
  const next = redact(key, CONFIG[key]);
  const same = String(remote[key] ?? '') === String(CONFIG[key] ?? '');
  console.log(`  ${key.padEnd(24)} ${same ? 'up to date' : 'will change'}`);
  if (CHECK || DRY) console.log(`      now:  ${live ?? '(unset)'}\n      next: ${next}`);
}

if (CHECK) process.exit(0);
if (DRY) {
  console.log('\nDry run -- nothing sent.');
  process.exit(0);
}

const patch = await fetch(`${API}/projects/${projectRef}/config/auth`, {
  method: 'PATCH',
  headers,
  body: JSON.stringify(CONFIG),
});
if (!patch.ok) {
  console.error(`\nPATCH failed: ${patch.status} ${patch.statusText}`);
  console.error(await patch.text());
  process.exit(1);
}

console.log(
  `\nDone. Auth mail now sends as ${SMTP_SENDER_NAME} <${SMTP_ADMIN_EMAIL}> via Resend.\n\n` +
    `If ${SENDER_DOMAIN} is not verified in Resend, delivery will silently\n` +
    `narrow to the Resend account owner. Confirm it reads "Verified" under\n` +
    `Domains before treating signup as fixed, and test with an address that is\n` +
    `not your own.`
);
