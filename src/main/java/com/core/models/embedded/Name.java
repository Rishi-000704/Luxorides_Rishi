package com.core.models.embedded;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Name {
	private String salutation; // Mr, Ms, Dr
	private String firstName;
	private String lastName;

	@Transient
	public String getDisplayName() {
		return Stream.of(salutation, firstName, lastName).filter(s -> s != null && !s.isBlank())
				.collect(Collectors.joining(" "));
	}
}
