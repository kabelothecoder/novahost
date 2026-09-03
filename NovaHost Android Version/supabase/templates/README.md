# NovaHost auth email templates

The six transactional emails the auth service sends. Stock, they arrive as
plain white pages headed "Confirm Your Signup" with no product name on them —
the first thing a new customer sees from NovaHost was unbranded. These replace
them with the same dark card, visor gradient and voice as the licence-key email
in `../functions/send-license-email`.

| File | Fires when | Variables used |
|---|---|---|
| `confirmation.html` | Someone signs up | `.ConfirmationURL` `.Token` |
| `invite.html` | A mentor invites a trader | `.ConfirmationURL` `.Email` |
| `magic_link.html` | Passwordless sign-in | `.ConfirmationURL` `.Token` |
| `recovery.html` | Password reset | `.ConfirmationURL` `.Email` `.Token` |
| `email_change.html` | Address change, sent to both addresses | `.ConfirmationURL` `.Email` `.NewEmail` `.Token` |
| `reauthentication.html` | Step-up check before a sensitive change | `.Token` |

## Editing

Do not hand-edit the `.html` files. The template system has no includes, so the
shell markup is duplicated into all six; editing one by hand desynchronises it
from the rest. Edit `build.mjs` and regenerate:

```bash
node build.mjs && node preview.mjs
```

`preview.mjs` writes `preview.out.html` — all six rendered with sample values,
openable in a browser. That is a design check only. A browser is far more
forgiving than Outlook, so run a real client test before changing anything
structural.

## Deploying

`config.toml` wires the files up for local development. A hosted project does
**not** pick them up from git — the dashboard stays the source of truth. To
ship:

1. Open **Authentication → Emails** in the project dashboard.
2. For each template, paste the contents of the matching `.html` file.
3. Set the subject lines to the ones in `../config.toml`.

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
