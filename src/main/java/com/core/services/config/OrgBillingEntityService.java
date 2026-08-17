package com.core.services.config;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.core.dtos.config.BillingEntityRequest;
import com.core.exception.ErrorCode;
import com.core.exception.NotFoundException;
import com.core.models.OrgBillingEntity;
import com.core.repositories.OrgBillingEntityRepository;
import com.core.services.common.FileService;
import com.core.util.AddressUtil;
import com.core.util.RichTextSanitizer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrgBillingEntityService {

	private final OrgBillingEntityRepository entityRepo;
	private final FileService fileService;

	/*
	 * Existing JSON API.
	 * No logo upload.
	 */
	@Transactional
	public OrgBillingEntity add(
			String orgId,
			BillingEntityRequest request
	) {
		return add(orgId, request, null);
	}

	/*
	 * Multipart API with optional logo.
	 */
	@Transactional
	public OrgBillingEntity add(
			String orgId,
			BillingEntityRequest request,
			MultipartFile logo
	) {

		OrgBillingEntity entity = new OrgBillingEntity();

		applyRequest(
				entity,
				orgId,
				request,
				false
		);

		String newLogo = saveLogoIfPresent(logo);

		if (newLogo != null) {

			entity.setLogo(newLogo);

			registerLogoFileLifecycle(
					newLogo,
					null
			);
		}

		return entityRepo.save(entity);
	}

	/*
	 * Existing JSON API.
	 *
	 * Existing logo is preserved because no replacement
	 * file was supplied.
	 */
	@Transactional
	public OrgBillingEntity update(
			String orgId,
			BillingEntityRequest request
	) {
		return update(orgId, request, null);
	}

	/*
	 * Multipart edit with optional replacement logo.
	 */
	@Transactional
	public OrgBillingEntity update(
			String orgId,
			BillingEntityRequest request,
			MultipartFile logo
	) {

		OrgBillingEntity entity =
				get(orgId, request.id());

		applyRequest(
				entity,
				orgId,
				request,
				true
		);

		String newLogo = saveLogoIfPresent(logo);

		if (newLogo != null) {

			String oldLogo = entity.getLogo();

			entity.setLogo(newLogo);

			registerLogoFileLifecycle(
					newLogo,
					oldLogo
			);
		}

		return entityRepo.save(entity);
	}

	private void applyRequest(
			OrgBillingEntity entity,
			String orgId,
			BillingEntityRequest request,
			boolean updating
	) {

		entity.setOrgId(orgId);

		entity.setAccountName(
				request.accountName()
		);

		entity.setAccountNumber(
				request.accountNumber()
		);

		entity.setAddress(
				AddressUtil.toDisplayAddress(
						request.address()
				)
		);

		entity.setAlternatePhone(
				request.alternatePhone()
		);

		entity.setBankName(
				request.bankName()
		);

		entity.setBrandName(
				request.brandName()
		);

		entity.setBusinessType(
				request.businessType()
		);

		entity.setCin(
				request.cin()
		);

		entity.setEmail(
				request.email()
		);

		entity.setGstin(
				request.gstin()
		);

		entity.setGstRate(
				request.gstRate()
		);

		entity.setIfsc(
				request.ifsc()
		);

		entity.setLegalName(
				request.legalName()
		);

		entity.setPhone(
				request.phone()
		);

		entity.setUpiId(
				request.upiId()
		);

		/*
		 * On update:
		 *
		 * null = field omitted by an older frontend, preserve
		 *        existing value.
		 *
		 * ""   = explicitly clear the current terms.
		 */
		if (!updating || request.termsAndConditions() != null) {

			entity.setTermsAndConditions(
					RichTextSanitizer.sanitizeInvoiceTerms(
							request.termsAndConditions()
					)
			);
		}
	}

	private String saveLogoIfPresent(
			MultipartFile logo
	) {

		if (logo == null || logo.isEmpty()) {
			return null;
		}

		try {

			return fileService.saveFile(logo);

		} catch (IOException ex) {

			throw new IllegalStateException(
					"Failed to store billing entity logo.",
					ex
			);
		}
	}

	/*
	 * Filesystem and database do not share one transaction.
	 *
	 * Therefore:
	 *
	 * - delete the previous logo only after DB commit;
	 * - delete the newly uploaded logo if DB transaction rolls back.
	 */
	private void registerLogoFileLifecycle(
			String newLogo,
			String oldLogo
	) {

		if (!TransactionSynchronizationManager
				.isSynchronizationActive()) {
			return;
		}

		TransactionSynchronizationManager
				.registerSynchronization(
						new TransactionSynchronization() {

							@Override
							public void afterCommit() {

								if (
										oldLogo != null
												&& !oldLogo.isBlank()
												&& !oldLogo.equals(newLogo)
								) {
									fileService.deleteFile(
											oldLogo
									);
								}
							}

							@Override
							public void afterCompletion(
									int status
							) {

								if (
										status
												!= TransactionSynchronization.STATUS_COMMITTED
												&& newLogo != null
												&& !newLogo.isBlank()
								) {
									fileService.deleteFile(
											newLogo
									);
								}
							}
						}
				);
	}

	@Transactional(readOnly = true)
	public OrgBillingEntity get(
			String orgId,
			String billingEntityId
	) {

		return entityRepo
				.findByIdAndOrgId(
						billingEntityId,
						orgId
				)
				.orElseThrow(
						() -> new NotFoundException(
								ErrorCode.BILLING_ENTITY_NOT_FOUND,
								"Billing entity not found with this id."
						)
				);
	}

	@Transactional(readOnly = true)
	public List<OrgBillingEntity> getList(
			String orgId
	) {
		return entityRepo.findByOrgId(orgId);
	}
}