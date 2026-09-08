package com.core.services;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.core.exception.BusinessException;
import com.core.models.Client;
import com.core.models.Passenger;
import com.core.repositories.ClientBillingEntityRepository;
import com.core.repositories.ClientRepository;
import com.core.repositories.PassengerRepository;
import com.core.repositories.UserRepository;
import com.core.services.common.FileService;

/*
 * P0 IDOR fix -- updatePassenger previously fetched the target Passenger by
 * id only, with no check that it actually belongs to the caller, so any
 * authenticated client could overwrite another client's passenger
 * name/email/phone by guessing a passengerId. PassengerController.update
 * always stamps passenger.clientId to the CALLER's own resolved client id
 * before calling this, so the check here is what actually stops the write
 * from landing on someone else's record -- same ownership check
 * deletePassenger already had.
 */
class ClientServicePassengerOwnershipTest {

	private static final String ORG_ID = "org-1";
	private static final String PASSENGER_ID = "passenger-1";
	private static final String OWNING_CLIENT_ID = "client-owner";
	private static final String OTHER_CLIENT_ID = "client-other";

	private PassengerRepository passengerRepository;
	private ClientRepository clientRepository;
	private ClientService service;

	@BeforeEach
	void setUp() {
		clientRepository = mock(ClientRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		FileService fileService = mock(FileService.class);
		ClientBillingEntityRepository billingEntityRepository = mock(ClientBillingEntityRepository.class);
		passengerRepository = mock(PassengerRepository.class);

		service = new ClientService(clientRepository, userRepository, fileService, billingEntityRepository, passengerRepository);
	}

	private Passenger existingPassengerOwnedBy(String clientId) {
		Passenger passenger = new Passenger();
		passenger.setId(PASSENGER_ID);
		passenger.setClientId(clientId);
		passenger.setPhone("9999999999");
		return passenger;
	}

	private Passenger incomingEdit(String requestingClientId) {
		Passenger edit = new Passenger();
		edit.setId(PASSENGER_ID);
		// The controller always overwrites clientId to the CALLER's own id
		// before invoking the service -- never the requested passengerId's
		// actual owner.
		edit.setClientId(requestingClientId);
		edit.setPhone("8888888888");
		return edit;
	}

	@Test
	void updatePassenger_ownerEditsTheirOwnPassenger_succeeds() {
		when(passengerRepository.findById(PASSENGER_ID)).thenReturn(Optional.of(existingPassengerOwnedBy(OWNING_CLIENT_ID)));
		when(passengerRepository.findByClientId(OWNING_CLIENT_ID)).thenReturn(java.util.List.of());

		Client owner = new Client();
		owner.setId(OWNING_CLIENT_ID);
		owner.setOrgId(ORG_ID);
		when(clientRepository.findByIdAndOrgId(OWNING_CLIENT_ID, ORG_ID)).thenReturn(Optional.of(owner));

		service.updatePassenger(incomingEdit(OWNING_CLIENT_ID), ORG_ID);

		verify(passengerRepository).save(any(Passenger.class));
	}

	@Test
	void updatePassenger_anotherClientAttemptsToEditIt_isDenied_recordIsNeverMutated() {
		when(passengerRepository.findById(PASSENGER_ID)).thenReturn(Optional.of(existingPassengerOwnedBy(OWNING_CLIENT_ID)));

		assertThrows(BusinessException.class,
				() -> service.updatePassenger(incomingEdit(OTHER_CLIENT_ID), ORG_ID));

		verify(passengerRepository, never()).save(any(Passenger.class));
	}

	@Test
	void deletePassenger_anotherClientAttemptsToDeleteIt_isDenied_stillCorrectlyProtected() {
		when(passengerRepository.findById(PASSENGER_ID)).thenReturn(Optional.of(existingPassengerOwnedBy(OWNING_CLIENT_ID)));

		assertThrows(BusinessException.class,
				() -> service.deletePassenger(OTHER_CLIENT_ID, PASSENGER_ID));

		verify(passengerRepository, never()).delete(any(Passenger.class));
	}
}
