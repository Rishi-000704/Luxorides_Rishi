package com.core.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DriverDutySosAlert;

@Repository
public interface DriverDutySosAlertRepository extends JpaRepository<DriverDutySosAlert, String> {
}
