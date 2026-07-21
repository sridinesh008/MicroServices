package com.learn.security.lesson14_testing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * =====================================================================
 *  LESSON 14 (part c) — TESTING CSRF PROTECTION
 * =====================================================================
 *
 * MockMvc requests carry no CSRF token, so state-changing requests
 * against a CSRF-protected app 403 in tests exactly like curl did in
 * Lesson 7. The csrf() post-processor attaches a valid test token.
 *
 * THE BUG THIS PAIR CATCHES: a developer annoyed by 403s in tests
 * "fixes" them by disabling CSRF in the APPLICATION. The first test
 * below (403 WITHOUT token) turns that silent security downgrade into
 * a red build. Always keep the negative test.
 * =====================================================================
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("lesson07")
class Lesson14c_CsrfTests {

    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void stateChangingPost_withoutCsrfToken_is403() throws Exception {
        mvc.perform(post("/transfer"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void stateChangingPost_withCsrfToken_succeeds() throws Exception {
        mvc.perform(post("/transfer").with(csrf()))
                .andExpect(status().isOk());
    }
}
