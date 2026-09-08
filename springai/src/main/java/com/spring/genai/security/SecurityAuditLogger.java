package com.spring.genai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Auth success/failure are published for free by Spring Security; denial events need the
 * {@code AuthorizationEventPublisher} bean in {@link SecurityConfig}. Never logs credentials —
 * only who/what/when, for anomaly detection and forensics.
 */
@Component
public class SecurityAuditLogger {

	private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

	@EventListener
	public void onSuccess(AuthenticationSuccessEvent event) {
		log.info("auth success user={}", event.getAuthentication().getName());
	}

	@EventListener
	public void onFailure(AbstractAuthenticationFailureEvent event) {
		log.warn("auth failure user={} reason={}", event.getAuthentication().getName(),
				event.getException().getClass().getSimpleName());
	}

	@EventListener
	public void onDenied(AuthorizationDeniedEvent<?> event) {
		log.warn("authorization denied user={}",
				event.getAuthentication().get().getName());
	}

}
