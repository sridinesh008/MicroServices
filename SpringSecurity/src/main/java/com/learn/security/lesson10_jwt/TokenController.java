package com.learn.security.lesson10_jwt;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * LESSON 10 — the token mint.
 *
 * Reached only through chain 1 (basic auth already succeeded), so the
 * `authentication` parameter is the fully verified user. We convert
 * that identity into signed claims.
 *
 * Claim naming follows RFC 7519 registered claims where one exists:
 *   iss (issuer), sub (subject), iat (issued at), exp (expiry).
 * "roles" is our private claim — private claims are fine, just document
 * them; the converter bean on the validating side must agree on the name.
 */
@RestController
@Profile("lesson10")
public class TokenController {

    private final JwtEncoder encoder;

    TokenController(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    @PostMapping("/token")
    public String issueToken(Authentication authentication) {
        Instant now = Instant.now();

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))   // store bare names; converter re-prefixes
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("spring-security-learning")        // who minted it
                .subject(authentication.getName())         // who it is about
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))  // SHORT. Prod APIs: 5-15 min + refresh token
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
