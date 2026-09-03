/**
 * Builds the thirteen NovaHost-branded auth email templates: six workflow
 * emails (something needs the recipient to act) and seven security
 * notifications (something already happened; recipient only needs to know).
 *
 * The auth provider's template system has no includes -- each template is one
 * standalone HTML string pasted into Authentication > Emails (or referenced
 * from config.toml). That means the ~120 lines of shell markup have to be
 * duplicated into every file. Generating them from one source keeps the
 * thirteen in sync; edit THIS file, run `node build.mjs`, commit the output.
 *
 * Design language is lifted from the shipped licence-key email
 * (supabase/functions/send-license-email) so a buyer who gets a licence key and
 * then resets their password sees the same brand both times.
 *
 * The two groups look deliberately different in one respect: the six workflow
 * emails end in a filled gradient button, because they ask for one specific
 * action. The seven notifications end in an outlined, unfilled button --
 * same shape, no fill -- because there is nothing to act on; a loud button
 * would misstate that. Neither group's provider passes it a `ConfirmationURL`
 * at all, which is *why* the outline exists: it links `{{ .SiteURL }}`
 * instead, a plain way back into the app rather than a call to action.
 */

import { writeFileSync } from 'node:fs';

// --- Brand tokens -------------------------------------------------------
// Hex only, no CSS variables: Outlook and Gmail strip <style> blocks and
// custom properties, so every colour has to be inlined at its use site.
const C = {
  ink:      '#07070E', // page background
  card:     '#0E1015', // card surface
  hairline: '#1D2029', // card border and dividers
  chipBg:   '#14171E',
  chipLine: '#23262F',
  chipText: '#A9B0BF',
  title:    '#FFFFFF',
  wordmark: '#F2F4F8',
  body:     '#98A0B0',
  bodySoft: '#A6ADBC',
  label:    '#6C7484',
  cyan:     '#22C9E8',
  violet:   '#A855F7',
  footer:   '#5B6272',
  copy:     '#454B58',
  codeLine: '#2A2E3A',
  strong:   '#E7EAF1',
};

const FONT = "'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
const MONO = "'SF Mono',Consolas,'Courier New',monospace";
// Layered over a solid background-color everywhere it is used, so a client
// that drops the gradient still renders a filled shape rather than nothing.
const VISOR = `linear-gradient(100deg,#F0439E 0%,${C.violet} 48%,${C.cyan} 100%)`;

// --- Shared fragments ---------------------------------------------------

const eyebrow = (text) => `
          <tr>
            <td style="padding:20px 36px 0 36px;">
              <div style="display:inline-block;background-color:${C.chipBg};border:1px solid ${C.chipLine};border-radius:999px;padding:6px 14px;font-family:${FONT};font-size:12px;color:${C.chipText};">
                ${text}
              </div>
            </td>
          </tr>`;

const heading = (title, lede) => `
          <tr>
            <td style="padding:20px 36px 0 36px;">
              <h1 style="margin:0;font-family:${FONT};font-size:27px;line-height:1.15;font-weight:700;color:${C.title};letter-spacing:-0.02em;">
                ${title}
              </h1>
              <p style="margin:12px 0 0 0;font-family:${FONT};font-size:15px;line-height:1.6;color:${C.body};">
                ${lede}
              </p>
            </td>
          </tr>`;

// Dark label on the gradient: #07070E over cyan/violet/pink all clear AA.
// Reserved for the six workflow emails -- one specific action, one loud button.
const button = (label) => `
          <tr>
            <td style="padding:28px 36px 0 36px;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td style="background-color:${C.violet};background-image:${VISOR};border-radius:999px;">
                    <a href="{{ .ConfirmationURL }}" style="display:inline-block;padding:14px 30px;font-family:${FONT};font-size:15px;font-weight:700;color:${C.ink};text-decoration:none;">
                      ${label}
                    </a>
                  </td>
                </tr>
              </table>
            </td>
          </tr>`;

// Outline-only counterpart, same shape, no fill. Used by the seven security
// notifications, none of which receive a `ConfirmationURL` -- there is
// nothing to confirm, so this links `.SiteURL` as a quiet way back into the
// app rather than a call to action.
const secondaryButton = (label) => `
          <tr>
            <td style="padding:26px 36px 0 36px;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td style="border:1px solid ${C.codeLine};border-radius:999px;">
                    <a href="{{ .SiteURL }}" style="display:inline-block;padding:12px 28px;font-family:${FONT};font-size:14px;font-weight:600;color:${C.cyan};text-decoration:none;">
                      ${label}
                    </a>
                  </td>
                </tr>
              </table>
            </td>
          </tr>`;

