# FinTrack — Progress Log

Status snapshot for resuming work. See `docs/architecture.md` for design, and the plan file
(`i-want-to-build-tingly-fountain.md`) for full original scope.

## Phase status

- **Phase 1 (backend)** — done.
- **Phase 2 (React SPA + PWA)** — done, plus tool-calling surface (`ExpenseAssistantTools`, 11
  audited tools, no LLM-driven confirm).
- **Phase 3 (AWS RDS + EC2 deploy)** — not started.

## Local dev environment

- No local Postgres/Docker on this machine -> using **Neon** (free cloud Postgres) for local dev.
- `DB_URL` must be `jdbc:postgresql://<host>.neon.tech/<db>?sslmode=require` (Neon's raw
  connection string is `postgresql://user:pass@host/db` — needs the `jdbc:` prefix, and
  user/password split into `DB_USERNAME`/`DB_PASSWORD` env vars, not embedded in the URL).
- PowerShell env vars: `$env:DB_URL="..."` (not bash `export`).

## Bugs found + fixed this session

1. `Expense.setExpenseDate` was `final` — Hibernate 7 can't build a lazy proxy for an entity
   with a final setter. Removed `final`.
2. **Missing `spring-boot-flyway` dependency.** Boot 4.1 split `FlywayAutoConfiguration` out of
   the main autoconfigure jar into its own module (`org.springframework.boot:spring-boot-flyway`).
   `flyway-core`/`flyway-database-postgresql` alone give you the engine but nothing wires it into
   the context. This was a real gap in earlier Phase 1 work, masked by an incidental test-scope
   transitive dependency (tests passed, real runs never triggered Flyway). Fixed in `pom.xml`.
3. **Fresh-Neon-DB bootstrap race.** `spring.session.jdbc.initialize-schema: always` and the
   Spring AI JDBC chat-memory starter's own auto-init both create their tables independently of
   Flyway. On a brand-new empty schema they raced ahead of Flyway's first run, leaving `public`
   non-empty with no `flyway_schema_history` table -> Flyway refused to proceed. One-time fix:
   dropped and recreated the `public` schema on Neon, then let Flyway create everything cleanly
   on the next boot. (Not a recurring issue once `flyway_schema_history` exists.)
4. **`@Lob` on plain-text `String` columns.** `CategorizationRule.instructionText` and
   `ExpenseBatch.rawUserText` were annotated `@Lob`, so Hibernate expected a CLOB (Postgres
   `oid`) column, but the Flyway migrations define both as plain `TEXT`. Schema validation
   failed with "wrong column type ... found text, expecting oid". Removed `@Lob` from both
   fields (short rule/user text, no reason to be a large object).
5. Windows `npm ci` `EBUSY` on `frontend/node_modules` (frontend-maven-plugin, runs on every
   Maven invocation) — Windows file-lock, not a code bug. Workaround: close any stray
   `spring-boot:run`, manually `Remove-Item -Recurse -Force frontend\node_modules`, retry; or
   exclude the project folder from Defender real-time scanning if it recurs.

**Result:** app now boots cleanly against Neon — Flyway applies V1–V3, Hibernate `validate`
passes.

## UX bug found + fixed (frontend, unverified by user yet)

Symptom: logging an expense conversationally in **Chat** mode (LLM calls the `logTextExpense`
tool) created a `PENDING_CONFIRMATION` draft, but the confirmation UI never appeared until
switching tabs (which remounts `ChatPage` and re-triggers its mount-only `api.drafts()` fetch).

Fixes in `frontend/src/pages/ChatPage.tsx` and `frontend/src/components/ExpenseConfirmationModal.tsx`:
- Re-fetch `api.drafts()` after every chat turn completes (in `sendChat`'s `finally`), not just
  on mount — surfaces tool-created drafts immediately.
- Changed the confirmation UI from a full-screen blocking overlay (`fixed inset-0`) to an inline
  card rendered within the chat message thread, so confirming a logged expense feels like part
  of the conversation rather than a popup interruption. Auto-scroll now also fires when
  `pendingItems` changes.

**Not yet done:** user has not rebuilt/retested this fix. First thing to check tomorrow.

## Next steps (in order)

1. Rebuild frontend (`npm run build` via Maven) + restart app; confirm chat-mode expense logging
   now shows the inline confirmation card without switching tabs, and that confirming it via
   that card actually persists (`status -> CONFIRMED`).
2. Resume the plan's verification checklist end-to-end against Neon: text + image expense
   flows, Claude-fail -> Gemini fallback, `#newCategorizationRule` (rule persists, same-message
   expense still uses the pre-existing rule set), every dashboard filter, "reapply rules to
   current month" (prior-month expense untouched), restart-survives-draft, restart-survives-session.
3. Decide whether to make `frontend-maven-plugin`'s `npm-ci` execution less aggressive on
   Windows (e.g. skip when `node_modules`/lockfile unchanged) — flagged during the EBUSY issue,
   not yet acted on.
4. Phase 3: AWS RDS (Postgres, private SG) + EC2 (systemd service, plain HTTP for now, TLS
   parked) — not started.
