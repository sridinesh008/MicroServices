# Spring Security — Interview Questions & Pitfalls

Companion to the course. Each section maps to a lesson; read the lesson first, then drill here.
**⚠ = pitfall that ships to production regularly.**

---

## Architecture & Filter Chain (Lessons 1–2)

**Q: How does Spring Security actually intercept requests?**
One servlet filter (`springSecurityFilterChain`, a `FilterChainProxy`) is registered with the container. It delegates to one or more `SecurityFilterChain` beans; the first chain whose matcher fits the request wins, and its ~16 filters run in fixed order — all *before* `DispatcherServlet`. Controllers never see rejected requests.

**Q: Name filters an interviewer expects you to know.**
`SecurityContextHolderFilter` (restores who-you-are from the session), `CsrfFilter`, `UsernamePasswordAuthenticationFilter` (form login), `BasicAuthenticationFilter`, `AnonymousAuthenticationFilter`, `ExceptionTranslationFilter` (exceptions → 401/403), `AuthorizationFilter` (the gate, last).

**Q: What happens when you define your own `SecurityFilterChain` bean?**
Boot's auto-configured default backs off entirely. Same for `UserDetailsService` (kills the generated user) and `PasswordEncoder`.

**Q: What is `SecurityContextHolder`?**
Thread-local storage for the current `Authentication`. Set by auth filters, read by everything (authorization, `@PreAuthorize`, `Principal` injection), cleared at request end.

- ⚠ **Async/thread pools lose the context.** `SecurityContextHolder` is thread-local; spawn a thread and it's empty. Use `DelegatingSecurityContextExecutor` or propagate explicitly.
- ⚠ **`@Component` on a security filter double-registers it** — Boot adds every `Filter` bean to the container, so it runs once inside the chain and once outside. Register on `HttpSecurity` only (Lesson 8).

---

## Authentication vs Authorization, 401 vs 403 (Lesson 3)

**Q: Difference?**
Authentication = who are you (credentials). Authorization = what may you do (rules). 401 = unauthenticated ("who are you?"), 403 = authenticated but not allowed ("you, specifically, no").

**Q: `hasRole` vs `hasAuthority`?**
Same mechanism — a role is just an authority with the `ROLE_` prefix. `hasRole("ADMIN")` checks authority `ROLE_ADMIN`. Convention: roles = coarse job titles for URL rules; plain authorities = fine-grained permissions (`invoice:read`) for method rules.

- ⚠ **`hasRole("ROLE_ADMIN")`** checks `ROLE_ROLE_ADMIN`. Never write the prefix inside `hasRole`.
- ⚠ **Rule order.** `requestMatchers` are evaluated top-down, first match wins. A broad rule above a narrow one makes the narrow one dead code.
- ⚠ **Missing catch-all.** Without `anyRequest().denyAll()` (or `.authenticated()`), next sprint's new endpoint ships with whatever default applies. Deny by default; open explicitly.
- ⚠ **The `/error` dispatch trap (Spring Security 6).** `sendError(403)` re-dispatches to Boot's `/error`; if your rules block `/error`, the real status mutates into confusing 401/500. Permit `/error`. (This course hit it live in Lessons 7–8.)

---

## Passwords (Lessons 4–5)

**Q: Why BCrypt over SHA-256?**
SHA is deliberately fast → billions of GPU guesses/sec. BCrypt/Argon2 are deliberately slow (tunable cost factor) and auto-salted (random salt per password, stored inside the hash string). Same password → different hash each time; rainbow tables dead.

**Q: What does `{bcrypt}` in a stored password mean?**
`DelegatingPasswordEncoder` prefix — tells Spring which algorithm to verify with. Enables migrating algorithms without resetting every password: old rows keep their prefix, new hashes get the current default, re-hash on next successful login.

**Q: How do you verify a password if you can't decode the hash?**
You never decode. `matches(raw, encoded)` re-hashes the submitted password with the salt extracted from the stored hash and compares. One-way, by design.

- ⚠ **User enumeration.** Login/registration/password-reset must return identical errors and (ideally) timing whether the username exists or not. "Wrong password" vs "no such user" hands attackers a valid username list.
- ⚠ **Logging credentials.** One `log.debug(request)` on a login endpoint = plaintext passwords in your log pipeline, backups, and vendor dashboards. Audit log events, never payloads (Lesson 13).
- ⚠ **Duplicate-check races.** `existsByUsername` then `save` is not atomic — two concurrent registrations pass the check. Keep the DB unique constraint as the last line (Lesson 5).

---

## Method Security (Lesson 6)

**Q: Why method security when URL rules exist?**
URL rules can't see data ownership ("only the owner reads this document") and don't cover non-HTTP entry points (schedulers, listeners, GraphQL). Defense in depth: coarse at the edge, precise at the service.

