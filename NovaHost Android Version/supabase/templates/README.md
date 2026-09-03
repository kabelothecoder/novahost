# NovaHost auth email templates

Thirteen transactional emails the auth service sends, in two groups. Stock,
they arrive as plain white pages headed "Confirm Your Signup" with no product
name on them — the first thing a new customer sees from NovaHost was
unbranded. These replace them with the same dark card, visor gradient and
voice as the licence-key email in `../functions/send-license-email`.

### Six workflow emails — something needs the recipient to act

Each ends in a filled gradient button, because there is one specific action.

| File | Fires when | Variables used |
|---|---|---|
| `confirmation.html` | Someone signs up | `.ConfirmationURL` `.Token` |
| `invite.html` | A mentor invites a trader | `.ConfirmationURL` `.Email` |
| `magic_link.html` | Passwordless sign-in | `.ConfirmationURL` `.Token` |
| `recovery.html` | Password reset | `.ConfirmationURL` `.Email` `.Token` |
| `email_change.html` | Address change, sent to both addresses | `.ConfirmationURL` `.Email` `.NewEmail` `.Token` |
| `reauthentication.html` | Step-up check before a sensitive change | `.Token` |

### Seven security notifications — something already happened

None of these receives a `ConfirmationURL` — there is nothing to confirm, so
each ends in an outlined, unfilled button linking `.SiteURL` (a way back into
the app) instead of a call to action.

**Every one is OFF at the project level until its own
`mailer_notifications_<key>_enabled` flag is turned on.** Building and
deploying the template is necessary but not sufficient — content and
enablement are separate switches on Supabase's side. See **Deploying** below.

| File | Fires when | Variables used |
|---|---|---|
| `password_changed_notification.html` | Password changed | `.Email` |
| `email_changed_notification.html` | Email address changed | `.Email` `.OldEmail` |
| `phone_changed_notification.html` | Phone number changed | `.Phone` `.OldPhone` |
| `identity_linked_notification.html` | A sign-in provider (Google, etc.) is linked | `.Provider` `.Email` |
| `identity_unlinked_notification.html` | A sign-in provider is removed | `.Provider` `.Email` |
| `mfa_factor_enrolled_notification.html` | A verification method (e.g. TOTP) is added | `.FactorType` |
| `mfa_factor_unenrolled_notification.html` | A verification method is removed | `.FactorType` |

As of 2026-09-03, NovaHost's Android app authenticates with email and password
only — no OAuth provider, no MFA, no phone auth is wired up anywhere in the
codebase. `password_changed` and `email_changed` are live-relevant today; the
other five are dormant until one of those features actually ships. They cost
nothing to have ready now, but turning them on before then has no effect —
nothing in the app can trigger them.

## Editing

Do not hand-edit the `.html` files. The template system has no includes, so the
shell markup is duplicated into all thirteen; editing one by hand
desynchronises it from the rest. Edit `build.mjs` and regenerate:

```bash
node build.mjs && node preview.mjs
```

`preview.mjs` writes `preview.out.html` — all thirteen rendered with sample
values, openable in a browser. That is a design check only. A browser is far
more forgiving than Outlook, so run a real client test before changing
anything structural.

## Deploying

`config.toml` wires the files up for **local development only** — a hosted
project does not read templates from git. Two ways to ship them:

### `node deploy.mjs` (preferred)

```bash
export SUPABASE_ACCESS_TOKEN="sbp_..."   # from supabase.com/dashboard/account/tokens
node deploy.mjs --check                  # what is live now vs. local, incl. notification on/off state
node deploy.mjs                          # apply all 13 templates
```

The plain run PATCHes exactly 26 fields on the Management API — thirteen
`mailer_subjects_*` and thirteen `mailer_templates_*_content` — and nothing
else. `--check` and `--dry-run` send nothing.

**The seven security notifications need one more step.** Content and
enablement are separate switches, so deploying the template does not turn the
notification on. `--check` reports each one's live enabled state; flip them
explicitly:

```bash
node deploy.mjs --enable-notifications    # or --disable-notifications
```

This touches exactly the 7 `mailer_notifications_<key>_enabled` fields, and
only when one of these two flags is passed by name — a plain `node deploy.mjs`
never reaches that code path. The dashboard screen in **Authentication →
Emails** did not list these seven as of 2026-09-03, so the Management API may
currently be the only way to toggle them; if a toggle later appears there,
that works too.

**Do not use `supabase config push` for this.** That command uploads the whole
`[auth]` section, and every setting absent from our `config.toml` goes up as a
CLI default. On this project that risks overwriting URL configuration, provider
toggles, SMTP settings and rate limits, none of which we track in config.toml.

### By hand

Open **Authentication → Emails**, paste each `.html` file into its matching
template, and set the subject lines to the ones in `../config.toml`. As of
2026-09-03 this screen only lists the six workflow templates — the seven
notifications aren't there to paste into, which is the other reason
`deploy.mjs` is the preferred path for those.

## Known limits

**The links point at `epulmnfbxjmaimefhofp.supabase.co`.** No template change
fixes this — the host is the auth service's own. A custom domain (a paid
add-on) makes it `auth.novahost.co`; until then the email body is fully branded
but the URL is not. This is the last visible piece of the rename.

**The sender address comes from SMTP settings, not from here.** While the
project is on the built-in mailer the From line reads `noreply@mail.app.supabase.io`
and delivery is rate-limited to a handful an hour. Point **Project Settings →
Auth → SMTP** at the same Resend account `send-license-email` already uses, with
a verified `novahost.co` sender, and both the From line and the throughput
problem go away together.

**Dark-background mail gets inverted by some clients.** `color-scheme: dark` is
declared, which modern Gmail, Apple Mail and Outlook.com honour. Older Outlook
desktop renders the card on its own light chrome; the card itself stays dark and
readable, so it degrades rather than breaks.

**Outlook desktop drops the gradient and the rounded corners.** Every gradient
is layered over a solid `background-color`, so the button renders as a solid
violet rectangle there rather than disappearing.
