package com.core.models;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/*
 * Locks in that the route cache key includes "profile" (routing audit Phase
 * 2I item 12): if a second travel profile is ever added, a cache row for one
 * profile must never be treated as valid for another, even when origin and
 * destination coordinates are identical.
 */
class RouteCacheEntryConstraintsTest {

	@Test
	void routeCacheKey_includesProfile_soDifferentProfilesNeverCollide() {
		Table table = RouteCacheEntry.class.getAnnotation(Table.class);
		assertTrue(hasCompositeKey(table, "origin_key", "destination_key", "profile"));
	}

	private boolean hasCompositeKey(Table table, String... expectedColumns) {
		List<String> expected = Arrays.asList(expectedColumns);

		for (UniqueConstraint constraint : table.uniqueConstraints()) {
			List<String> actual = Arrays.asList(constraint.columnNames());
			if (actual.containsAll(expected) && expected.containsAll(actual)) {
				return true;
			}
		}

		return false;
	}
}
