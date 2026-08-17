package com.core.controllers;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.dtos.estimate.EstimatePaymentInitResponse;
import com.core.dtos.estimate.EstimatePaymentStatusResponse;
import com.core.dtos.estimate.EstimatePaymentVerifyRequest;
import com.core.dtos.estimate.PublicEstimateDTO;
import com.core.mapper.EstimateAssembler;
import com.core.models.Estimate;
import com.core.services.PublicEstimateService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/estimate-api/client")
@CrossOrigin("*")
@RequiredArgsConstructor
public class PublicEstimateController {

	private final PublicEstimateService publicEstimateService;
	private final EstimateAssembler estimateAssembler;

	@GetMapping("/{token}")
	public PublicEstimateDTO view(@PathVariable String token) {
		Estimate estimate = publicEstimateService.viewEstimate(token);
		return estimateAssembler.assemblePublic(estimate, publicEstimateService.payableNow(estimate));
	}

	@PostMapping("/{token}/payment")
	public EstimatePaymentInitResponse createPayment(@PathVariable String token) throws Exception {
		return publicEstimateService.createPayment(token);
	}

	@PostMapping("/{token}/payment/verify")
	public EstimatePaymentStatusResponse verifyPayment(
			@PathVariable String token,
			@RequestBody EstimatePaymentVerifyRequest request) throws Exception {

		return publicEstimateService.verifyPayment(
				token,
				request.razorpayOrderId(),
				request.razorpayPaymentId(),
				request.razorpaySignature());
	}

	@GetMapping("/{token}/payment-status")
	public EstimatePaymentStatusResponse paymentStatus(@PathVariable String token) {
		return publicEstimateService.getPaymentStatus(token);
	}
}