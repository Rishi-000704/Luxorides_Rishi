package com.core.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
	private final AuthenticationProvider authenticationProvider;
	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final List<String> corsAllowedOrigins;

	public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter,
			AuthenticationProvider authenticationProvider,
			@Value("${cors.allowed-origins}") String corsAllowedOrigins) {
		this.authenticationProvider = authenticationProvider;
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.corsAllowedOrigins = CorsOrigins.parse(corsAllowedOrigins);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable()).cors(cors -> cors.configurationSource(corsConfigurationSource())) // Explicit
																											// CORS
																											// configuration
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/**", "/file/**", "/", "/external/**", "/driver-api/**", "/actuator/**", "/estimate-api/**", "/ws/**", "/public/**", "/webhooks/razorpay/**")
						.permitAll().anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authenticationProvider(authenticationProvider)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();

		// Exact allowed origins, environment-driven via cors.allowed-origins
		// -- no wildcard, no origin patterns, no subdomain matching. See
		// CorsOrigins for the shared parsing WebSocketConfig also uses.
		configuration.setAllowedOrigins(corsAllowedOrigins);

		configuration.setExposedHeaders(
				List.of(RequestTraceFilter.TRACE_ID_HEADER)
		);

		// Allow all standard HTTP methods
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

		// Allow all headers
		configuration.setAllowedHeaders(List.of("*"));

		// Customer and Fleetovo authenticate exclusively via a Bearer JWT in
		// the Authorization header; neither relies on cookies, and this
		// backend never sets or reads a cookie anywhere -- so credentialed
		// CORS isn't needed here.
		configuration.setAllowCredentials(false);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		// Apply CORS settings to all paths
		source.registerCorsConfiguration("/**", configuration);

		return source;
	}
}
