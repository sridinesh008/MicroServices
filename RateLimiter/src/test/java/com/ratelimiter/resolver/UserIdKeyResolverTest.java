package com.ratelimiter.resolver;

import com.ratelimiter.model.RateLimitScope;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdKeyResolverTest {

    private final UserIdKeyResolver resolver = new UserIdKeyResolver();

    // Build a syntactically valid JWT with given payload JSON (no real signature)
    private String buildJwt(String payloadJson) {
        String header  = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes());
        return header + "." + payload + ".fake_sig";
    }

    @Test
    void extractsSubFromJwtPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("Authorization", "Bearer " + buildJwt("{\"sub\":\"user123\",\"exp\":9999999999}"));

        var key = resolver.resolve(request);

        assertThat(key.scope()).isEqualTo(RateLimitScope.USER_ID);
        assertThat(key.clientId()).isEqualTo("user123");
    }

    @Test
    void fallsBackToIpWhenNoAuthHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.5");

        var key = resolver.resolve(request);

        assertThat(key.clientId()).isEqualTo("192.168.1.5");
    }

    @Test
    void fallsBackToIpWhenNotBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.6");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        var key = resolver.resolve(request);

        assertThat(key.clientId()).isEqualTo("192.168.1.6");
    }

    @Test
    void fallsBackToIpWhenJwtMalformed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.7");
        request.addHeader("Authorization", "Bearer not-a-jwt");

        var key = resolver.resolve(request);

        assertThat(key.clientId()).isEqualTo("192.168.1.7");
    }

    @Test
    void fallsBackToIpWhenSubClaimMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.8");
        request.addHeader("Authorization", "Bearer " + buildJwt("{\"email\":\"user@example.com\"}"));

        var key = resolver.resolve(request);

        assertThat(key.clientId()).isEqualTo("192.168.1.8");
    }
}
