/**
 * Pushes the built auth email templates to the linked project.
 *
 *   node deploy.mjs --check                  what is live now vs. local
 *   node deploy.mjs --dry-run                print exactly what would be sent
 *   node deploy.mjs                          apply all 13 templates (26 fields)
 *   node deploy.mjs --enable-notifications   turn on all 7 security notifications
 *   node deploy.mjs --disable-notifications  turn them back off
 *
 * Needs a Supabase personal access token in the environment. Create one at
 * https://supabase.com/dashboard/account/tokens and export it in your own
 * shell -- it is never read from a file or committed:
 *
 *   export SUPABASE_ACCESS_TOKEN="sbp_..."     # bash
 *   $env:SUPABASE_ACCESS_TOKEN = "sbp_..."     # PowerShell
 *
 * WHY NOT `supabase config push`: that command sends the whole [auth] section,
 * and any setting absent from config.toml goes up as a CLI default. On this
 * project that would risk overwriting URL configuration, provider toggles,
 * SMTP and rate limits -- none of which live in our config.toml. This script
 * PATCHes only the named fields below, so nothing else on the project can be
 * touched by running it: the plain (no-flag) run touches exactly the 26
 * subject/content fields; --enable-notifications and --disable-notifications
 * touch exactly the 7 enable flags, and only when explicitly passed.
 */

import { readFileSync } from 'node:fs';

const API = 'https://api.supabase.com/v1';

// file -> the API's field names, and the subject line that ships with it.
// `enableFlag` is set only for the seven security notifications, which are
// off at the project level until that flag is true -- content and
// enablement are separate switches on Supabase's side, so this script keeps
// them separate too.
const TEMPLATES = [
  { file: 'confirmation',     key: 'confirmation',     subject: 'Confirm your NovaHost account' },
  { file: 'invite',           key: 'invite',           subject: "You've been invited to NovaHost" },
  { file: 'magic_link',       key: 'magic_link',       subject: 'Your NovaHost sign-in link' },
  { file: 'recovery',         key: 'recovery',         subject: 'Reset your NovaHost password' },
  { file: 'email_change',     key: 'email_change',     subject: 'Confirm your new NovaHost email' },
  { file: 'reauthentication', key: 'reauthentication', subject: 'Your NovaHost verification code' },

  { file: 'password_changed_notification', key: 'password_changed_notification',
    subject: 'Your NovaHost password was changed',
    enableFlag: 'mailer_notifications_password_changed_enabled' },
  { file: 'email_changed_notification', key: 'email_changed_notification',
    subject: 'Your NovaHost email address was changed',
    enableFlag: 'mailer_notifications_email_changed_enabled' },
  { file: 'phone_changed_notification', key: 'phone_changed_notification',
    subject: 'Your NovaHost phone number was changed',
    enableFlag: 'mailer_notifications_phone_changed_enabled' },
  { file: 'identity_linked_notification', key: 'identity_linked_notification',
    subject: 'A sign-in method was linked to your NovaHost account',
    enableFlag: 'mailer_notifications_identity_linked_enabled' },
  { file: 'identity_unlinked_notification', key: 'identity_unlinked_notification',
    subject: 'A sign-in method was removed from your NovaHost account',
    enableFlag: 'mailer_notifications_identity_unlinked_enabled' },
  { file: 'mfa_factor_enrolled_notification', key: 'mfa_factor_enrolled_notification',
    subject: 'A verification method was added to your NovaHost account',
    enableFlag: 'mailer_notifications_mfa_factor_enrolled_enabled' },
  { file: 'mfa_factor_unenrolled_notification', key: 'mfa_factor_unenrolled_notification',
    subject: 'A verification method was removed from your NovaHost account',
    enableFlag: 'mailer_notifications_mfa_factor_unenrolled_enabled' },
];

const args = new Set(process.argv.slice(2));
const CHECK = args.has('--check');
const DRY = args.has('--dry-run');
const ENABLE = args.has('--enable-notifications');
const DISABLE = args.has('--disable-notifications');

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

const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
const notifications = TEMPLATES.filter((t) => t.enableFlag);

