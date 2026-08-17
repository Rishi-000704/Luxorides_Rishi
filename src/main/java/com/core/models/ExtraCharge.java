package com.core.models;

import com.core.models.embedded.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExtraCharge {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, length = 40)
	private String id;

	@Column(nullable = false, length = 100)
	private String description;

	@Embedded
	private Money amount;

	@Column(length = 255)
	private String image;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_entry_id")
	private BookingEntry bookingEntry;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "estimate_entry_id")
	private EstimateEntry estimateEntry;

}
