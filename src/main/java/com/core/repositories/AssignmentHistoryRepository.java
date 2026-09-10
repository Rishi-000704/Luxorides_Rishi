package com.core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.AssignmentHistory;

@Repository
public interface AssignmentHistoryRepository extends JpaRepository<AssignmentHistory, String> {

	List<AssignmentHistory> findByOrgIdAndDutyIdOrderByCreatedAtAsc(String orgId, String dutyId);
}
