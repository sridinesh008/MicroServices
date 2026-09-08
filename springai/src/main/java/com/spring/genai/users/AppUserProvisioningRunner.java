package com.spring.genai.users;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.spring.genai.config.AssistantProperties;

/**
 * Seeds app users from {@code assistant.seed-users} on first boot only (never overwrites an
 * existing row -- an admin may have since rotated a password by direct DB edit). There is no
 * self-service signup; this is the only account-provisioning path. Caps at
 * {@link #MAX_USERS} total accounts -- extra seed entries beyond that are logged and skipped
 * rather than crashing a system that already has working users.
 */
@Component
public class AppUserProvisioningRunner implements ApplicationRunner {

	static final int MAX_USERS = 5;

	private static final Logger log = LoggerFactory.getLogger(AppUserProvisioningRunner.class);

	private final AppUserRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final String seedUsers;

	public AppUserProvisioningRunner(AppUserRepository repository, PasswordEncoder passwordEncoder,
			AssistantProperties properties) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.seedUsers = properties.getSeedUsers();
	}

	@Override
	public void run(ApplicationArguments args) {
		if (repository.count() > 0) {
			log.info("app_user table already populated, skipping seed");
			return;
		}
		if (seedUsers == null || seedUsers.isBlank()) {
			log.warn("no assistant.seed-users configured -- no accounts will exist, nobody can log in");
			return;
		}

		int seeded = 0;
		for (String entry : seedUsers.split(",")) {
			if (entry.isBlank()) {
				continue;
			}
			if (seeded >= MAX_USERS) {
				log.error("assistant.seed-users lists more than {} accounts -- ignoring '{}' and beyond",
						MAX_USERS, entry);
				break;
			}
			String[] parts = entry.split(":", 3);
			if (parts.length != 3) {
				log.error("malformed seed-users entry, expected username:password:ROLE1|ROLE2 -- got '{}'", entry);
				continue;
			}
			String username = parts[0].trim();
			String password = parts[1];
			List<String> roles = Arrays.asList(parts[2].split("\\|"));
			if (repository.existsByUsername(username)) {
				log.warn("seed user '{}' already exists, skipping", username);
				continue;
			}
			repository.save(new AppUser(username, passwordEncoder.encode(password), roles));
			seeded++;
			log.info("seeded user username={} roles={}", username, roles);
		}
	}

}
