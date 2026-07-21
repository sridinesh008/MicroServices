package com.learn.security.lesson01_defaults;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * =====================================================================
 *  LESSON 1 — WHAT SPRING SECURITY DOES WITH ZERO CONFIGURATION
 * =====================================================================
 *
 * Run:  mvn spring-boot:run -Dspring-boot.run.profiles=lesson01
 *
 * Notice this class defines NO security rules. That is the point.
 * Just having `spring-boot-starter-security` on the classpath changes
 * everything. Boot's auto-configuration kicks in and:
 *
 *   1. LOCKS EVERY ENDPOINT. Even /public/hello now returns a login
 *      page (in a browser) or 401 (for API clients). Secure by default:
 *      deny everything, then open up deliberately. The opposite default
 *      (open everything, lock deliberately) is how data breaches happen —
 *      someone forgets to lock the new endpoint.
 *
 *   2. CREATES ONE USER. Username: "user". Password: random UUID printed
 *      in the startup log, look for:
 *
 *        Using generated security password: 8e557245-73e2-4286-969a-ff57fe326336
 *
 *      New password every restart — this is deliberately unusable for
 *      production, it forces you to configure real users (Lesson 2).
 *
 *   3. INSTALLS A CHAIN OF ~15 SERVLET FILTERS. This is the core
 *      architecture of Spring Security:
 *
 *        Request -> F1 -> F2 -> ... -> F15 -> DispatcherServlet -> Controller
 *
 *      Each filter has ONE job. The runner bean below prints the real
 *      chain at startup. The ones worth knowing now:
 *
 *        SecurityContextHolderFilter   - loads "who is logged in" from the
 *                                        session into a thread-local
 *                                        (SecurityContextHolder), clears it after
 *        CsrfFilter                    - blocks forged POST/PUT/DELETE (Lesson 7)
 *        LogoutFilter                  - handles POST /logout
 *        UsernamePasswordAuthenticationFilter - handles the login form POST
 *        BasicAuthenticationFilter     - handles "Authorization: Basic ..." header
 *        AnonymousAuthenticationFilter - if nobody logged in, inserts an
 *                                        "anonymous" user (so code never sees null)
 *        ExceptionTranslationFilter    - converts security exceptions into
 *                                        HTTP: not logged in -> 401/redirect,
 *                                        logged in but forbidden -> 403
 *        AuthorizationFilter           - THE GATE. Last in chain. Checks the
 *                                        rules; default rule = any request
 *                                        must be authenticated
 *
 *   4. ENABLES BOTH LOGIN STYLES:
 *        - formLogin: browser gets an auto-generated /login HTML page
 *        - httpBasic: curl/Postman can send credentials in a header
 *
 * =====================================================================
 *  TRY IT (do these, really)
 * =====================================================================
 *
 *  A. Browser: http://localhost:8080/public/hello
 *     -> redirected to /login. Log in: user / <password from log>.
 *     -> now you see the response. Visit /logout to log out.
 *
 *  B. curl without credentials:
 *       curl -i http://localhost:8080/public/hello
 *     -> HTTP 401. Note: NOT a redirect. Spring detects "browser vs API
 *        client" via Accept headers and answers appropriately.
 *
 *  C. curl with credentials (fills the Authorization: Basic header):
 *       curl -i -u user:<password> http://localhost:8080/public/hello
 *     -> HTTP 200.
 *
 *  D. curl -i -u user:<password> http://localhost:8080/admin/hello
 *     -> HTTP 200 (!!). Default rule is only "authenticated" — there is
 *        no role concept yet. Fixing this is Lesson 3.
 *
 * =====================================================================
 *  KEY TAKEAWAYS
 * =====================================================================
 *  - Security lives in FILTERS, outside your controllers.
 *  - Default = deny all, one generated user, form + basic login.
 *  - "Authenticated" (who are you) is not "authorized" (what may you do).
 * =====================================================================
 */
@Configuration
@Profile("lesson01")
public class Lesson01_DefaultSecurity {

    /**
     * Prints the REAL filter chain at startup so you can see point 3 above
     * with your own eyes.
     *
     * FilterChainProxy is the single servlet filter Boot registers under the
     * name "springSecurityFilterChain". It delegates to one or more
     * SecurityFilterChain beans (we have exactly one — the auto-configured
     * default) and runs that chain's filters in order.
     */
    @Bean
    CommandLineRunner printFilterChain(FilterChainProxy filterChainProxy) {
        return args -> {
            System.out.println("\n================ LESSON 1: ACTIVE SECURITY FILTER CHAIN ================");
            int chainNo = 0;
            for (SecurityFilterChain chain : filterChainProxy.getFilterChains()) {
                chainNo++;
                System.out.println("Chain #" + chainNo + " (" + chain.getClass().getSimpleName() + "):");
                int i = 0;
                for (var filter : chain.getFilters()) {
                    System.out.printf("  %2d. %s%n", ++i, filter.getClass().getSimpleName());
                }
            }
            System.out.println("=========================================================================\n");
        };
    }
}
