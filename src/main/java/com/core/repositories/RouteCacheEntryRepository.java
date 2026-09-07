package com.core.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.core.models.RouteCacheEntry;

@Repository
public interface RouteCacheEntryRepository extends JpaRepository<RouteCacheEntry, String> {

	Optional<RouteCacheEntry> findByOriginKeyAndDestinationKeyAndProfile(
			String originKey,
			String destinationKey,
			String profile);

	/*
	 * Bulk update (not read-modify-write on the entity) so concurrent cache
	 * hits on the same popular route never lose an increment to a
	 * last-write-wins race.
	 */
	@Modifying
	@Transactional
	@Query("update RouteCacheEntry r set r.usageCount = r.usageCount + 1, r.lastUsedAt = :now where r.id = :id")
	void recordHit(@Param("id") String id, @Param("now") Instant now);
}
