package com.core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.DriverDutyExpense;

@Repository
public interface DriverDutyExpenseRepository extends JpaRepository<DriverDutyExpense, String> {

	List<DriverDutyExpense> findByBookingEntry_Id(String bookingEntryId);
	
	List<DriverDutyExpense> findByBookingEntry_IdOrderByCreatedAtAsc(String bookingEntryId);
}