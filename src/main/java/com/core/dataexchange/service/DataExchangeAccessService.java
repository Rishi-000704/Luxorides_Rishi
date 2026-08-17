package com.core.dataexchange.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.core.dataexchange.model.DataExchangeResource;
import com.core.models.enums.Authority;
import com.core.security.SecurityContextUtil;

@Service
public class DataExchangeAccessService {

    private final SecurityContextUtil security;
    private final Map<DataExchangeResource, ResourceAuthorities> authorities;

    public DataExchangeAccessService(SecurityContextUtil security) {
        this.security = security;

        EnumMap<DataExchangeResource, ResourceAuthorities> map =
                new EnumMap<>(DataExchangeResource.class);

        map.put(
                DataExchangeResource.CLIENT,
                new ResourceAuthorities(
                        Authority.CLIENT_VIEW,
                        Authority.CLIENT_ADD,
                        Authority.CLIENT_EDIT
                )
        );

        map.put(
                DataExchangeResource.VENDOR,
                new ResourceAuthorities(
                        Authority.CLIENT_VIEW,
                        Authority.CLIENT_ADD,
                        Authority.CLIENT_EDIT
                )
        );

        map.put(
                DataExchangeResource.DRIVER,
                new ResourceAuthorities(
                        Authority.DRIVER_VIEW,
                        Authority.DRIVER_ADD,
                        Authority.DRIVER_EDIT
                )
        );

        map.put(
                DataExchangeResource.PACKAGE,
                new ResourceAuthorities(
                        Authority.PACKAGE_VIEW,
                        Authority.PACKAGE_ADD,
                        Authority.PACKAGE_EDIT
                )
        );

        map.put(
                DataExchangeResource.MASTER_VEHICLE,
                new ResourceAuthorities(
                        Authority.MASTER_VEHICLE_VIEW,
                        Authority.MASTER_VEHICLE_ADD,
                        Authority.MASTER_VEHICLE_EDIT
                )
        );

        map.put(
                DataExchangeResource.FLEET_VEHICLE,
                new ResourceAuthorities(
                        Authority.FLEET_VEHICLE_VIEW,
                        Authority.FLEET_VEHICLE_ADD,
                        Authority.FLEET_VEHICLE_EDIT
                )
        );

        validateCompleteMapping(map);
        this.authorities = Map.copyOf(map);
    }

    /**
     * Requires access to the Data Exchange module.
     */
    public void requireModuleView() {
        requireEmployee();
        require(Authority.DATA_EXCHANGE_VIEW);
    }

    /**
     * Used when building the list of resources visible to the current employee.
     */
    public boolean canView(DataExchangeResource resource) {
        if (!security.isEmployee()) {
            return false;
        }

        ResourceAuthorities resourceAuthorities = authoritiesFor(resource);

        return has(Authority.DATA_EXCHANGE_VIEW)
                && has(resourceAuthorities.view());
    }

    /**
     * Metadata, template and resource-level view access.
     */
    public void requireView(DataExchangeResource resource) {
        requireModuleView();
        require(authoritiesFor(resource).view());
    }

    /**
     * Export requires module view, export capability and resource visibility.
     */
    public void requireExport(DataExchangeResource resource) {
        requireModuleView();
        require(Authority.DATA_EXCHANGE_EXPORT);
        require(authoritiesFor(resource).view());
    }

    /**
     * Import and validation require module view, import capability and resource
     * visibility. ADD and EDIT are checked only when the prepared file actually
     * contains those operations.
     */
    public void requireImport(
            DataExchangeResource resource,
            int creates,
            int updates
    ) {
        requireModuleView();
        require(Authority.DATA_EXCHANGE_IMPORT);

        ResourceAuthorities resourceAuthorities = authoritiesFor(resource);

        require(resourceAuthorities.view());

        if (creates > 0) {
            require(resourceAuthorities.add());
        }

        if (updates > 0) {
            require(resourceAuthorities.edit());
        }
    }

    private ResourceAuthorities authoritiesFor(
            DataExchangeResource resource
    ) {
        ResourceAuthorities resourceAuthorities = authorities.get(resource);

        if (resourceAuthorities == null) {
            throw new IllegalStateException(
                    "No Data Exchange authority mapping is configured for "
                            + resource.name()
            );
        }

        return resourceAuthorities;
    }

    private void validateCompleteMapping(
            Map<DataExchangeResource, ResourceAuthorities> mappings
    ) {
        Set<DataExchangeResource> missing =
                EnumSet.allOf(DataExchangeResource.class);

        missing.removeAll(mappings.keySet());

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing Data Exchange authority mappings: " + missing
            );
        }
    }

    private void requireEmployee() {
        if (!security.isEmployee()) {
            throw new AccessDeniedException(
                    "An employee account is required."
            );
        }
    }

    private boolean has(Authority authority) {
        return security.currentUser()
                .getAuthorityList()
                .contains(authority);
    }

    private void require(Authority authority) {
        if (!has(authority)) {
            throw new AccessDeniedException(
                    "Missing authority: " + authority.name()
            );
        }
    }

    private record ResourceAuthorities(
            Authority view,
            Authority add,
            Authority edit
    ) {
    }
}