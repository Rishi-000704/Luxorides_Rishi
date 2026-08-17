package com.core.dtos.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CloseDutyCommand {
	private String bookingId;
	private String dutyId;

	private Instant startAt;
	private Instant reportingTime;
	private Integer startingKM;
	private Instant endAt;
	private Instant dropTime;
	private Integer closingKM;

	private Boolean nightChargeble;

	// existing filename, useful during reclose
	private String dutySlipImage;

	// new uploaded file
	private MultipartFile dutySlipImageFile;

	private List<ExtraCharge> extraCharges = new ArrayList<>();

	@Getter
	@Setter
	@NoArgsConstructor
	public static class ExtraCharge {
		private String description;
		private BigDecimal amount;

		// existing filename, useful during reclose
		private String image;

		// new uploaded file
		private MultipartFile imageFile;
	}
}
