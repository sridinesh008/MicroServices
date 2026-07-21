package com.learn.security.lesson14_testing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import com.learn.security.lesson06_method_security.DocumentService;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * =====================================================================
 *  LESSON 14 (part b) — TESTING METHOD SECURITY, NO HTTP NEEDED
 * =====================================================================
 *
 * @PreAuthorize lives on the service bean, so call the SERVICE
 * DIRECTLY — @WithMockUser still populates the SecurityContext
 * (thread-local, Lesson 8), the proxy still enforces. Faster and more
 * precise than going through controllers, and it covers what MockMvc
 * never could: the scheduler/listener/GraphQL callers that also hit
 * this service.
 *
 * Denied call -> AccessDeniedException (the web layer would translate
 * it to 403 — Lesson 3's ExceptionTranslationFilter).
 *
 * Note there is no MockMvc anywhere in this class. Method security
 * tests are plain bean tests with a fabricated caller identity.
 * =====================================================================
 */
@SpringBootTest
@ActiveProfiles("lesson06")
class Lesson14b_MethodSecurityTests {

    @Autowired
    DocumentService documents;   // the proxied bean — annotations active

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void ownerCanListOwnDocuments() {
        documents.findByOwner("alice");   // no exception = allowed
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void nonOwnerIsDenied_evenWithoutHttp() {
        assertThrows(AccessDeniedException.class,
                () -> documents.findByOwner("bob"));
    }

    @Test
    @WithMockUser(username = "boss", roles = "ADMIN")
    void adminBypassesOwnershipCheck() {
        documents.findByOwner("alice");
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void postAuthorize_deniesForeignDocumentAfterLoad() {
        assertThrows(AccessDeniedException.class,
                () -> documents.findById(2L));    // doc 2 belongs to bob
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void deleteRequiresAdmin() {
        assertThrows(AccessDeniedException.class,
                () -> documents.delete(1L));
    }
}
