package com.learn.security.lesson14_testing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * =====================================================================
 *  LESSON 14 (part d) — TESTING A JWT RESOURCE SERVER
 * =====================================================================
 *
 * TWO LEVELS, USE BOTH:
 *
 * 1. FAKE TOKEN — jwt() post-processor. Injects an already-"validated"
 *    Jwt straight past the decoder; you set the final authorities.
 *    Fast, no signing key involved. GOTCHA (worth an interview answer):
 *    jwt() bypasses BOTH the decoder AND your custom
 *    JwtAuthenticationConverter — .authorities() below states the end
 *    result directly. So fake-token tests pin the URL RULES only.
 *
 * 2. REAL ROUND TRIP — because of that gotcha, keep at least one test
 *    that goes the whole way: basic-auth to /token (real encoder signs),
 *    then send the returned Bearer token (real decoder verifies, real
 *    converter maps the roles claim). If someone renames the "roles"
 *    claim on one side only, ONLY this style of test catches it.
 * =====================================================================
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("lesson10")
class Lesson14d_JwtResourceServerTests {

    @Autowired
    MockMvc mvc;

    // ---------- level 1: fake tokens (fast, decoder/converter bypassed) ----------

    @Test
    void noToken_is401() throws Exception {
        mvc.perform(get("/private/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fakeJwt_withUserAuthority_reachesPrivate() throws Exception {
        mvc.perform(get("/private/hello")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void fakeJwt_withUserAuthority_cannotReachAdmin() throws Exception {
        mvc.perform(get("/admin/hello")
                        .with(jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void fakeJwt_withNoAuthorities_isAuthenticatedButForbidden() throws Exception {
        mvc.perform(get("/private/hello").with(jwt()))
                .andExpect(status().isForbidden());
    }

    // ---------- level 2: real token round trip (encoder -> decoder -> converter) ----------

    @Test
    void realToken_fullRoundTrip_pinsTheRolesClaimContract() throws Exception {
        String token = mvc.perform(post("/token").with(httpBasic("alice", "alice-pass")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/private/hello")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/admin/hello")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());   // alice's token carries only USER
    }

    @Test
    void tamperedToken_is401() throws Exception {
        String token = mvc.perform(post("/token").with(httpBasic("alice", "alice-pass")))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/private/hello")
                        .header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }
}
