package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.RefundRequest;
import com.core.models.enums.RefundRequestStatus;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, String> {

	List<RefundRequest> findByOrgIdOrderByCreatedAtDesc(String orgId);

	List<RefundRequest> findByOrgIdAndStatusOrderByCreatedAtDesc(String orgId, RefundRequestStatus status);

	Optional<RefundRequest> findByIdAndOrgId(String id, String orgId);

	Optional<RefundRequest> findFirstByOrgIdAndBookingIdOrderByCreatedAtDesc(String orgId, String bookingId);
}
