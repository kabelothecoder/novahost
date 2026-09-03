/**
 * Renders the built templates into one scrollable page with the Go template
 * variables filled in, so the design can be eyeballed without sending real
 * mail. Writes preview.out.html (gitignored) and prints the path.
 *
 *   node build.mjs && node preview.mjs
 *
 * This is a design check, not a deliverability check -- it renders in a modern
 * browser, which is far more forgiving than Outlook. Before changing anything
 * structural, put the built file through a real client test.
 */

import { readFileSync, writeFileSync } from 'node:fs';

const SAMPLE = {
  '{{ .ConfirmationURL }}':
    'https://epulmnfbxjmaimefhofp.supabase.co/auth/v1/verify?token=pkce_9f3a7c&type=signup&redirect_to=https://novahost.co',
  '{{ .Token }}': '418902',
  '{{ .Email }}': 'thabo.m@gmail.com',
  '{{ .NewEmail }}': 'thabo@novatrader.co.za',
  '{{ .SiteURL }}': 'https://novahost.co',
};

const NAMES = ['confirmation', 'invite', 'magic_link', 'recovery', 'email_change', 'reauthentication'];

const fill = (html) =>
  Object.entries(SAMPLE).reduce((acc, [k, v]) => acc.split(k).join(v), html);

const sections = NAMES.map((name) => {
  const url = new URL(`${name}.html`, import.meta.url);
  const body = fill(readFileSync(url, 'utf8'))
    .replace(/[\s\S]*?<body[^>]*>/, '')
    .replace(/<\/body>[\s\S]*/, '');
  return `<div style="color:#6C7484;font-family:'Segoe UI',Arial,sans-serif;font-size:11px;font-weight:600;letter-spacing:0.14em;text-transform:uppercase;padding:30px 0 2px 26px;border-top:1px solid #1D2029;">${name}.html</div>${body}`;
}).join('\n');

const out = new URL('preview.out.html', import.meta.url);
writeFileSync(
  out,
  `<!DOCTYPE html><html><head><meta charset="utf-8"><title>NovaHost auth emails</title></head><body style="margin:0;background:#07070E;">${sections}</body></html>`,
  'utf8'
);
console.log(`wrote ${decodeURIComponent(out.pathname).replace(/^\//, '')}`);
