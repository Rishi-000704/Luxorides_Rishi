package com.core.models;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.core.models.enums.AccountType;
import com.core.models.enums.Authority;
import com.core.util.PhoneNumberNormalizer;
import com.core.validation.ValidPhone;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.ArrayList;

@Entity
@Getter
@Setter
public class User implements UserDetails {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;

	@Column(length = 100)
	private String email;

	@Column(length = 15)
	@NotBlank
	@ValidPhone
	private String phone;

	@Column(length = 100)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountType accountType;

	@Column(nullable = false)
	private Boolean enabled = true;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(
			name = "user_authorities",
			joinColumns = @JoinColumn(name = "user_id")
	)
	@Column(name = "authority", nullable = false, length = 80)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	private List<Authority> authorities;

	@Column(length = 40)
	private String orgId;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		List<GrantedAuthority> grantedAuthorities = new ArrayList<>();

		if (accountType != null) {
			grantedAuthorities.add(
					new SimpleGrantedAuthority("ROLE_" + accountType.name())
			);
		}

		if (authorities != null) {
			authorities.stream()
					.map(Authority::name)
					.map(SimpleGrantedAuthority::new)
					.forEach(grantedAuthorities::add);
		}

		return List.copyOf(grantedAuthorities);
	}

	public List<Authority> getAuthorityList() {
		return authorities == null ? List.of() : authorities;
	}

	@Override
	public String getUsername() {
		return this.id;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	@PrePersist
	@PreUpdate
	private void normalizePhones() {

		// Required phone
		this.phone = PhoneNumberNormalizer.normalize(this.phone);
	}
}
