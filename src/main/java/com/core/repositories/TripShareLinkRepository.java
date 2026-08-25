package com.core.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.TripShareLink;

@Repository
public interface TripShareLinkRepository extends JpaRepository<TripShareLink, String> {

	Optional<TripShareLink> findByTokenHash(String tokenHash);

	Optional<TripShareLink> findFirstByDutyIdAndOrgIdAndRevokedFalseOrderByCreatedAtDesc(String dutyId, String orgId);
}
