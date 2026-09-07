package com.core.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/*
 * Regression guard for Payment Recovery Audit Phase 1F: locks in that
 * gateway_order_id and gateway_payment_id carry a DB-level uniqueness
 * constraint via Hibernate's ddl-auto=update (this project's only schema
 * mechanism -- there is no separate migration framework these annotations
 * could drift out of sync with). This only proves the constraint is
 * DECLARED, not that it has been applied against live production data --
 * see Payment.java's uniqueConstraints comment for the pre-deploy
 * duplicate-check this repository could not verify without a DB connection.
 */
class PaymentUniqueConstraintsTest {

	@Test
	void payment_declaresUniqueConstraintOnGatewayOrderId() {
		assertTrue(hasUniqueConstraintOn("gateway_order_id"),
				"Payment must declare a unique constraint on gateway_order_id");
	}

	@Test
	void payment_declaresUniqueConstraintOnGatewayPaymentId() {
		assertTrue(hasUniqueConstraintOn("gateway_payment_id"),
				"Payment must declare a unique constraint on gateway_payment_id");
	}

	private boolean hasUniqueConstraintOn(String columnName) {
		Table table = Payment.class.getAnnotation(Table.class);
		assertEquals("payment", table.name());

		for (UniqueConstraint constraint : table.uniqueConstraints()) {
			List<String> columns = Arrays.asList(constraint.columnNames());
			if (columns.contains(columnName)) {
				return true;
			}
		}

		return false;
	}
}
