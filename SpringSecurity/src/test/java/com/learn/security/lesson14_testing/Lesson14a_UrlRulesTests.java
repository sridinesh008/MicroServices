package com.learn.security.lesson14_testing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * =====================================================================
 *  LESSON 14 (part a) — TESTING SECURITY: URL RULES
 * =====================================================================
 *
 * Run:  mvn test
 *
 * WHY THIS LESSON EXISTS
 * ----------------------
 * Security config is CODE, and untested code regresses. The nightmare
 * scenario is silent: someone reorders requestMatchers, or renames
 * /admin to /management and forgets the rule — every endpoint still
 * WORKS (200s all around), nothing crashes, but authorization is gone.
 * Functional tests won't catch it; only tests that assert 401/403 will.
 * Industry rule: EVERY authorization rule gets a test asserting both
 * sides — who gets in AND who is kept out. The "kept out" half is the
 * one that saves you.
 *
 * THE TOOLBOX (from spring-security-test, already in the pom)
 * -----------------------------------------------------------
 * MockMvc         drives the FULL servlet stack — including the entire
 *                 security filter chain — without a real HTTP port.
 *                 Same filters, same order, same 401/403 behavior you
 *                 curl'ed in Lessons 1-13.
 *
 * @WithMockUser   fabricates an already-authenticated SecurityContext:
 *                 "pretend someone with these roles is logged in".
 *                 SKIPS authentication (no password check!) — perfect
 *                 for testing AUTHORIZATION in isolation.
 *
 * httpBasic(...)  request post-processor that sends real credentials
 *                 through the REAL authentication path: UserDetailsService
 *                 lookup + PasswordEncoder check. Use a few of these to
 *                 cover authentication itself; use @WithMockUser for the
 *                 many authorization cases (faster, no user setup).
 *
 * anonymous()     explicit "nobody logged in" — for the deny cases.
 *
 * THE PATTERN: same profile system as the lessons. @ActiveProfiles
 * boots the exact config under test — these tests pin Lesson 3's rules
 * (including the role hierarchy) so they can never silently regress.
 * =====================================================================
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("lesson03")
class Lesson14a_UrlRulesTests {

    @Autowired
    MockMvc mvc;

    // ---------- the "who gets in" half ----------

    @Test
    void publicEndpoint_isOpenToAnonymous() throws Exception {
        mvc.perform(get("/public/hello").with(anonymous()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "anyone", roles = "USER")
    void privateEndpoint_allowsUserRole() throws Exception {
        mvc.perform(get("/private/hello"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void adminEndpoint_allowsAdminRole() throws Exception {
        mvc.perform(get("/admin/hello"))
                .andExpect(status().isOk());
    }

    /** Pins the ROLE HIERARCHY (L3): ADMIN implies USER. If someone
     *  deletes the RoleHierarchy bean, THIS test fails — not production. */
    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void roleHierarchy_adminPassesUserRule() throws Exception {
        mvc.perform(get("/private/hello"))
                .andExpect(status().isOk());
    }

    /** Real authentication path: UserDetailsService + password check. */
    @Test
    void basicAuth_realCredentialsAuthenticate() throws Exception {
        mvc.perform(get("/private/hello").with(httpBasic("alice", "alice-pass")))
                .andExpect(status().isOk());
    }

    // ---------- the "kept out" half — the tests that matter ----------

    @Test
    void privateEndpoint_rejectsAnonymous_with401() throws Exception {
        mvc.perform(get("/private/hello").with(anonymous()))
                .andExpect(status().isUnauthorized());   // 401: who ARE you
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void adminEndpoint_rejectsUserRole_with403() throws Exception {
        mvc.perform(get("/admin/hello"))
                .andExpect(status().isForbidden());      // 403: you, specifically, no
    }

    /** Pins the denyAll catch-all: unlisted URL stays dead even for admin. */
    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void unlistedUrl_isDeniedEvenForAdmin() throws Exception {
        mvc.perform(get("/some/new/endpoint"))
                .andExpect(status().isForbidden());
    }

    @Test
    void basicAuth_wrongPassword_is401() throws Exception {
        mvc.perform(get("/private/hello").with(httpBasic("alice", "wrong")))
                .andExpect(status().isUnauthorized());
    }
}
