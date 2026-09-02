package com.core.mapper;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.core.dtos.client.app.VehicleCatalogDTO;
import com.core.models.MasterVehicle;
import com.core.models.Package;
import com.core.models.embedded.Money;
import com.core.models.enums.FileAccessCategory;
import com.core.services.common.FileAccessTokenService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class VehicleCatalogAssembler {

    private final FileAccessTokenService fileAccessTokenService;

    public VehicleCatalogDTO assemble(MasterVehicle vehicle, List<Package> packages) {

        return new VehicleCatalogDTO(
            vehicle.getId(),
            vehicle.getName(),
            fileAccessTokenService.toAccessUrl(vehicle.getPic(), vehicle.getOrgId(), FileAccessCategory.PUBLIC),
            vehicle.getFuelSystem(),
            vehicle.getFuelConsumption(),
            vehicle.getVehicleColor(),
            vehicle.getCategory(),
            vehicle.getBrand(),
            vehicle.getSeats(),
            vehicle.getDoors(),
            vehicle.getTransmissionType(),
            vehicle.getHorsePower(),
            vehicle.getVehicleClass(),
            vehicle.getModelYear(),
            vehicle.getPerformance(),
            vehicle.getDimension_length(),
            vehicle.getDimension_width(),
            vehicle.getDimension_height(),
            vehicle.getDimension_wheelbase(),
            vehicle.getSlug(),
            vehicle.getRating(),
            vehicle.getPopularity(),
            vehicle.getChauffeurDriven(),

            // ✅ derived field
            resolveStartingPrice(packages),

            packages == null
                ? List.of()
                : packages.stream().map(this::toPackage).toList()
        );
    }

    /**
     * Starting price = minimum baseFare among packages
     */
    private Money resolveStartingPrice(List<Package> packages) {

        if (packages == null || packages.isEmpty()) {
            return null;
        }

        return packages.stream()
            .map(Package::getBaseFare)
            .filter(Objects::nonNull)
            .min((m1, m2) -> m1.getAmount().compareTo(m2.getAmount()))
            .orElse(null);
    }

    private VehicleCatalogDTO.Package toPackage(Package p) {

        String dutyLabel = String.format(
            "%s (%d %s, %d KM)",
            p.getDutyType(),
            p.getTime(),
            p.getUnit(),
            p.getDistance()
        );

        return new VehicleCatalogDTO.Package(
            p.getId(),
            dutyLabel,
            p.getBaseFare(),
            p.getExtraPerKM(),
            p.getExtraPerHS(),
            p.getNightCharge()
        );
    }
}