const codeBlock = (label, value, opts = {}) => `
          <tr>
            <td style="padding:${opts.tight ? '22px' : '28px'} 36px 0 36px;">
              <div style="font-family:${FONT};font-size:11px;font-weight:600;letter-spacing:0.14em;text-transform:uppercase;color:${C.label};padding-bottom:10px;">
                ${label}
              </div>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td style="background-color:${C.ink};border:1px solid ${C.codeLine};border-radius:10px;padding:20px;text-align:center;font-family:${MONO};font-size:${opts.size || 26}px;font-weight:700;letter-spacing:0.22em;color:${C.cyan};">
                    ${value}
                  </td>
                </tr>
              </table>
            </td>
          </tr>`;

// Two labelled rows in one card -- "before" then "after". Shared by the
// email-change confirmation link, and by the email/phone-changed
// notifications that report a completed change.
const factPair = (labelA, valueA, labelB, valueB, opts = {}) => `
          <tr>
            <td style="padding:${opts.tight ? '22px' : '26px'} 36px 0 36px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:${C.ink};border:1px solid ${C.codeLine};border-radius:10px;">
                <tr>
                  <td style="padding:16px 18px 6px 18px;font-family:${FONT};font-size:11px;font-weight:600;letter-spacing:0.14em;text-transform:uppercase;color:${C.label};">${labelA}</td>
                </tr>
                <tr>
                  <td style="padding:0 18px 14px 18px;font-family:${MONO};font-size:14px;color:${C.bodySoft};word-break:break-all;">${valueA}</td>
                </tr>
                <tr>
                  <td style="padding:0 18px;"><div style="border-top:1px solid ${C.codeLine};font-size:0;line-height:0;">&nbsp;</div></td>
                </tr>
                <tr>
                  <td style="padding:14px 18px 6px 18px;font-family:${FONT};font-size:11px;font-weight:600;letter-spacing:0.14em;text-transform:uppercase;color:${C.label};">${labelB}</td>
                </tr>
                <tr>
                  <td style="padding:0 18px 16px 18px;font-family:${MONO};font-size:14px;font-weight:700;color:${C.cyan};word-break:break-all;">${valueB}</td>
                </tr>
              </table>
            </td>
          </tr>`;

// The raw link. Some corporate mail gateways rewrite or strip anchor hrefs;
// printing the URL as text gives the recipient a path that survives that.
// Workflow emails only -- the notifications' secondaryButton has no matching
// fallback because .SiteURL is a destination, not a one-time token URL; a
// recipient who cannot click it can just navigate to the app directly.
const fallbackLink = `
          <tr>
            <td style="padding:24px 36px 0 36px;">
              <div style="font-family:${FONT};font-size:12.5px;line-height:1.6;color:${C.label};">
                Button not working? Paste this into your browser:
                <br>
                <a href="{{ .ConfirmationURL }}" style="color:${C.cyan};text-decoration:none;word-break:break-all;">{{ .ConfirmationURL }}</a>
              </div>
            </td>
          </tr>`;

const footer = (lines) => `
          <tr>
            <td style="padding:30px 36px 34px 36px;">
              <div style="border-top:1px solid ${C.hairline};padding-top:18px;font-family:${FONT};font-size:12.5px;line-height:1.6;color:${C.footer};">
                ${lines
                  .map((l, i) => `<p style="margin:${i === lines.length - 1 ? '0' : '0 0 6px 0'};">${l}</p>`)
                  .join('\n                ')}
              </div>
            </td>
          </tr>`;

/**
 * Wraps the per-template rows in the shared shell.
 * `preheader` is the grey line the inbox shows next to the subject -- left
 * unset it would leak whatever markup came first, so every template sets one.
 */
const shell = ({ title, preheader, rows }) => `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>${title}</title>
</head>
<body style="margin:0;padding:0;background-color:${C.ink};">
  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">${preheader}</div>

  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:${C.ink};padding:32px 16px;">
    <tr>
      <td align="center">

        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;width:100%;background-color:${C.card};border:1px solid ${C.hairline};border-radius:16px;overflow:hidden;">

          <!-- Gradient hairline, the brand's visor -->
          <tr>
            <td style="height:3px;background-color:${C.violet};background-image:${VISOR};font-size:0;line-height:0;">&nbsp;</td>
          </tr>

          <tr>
            <td style="padding:36px 36px 0 36px;">
              <div style="font-family:${FONT};font-size:17px;font-weight:700;color:${C.wordmark};letter-spacing:-0.02em;">
                NovaHost
              </div>
            </td>
          </tr>
${rows.join('\n')}
        </table>

        <div style="font-family:${FONT};font-size:11.5px;color:${C.copy};padding-top:18px;">
          &copy; NovaHost
        </div>

      </td>
    </tr>
  </table>
</body>
</html>
`;

