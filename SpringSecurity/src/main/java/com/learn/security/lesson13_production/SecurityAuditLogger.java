package com.learn.security.lesson13_production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.stereotype.Component;

/**
 * LESSON 13 — audit logging, the industry way.
 *
 * Spring Security publishes application events for every authentication
 * outcome; we just listen. Zero coupling to filters or providers — this
 * class works unchanged whether logins come from form, basic, JWT, or
 * OIDC.
 *
 * In production these lines go to a structured log pipeline (JSON ->
 * ELK/Datadog) with alerts on patterns: N failures per user per minute,
 * failures from new geographies, denied-authorization spikes.
 *
 * RULES: log WHO and WHY — never the credential itself. Failure reason
 * stays generic toward the CLIENT (Lesson 5, user enumeration) but may
 * be precise in the INTERNAL log, which is what this is.
 */
@Component
@Profile("lesson13")
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        log.info("AUTH SUCCESS user='{}' type={}",
                event.getAuthentication().getName(),
                event.getAuthentication().getClass().getSimpleName());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        // event class name encodes the reason: BadCredentials, Disabled, Locked...
        log.warn("AUTH FAILURE user='{}' reason={}",
                event.getAuthentication().getName(),
                event.getException().getClass().getSimpleName());
    }

    @EventListener
    public void onDenied(AuthorizationDeniedEvent<?> event) {
        log.warn("ACCESS DENIED user='{}'",
                event.getAuthentication().get().getName());
    }
}
