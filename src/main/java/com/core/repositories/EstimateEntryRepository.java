package com.core.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.core.models.EstimateEntry;

public interface EstimateEntryRepository extends JpaRepository<EstimateEntry, String> {
}