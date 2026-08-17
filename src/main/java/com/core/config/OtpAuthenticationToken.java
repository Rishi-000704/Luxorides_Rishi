package com.core.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import com.core.models.User;

public class OtpAuthenticationToken extends AbstractAuthenticationToken {

	private static final long serialVersionUID = 1L;
	private final User principal;

	public OtpAuthenticationToken(User user) {
		super(user.getAuthorities());
		this.principal = user;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return null; // OTP already validated
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}
}
