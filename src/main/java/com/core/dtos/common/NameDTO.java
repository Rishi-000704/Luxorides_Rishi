package com.core.dtos.common;

public record NameDTO(
	String salutation,
	String firstName,
	String lastName
) {

	public String displayName() {
		StringBuilder sb = new StringBuilder();
		if (salutation != null && !salutation.isBlank()) sb.append(salutation).append(" ");
		if (firstName != null && !firstName.isBlank()) sb.append(firstName).append(" ");
		if (lastName != null && !lastName.isBlank()) sb.append(lastName);
		return sb.toString().trim();
	}
}
