package com.core.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.Passenger;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, String> {
	List<Passenger> findByClientId(String clientId);
}
