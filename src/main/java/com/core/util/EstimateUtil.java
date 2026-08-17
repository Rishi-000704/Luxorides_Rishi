package com.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.core.models.Estimate;
import com.core.models.EstimateEntry;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;

public final class EstimateUtil {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private EstimateUtil() {
	}

	public static String generateEstimateId() {
		int suffix = ThreadLocalRandom.current().nextInt(100, 1000);
		return "E" + LocalDateTime.now().format(FORMATTER) + suffix;
	}

	public static String generateEstimateEntryId(Estimate estimate, int index) {
		return estimate.getEstimateId() + "-" + index;
	}

	public static String generatePublicToken() {
		byte[] random = new byte[32];
		SECURE_RANDOM.nextBytes(random);
		return "est_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
	}

	public static String sha256(String raw) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(raw.getBytes()));
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to hash token", ex);
		}
	}

	public static Money calculateLineTotal(
			EstimateEntry entry) {

		if (entry == null
				|| entry.getPack() == null
				|| entry.getPack().getBaseFare() == null
				|| entry.getPack()
				.getBaseFare()
				.getAmount() == null) {
			return Money.INR(BigDecimal.ZERO);
		}

		BigDecimal total = entry.getPack()
				.getBaseFare()
				.getAmount()
				.setScale(2, RoundingMode.HALF_UP);

		return Money.INR(total);
	}

	public static Estimate calculateTotals(Estimate estimate, BigDecimal discountAmount, Integer gstRate) {
		List<EstimateEntry> entries = estimate.getEntries();
		BigDecimal subtotal = BigDecimal.ZERO;

		if (entries != null) {
			for (EstimateEntry entry : entries) {
				subtotal = subtotal.add(calculateLineTotal(entry).getAmount());
			}
		}

		subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

		BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount.max(BigDecimal.ZERO);

		if (discount.compareTo(subtotal) > 0) {
			discount = subtotal;
		}

		BigDecimal taxable = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);

		GstSnapshot gstSnapshot = GstSnapshot.of(
				estimate.getGstSnapshot().getGstType(),
				taxable,
				gstRate == null ? 0 : gstRate);

		BigDecimal payable = taxable.add(gstSnapshot.getTotalTax()).setScale(2, RoundingMode.HALF_UP);

		estimate.setSubtotal(Money.INR(subtotal));
		estimate.setDiscount(Money.INR(discount));
		estimate.setTaxableAmount(Money.INR(taxable));
		estimate.setGstSnapshot(gstSnapshot);
		estimate.setEstimatedPayable(Money.INR(payable));

		return estimate;
	}
}