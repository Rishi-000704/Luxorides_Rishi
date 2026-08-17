package com.core.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.InvoiceEntry;

@Repository
public interface InvoiceEntryRepository extends JpaRepository<InvoiceEntry, String> {

}
