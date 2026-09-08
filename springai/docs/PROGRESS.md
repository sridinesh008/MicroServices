# FinTrack — Progress Log

Status snapshot for resuming work. See `docs/architecture.md` for design, and the plan file
(`i-want-to-build-tingly-fountain.md`) for full original scope.

## Phase status

- **Phase 1 (backend)** — done.
- **Phase 2 (React SPA + PWA)** — done. Original single-design UI has been fully replaced by
  two live, toggleable redesigns (see "UI redesign" below).
- **Phase 3 (AWS RDS + EC2 deploy)** — not started.

## Local dev environment

- No local Postgres/Docker on this machine -> using **Neon** (free cloud Postgres) for local dev.
- `DB_URL` must be `jdbc:postgresql://<host>.neon.tech/<db>?sslmode=require` (Neon's raw
  connection string is `postgresql://user:pass@host/db` — needs the `jdbc:` prefix, and
  user/password split into `DB_USERNAME`/`DB_PASSWORD` env vars, not embedded in the URL).
- PowerShell env vars: `$env:DB_URL="..."` (not bash `export`).
- Backend test/compile without triggering the slow/EBUSY-prone `frontend-maven-plugin` npm
  phase: invoke the compiler/surefire/resources plugins directly, e.g.
  `mvn -o org.apache.maven.plugins:maven-compiler-plugin:compile
  org.apache.maven.plugins:maven-compiler-plugin:testCompile` then
  `org.apache.maven.plugins:maven-surefire-plugin:test`. To ship a frontend change to the
  running (already-started) backend without a full `mvn package`: `npm run build` in
  `frontend/`, then `mvn -o org.apache.maven.plugins:maven-resources-plugin:copy-resources@copy-frontend-dist`
  to land `frontend/dist` into `target/classes/static` — devtools picks up the change and
  restarts, but the browser's PWA service worker may still serve a cached shell (hard refresh /
  unregister the service worker if the old UI still shows).
- `GenaiApplicationTests` needs Docker (Testcontainers) — fails on this machine (no Docker),
  unrelated to code changes. Exclude with `-Dtest='!GenaiApplicationTests'` when running the
  full suite here.

## Perf fixes (this session)

`ExpenseIngestionService.ingest()` and `ExpenseRecategorizationService
.reapplyRulesToCurrentMonth()` both used to run their LLM categorization call(s) inside a single
`@Transactional` method, pinning a DB connection for the whole call — `reapplyRulesToCurrentMonth`
was worse: one sequential LLM call per confirmed expense in the month, all inside one open
transaction. Fixed: both now split into short DB-only transactions (via `TransactionTemplate`)
around LLM calls that run with no transaction open; `reapplyRulesToCurrentMonth` additionally
runs its per-expense LLM calls in parallel (bounded to 8). See `docs/architecture.md` for detail.

Also swapped the categorization provider order: **Gemini is now primary, Claude the fallback**
(previously the reverse) in `ExpenseCategorizationService`.

## UI redesign (this session)

User rejected the original single-design UI. Landed on two structurally distinct redesigns —
**Rail Bento** and **Command Feed** — both fully wired to the real backend (not mockups), toggled
live via a floating pill (persisted to `localStorage`). Old pages/components
(`DashboardPage`, `ChatPage`, `components/dashboard/*`, `ChatComposer`,
`ExpenseConfirmationModal`) were deleted; `react-router-dom` dropped (neither skin uses routes).
Shared business logic lives in `frontend/src/design/` (hooks), consumed identically by both
skins in `frontend/src/layouts/{rail-bento,command-feed}/`. Full detail in `docs/architecture.md`.

New backend capability added to support this: `POST /api/expenses/manual`
(`SourceMode.MANUAL`) — direct manual entry, no LLM round trip, written straight to `CONFIRMED`
(used by each skin's "quick log" affordance, where the user types the exact category themselves).

Also added: a global loading indicator (instrumented once in `api/client.ts`, shows for any
in-flight backend request across both skins — see `docs/architecture.md`), and a system-prompt
guardrail scoping the chat assistant to FinTrack expense-tracking topics only.

**Not yet done:**
- Full manual walkthrough of both skins against the live app hasn't been completed by the user
  yet (was mid-verification when this session's other work — guardrail, loading state — got
  added on top). Re-verify end to end before considering the redesign "done": toggle
  persistence, chat send/stream/confirm-card/confirm-persists under both skins, dashboard/feed
  filters+pagination+aggregates, reapply-rules, rules add/deactivate, Rail Bento FAB Manual +
  Photo tabs, Command Feed's photo-attach icon, logout from both skins, off-topic chat refusal.
- `npm install` hasn't been re-run after removing `react-router-dom` from `package.json`
  (avoiding a known Windows `EBUSY` risk on `node_modules` from this project) — `package-lock.json`
  still lists it as installed. Harmless (nothing imports it), just lockfile drift; clean up
  whenever convenient (`npm install` once, or `npm prune`).
- Command Feed's feed has no pagination (matches its mockup — flat feed, first page/50 items
  only). Rail Bento's dashboard keeps full pagination.

## Next steps (in order)

1. User completes the manual verification checklist above against the running app.
2. Decide whether to make `frontend-maven-plugin`'s `npm-ci` execution less aggressive on
   Windows (e.g. skip when `node_modules`/lockfile unchanged) — flagged earlier, not yet acted on.
3. Phase 3: AWS RDS (Postgres, private SG) + EC2 (systemd service, plain HTTP for now, TLS
   parked) — not started.
