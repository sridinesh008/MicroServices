package com.spring.genai.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.spring.genai.config.AssistantProperties;

class AppUserProvisioningRunnerTest {

	private final AppUserRepository repository = mock(AppUserRepository.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);

	private AppUserProvisioningRunner runner(String seedUsers) {
		AssistantProperties properties = new AssistantProperties();
		properties.setSeedUsers(seedUsers);
		return new AppUserProvisioningRunner(repository, encoder, properties);
	}

	@Test
	void seedsEachConfiguredUserOnFirstBoot() throws Exception {
		when(repository.count()).thenReturn(0L);
		when(encoder.encode(anyString())).thenReturn("hashed");

		runner("user:pass1:USER,admin:pass2:USER|ADMIN").run(null);

		verify(repository, times(2)).save(any(AppUser.class));
	}

	@Test
	void skipsEntirelyWhenTableAlreadyHasUsers() throws Exception {
		when(repository.count()).thenReturn(1L);

		runner("user:pass1:USER").run(null);

		verify(repository, never()).save(any());
	}

	@Test
	void refusesToSeedBeyondFiveAccounts() throws Exception {
		when(repository.count()).thenReturn(0L);
		when(encoder.encode(anyString())).thenReturn("hashed");
		String sixUsers = "u1:p:USER,u2:p:USER,u3:p:USER,u4:p:USER,u5:p:USER,u6:p:USER";

		runner(sixUsers).run(null);

		verify(repository, times(5)).save(any(AppUser.class));
	}

	@Test
	void skipsAnEntryWhoseUsernameAlreadyExists() throws Exception {
		when(repository.count()).thenReturn(0L);
		when(encoder.encode(anyString())).thenReturn("hashed");
		when(repository.existsByUsername("user")).thenReturn(true);

		runner("user:pass1:USER,admin:pass2:USER").run(null);

		verify(repository, times(1)).save(any(AppUser.class));
	}

	@Test
	void doesNothingWhenNoSeedUsersConfigured() throws Exception {
		when(repository.count()).thenReturn(0L);

		runner("").run(null);

		verify(repository, never()).save(any());
		assertThat(AppUserProvisioningRunner.MAX_USERS).isEqualTo(5);
	}

}
