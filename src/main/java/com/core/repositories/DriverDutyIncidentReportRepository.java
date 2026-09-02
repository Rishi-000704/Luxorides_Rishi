package com.core.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DriverDutyIncidentReport;
import com.core.models.enums.DriverDutyIncidentCategory;

@Repository
public interface DriverDutyIncidentReportRepository extends JpaRepository<DriverDutyIncidentReport, String> {

	Optional<DriverDutyIncidentReport> findFirstByDutyIdAndCategoryAndDescriptionAndCreatedAtAfterOrderByCreatedAtDesc(
			String dutyId, DriverDutyIncidentCategory category, String description, Instant after);
}
