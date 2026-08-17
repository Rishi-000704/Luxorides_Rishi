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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
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