**Q: `@PreAuthorize` vs `@PostAuthorize`?**
Pre = gate before the call (can reference parameters: `#owner == authentication.name`). Post = gate after, inspecting `returnObject` — for when you must load the object to know who owns it.

- ⚠ **Self-invocation bypass.** `this.securedMethod()` skips the proxy — the annotation silently does nothing. Same trap as `@Transactional`. #1 method-security bug in production. Fix: separate bean, or check earlier.
- ⚠ **Forgetting `@EnableMethodSecurity`.** Annotations without it are dead text — everything returns 200 and nothing fails. A "kept-out" test (Lesson 14b) is the only reliable alarm.
- ⚠ **`@PostFilter` on large collections** filters in Java after fetching everything. Filter in the query instead.

---

## CSRF & CORS (Lesson 7)

**Q: Explain CSRF in one breath.**
Browser auto-attaches cookies by destination; evil.com can therefore trigger authenticated POSTs to yourbank.com from the victim's browser. Defense: a token that evil.com can't read (same-origin policy) must accompany state-changing requests; auto-sent cookie + deliberately-attached token must both be present.

**Q: When is disabling CSRF correct?**
When auth doesn't ride on cookies — pure bearer-token APIs (JWT in a header). Browsers never auto-attach headers, so there's nothing to forge. Cookie-session apps: never disable.

**Q: CSRF vs CORS?**
CSRF = browser sends *too much* automatically (your cookies) → protect with token. CORS = browser shares *too little* by default (blocks cross-origin reads) → server opts in specific origins. Unrelated mechanisms, constantly confused.

- ⚠ **GET endpoints that mutate state.** CSRF protection exempts GET. A `GET /delete?id=5` is forgeable by an `<img src>` tag.
- ⚠ **`allowedOrigins("*")` with credentials** — spec forbids it (Spring throws), but the common "fix" `allowedOriginPatterns("*")` + credentials effectively disables CORS protection. List exact origins.
- ⚠ **CORS is not access control.** curl ignores it entirely; it only governs what browser JS can read. Authorization still comes from your rules.

---

## Sessions & Stateless (Lesson 9)

**Q: Session fixation — attack and defense?**
Attacker plants a known session id in the victim's browser; victim logs in; if the server keeps that id, attacker's copy is now authenticated. Defense: swap the id at login — Spring's default (`changeSessionId`) since forever. Know it exists; you get asked "what does Spring protect you from by default".

**Q: The cookie hardening trio?**
`HttpOnly` (JS can't read → XSS can't exfiltrate), `Secure` (HTTPS only → nothing to sniff), `SameSite=Lax` (not attached to most cross-site requests → CSRF backup).

**Q: `STATELESS` policy — what does it buy and cost?**
No session → fixation, hijacking, CSRF all structurally impossible; any instance serves any request. Cost: every request must carry credentials, and logout/revocation becomes your problem.

- ⚠ **Sessions behind a load balancer** without sticky sessions or shared storage (Spring Session + Redis) = random logouts. Interviewers love this one.

---

## JWT (Lesson 10)

**Q: Is a JWT encrypted?**
No — base64-encoded, readable by anyone (paste into jwt.io). The signature gives *integrity* (tamper-evident), not secrecy. Never put sensitive data in claims.

**Q: How do you revoke a JWT?**
You don't — that's the trade-off. Valid until `exp`, period. Mitigations: short expiry (5–15 min) + refresh tokens (server-side state, revocable — Lesson 12), or a denylist (which quietly reintroduces the state you were avoiding). Saying "you can't, and here's how the industry copes" is the *correct* interview answer.

**Q: HS256 vs RS256?**
HS256 = one shared secret signs and verifies → any verifier can mint. Fine inside one service. RS256 = private key signs (auth server only), public key verifies (everyone, via JWKS) → verifiers can't forge. Required across trust boundaries.

- ⚠ **`alg: none` / algorithm-confusion attacks.** Never let the token's header choose the verification algorithm — pin it server-side (the course pins HS256 in the decoder). Historic RS256→HS256 confusion: attacker signs with the *public* key as HMAC secret.
- ⚠ **JWT in `localStorage`** — readable by any XSS. Cookie (`HttpOnly` + `SameSite`) or in-memory storage; each choice trades XSS vs CSRF exposure — know the trade-off, don't just pick one.
- ⚠ **Long-lived access tokens.** 24h token = 24h of free access after theft. Short access + refresh pair.
- ⚠ **No clock skew allowance** between issuer and validator → intermittent "expired" errors. Decoders accept a small leeway (Spring default 60 s).

---

## OAuth2 / OIDC (Lessons 11–12)

**Q: OAuth2 vs OIDC?**
OAuth2 = authorization framework (delegated *permission* — "app X may read resource Y"). OIDC = identity layer on top adding the ID token (a JWT about *who the user is*). "Login with Google" is OIDC.

