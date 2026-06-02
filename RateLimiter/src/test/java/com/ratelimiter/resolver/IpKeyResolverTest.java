package com.ratelimiter.resolver;

import com.ratelimiter.model.RateLimitScope;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpKeyResolverTest {

    private final IpKeyResolver resolver = new IpKeyResolver();

    @Test
    void usesRemoteAddrWhenNoForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");

        var key = resolver.resolve(request);

        assertThat(key.scope()).isEqualTo(RateLimitScope.IP);
        assertThat(key.clientId()).isEqualTo("192.168.1.1");
    }

    @Test
    void usesFirstIpFromXForwardedForChain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18, 150.172.238.178");

        var key = resolver.resolve(request);

        // 203.0.113.1 = original client; 70.41.3.18, 150.172.238.178 = proxies
        assertThat(key.clientId()).isEqualTo("203.0.113.1");
    }

    @Test
    void handlesSingleIpInForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5");

        var key = resolver.resolve(request);

        assertThat(key.clientId()).isEqualTo("203.0.113.5");
    }

    @Test
    void endpointFieldMatchesRequestUri() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.setRemoteAddr("1.2.3.4");

        var key = resolver.resolve(request);

        assertThat(key.endpoint()).isEqualTo("/api/orders");
    }
}
