package com.core.dtos.driver;

import com.core.dtos.common.AddressSnapshotDTO;

// One real, org-configured garage a driver can select as their base/departure
// location -- see CityGarageService, the same ops-managed source Fleetovo's
// own garage config screen reads. Deliberately a purpose-built DTO (not the
// raw CityGarage entity CityGarageController returns to ops) to match every
// other driver-app-facing response shape in this package.
public record DriverGarageOptionDTO(String id, String city, AddressSnapshotDTO garageLocation) {
}
