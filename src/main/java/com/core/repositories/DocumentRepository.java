package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.Document;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

	Optional<Document> findByReferenceIdAndOrgIdAndDocumentType(String referenceId, String orgId, String documentType);

	List<Document> findByReferenceIdAndOrgId(String referenceId, String orgId);
}
