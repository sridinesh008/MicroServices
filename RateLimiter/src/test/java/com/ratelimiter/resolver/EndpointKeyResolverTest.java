package com.ratelimiter.resolver;

import com.ratelimiter.model.RateLimitScope;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointKeyResolverTest {

    private final EndpointKeyResolver resolver = new EndpointKeyResolver();

    @Test
    void buildsClientIdFromMethodAndUri() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

        var key = resolver.resolve(request);

        assertThat(key.scope()).isEqualTo(RateLimitScope.ENDPOINT);
        assertThat(key.clientId()).isEqualTo("GET:/api/users");
    }

    @Test
    void differentMethodsSamePathProduceDifferentKeys() {
        var get  = resolver.resolve(new MockHttpServletRequest("GET",    "/api/users"));
        var post = resolver.resolve(new MockHttpServletRequest("POST",   "/api/users"));

        assertThat(get.clientId()).isNotEqualTo(post.clientId());
    }

    @Test
    void endpointFieldMatchesUri() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/users/123");

        var key = resolver.resolve(request);

        assertThat(key.endpoint()).isEqualTo("/api/users/123");
    }
}
