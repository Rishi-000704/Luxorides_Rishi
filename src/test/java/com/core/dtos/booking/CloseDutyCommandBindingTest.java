package com.core.dtos.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;

class CloseDutyCommandBindingTest {

	@Test
	void bindsIndexedExtraChargeFromMultipartRequest() {
		MockMultipartFile receipt = new MockMultipartFile(
				"extraCharges[0].imageFile",
				"receipt.png",
				"image/png",
				new byte[] { 1 }
		);

		MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
		request.addParameter("bookingId", "booking-1");
		request.addParameter("dutyId", "duty-1");
		request.addParameter("extraCharges[0].description", "Parking");
		request.addParameter("extraCharges[0].amount", "125.50");
		request.addFile(receipt);

		CloseDutyCommand command = new CloseDutyCommand();
		ServletRequestDataBinder binder = new ServletRequestDataBinder(command);
		binder.bind(request);

		assertFalse(binder.getBindingResult().hasErrors());
		assertEquals(1, command.getExtraCharges().size());
		assertEquals("Parking", command.getExtraCharges().get(0).getDescription());
		assertEquals(new BigDecimal("125.50"), command.getExtraCharges().get(0).getAmount());
		assertSame(receipt, command.getExtraCharges().get(0).getImageFile());
	}
}
