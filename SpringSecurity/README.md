# Spring Security — Step-by-Step Course

Learn Spring Security from zero to production-grade, one lesson at a time.
Each lesson is a package; each lesson's config is activated by a Spring profile,
so exactly one lesson's security setup runs at a time.

## How to run a lesson

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=lesson01
```

IntelliJ: edit Run Configuration → *Active profiles* → `lesson01`.

## Shared playground endpoints (all lessons)

| Endpoint | Intended access |
|---|---|
| `GET /public/hello` | everyone |
| `GET /private/hello` | any logged-in user |
| `GET /admin/hello` | ADMIN role only |

Whether the intention is enforced depends on the lesson — that difference is the course.

## Curriculum

| # | Package | Status | Teaches |
|---|---|---|---|
| 1 | `lesson01_defaults` | ✅ | Zero-config behavior, the security filter chain, secure-by-default |
| 2 | `lesson02_basicauth` | ✅ | `SecurityFilterChain` DSL, in-memory users, `UserDetailsService` |
| 3 | `lesson03_authorization` | ✅ | URL rules, roles vs authorities, role hierarchy |
| 4 | `lesson04_passwords` | ✅ | BCrypt, `DelegatingPasswordEncoder`, salting, storage rules |
| 5 | `lesson05_database_users` | ✅ | JPA-backed `UserDetailsService`, registration endpoint, H2 console |
| 6 | `lesson06_method_security` | ✅ | `@PreAuthorize`/`@PostAuthorize`, SpEL, defense in depth |
| 7 | `lesson07_csrf_cors` | ✅ | CSRF attack + token defense, CORS allow-lists, preflight |
| 8 | `lesson08_custom_filters` | ✅ | `OncePerRequestFilter`, API-key auth, chain ordering |
| 9 | `lesson09_sessions` | ✅ | Session fixation/hijacking, cookie attributes, concurrent control |
| 10 | `lesson10_jwt` | ✅ | Issuing + validating JWTs, multiple filter chains, stateless APIs |
| 11 | `lesson11_oauth2` | ✅ | OAuth2/OIDC concepts, authorization-code flow, login with Google |
| 12 | `lesson12_oauth2_server` | ✅ | Run your own authorization server: client-credentials, scopes, RS256/JWKS, refresh tokens — all local, no Google needed |
| 13 | `lesson13_production` | ✅ | Security headers, TLS, secrets, audit logging, hardening checklist |
| 14 | `lesson14_testing` (in `src/test`) | ✅ | `@WithMockUser`, MockMvc, `csrf()`/`jwt()` post-processors, deny-tests |

Lessons 5, 8, 10, 12 span multiple files — start with the `LessonNN_*Config` class; its javadoc says what to read next.
Lesson 14 lives under [src/test/java](src/test/java/com/learn/security/lesson14_testing/) — run with `mvn test`; the tests re-verify lessons 3, 6, 7 and 10.

## Interview preparation

[INTERVIEW.md](INTERVIEW.md) — questions, answers, and production pitfalls, organized by lesson. Includes topics deliberately outside the code (PKCE, MFA, ACLs, WebFlux, Spring Session, mTLS).

## Study method per lesson

1. Read the lesson class top-to-bottom (the javadoc is the textbook).
2. Run the app with the lesson profile.
3. Do every **TRY IT** step with a browser and curl.
4. Read the **KEY TAKEAWAYS**.
