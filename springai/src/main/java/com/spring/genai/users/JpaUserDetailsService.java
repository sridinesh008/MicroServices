package com.spring.genai.users;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JpaUserDetailsService implements UserDetailsService {

	private final AppUserRepository repository;

	public JpaUserDetailsService(AppUserRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AppUser user = repository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("No user " + username));
		return User.builder()
				.username(user.getUsername())
				.password(user.getPasswordHash())
				.disabled(!user.isEnabled())
				.roles(user.getRoles().toArray(String[]::new))
				.build();
	}

}
