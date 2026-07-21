package com.learn.security.lesson11_oauth2;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LESSON 11 — inspect what the identity provider told us about the user.
 *
 * After oauth2Login the principal is an OidcUser: the verified claims
 * from the ID token plus (optionally) the userinfo endpoint. Note what
 * is ABSENT: any password of ours.
 */
@RestController
@Profile("lesson11")
public class OidcUserController {

    @GetMapping("/user")
    public Map<String, Object> user(@AuthenticationPrincipal OidcUser user) {
        return Map.of(
                "subject", user.getSubject(),          // provider's stable user id
                "email", String.valueOf(user.getEmail()),
                "name", String.valueOf(user.getFullName()),
                "issuer", user.getIssuer().toString(), // who vouched for this user
                "allClaims", user.getClaims()
        );
    }
}