**Q: Walk through the authorization code flow.**
Redirect to provider with `client_id` + `redirect_uri` + `state` → user authenticates *at the provider* → provider redirects back with one-time `code` → **server-side** exchange of code + `client_secret` for tokens → validate ID token → session. Code travels through the browser because it's worthless without the secret; tokens travel the back channel only.

**Q: What is PKCE and when is it required?** *(gap topic — not in the course code; learn it here)*
Proof Key for Code Exchange. Public clients (SPAs, mobile) can't keep a `client_secret`, so instead: client invents a random `code_verifier`, sends its hash (`code_challenge`) with the authorize request; at exchange time sends the verifier; server re-hashes and compares. A stolen code alone is useless. OAuth 2.1 makes PKCE mandatory for *all* authorization-code clients. The old **implicit flow** (tokens directly in the redirect URL) is deprecated — answer "implicit" only when asked what *not* to use.

**Q: Scopes vs roles?**
Roles bound the *user*; scopes bound the *token* — what this client was granted, possibly a slice of the user's power. Resource server maps `scope` → `SCOPE_x` authorities.

**Q: client_credentials flow?**
Machine-to-machine, no human: the client authenticates as itself and gets a scoped, expiring token. Replaces ad-hoc API keys in microservice fleets (Lesson 12 vs Lesson 8 — compare, interviewers ask for exactly that contrast).

- ⚠ **Missing/unverified `state`** on the redirect = CSRF on the login flow itself (attacker logs victim into attacker's account).
- ⚠ **Wildcard or over-broad `redirect_uri`.** Exact-match registration only; an open redirect on your domain + loose matching = stolen codes.
- ⚠ **Validating only the signature of an ID token.** Must also check `iss`, `aud` (a token minted for another app must not log into yours), `exp`, and `nonce` when present.

---

## Testing (Lesson 14)

**Q: How do you test authorization rules?**
`MockMvc` (runs the real filter chain, no port) + `@WithMockUser` for fabricated identities (skips authentication — perfect isolation of authorization), `httpBasic()` for the real credential path, `csrf()` for write requests, `jwt()` for resource servers. Every rule gets both an allow test and a **deny test** — the deny test is the one that catches regressions.

- ⚠ **`jwt()` post-processor bypasses your decoder AND your custom claims converter.** Keep one real-token round-trip test (mint → send → verify) or a converter rename ships broken (Lesson 14d).
- ⚠ **Only positive tests.** A config that accidentally `permitAll`s everything passes every positive test you have.

---

## Rapid-fire classics

| Question | One-line answer |
|---|---|
| `Authentication` object parts? | principal (who), credentials (wiped after auth), authorities, authenticated flag |
| `AuthenticationManager` vs `AuthenticationProvider`? | Manager delegates to a list of Providers; first that supports the token type wins |
| `DaoAuthenticationProvider`? | The provider that calls `UserDetailsService` + `PasswordEncoder` — powers form/basic login |
| Where does `Principal` in a controller come from? | Resolved from `SecurityContextHolder`, populated by the filter chain |
| `@AuthenticationPrincipal`? | Injects the principal object directly (`UserDetails`, `OidcUser`, `Jwt`) |
| Custom filter vs custom `AuthenticationProvider`? | Filter = new *transport* (header, cookie); Provider = new *verification* against existing transport |
| Multiple `SecurityFilterChain` beans? | `securityMatcher` + `@Order`; first matching chain handles the request exclusively (L10/L12) |
| Why is the anonymous user a thing? | So authorization always has an `Authentication` to evaluate — no null checks, `permitAll` still runs rules |
| Sessions vs JWT — pick one? | Cookie sessions for server-rendered same-origin apps (revocable, simple); JWT for APIs/microservices (stateless, scalable). Say "it depends", then say on what. |
| BCrypt cost factor today? | 10–12; raise as hardware improves — verification should cost ~100 ms |

---

## Beyond this course (know these exist; one-liner each)

- **MFA/TOTP** — second factor after password; Spring has no built-in, integrate (e.g. time-based codes, WebAuthn/passkeys).
- **Remember-me** — persistent login cookie; Spring supports hashed & persistent-token flavors; a stolen remember-me cookie = long-lived session, treat accordingly.
- **ACLs (`spring-security-acl`)** — per-object permission tables when `@PreAuthorize` expressions stop scaling.
- **WebFlux security** — same concepts, reactive API (`SecurityWebFilterChain`, `ReactiveUserDetailsService`); context rides the subscription, not a thread-local.
- **Spring Session** — externalizes sessions to Redis/JDBC; fixes the load-balancer problem without going stateless.
- **mTLS** — both sides present certificates; service-to-service auth in zero-trust networks, often via service mesh.
- **Vault / KMS** — secret storage with rotation and audit; the "where do secrets live" answer past env vars.
