package com.core.models;

import java.util.List;

import com.core.models.embedded.AuditableEntity;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.Name;
import com.core.util.PhoneNumberNormalizer;
import com.core.validation.ValidPhone;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.BatchSize;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * P1.1 -- indexes added against verified repository query evidence. See
 * ClientRepository / actual callers:
 *   idx_client_user         : findByUserId -- resolves the authenticated
 *                             client on nearly every customer-app request
 *                             (ClientBookingController, ProfileController,
 *                             PassengerController, NotificationController,
 *                             SupportTicketController, BillingEntityController,
 *                             VehicleCatalogController -- dozens of call
 *                             sites). The single hottest query in this whole
 *                             audit.
 *   idx_client_phone_org    : findByPhoneAndOrgId (login/OTP flow, duplicate
 *                             check in ClientService). phone leads here
 *                             (not org_id) because org-alone lookups are
 *                             already covered by idx_client_org_supplier
 *                             below, freeing this index to be ordered for
 *                             the login lookup's own best selectivity.
 *   idx_client_org_supplier : findByOrgId / getPage / findClientsByOrgId
 *                             (org_id prefix -- the ops app client list) and
 *                             findByOrgIdAndSupplierTrue (full composite --
 *                             the dedicated vendor/supplier picker).
 * findByIdAndOrgId is not indexed separately: id is already the primary key.
 */
/*
 * Phase A -- @BatchSize on the class batches every lazy @ManyToOne load of
 * a Client proxy (Booking.client among others) across the current
 * persistence context into one "WHERE id IN (...)" query instead of one
 * per row. This is the only valid place for it: Hibernate rejects
 * @BatchSize on a to-one association field directly.
 */
@Entity
@BatchSize(size = 100)
@Table(name = "client", indexes = {
		@Index(name = "idx_client_user", columnList = "user_id"),
		@Index(name = "idx_client_phone_org", columnList = "phone, org_id"),
		@Index(name = "idx_client_org_supplier", columnList = "org_id, supplier")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Client extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false)
	private String id;

	@Column(nullable = false, length = 40)
	private String orgId;
	
	@Column(length = 40)
	private String userId;

	@Embedded
	private Name name;

	@Column(length = 100)
	private String email;

	@NotBlank
	@ValidPhone
	@Column(length = 15, nullable = false)
	private String phone;

	@Embedded
	private DisplayAddress address;

	private String pic;

	private Boolean supplier = false;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "client_billing_entities",
		joinColumns = @JoinColumn(name = "client_id"))
	@Column(name = "billing_entity_id")
	private List<String> clientBillingEntityIds;


	transient List<ClientBillingEntity> clientBillingEntity;
	transient List<Passenger> passengers;
	
	   @PrePersist
	    @PreUpdate
	    private void normalizePhones() {

	        // Required phone
	        this.phone = PhoneNumberNormalizer.normalize(this.phone);
	    }
}