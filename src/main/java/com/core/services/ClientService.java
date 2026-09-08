package com.core.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.Client;
import com.core.models.ClientBillingEntity;
import com.core.models.Passenger;
import com.core.repositories.ClientBillingEntityRepository;
import com.core.repositories.ClientRepository;
import com.core.repositories.PassengerRepository;
import com.core.repositories.UserRepository;
import com.core.services.common.FileService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientService {

	private final ClientRepository clientRepository;
	private final UserRepository userRepository;
	private final FileService fileService;
	private final ClientBillingEntityRepository billingEntityRepository;
	private final PassengerRepository passengerRepository;

	/* ===================== CREATE ===================== */

	@Transactional
	public Client save(Client client, String orgId) {

		// Email uniqueness (optional field)
		if (client.getEmail() != null && !client.getEmail().equals("")) {
			Client byEmail = clientRepository.findByEmailAndOrgId(client.getEmail(), orgId);

			if (byEmail != null) {
				throw new BusinessException(ErrorCode.CLIENT_ALREADY_EXISTS,
						"Client already registered with this email.");
			}
		}

		// Phone uniqueness (mandatory field)
		Client byPhone = clientRepository.findByPhoneAndOrgId(client.getPhone(), orgId);

		if (byPhone != null) {
			throw new BusinessException(ErrorCode.CLIENT_ALREADY_EXISTS,
					"Client already registered with this mobile number.");
		}
		client.setOrgId(orgId);
		return this.attachData(clientRepository.save(client));
	}

	/* ===================== UPDATE ===================== */

	@Transactional
	public Client update(Client incoming, String orgId) {

		Client local = get(incoming.getId(), orgId);

		// ===================== UPDATE CLIENT FIELDS =====================

		local.setName(incoming.getName());
		local.setEmail(incoming.getEmail());
		local.setPhone(incoming.getPhone());
		local.setAddress(incoming.getAddress());
		local.setSupplier(incoming.getSupplier());

		// ===================== SYNC USER ACCOUNT =====================

		if (local.getUserId() != null) {
			userRepository.findById(local.getUserId()).ifPresent(user -> {
				user.setEmail(incoming.getEmail());
				user.setPhone(incoming.getPhone());
			});
		}

		// Auditing hooks fire here (transactional + dirty entity)
		return this.attachData(clientRepository.save(local));
	}

	/* ===================== FETCH ===================== */

	@Transactional(readOnly = true)
	public Client get(String id, String orgId) {
		Client client = clientRepository.findByIdAndOrgId(id, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.CLIENT_NOT_FOUND, "Client not found with this ID."));

		return this.attachData(client);
	}

	@Transactional(readOnly = true)
	public Client findByUserId(String userId) {
		return this.attachData(this.clientRepository.findByUserId(userId));
	}

	@Transactional(readOnly = true)
	public Client findUser(String str, String orgId) {
		Client client = clientRepository.findByEmailAndOrgId(str, orgId);

		if (client == null) {
			client = clientRepository.findByPhoneAndOrgId(str, orgId);
			if (client == null) {
				throw new NotFoundException(ErrorCode.USER_NOT_FOUND, "User not found.");
			}
		}
		return this.attachData(client);
	}

	@Transactional(readOnly = true)
	public List<Client> getByORG(String orgId) {

		List<Client> clients = clientRepository.findClientsByOrgId(orgId);

		List<Client> result = new ArrayList<>();

		for (Client client : clients) {
			result.add(attachData(client));
		}

		return result;
	}

	@Transactional(readOnly = true)
	public Page<Client> getPage(String orgId, String searchStr, Pageable pageable) {
		Page<Client> page = this.clientRepository.getPage(orgId, searchStr, pageable);
		page.getContent().stream().map(this::attachData).toList();
		return page;
	}

	@Transactional(readOnly = true)
	public List<Client> getSuppliers(String orgId) {
		return this.clientRepository.findByOrgIdAndSupplierTrue(orgId).stream().map(this::attachData).toList();
	}

	/* ===================== PROFILE PIC ===================== */

	@Transactional
	public Client updateProfile(String id, String orgId, MultipartFile file) {

		try {
			Client client = get(id, orgId);
			if (client.getPic() != null) {
				fileService.deleteFile(client.getPic());
			}
			client.setPic(fileService.saveDisplayImage(file));
			return this.attachData(clientRepository.save(client));
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	@Transactional
	public void attachBillingEntityToClient(String orgId, String clientId, String billingEntityId) {
		Client client = this.get(clientId, orgId);

		if (!client.getClientBillingEntityIds().contains(billingEntityId)) {
			client.getClientBillingEntityIds().add(billingEntityId);
			clientRepository.save(client);
		}
	}

	@Transactional
	public void detachBillingEntityFromClient(String orgId, String clientId, String billingEntityId) {
		Client client = this.get(clientId, orgId);

		List<String> ids = client.getClientBillingEntityIds();

		if (ids == null || ids.isEmpty()) {
			return; // nothing to remove
		}

		boolean removed = ids.removeIf(id -> id.equals(billingEntityId));

		if (removed) {
			clientRepository.save(client);
		}
	}

	/* ===================== PASSENGERS ===================== */

	@Transactional
	public Client addPassenger(Passenger passenger, String orgId) {
		passenger.setOrgId(orgId);
		this.passengerRepository.save(passenger);
		return this.get(passenger.getClientId(), orgId);
	}

	/*
	 * P0 IDOR fix -- previously fetched by id only, with no check that the
	 * passenger being edited actually belongs to the caller (passenger.clientId
	 * here is always the CALLER's own id -- see PassengerController.update,
	 * which stamps it before calling this). Any authenticated client could
	 * overwrite another client's passenger name/email/phone by guessing a
	 * passengerId. Same ownership check deletePassenger already applies.
	 */
	@Transactional
	public Client updatePassenger(Passenger passenger, String orgId) {
		Passenger local = this.getPassenger(passenger.getId());

		if (!local.getClientId().equals(passenger.getClientId())) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "Unauthorized passenger access.");
		}

		local.setName(passenger.getName());
		local.setEmail(passenger.getEmail());
		local.setPhone(passenger.getPhone());
		this.passengerRepository.save(local);
		return this.get(passenger.getClientId(), orgId);
	}

	@Transactional
	public void deletePassenger(String clientId, String passengerId) {
		Passenger passenger = getPassenger(passengerId);
		if (!passenger.getClientId().equals(clientId)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED, "Unauthorized passenger access.");
		}
		passengerRepository.delete(passenger);
	}

	@Transactional(readOnly = true)
	public Passenger getPassenger(String passengerId) {
		return this.passengerRepository.findById(passengerId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.PASSENGER_NOT_FOUND, "Passenger Not Found!!"));
	}

	private Client attachData(Client client) {
		if (client.getClientBillingEntityIds() != null && !client.getClientBillingEntityIds().isEmpty()) {
			List<ClientBillingEntity> entities = billingEntityRepository.findByIdIn(client.getClientBillingEntityIds());
			client.setClientBillingEntity(entities);
		}
		client.setPassengers(this.passengerRepository.findByClientId(client.getId()));
		return client;
	}

	/* ===================== Billing Entity ===================== */

	@Transactional
	public ClientBillingEntity saveCorporate(ClientBillingEntity entity, String orgId) {
		ClientBillingEntity local = billingEntityRepository.findByGstinAndOrgId(entity.getGstin(), orgId).orElse(null);
		if (local == null) {
			entity.setOrgId(orgId);
			return this.billingEntityRepository.save(entity);
		}
		throw new BusinessException(ErrorCode.BILLING_ENTITY_ALREADY_EXISTS,
				"Corporate already exsists with this GSTIN.");
	}

	@Transactional
	public ClientBillingEntity updateCorporate(ClientBillingEntity entity, String orgId) {
		ClientBillingEntity local = this.getCorporate(entity.getId(), orgId);
		local.setAddress(entity.getAddress());
		local.setBrandName(entity.getBrandName());
		local.setBusinessType(entity.getBusinessType());
		local.setCin(entity.getCin());
		local.setLegalName(entity.getLegalName());
		local.setEmail(entity.getEmail());
		local.setGstin(entity.getGstin());
		local.setPhone(entity.getPhone());
		local.setAddress(entity.getAddress());

		return this.billingEntityRepository.save(local);
	}

	@Transactional(readOnly = true)
	public ClientBillingEntity getCorporate(String id, String orgId) {
		return this.billingEntityRepository.findByIdAndOrgId(id, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.BILLING_ENTITY_NOT_FOUND,
						"No billing enity found with this ID."));
	}

	@Transactional(readOnly = true)
	public ClientBillingEntity getByGstin(String gstin, String orgId) {
		return billingEntityRepository.findByGstinAndOrgId(gstin, orgId)
				.orElseThrow(() -> new NotFoundException(ErrorCode.BILLING_ENTITY_NOT_FOUND,
						"No billing enity found with this GSTIN."));
	}
}
