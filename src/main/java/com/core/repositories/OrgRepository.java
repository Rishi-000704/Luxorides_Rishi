package com.core.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.core.models.Org;

@Repository
public interface OrgRepository extends JpaRepository<Org, String> {
	Optional<Org> findByOrgId(String orgId);
}
