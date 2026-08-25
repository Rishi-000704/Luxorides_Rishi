package com.core.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DriverDutyIncidentReport;

@Repository
public interface DriverDutyIncidentReportRepository extends JpaRepository<DriverDutyIncidentReport, String> {
}
