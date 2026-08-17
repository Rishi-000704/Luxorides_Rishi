package com.core.dataexchange.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.core.dataexchange.dto.DataExchangeLookupItem;
import com.core.dataexchange.model.DataExchangeLookupType;
import com.core.dataexchange.model.DataExchangeResource;
import com.core.models.Client;
import com.core.repositories.ClientRepository;
import com.core.repositories.MasterVehicleRepository;

@Service
public class DataExchangeLookupService {

    private final ClientRepository clientRepository;
    private final MasterVehicleRepository masterVehicleRepository;
    private final DataExchangeAccessService accessService;

    public DataExchangeLookupService(ClientRepository clientRepository,
                                     MasterVehicleRepository masterVehicleRepository,
                                     DataExchangeAccessService accessService) {
        this.clientRepository = clientRepository;
        this.masterVehicleRepository = masterVehicleRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<DataExchangeLookupItem> get(DataExchangeLookupType type, String orgId) {
        return switch (type) {
            case CLIENT -> {
                accessService.requireView(DataExchangeResource.CLIENT);
                yield clientRepository.findByOrgId(orgId).stream()
                        .filter(client -> !Boolean.TRUE.equals(client.getSupplier()))
                        .map(this::partyItem)
                        .toList();
            }
            case VENDOR -> {
                accessService.requireView(DataExchangeResource.VENDOR);
                yield clientRepository.findByOrgId(orgId).stream()
                        .filter(client -> Boolean.TRUE.equals(client.getSupplier()))
                        .map(this::partyItem)
                        .toList();
            }
            case MASTER_VEHICLE -> {
                accessService.requireView(DataExchangeResource.MASTER_VEHICLE);
                yield masterVehicleRepository.findByOrgId(orgId).stream()
                        .map(vehicle -> new DataExchangeLookupItem(
                                vehicle.getId(),
                                vehicle.getName(),
                                join(vehicle.getBrand(), vehicle.getCategory())
                        ))
                        .toList();
            }
        };
    }

    private DataExchangeLookupItem partyItem(Client client) {
        String label = client.getName() == null ? client.getPhone() : client.getName().getDisplayName();
        return new DataExchangeLookupItem(client.getId(), label, client.getPhone());
    }

    private String join(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " · " + second;
    }
}
