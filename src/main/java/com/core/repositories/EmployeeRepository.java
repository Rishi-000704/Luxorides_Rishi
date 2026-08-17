package com.core.repositories;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.core.models.Employee;

@Repository
public interface EmployeeRepository extends CrudRepository<Employee, String> {
	
	Employee findByEmail(String email);

	Employee findByPhone(String phone);
	
	Employee findByEmailAndOrgId(String email, String orgId);

	Employee findByPhoneAndOrgId(String phone, String orgId);

	Employee findByUserId(String userId);

	List<Employee> findByOrgId(String orgId);
}