// --- Explicit, opt-in enable/disable path -------------------------------
// Deliberately separate from everything below: it is the only code path that
// touches the 7 enable flags, and only runs when one of these two flags is
// passed by name. A plain `node deploy.mjs` never reaches this branch.
if (ENABLE || DISABLE) {
  const body = Object.fromEntries(notifications.map((t) => [t.enableFlag, ENABLE]));
  console.log(`${ENABLE ? 'Enabling' : 'Disabling'} ${notifications.length} security notification(s):`);
  notifications.forEach((t) => console.log(`  ${t.enableFlag} -> ${ENABLE}`));

  if (DRY) {
    console.log('\nDry run -- nothing sent.');
    process.exit(0);
  }

  const res = await fetch(`${API}/projects/${projectRef}/config/auth`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    console.error(`\nPATCH failed: ${res.status} ${res.statusText}`);
    console.error(await res.text());
    process.exit(1);
  }
  console.log('\nDone.');
  process.exit(0);
}

// --- Content path: subjects + bodies only -------------------------------

const local = Object.fromEntries(
  TEMPLATES.flatMap((t) => [
    [`mailer_subjects_${t.key}`, t.subject],
    [`mailer_templates_${t.key}_content`, readFileSync(new URL(`${t.file}.html`, import.meta.url), 'utf8')],
  ])
);

const res = await fetch(`${API}/projects/${projectRef}/config/auth`, { headers });
if (!res.ok) {
  console.error(`GET config/auth failed: ${res.status} ${res.statusText}`);
  console.error(await res.text());
  process.exit(1);
}
const remote = await res.json();

console.log(`project ${projectRef}\n`);
let differs = 0;
for (const t of TEMPLATES) {
  const sk = `mailer_subjects_${t.key}`;
  const ck = `mailer_templates_${t.key}_content`;
  const subjSame = (remote[sk] ?? '') === local[sk];
  const bodySame = (remote[ck] ?? '') === local[ck];
  if (!subjSame || !bodySame) differs++;
  const mark = subjSame && bodySame ? 'up to date' : 'will change';
  console.log(`  ${t.key.padEnd(38)} ${mark}`);
  if (CHECK) {
    console.log(`      live subject: ${JSON.stringify(remote[sk] ?? '(unset)')}`);
    console.log(`      new  subject: ${JSON.stringify(local[sk])}`);
    console.log(`      live body:    ${(remote[ck] ?? '').length} bytes -> ${local[ck].length} bytes`);
    if (t.enableFlag) {
      const on = remote[t.enableFlag];
      console.log(
        `      remote enabled: ${on === true ? 'YES -- Supabase is sending this' : on === false ? 'no -- content will sit unused' : '(field not present in this response)'}`
      );
    }
  }
}

const offNotifications = notifications.filter((t) => remote[t.enableFlag] !== true);
if (offNotifications.length) {
  console.log(
    `\n${offNotifications.length} of ${notifications.length} security notification(s) are OFF at the project level -- their content will be saved but Supabase will not send them:`
  );
  offNotifications.forEach((t) => console.log(`  ${t.key}  (${t.enableFlag})`));
  console.log('Turn them on with: node deploy.mjs --enable-notifications');
}

if (CHECK || DRY) {
  console.log(`\n${differs} of ${TEMPLATES.length} would change. Nothing was sent.`);
  console.log('Content fields this script touches, and no others:');
  Object.keys(local).forEach((k) => console.log('  ' + k));
  process.exit(0);
}

if (differs === 0) {
  console.log('\nContent already up to date. Nothing sent.');
  process.exit(0);
}

const patch = await fetch(`${API}/projects/${projectRef}/config/auth`, {
  method: 'PATCH',
  headers,
  body: JSON.stringify(local),
});

if (!patch.ok) {
  console.error(`\nPATCH failed: ${patch.status} ${patch.statusText}`);
  console.error(await patch.text());
  process.exit(1);
}

console.log(`\nApplied ${TEMPLATES.length} templates to ${projectRef}.`);
console.log('Verify with: node deploy.mjs --check');
