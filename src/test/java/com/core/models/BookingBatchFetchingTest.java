package com.core.models;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.hibernate.annotations.BatchSize;
import org.junit.jupiter.api.Test;

/*
 * Phase A regression guard: BookingRepository's three paginated booking-list
 * queries (searchBookingsByStatus / searchBookingsOrderByFirstDutyReportingTimeAsc
 * / ...Desc) cannot safely JOIN FETCH the `entries` collection without
 * breaking Pageable's SQL-level LIMIT/OFFSET, so the N+1 fix here is
 * @BatchSize instead of a query-text change: on the `entries` collection
 * field directly (valid there), and on the Client/ClientBillingEntity
 * *classes* for the client/clientBillingEntity associations, since
 * Hibernate rejects @BatchSize on a @ManyToOne field itself
 * ("Property 'client' may not be annotated '@BatchSize'", confirmed against
 * this app's actual Hibernate 6.5 metadata build, not assumed). This only
 * proves the annotations are DECLARED (same caveat as
 * PaymentUniqueConstraintsTest) -- it cannot prove the resulting query count
 * against a live database, since this test suite has no test datasource.
 */
class BookingBatchFetchingTest {

	@Test
	void entries_isBatchFetched() {
		try {
			Field field = Booking.class.getDeclaredField("entries");
			BatchSize batchSize = field.getAnnotation(BatchSize.class);
			assertTrue(batchSize != null, "Booking.entries must declare @BatchSize");
			assertTrue(batchSize.size() == 100,
					"Booking.entries @BatchSize.size() must be 100 but was " + batchSize.size());
		} catch (NoSuchFieldException e) {
			throw new AssertionError("Booking.entries field not found", e);
		}
	}

	@Test
	void client_targetClassIsBatchFetched() {
		BatchSize batchSize = Client.class.getAnnotation(BatchSize.class);
		assertTrue(batchSize != null,
				"Client must declare a class-level @BatchSize so Booking.client's lazy load batches "
						+ "across a page (Hibernate does not allow @BatchSize on the @ManyToOne field itself)");
		assertTrue(batchSize.size() == 100, "Client @BatchSize.size() must be 100 but was " + batchSize.size());
	}

	@Test
	void clientBillingEntity_targetClassIsBatchFetched() {
		BatchSize batchSize = ClientBillingEntity.class.getAnnotation(BatchSize.class);
		assertTrue(batchSize != null,
				"ClientBillingEntity must declare a class-level @BatchSize so "
						+ "Booking.clientBillingEntity's lazy load batches across a page");
		assertTrue(batchSize.size() == 100,
				"ClientBillingEntity @BatchSize.size() must be 100 but was " + batchSize.size());
	}
}
