package com.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.core.models.Client;

public interface ClientRepository extends JpaRepository<Client, String> {

	Optional<Client> findByIdAndOrgId(String id, String orgId);

	List<Client> findByOrgId(String orgId);

	Client findByEmailAndOrgId(String email, String orgId);

	Client findByPhoneAndOrgId(String phone, String orgId);

	Client findByUserId(String userId);

	/*
	 * Client listing page.
	 *
	 * supplier = true records are vendors and must only appear through
	 * the dedicated supplier/vendor API.
	 *
	 * supplier IS NULL is treated as a normal client for backward
	 * compatibility with existing records.
	 */
	@Query("""
			SELECT x
			FROM Client x
			WHERE x.orgId = :orgId
			  AND (x.supplier IS NULL OR x.supplier = false)
			  AND (
					:searchStr IS NULL
					OR LOWER(x.phone) LIKE LOWER(CONCAT('%', :searchStr, '%'))
					OR LOWER(x.email) LIKE LOWER(CONCAT('%', :searchStr, '%'))
					OR LOWER(x.name.firstName) LIKE LOWER(CONCAT('%', :searchStr, '%'))
					OR LOWER(x.name.lastName) LIKE LOWER(CONCAT('%', :searchStr, '%'))
					OR LOWER(x.name.salutation) LIKE LOWER(CONCAT('%', :searchStr, '%'))
			  )
			""")
	Page<Client> getPage(
			@Param("orgId") String orgId,
			@Param("searchStr") String searchStr,
			Pageable pageable
	);

	/*
	 * Non-paginated client list.
	 */
	@Query("""
			SELECT x
			FROM Client x
			WHERE x.orgId = :orgId
			  AND (x.supplier IS NULL OR x.supplier = false)
			""")
	List<Client> findClientsByOrgId(@Param("orgId") String orgId);

	/*
	 * Dedicated supplier/vendor list.
	 */
	List<Client> findByOrgIdAndSupplierTrue(String orgId);
}