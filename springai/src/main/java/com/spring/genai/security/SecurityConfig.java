package com.spring.genai.security;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Deny-by-default: only static assets and the health probe are public. Everything else,
 * including the SPA shell and the API, requires the session cookie from a successful form
 * login (or a Basic-auth header, for scripted/curl access). Accounts are DB-backed (see
 * {@code users.JpaUserDetailsService} / {@code users.AppUserProvisioningRunner}) — up to 5
 * admin-provisioned accounts, no self-service signup.
 *
 * <p>CSRF uses a readable cookie (not the session-attribute default) because the React SPA is
 * a JS client that must read the {@code XSRF-TOKEN} cookie itself and echo it back as the
 * {@code X-XSRF-TOKEN} header on state-changing requests.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/css/**", "/js/**", "/assets/**", "/manifest.webmanifest",
								"/favicon.ico", "/sw.js")
						.permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
				.headers(headers -> headers
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31536000))
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; script-src 'self'; style-src 'self'; "
										+ "img-src 'self' data:; connect-src 'self'; "
										+ "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
						.frameOptions(frame -> frame.deny())
						.referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
				.sessionManagement(session -> session
						.sessionFixation(fixation -> fixation.changeSessionId())
						.maximumSessions(1))
				.logout(logout -> logout
						.logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/logout"))
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID"))
				.httpBasic(Customizer.withDefaults())
				.formLogin(Customizer.withDefaults());
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
		return new SpringAuthorizationEventPublisher(publisher);
	}

}
