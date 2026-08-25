package com.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.repositories.UserRepository;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class ApplicationConfiguration {
	private final UserRepository userRepo;

	public ApplicationConfiguration(UserRepository userRepo) {
		super();
		this.userRepo = userRepo;
	}

	@Bean
	@SuppressWarnings("null")
	UserDetailsService userDetailsService() {
		try {
			return username -> userRepo.findById(username)
					.orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found."));
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	@Bean
	BCryptPasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

		authProvider.setUserDetailsService(userDetailsService());
		authProvider.setPasswordEncoder(passwordEncoder());

		return authProvider;
	}
}