package com.ratelimiter.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    // Minimal controller that throws controlled exceptions — test-only, not a Spring bean
    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        public void throwNotFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }

        @GetMapping("/test/conflict")
        public void throwConflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate rule id");
        }

        @GetMapping("/test/generic")
        public void throwGeneric() {
            throw new RuntimeException("Unexpected failure");
        }
    }

    @BeforeEach
    void setUp() {
        // standaloneSetup: no Spring context — wires only TestController + GlobalExceptionHandler
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void responseStatusExceptionReturnsCorrectStatusAndJsonShape() throws Exception {
        mockMvc.perform(get("/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.message").value("Resource not found"))
            .andExpect(jsonPath("$.path").value("/test/not-found"));
    }

    @Test
    void differentResponseStatusCodesPreservedInBody() throws Exception {
        mockMvc.perform(get("/test/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("Duplicate rule id"));
    }

    @Test
    void genericExceptionReturns500WithSafeMessage() throws Exception {
        // Must NOT leak stack trace or internal exception message to caller
        mockMvc.perform(get("/test/generic"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error").value("Internal Server Error"))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void errorResponseAlwaysIncludesPath() throws Exception {
        mockMvc.perform(get("/test/not-found"))
            .andExpect(jsonPath("$.path").value("/test/not-found"));

        mockMvc.perform(get("/test/generic"))
            .andExpect(jsonPath("$.path").value("/test/generic"));
    }
}
