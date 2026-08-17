package com.core.security;

import com.core.models.enums.Authority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.core.models.User;
import com.core.models.enums.AccountType;

@Component
public class SecurityContextUtil {

    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (User) auth.getPrincipal();
    }

    public String orgId() {
        return currentUser().getOrgId();
    }

    public boolean isClient() {
        return currentUser().getAccountType() == AccountType.CLIENT;
    }

    public boolean isEmployee() {
        return currentUser().getAccountType() == AccountType.EMPLOYEE;
    }
    
    public String userId() {
    	return currentUser().getId();
    }

    public boolean hasAuthority(Authority authority) {
        return currentUser()
                .getAuthorityList()
                .contains(authority);
    }
}
