package com.ratelimiter.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "rate-limiter.admin-key=test-secret")
class AdminKeyAuthFilterTest {

    @Autowired MockMvc mockMvc;

    private static final String BASE = "/api/v1/admin/rules";
    private static final String HEADER = "X-Admin-Key";
    private static final String CORRECT_KEY = "test-secret";
    private static final String WRONG_KEY   = "wrong-key";

    @Test
    void correctKey_allows() throws Exception {
        mockMvc.perform(get(BASE).header(HEADER, CORRECT_KEY))
            .andExpect(status().isOk());
    }

    @Test
    void wrongKey_returns401() throws Exception {
        mockMvc.perform(get(BASE).header(HEADER, WRONG_KEY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void missingHeader_returns401() throws Exception {
        mockMvc.perform(get(BASE))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void nonAdminPath_notFiltered() throws Exception {
        // /actuator/health is not an admin endpoint — filter must not apply
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void adminSubPath_alsoRequiresKey() throws Exception {
        mockMvc.perform(get(BASE + "/rule-123"))
            .andExpect(status().isUnauthorized());
    }
}
