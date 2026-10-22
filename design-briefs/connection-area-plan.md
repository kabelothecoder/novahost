# Connection-area redesign: plan

Branch: `feat/connection-area-redesign` (off `feat/home-v4-web`)
Target: iOS Vue PWA (`MetaHost iOS Version`). Source design: Claude Design project `13b7898a`, file `NovaHost Multiple Accounts.dc.html` (Part 3), rendered by `AccountsPhone.dc.html`.

Decisions already made: whole connection area; stack, carousel and list all built behind a user setting (stack default); billing is UI-only with a mocked charge.

## 1. What the design specifies (Part 3)

One screen, "My accounts", with scenes: default, detail (card pulled out), empty (first-time), pending, failed, ready (paid, unlinked), max (5 of 5), plus sheets for upsell, PayFast, unlock animation, connect (broker picker, "step 1 of 3"), confirm switch, delink.

Rules the UI encodes:
- 1 account is included, extras are a monthly add-on, max 5.
- Only the ACTIVE account trades; the others are paused. Switching lives only here.
- Switching while the robot runs shows a confirm sheet (which account pauses, which starts).
- The active account cannot be delinked; delink an extra stops billing at period end; open trades stay at the broker.
- A slot is a separate object from an account: it can be pending, failed, or ready (paid, not linked).

## 2. What exists today (iOS)

- `src/services/brokerService.js`: `linkBroker`, `loadLinkState`, `loadAccount`, `loadQuote`, `loadPositions`, `loadHistory`, `setAutoExecute`. All are licence-scoped and take no account id.
- `src/views/TerminalView.vue`: a single-account connect form plus status badge. Writes `session.brokerConnected/brokerServer/brokerCurrency` and `accent.brokerBalance`.
- Consumers of the single-account state: HomeView, `CommandCore.vue`, ScannerView, SettingsView, PairsView (risk calculator seeds from balance and currency).
- `src/services/robotService.js` / `stores/robot.js`: a handset can hold SEVERAL LICENCES (`my-licenses`). That is a different axis from several broker accounts on one licence. Do not conflate them.

## 3. Blocking finding: the backend is one account per licence

`metacopier-connect` stores exactly one id at `licenses.metadata.metacopier_account_id` (index.ts:654, 791) and a second connect REUSES or REPLACES it. `license-status`, `broker-account`, `broker-positions`, `broker-quote`, `broker-history`, `broker-performance`, `metacopier-execute`, `auto-execute-signals` and `broker-symbols` all resolve the account from that single field.

So "multiple accounts" is not a front-end task. It needs:

| Piece | Change |
|---|---|
| Schema | New table `licence_accounts` (licence_id, metacopier_account_id, login, server, platform, broker, slot_state, is_active, is_included, created_at, delinked_at). Backfill from the existing metadata field. RLS owner-scoped like the other licence tables. |
| `metacopier-connect` | Accept an optional `slot_id`; create an additional account instead of replacing. Enforce the cap of 5. |
| Active pointer | One active row per licence. New function `set-active-account` (pauses the old MetaCopier account, starts the new one, writes the pointer). |
| Readers | `license-status`, `broker-*`, `metacopier-execute`, `auto-execute-signals` resolve the ACTIVE account from the new table, falling back to the old field. |
| List | New function `list-accounts` returning masked login, broker, server, balance, currency, slot state, billing tag. Never the MetaCopier id. |
| Delink | New function `delink-account` (extras only; refuses the active one). |
| Slots and billing | `account_slots` plus mocked purchase path (see 5). |

CLAUDE.md requires confirmation before schema changes, so this section waits for a yes. Also unresolved: the MetaCopier project is at its account cap (`needed=160|funding=75.00`), so real extra accounts cannot be created until the project is funded. Everything below is built and tested against mock data first.

## 4. Front-end plan (no backend dependency)

New:
- `src/services/accountsService.js`: `listAccounts`, `setActive`, `delink`, `startSlotPurchase`. Starts as a mock adapter behind one interface; swapping to the real functions later changes this one file.
- `src/stores/accounts.js`: accounts, slots, activeId, plus a facade so existing `session.broker*` fields are derived from the active account (consumers do not change).
- `src/views/AccountsView.vue` (`/accounts`): header chip, subtitle, switcher, billing summary row.
- `src/components/accounts/`: `AccountCard.vue`, `SlotCard.vue`, `AddCard.vue`, `StackSwitcher.vue`, `CarouselSwitcher.vue`, `ListSwitcher.vue`, `AccountDetail.vue`, `sheets/{Upsell,ConfirmSwitch,Delink}Sheet.vue`, `PayfastSheet.vue`, `UnlockCelebration.vue`, `ConnectFlow.vue`.
- `src/components/accounts/accounts.css`: scoped tokens for this area, built on the existing `--cc-*` Command Center tokens (not a fourth design system).

Changed:
- `TerminalView.vue`: the connect form moves into `ConnectFlow.vue` (broker picker, then login and server, then connecting/verifying). Terminal keeps positions and history for the active account and shows the active account chip.
- `SettingsView.vue`: add "Accounts switcher layout" (stack / carousel / list), stored like the other `data-*` display prefs.
- `HomeView.vue` / `CommandCore.vue`: an active-account chip that opens `/accounts`; balance and currency come from the facade.
- `src/router/index.js`: add `/accounts` and register it in the command-view route list.
- `brokerService.js`: `linkBroker` gains optional `slotId`; read functions gain an optional `accountId` that is ignored until the backend supports it.

Rules carried over from the repo:
- Views are `height:100dvh` + `overflow-y:auto`, never `min-height`.
- Broker password is a parameter only; never stored in a ref or storage after the call.
- Currency travels with every balance; link state stays tri-state (unknown is not disconnected).
- No hardcoded broker names in copy; the design's five brokers appear only as logo assets in the picker, driven by data.
- "Connecting" is a wait state, never a green light.

## 5. Billing (UI only)

- Price, cap and proration come from one constants module (`ACCOUNT_ADDON`), flagged `PLACEHOLDER` in code and in the sheet footer in dev builds.
- The PayFast sheet is a mock: no network call, 1.5s processing, then unlock animation.
- The design's R149 per month conflicts with the shipped once-off pricing (R599 / R349 / R150, no subscription). Resolve before any real wiring; the mock must not ship to production.

## 6. Phases

1. Tokens, `accountsService` mock, store and facade, route, `AccountsView` shell with the stack switcher and card states.
2. Carousel and list switchers, the layout setting, detail panel, billing summary.
3. Sheets: upsell, mock PayFast, unlock animation, confirm switch, delink.
4. `ConnectFlow` (replaces the Terminal form): picker, credentials, connecting/verifying, error copy reused from `brokerService`.
5. Wire Home, Terminal, Settings, Scanner and Pairs to the facade; remove the old form.
6. QA in the in-app browser at 390x844 and installed-PWA constraints; `npm run build`.
7. Backend (separate, needs sign-off): migration, functions in section 3, deploy and hash-verify (git is not live).

## 7. Open questions for you

1. OK to proceed with the schema in section 3, as a separate step after the UI?
2. When MetaCopier funding is sorted, should extra accounts really be created, or stay mocked until pricing is decided?
3. Does "pause" an account mean stopping it in MetaCopier (frees nothing) or only ignoring its signals? The design implies robot-level pause; I would start with ignoring signals.
4. The design is dark-only. The app is dark-first, so I am assuming no light variant is needed.