// --- The thirteen templates ----------------------------------------------
// Filenames match the provider's own template keys so the config.toml and
// deploy.mjs wiring is obvious at a glance. The seven notification filenames
// keep the `_notification` suffix that the Management API's field names use
// (`mailer_templates_<key>_content`) even though config.toml's own section
// key drops it (`[auth.email.notification.password_changed]`) -- the doc's
// own example does the same mismatch, since content_path is just a path.

const templates = {
  // ── Six workflow emails: something needs the recipient to act ─────────

  confirmation: shell({
    title: 'Confirm your NovaHost account',
    preheader: 'One tap to confirm your email and finish setting up NovaHost.',
    rows: [
      eyebrow('New account'),
      heading(
        'Confirm your email',
        'You are almost in. Confirm this address and your NovaHost account is live &mdash; then link a broker and your mentor&rsquo;s trades start arriving.'
      ),
      button('Confirm my email'),
      codeBlock('Or enter this code', '{{ .Token }}'),
      fallbackLink,
      footer([
        'This link expires in 24 hours. NovaHost hosts the robot and copies its trades to your own broker account &mdash; we never hold your funds.',
        'If you did not sign up, ignore this email. No account is created until the address is confirmed.',
      ]),
    ],
  }),

  invite: shell({
    title: 'You have been invited to NovaHost',
    preheader: 'Your mentor invited you to NovaHost. Accept to set up your account.',
    rows: [
      eyebrow('Invitation'),
      heading(
        'You&rsquo;ve been invited',
        `Someone has invited <strong style="color:${C.strong};">{{ .Email }}</strong> to NovaHost. Accept below to pick a password and get your account running.`
      ),
      button('Accept the invitation'),
      fallbackLink,
      footer([
        'NovaHost copies a mentor&rsquo;s trades to your own MT4 or MT5 account. You keep your funds with your own broker &mdash; we never hold them.',
        'Not expecting this? You can ignore it. The invitation does nothing until you accept it.',
      ]),
    ],
  }),

  magic_link: shell({
    title: 'Your NovaHost sign-in link',
    preheader: 'Your one-time sign-in link for NovaHost. It expires in one hour.',
    rows: [
      eyebrow('Sign in'),
      heading(
        'Your sign-in link',
        'Tap below to sign in to NovaHost. No password needed &mdash; this link signs you in on the device that opens it.'
      ),
      button('Sign me in'),
      codeBlock('Or enter this code', '{{ .Token }}'),
      fallbackLink,
      footer([
        'This link works once and expires in one hour. Anyone who opens it can sign in as you, so do not forward it.',
        'If you did not ask to sign in, ignore this email and your account stays as it is.',
      ]),
    ],
  }),

  recovery: shell({
    title: 'Reset your NovaHost password',
    preheader: 'Reset your NovaHost password. The link expires in one hour.',
    rows: [
      eyebrow('Password reset'),
      heading(
        'Reset your password',
        `We got a request to reset the password for <strong style="color:${C.strong};">{{ .Email }}</strong>. Set a new one below.`
      ),
      button('Set a new password'),
      codeBlock('Or enter this code', '{{ .Token }}'),
      fallbackLink,
      footer([
        'This link expires in one hour and can be used once. Your current password keeps working until you set a new one.',
        'If you did not ask for this, ignore the email &mdash; but if it keeps arriving, someone knows your address and you should change your password from inside the app.',
      ]),
    ],
  }),

  email_change: shell({
    title: 'Confirm your new NovaHost email',
    preheader: 'Confirm the new email address on your NovaHost account.',
    rows: [
      eyebrow('Email change'),
      heading(
        'Confirm your new address',
        'You asked to move your NovaHost account to a new email address. Confirm it below and sign-ins, licence keys and receipts all follow it.'
      ),
      factPair('Current', '{{ .Email }}', 'New', '{{ .NewEmail }}'),
      button('Confirm the change'),
      codeBlock('Or enter this code', '{{ .Token }}', { tight: true }),
      fallbackLink,
      footer([
        'Until you confirm, your account stays on the current address and nothing changes.',
        'If you did not request this, do not confirm &mdash; change your password instead, because someone with access to your session tried to move your account.',
      ]),
    ],
  }),

  reauthentication: shell({
    title: 'Your NovaHost verification code',
    preheader: 'Your NovaHost verification code. It expires in one hour.',
    rows: [
      eyebrow('Verification'),
      heading('Confirm it&rsquo;s you', 'Enter this code in NovaHost to confirm the change you just started.'),
      codeBlock('Verification code', '{{ .Token }}', { size: 30 }),
      footer([
        'This code expires in one hour and works once. NovaHost will never ask you for it over chat, phone or email.',
        'If you did not start this, ignore the code and change your password.',
      ]),
    ],
  }),

  // ── Seven security notifications: something already happened ──────────
  // Every one of these is OFF at the project level until its matching
  // `mailer_notifications_<key>_enabled` flag is turned on -- building the
  // template is necessary but not sufficient. See templates/README.md.
  // None receives a `ConfirmationURL`; none has a fallback link for the same
  // reason `secondaryButton` above is outline-only, not filled.

  password_changed_notification: shell({
    title: 'Your NovaHost password was changed',
    preheader: 'Your NovaHost password was just changed. If this was not you, act now.',
    rows: [
      eyebrow('Security notification'),
      heading(
        'Your password was changed',
        `The password for <strong style="color:${C.strong};">{{ .Email }}</strong> was just changed. If this was you, no action is needed.`
      ),
      secondaryButton('Open NovaHost'),
      footer([
        'If you did not make this change, whoever did may now control this account. Open NovaHost, choose Forgot password to reset it, and contact support right away.',
      ]),
    ],
  }),

  email_changed_notification: shell({
    title: 'Your NovaHost email address was changed',
    preheader: 'The email address on your NovaHost account was just changed.',
    rows: [
      eyebrow('Security notification'),
      heading('Your email address was changed', 'Your NovaHost account now signs in with a different address.'),
      factPair('Previous address', '{{ .OldEmail }}', 'New address', '{{ .Email }}'),
      secondaryButton('Open NovaHost'),
      footer([
        'If you did not make this change, your account may be compromised. Open NovaHost, reset your password immediately, and contact support.',
      ]),
    ],
  }),

  phone_changed_notification: shell({
    title: 'Your NovaHost phone number was changed',
    preheader: 'The phone number on your NovaHost account was just changed.',
    rows: [
      eyebrow('Security notification'),
      heading('Your phone number was changed', 'Your NovaHost account now uses a different phone number.'),
      factPair('Previous number', '{{ .OldPhone }}', 'New number', '{{ .Phone }}'),
      secondaryButton('Open NovaHost'),
      footer(['If you did not make this change, open NovaHost, reset your password, and contact support.']),
    ],
  }),

  identity_linked_notification: shell({
    title: 'A sign-in method was linked to your NovaHost account',
    preheader: 'A new sign-in method was linked to your NovaHost account.',
    rows: [
      eyebrow('Security notification'),
      heading(
        'A sign-in method was linked',
        `<strong style="color:${C.strong};">{{ .Provider }}</strong> can now be used to sign in to {{ .Email }}.`
      ),
      secondaryButton('Open NovaHost'),
      footer(['If you did not do this, remove it from Settings in NovaHost and contact support.']),
    ],
  }),

  identity_unlinked_notification: shell({
    title: 'A sign-in method was removed from your NovaHost account',
    preheader: 'A sign-in method was removed from your NovaHost account.',
    rows: [
      eyebrow('Security notification'),
      heading(
        'A sign-in method was removed',
        `<strong style="color:${C.strong};">{{ .Provider }}</strong> can no longer be used to sign in to {{ .Email }}.`
      ),
      secondaryButton('Open NovaHost'),
      footer(['If you did not do this, your account may be compromised &mdash; contact support.']),
    ],
  }),

  mfa_factor_enrolled_notification: shell({
    title: 'A verification method was added to your NovaHost account',
    preheader: 'A new verification method was added to your NovaHost account.',
    rows: [
      eyebrow('Security notification'),
      heading(
        'A verification method was added',
        `A new <strong style="color:${C.strong};">{{ .FactorType }}</strong> verification method was added to your account.`
      ),
      secondaryButton('Open NovaHost'),
      footer(['If you did not add this, remove it from Settings in NovaHost and contact support immediately.']),
    ],
  }),

  mfa_factor_unenrolled_notification: shell({
    title: 'A verification method was removed from your NovaHost account',
    preheader: 'A verification method was removed from your NovaHost account.',
    rows: [
      eyebrow('Security notification'),
      heading(
        'A verification method was removed',
        `A <strong style="color:${C.strong};">{{ .FactorType }}</strong> verification method was removed from your account.`
      ),
      secondaryButton('Open NovaHost'),
      footer(['If you did not remove this, your account may be less protected than you expect &mdash; contact support.']),
    ],
  }),
};

for (const [name, html] of Object.entries(templates)) {
  writeFileSync(new URL(`${name}.html`, import.meta.url), html, 'utf8');
  console.log(`wrote ${name}.html  (${html.length} bytes)`);
}
