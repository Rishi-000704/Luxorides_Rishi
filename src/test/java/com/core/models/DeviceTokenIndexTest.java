package com.core.models;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Index;
import jakarta.persistence.Table;

/*
 * Phase A regression guard: DeviceTokenRepository.findByOrgIdAndRecipientTypeAndRecipientId
 * runs on every push send (NotificationService.create) and previously had no
 * covering index -- a full table scan of device_token. Locks in that the
 * composite index matching that exact lookup's predicate columns stays
 * declared. Same caveat as PaymentUniqueConstraintsTest: this only proves the
 * index is DECLARED via ddl-auto=update (this project's only schema
 * mechanism), not that it exists against live production data.
 *
 * columnList is asserted in camelCase (orgId, recipientType, recipientId),
 * not snake_case -- this app has no Hibernate physical naming strategy, so
 * these unannotated @Column fields' real physical column names are the
 * literal Java property names. Verified empirically against Hibernate's own
 * resolved Table/Index/Column metadata, not assumed.
 */
class DeviceTokenIndexTest {

	@Test
	void deviceToken_declaresIndexOnOrgRecipientTypeRecipientId() {
		Table table = DeviceToken.class.getAnnotation(Table.class);

		boolean hasMatchingIndex = Arrays.stream(table.indexes())
				.map(Index::columnList)
				.anyMatch(cols -> normalize(cols).equals("orgId,recipientType,recipientId"));

		assertTrue(hasMatchingIndex,
				"DeviceToken must declare an index covering (orgId, recipientType, recipientId), "
						+ "matching DeviceTokenRepository.findByOrgIdAndRecipientTypeAndRecipientId's predicate");
	}

	private String normalize(String columnList) {
		return columnList.replace(" ", "");
	}
}
