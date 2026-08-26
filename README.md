# NovaHost

Mobile-first automated trading platform. Mentors create trading robots and sell
licence keys; users enter a key, connect their broker account, and the robot
executes the mentor's signals on their account.

## Repository layout

| Path | What it is |
|---|---|
| `NovaHost Android Version/` | Native Android app (Kotlin + Jetpack Compose) |
| `NovaHost Android Version/supabase/functions/` | Supabase Edge Functions (Deno) |
| `MetaHost Portal/lumin-dash/` | Mentor portal (React + Vite) — **this is what deploys to Vercel** |
| `MetaHost Portal/metahost-landing/` | Marketing landing site (Next.js) |
| `MetaHost iOS Version/` | iOS build (Vue + Capacitor) |

## How it fits together

```
Mentor portal ──broadcast-signal──► Supabase ──realtime──► Android app
                                       │                        │
                                  licences,                metacopier-execute
                                  robots, signals                │
                                                                 ▼
                                                          MetaCopier ──► broker
```

- **Robot identity** (name, avatar, colours, symbols) travels to the app through
  the licence key, joined from `expert_advisors`.
- **Authorization is by ownership** — a mentor can only broadcast to robots they
  own, so one mentor's signals never reach another's subscribers.
- **Execution** goes through MetaCopier. Broker credentials never leave the
  server; the app sends only its licence key.
- **Broker-agnostic.** The server string a user types is passed straight to
  MetaCopier. No broker is hardcoded.

## Deployment

### Mentor portal → Vercel

The Vercel project builds only the portal:

| Setting | Value |
|---|---|
| Root Directory | `MetaHost Portal/lumin-dash` |
| Framework | Vite |
| Output | `dist` |

Environment variables (Vercel → Settings → Environment Variables):

```
VITE_SUPABASE_URL
VITE_SUPABASE_PUBLISHABLE_KEY
```

> Never add `SUPABASE_SERVICE_ROLE_KEY` to Vercel. Any variable in a Vite build
> is compiled into the public bundle; that key grants full database access.

`vercel.json` rewrites all routes to `index.html` so client-side routes such as
`/update-password` resolve instead of returning 404.

### Supabase auth URLs

After deploying, set **Authentication → URL Configuration**:

- **Site URL** — the deployed portal URL
- **Redirect URLs** — add `<portal-url>/update-password`

Without this, password-reset emails point at `localhost` and cannot be opened on
a phone.

## Local development

```bash
# Mentor portal
cd "MetaHost Portal/lumin-dash" && npm install && npm run dev

# Android
cd "NovaHost Android Version" && ./gradlew assembleDebug
```

Secrets live in files git ignores — `local.properties` (Android) and `.env`
(web). Copy the `.env.example` / `.env.template` files and fill them in.
