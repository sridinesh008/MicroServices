package com.spring.genai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Full context load against a real, fresh Postgres container -- confirms every Flyway
 * migration applies cleanly and every entity mapping matches (ddl-auto=validate would fail
 * loudly otherwise). Dummy API keys are enough for the vision/chat clients to construct; none
 * of them are actually called here.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
		"spring.ai.google.genai.api-key=test-key",
		"spring.ai.anthropic.api-key=test-key",
		"assistant.seed-users=user:pass:USER"
})
class GenaiApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void anonymousRootRedirectsToLogin() {
		ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
	}

	@Test
	void authenticatedUserIsServedTheBundledSpaShell() {
		ResponseEntity<String> response = restTemplate.withBasicAuth("user", "pass").getForEntity("/", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("<title>FinTrack</title>");
	}

}
