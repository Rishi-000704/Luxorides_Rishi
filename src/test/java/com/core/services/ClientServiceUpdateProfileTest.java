package com.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.core.models.Client;
import com.core.repositories.ClientBillingEntityRepository;
import com.core.repositories.ClientRepository;
import com.core.repositories.PassengerRepository;
import com.core.repositories.UserRepository;
import com.core.services.common.FileService;

/*
 * P1.6 -- Client.pic is an ordinary display avatar (not KYC/evidence), so
 * updateProfile now goes through FileService.saveDisplayImage instead of
 * saveFile. The pre-existing delete-old-file-before-save behavior is
 * unchanged.
 */
class ClientServiceUpdateProfileTest {

	private static final String ORG_ID = "org-1";
	private static final String CLIENT_ID = "client-1";

	private ClientRepository clientRepository;
	private FileService fileService;
	private ClientService service;

	@BeforeEach
	void setUp() {
		clientRepository = mock(ClientRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		fileService = mock(FileService.class);
		ClientBillingEntityRepository billingEntityRepository = mock(ClientBillingEntityRepository.class);
		PassengerRepository passengerRepository = mock(PassengerRepository.class);

		service = new ClientService(clientRepository, userRepository, fileService, billingEntityRepository, passengerRepository);
	}

	private Client client() {
		Client c = new Client();
		c.setId(CLIENT_ID);
		c.setOrgId(ORG_ID);
		return c;
	}

	@Test
	void updateProfile_usesSaveDisplayImage_notSaveFile() throws Exception {
		when(clientRepository.findByIdAndOrgId(CLIENT_ID, ORG_ID)).thenReturn(Optional.of(client()));
		when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
		when(fileService.saveDisplayImage(any())).thenReturn("resized.jpg");

		MultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		Client result = service.updateProfile(CLIENT_ID, ORG_ID, file);

		assertEquals("resized.jpg", result.getPic());
		verify(fileService).saveDisplayImage(file);
		verify(fileService, never()).saveFile(any());
	}

	@Test
	void updateProfile_deletesThePreviousPic_beforeSavingTheNewOne() throws Exception {
		Client existing = client();
		existing.setPic("old-avatar.jpg");
		when(clientRepository.findByIdAndOrgId(CLIENT_ID, ORG_ID)).thenReturn(Optional.of(existing));
		when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
		when(fileService.saveDisplayImage(any())).thenReturn("new-avatar.jpg");

		MultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
		service.updateProfile(CLIENT_ID, ORG_ID, file);

		verify(fileService).deleteFile("old-avatar.jpg");
	}
}
