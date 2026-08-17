package com.core.services.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.awt.image.BufferedImage;
import java.net.URLEncoder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import com.core.dtos.common.PdfStream;
import com.core.models.Invoice;
import com.core.models.InvoiceEntry;
import com.core.models.InvoiceExtraCharge;
import com.core.models.Payment;
import com.core.models.PaymentOut;
import com.core.models.PurchaseInvoice;
import com.core.models.embedded.DisplayAddress;
import com.core.models.embedded.GstSnapshot;
import com.core.models.embedded.Money;
import com.core.models.enums.GstType;
import com.core.models.enums.InvoiceStatus;
import com.core.models.enums.PaymentStatus;
import com.core.models.enums.PurchaseInvoiceStatus;
import com.core.util.AmountToWordsUtil;
import com.core.util.RichTextSanitizer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PdfService {

	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal TWO = BigDecimal.valueOf(2);

	private final TemplateEngine templateEngine;
	private final FileService fileService;

	/*
	 * =========================================================
	 * PURCHASE INVOICE PDF
	 * =========================================================
	 */

	public PdfStream generatePurchaseInvoicePdfStream(
			PurchaseInvoice invoice) {

		File pdf = generatePurchaseInvoicePdfFile(invoice);

		try {

			if (pdf == null || !pdf.exists()) {
				throw new IllegalStateException(
						"Purchase invoice PDF file was not generated"
				);
			}

			InputStream inputStream =
					new AutoDeleteFileInputStream(pdf);

			InputStreamResource resource =
					new InputStreamResource(inputStream);

			return new PdfStream(
					resource,
					pdf.length(),
					"purchase-invoice-"
							+ safeFileName(
							invoice.getPurchaseInvoiceNumber()
					)
							+ ".pdf"
			);

		} catch (Exception ex) {

			if (pdf != null && pdf.exists()) {
				pdf.delete();
			}

			throw new IllegalStateException(
					"Failed to stream purchase invoice PDF",
					ex
			);
		}
	}

	private File generatePurchaseInvoicePdfFile(
			PurchaseInvoice invoice) {

		try {

			DateTimeFormatter paymentDateFormatter =
					DateTimeFormatter.ofPattern(
							"dd MMM yyyy, hh:mm a",
							Locale.ENGLISH
					);

			Context ctx = new Context();

			ctx.setVariable(
					"purchaseInvoice",
					invoice
			);

			ctx.setVariable(
					"entries",
					invoice.getEntries() == null
							? List.of()
							: invoice.getEntries()
			);

			ctx.setVariable(
					"orgEntity",
					invoice.getOrgBillingEntity()
			);

			ctx.setVariable(
					"vendor",
					invoice.getVendor()
			);

			ctx.setVariable(
					"vendorBillingEntity",
					invoice.getVendorBillingEntity()
			);

			ctx.setVariable(
					"invoiceCancelled",
					invoice.getStatus()
							== PurchaseInvoiceStatus.CANCELLED
			);

			/*
			 * Set GST amounts and rates.
			 *
			 * This supports both:
			 * 1. GST rate stored directly in GstSnapshot.
			 * 2. Historical data where gstRate is null but tax
			 *    amounts and taxable amount are available.
			 */
			applyGstVariables(
					ctx,
					invoice.getGstSnapshot(),
					invoice.getTaxableAmount()
			);

			Map<String, BigDecimal> completedPayments =
					new LinkedHashMap<>();

			BigDecimal totalPaid = BigDecimal.ZERO;

			if (invoice.getPayments() != null) {

				for (PaymentOut payment : invoice.getPayments()) {

					if (payment.getStatus()
							!= PaymentStatus.CONFIRMED) {
						continue;
					}

					BigDecimal paid =
							moneyAmount(payment.getPaidAmount());

					BigDecimal tds =
							moneyAmount(payment.getTds());

					BigDecimal paymentTotal =
							paid.add(tds);

					totalPaid =
							totalPaid.add(paymentTotal);

					String description =
							"Payment out via "
									+ payment
									.getPaymentMode()
									.name()
									+ (
									payment.getTransactionDate()
											!= null
											? "|on "
											+ paymentDateFormatter
											.format(
													payment
															.getTransactionDate()
															.atZone(
																	ZoneId
																			.systemDefault()
															)
											)
											.toUpperCase()
											: ""
							);

					completedPayments.put(
							description,
							paymentTotal
					);
				}
			}

			BigDecimal grandTotal =
					moneyAmount(invoice.getGrandTotal());

			BigDecimal pendingAmount =
					grandTotal.subtract(totalPaid);

			if (pendingAmount.signum() < 0) {
				pendingAmount = BigDecimal.ZERO;
			}

			ctx.setVariable(
					"payments",
					completedPayments
			);

			ctx.setVariable(
					"totalPaid",
					totalPaid
			);

			ctx.setVariable(
					"pendingAmount",
					pendingAmount
			);

			ctx.setVariable(
					"amountInWords",
					AmountToWordsUtil.convert(grandTotal)
			);

			String html = templateEngine.process(
					"purchase-invoice/purchase-invoice",
					ctx
			);

			Path tempAssetDir =
					Files.createTempDirectory(
							"purchase-invoice-assets-"
					);

			copyClasspathAsset(
					"logo.png",
					tempAssetDir
			);

			copyClasspathAsset(
					"Montserrat-Regular.ttf",
					tempAssetDir
			);

			copyClasspathAsset(
					"Montserrat-SemiBold.ttf",
					tempAssetDir
			);

			copyClasspathAsset(
					"Montserrat-Bold.ttf",
					tempAssetDir
			);

			File pdfFile = File.createTempFile(
					"purchase-invoice-"
							+ safeFileName(
							invoice
									.getPurchaseInvoiceNumber()
					)
							+ "-",
					".pdf"
			);

			try (
					OutputStream outputStream =
							new FileOutputStream(pdfFile)
			) {

				PdfRendererBuilder builder =
						new PdfRendererBuilder();

				builder.withHtmlContent(
						html,
						tempAssetDir.toUri().toString()
				);

				builder.toStream(outputStream);
				builder.useFastMode();
				builder.run();
			}

			return pdfFile;

		} catch (Exception ex) {

			System.err.println(
					"Purchase invoice PDF generation failed: "
							+ ex.getMessage()
			);

			return null;
		}
	}

	/*
	 * =========================================================
	 * SALES INVOICE PDF
	 * =========================================================
	 */

	public PdfStream generateInvoicePdfStream(
			Invoice invoice) {

		File pdf = generateInvoicePdfFile(invoice);

		try {

			if (pdf == null || !pdf.exists()) {
				throw new IllegalStateException(
						"Invoice PDF file was not generated"
				);
			}

			InputStream inputStream =
					new AutoDeleteFileInputStream(pdf);

			InputStreamResource resource =
					new InputStreamResource(inputStream);

			return new PdfStream(
					resource,
					pdf.length(),
					"invoice-"
							+ safeFileName(
							invoice.getInvoiceNumber()
					)
							+ ".pdf"
			);

		} catch (Exception ex) {

			if (pdf != null && pdf.exists()) {
				pdf.delete();
			}

			System.err.println(
					"Failed to stream invoice PDF: "
							+ ex.getMessage()
			);

			return null;
		}
	}

	private File generateInvoicePdfFile(
			Invoice invoice) {

		Path tempAssetDir = null;
		File pdfFile = null;

		try {

			DateTimeFormatter paymentDateFormatter =
					DateTimeFormatter.ofPattern(
							"dd MMM yyyy, hh:mm a",
							Locale.ENGLISH
					);

			Context ctx = new Context();

			var orgEntity =
					invoice.getOrgBillingEntity();

			var client =
					invoice.getClient();

			var billingEntity =
					invoice.getClientBillingEntity();

			ctx.setVariable(
					"invoice",
					invoice
			);

			ctx.setVariable(
					"entries",
					invoice.getEntries() == null
							? List.of()
							: invoice.getEntries()
			);

			ctx.setVariable(
					"orgEntity",
					orgEntity
			);

			ctx.setVariable(
					"client",
					client
			);

			ctx.setVariable(
					"billingEntity",
					billingEntity
			);

			/*
			 * Preformatted addresses ensure that the invoice
			 * template never has to render punctuation around
			 * nullable address components.
			 */
			ctx.setVariable(
					"orgAddress",
					orgEntity == null
							? null
							: formatDisplayAddress(
							orgEntity.getAddress()
					)
			);

			ctx.setVariable(
					"billingAddress",
					billingEntity == null
							? null
							: formatDisplayAddress(
							billingEntity.getAddress()
					)
			);

			ctx.setVariable(
					"clientAddress",
					client == null
							? null
							: formatDisplayAddress(
							client.getAddress()
					)
			);

			/*
			 * Join organization phone numbers only when present,
			 * preventing dangling commas.
			 */
			ctx.setVariable(
					"orgPhones",
					orgEntity == null
							? null
							: joinNonBlank(
							", ",
							orgEntity.getPhone(),
							orgEntity.getAlternatePhone()
					)
			);

			/*
			 * Sales invoice logo comes from the selected
			 * organization billing entity and is resolved using
			 * the existing FileService.
			 */
			ctx.setVariable(
					"invoiceLogoUri",
					orgEntity == null
							? null
							: resolveAttachmentUri(
							orgEntity.getLogo()
					)
			);

			/*
			 * Terms are sanitized when saved and sanitized again
			 * at the PDF rendering boundary before th:utext uses
			 * them.
			 */
			ctx.setVariable(
					"termsAndConditions",
					orgEntity == null
							? null
							: RichTextSanitizer
							.sanitizeInvoiceTerms(
									orgEntity
											.getTermsAndConditions()
							)
			);

			ctx.setVariable(
					"attachments",
					buildInvoiceAttachments(invoice)
			);

			ctx.setVariable(
					"invoiceCancelled",
					invoice.getStatus()
							== InvoiceStatus.CANCELLED
			);

			/*
			 * GST is stored on Invoice.gstSnapshot.
			 *
			 * PackageSnapshot does not contain the GST rate.
			 *
			 * If gstSnapshot.gstRate is null for an older invoice,
			 * the rate is calculated using stored tax and taxable
			 * amounts.
			 */
			applyGstVariables(
					ctx,
					invoice.getGstSnapshot(),
					invoice.getTaxableAmount()
			);

			/*
			 * =====================================================
			 * PAYMENT COMPUTATION
			 * =====================================================
			 *
			 * A list is intentionally used instead of a map.
			 * Multiple payments can have identical presentation
			 * text, so keying by description can silently overwrite
			 * earlier payment rows.
			 */

			List<InvoicePaymentLine> completedPayments =
					new ArrayList<>();

			BigDecimal totalPaid = BigDecimal.ZERO;

			if (invoice.getPayments() != null) {

				List<Payment> payments =
						new ArrayList<>(
								invoice.getPayments()
						);

				/*
				 * JPA does not guarantee ordering for this
				 * relationship. Sort a copy so the PDF remains
				 * deterministic without mutating the entity
				 * collection.
				 */
				payments.sort(
						Comparator
								.comparing(
										Payment::getTransactionDate,
										Comparator.nullsLast(
												Comparator.naturalOrder()
										)
								)
								.thenComparing(
										Payment::getId,
										Comparator.nullsLast(
												Comparator.naturalOrder()
										)
								)
				);

				for (Payment payment : payments) {

					if (payment.getStatus()
							!= PaymentStatus.CONFIRMED) {
						continue;
					}

					BigDecimal received =
							moneyAmount(
									payment.getReceivedAmount()
							);

					BigDecimal tds =
							moneyAmount(payment.getTds());

					BigDecimal paymentTotal =
							received.add(tds);

					totalPaid =
							totalPaid.add(paymentTotal);

					String paymentTitle =
							"Payment received via "
									+ payment
									.getPaymentMode()
									.name();

					String paymentDateLine = null;

					if (payment.getTransactionDate() != null) {

						paymentDateLine =
								"on "
										+ paymentDateFormatter
										.format(
												payment
														.getTransactionDate()
														.atZone(
																ZoneId
																		.systemDefault()
														)
										)
										.toUpperCase(
												Locale.ENGLISH
										);
					}

					completedPayments.add(
							new InvoicePaymentLine(
									paymentTitle,
									paymentDateLine,
									paymentTotal
							)
					);
				}
			}

			BigDecimal grandTotal =
					moneyAmount(invoice.getGrandTotal());

			BigDecimal pendingAmount =
					grandTotal.subtract(totalPaid);

			if (pendingAmount.signum() < 0) {
				pendingAmount = BigDecimal.ZERO;
			}

			ctx.setVariable(
					"payments",
					completedPayments
			);

			ctx.setVariable(
					"totalPaid",
					totalPaid
			);

			ctx.setVariable(
					"pendingAmount",
					pendingAmount
			);

			ctx.setVariable(
					"amountInWords",
					AmountToWordsUtil.convert(grandTotal)
			);

			/*
			 * Prepare temporary PDF assets before rendering the
			 * Thymeleaf template because the dynamic UPI QR image
			 * is generated into this directory.
			 */
			tempAssetDir =
					Files.createTempDirectory(
							"invoice-assets-"
					);

			/*
			 * Generate a UPI QR from the selected organization
			 * billing entity. The payload contains only the payee
			 * UPI ID (pa) and intentionally contains no amount,
			 * invoice number, note, or transaction reference.
			 */
			String upiQrImage =
					orgEntity == null
							? null
							: generateUpiQrCode(
							orgEntity.getUpiId(),
							tempAssetDir
					);

			ctx.setVariable(
					"upiQrImage",
					upiQrImage
			);

			String html = templateEngine.process(
					"invoice/invoice",
					ctx
			);

			/*
			 * The sales invoice logo now comes from
			 * OrgBillingEntity.logo through FileService.
			 *
			 * Keep the old classpath asset disabled.
			 */
			// copyClasspathAsset(
			// 		"logo.png",
			// 		tempAssetDir
			// );


			copyClasspathAsset(
					"Montserrat-Regular.ttf",
					tempAssetDir
			);

			copyClasspathAsset(
					"Montserrat-SemiBold.ttf",
					tempAssetDir
			);

			copyClasspathAsset(
					"Montserrat-Bold.ttf",
					tempAssetDir
			);

			pdfFile = File.createTempFile(
					"invoice-"
							+ safeFileName(
							invoice.getInvoiceNumber()
					)
							+ "-",
					".pdf"
			);

			try (
					OutputStream outputStream =
							new FileOutputStream(pdfFile)
			) {

				PdfRendererBuilder builder =
						new PdfRendererBuilder();

				builder.withHtmlContent(
						html,
						tempAssetDir.toUri().toString()
				);

				builder.toStream(outputStream);
				builder.useFastMode();
				builder.run();
			}

			return pdfFile;

		} catch (Exception ex) {

			if (pdfFile != null && pdfFile.exists()) {
				pdfFile.delete();
			}

			System.err.println(
					"Invoice PDF generation failed: "
							+ ex.getMessage()
			);

			return null;

		} finally {

			/*
			 * The renderer has already embedded the QR and fonts
			 * into the PDF by the time builder.run() returns.
			 * Remove the temporary QR/font asset directory on both
			 * success and failure so generated QR files are never
			 * left behind as junk.
			 */
			deleteDirectoryQuietly(tempAssetDir);
		}
	}

	/*
	 * =========================================================
	 * GST CONTEXT
	 * =========================================================
	 */

	private void applyGstVariables(
			Context ctx,
			GstSnapshot gstSnapshot,
			Money taxableAmount) {

		GstDisplay gstDisplay =
				resolveGstDisplay(
						gstSnapshot,
						taxableAmount
				);

		ctx.setVariable(
				"hasIgst",
				gstDisplay.hasIgst()
		);

		ctx.setVariable(
				"hasCgst",
				gstDisplay.hasCgst()
		);

		ctx.setVariable(
				"hasSgst",
				gstDisplay.hasSgst()
		);

		ctx.setVariable(
				"igstAmount",
				gstDisplay.igstAmount()
		);

		ctx.setVariable(
				"cgstAmount",
				gstDisplay.cgstAmount()
		);

		ctx.setVariable(
				"sgstAmount",
				gstDisplay.sgstAmount()
		);

		/*
		 * These values are always non-null strings.
		 */
		ctx.setVariable(
				"igstRate",
				gstDisplay.igstRate()
		);

		ctx.setVariable(
				"cgstRate",
				gstDisplay.cgstRate()
		);

		ctx.setVariable(
				"sgstRate",
				gstDisplay.sgstRate()
		);
	}

	private GstDisplay resolveGstDisplay(
			GstSnapshot gstSnapshot,
			Money taxableAmount) {

		if (gstSnapshot == null) {
			return GstDisplay.empty();
		}

		BigDecimal igstAmount =
				zeroIfNull(
						gstSnapshot.getIgstAmount()
				);

		BigDecimal cgstAmount =
				zeroIfNull(
						gstSnapshot.getCgstAmount()
				);

		BigDecimal sgstAmount =
				zeroIfNull(
						gstSnapshot.getSgstAmount()
				);

		boolean hasIgst =
				igstAmount.signum() > 0;

		boolean hasCgst =
				cgstAmount.signum() > 0;

		boolean hasSgst =
				sgstAmount.signum() > 0;

		GstType gstType =
				gstSnapshot.getGstType();

		/*
		 * Backward-compatible GST type inference.
		 *
		 * This supports historical rows where gstType may not
		 * have been stored correctly but individual GST amounts
		 * exist.
		 */
		if (gstType == null) {

			if (hasIgst) {

				gstType = GstType.IGST;

			} else if (hasCgst || hasSgst) {

				gstType = GstType.CGST_SGST;

			} else {

				gstType = GstType.EXEMPT;
			}
		}

		BigDecimal totalGstRate =
				resolveTotalGstRate(
						gstSnapshot,
						taxableAmount
				);

		String igstRate = "0";
		String cgstRate = "0";
		String sgstRate = "0";

		if (gstType == GstType.IGST) {

			igstRate =
					formatPercentage(totalGstRate);

		} else if (
				gstType == GstType.CGST_SGST
		) {

			BigDecimal splitRate =
					totalGstRate.divide(
							TWO,
							6,
							RoundingMode.HALF_UP
					);

			cgstRate =
					formatPercentage(splitRate);

			sgstRate =
					formatPercentage(splitRate);
		}

		return new GstDisplay(
				hasIgst,
				hasCgst,
				hasSgst,
				igstAmount,
				cgstAmount,
				sgstAmount,
				igstRate,
				cgstRate,
				sgstRate
		);
	}

	private BigDecimal resolveTotalGstRate(
			GstSnapshot gstSnapshot,
			Money taxableAmount) {

		/*
		 * Primary source: persisted invoice GST rate.
		 */
		if (
				gstSnapshot.getGstRate() != null
						&& gstSnapshot.getGstRate() > 0
		) {

			return BigDecimal.valueOf(
					gstSnapshot.getGstRate()
			);
		}

		/*
		 * Fallback for older invoices where gst_rate is null.
		 */
		BigDecimal taxable =
				moneyAmount(taxableAmount);

		if (taxable.signum() <= 0) {
			return BigDecimal.ZERO;
		}

		BigDecimal totalTax =
				zeroIfNull(
						gstSnapshot.getTotalTax()
				);

		/*
		 * Some historical rows may not have totalTax populated.
		 * In that case, reconstruct it from GST components.
		 */
		if (totalTax.signum() <= 0) {

			totalTax =
					zeroIfNull(
							gstSnapshot.getIgstAmount()
					)
							.add(
									zeroIfNull(
											gstSnapshot
													.getCgstAmount()
									)
							)
							.add(
									zeroIfNull(
											gstSnapshot
													.getSgstAmount()
									)
							);
		}

		if (totalTax.signum() <= 0) {
			return BigDecimal.ZERO;
		}

		return totalTax
				.multiply(ONE_HUNDRED)
				.divide(
						taxable,
						6,
						RoundingMode.HALF_UP
				);
	}

	/*
	 * =========================================================
	 * INVOICE ATTACHMENTS
	 * =========================================================
	 */

	private List<InvoiceAttachment> buildInvoiceAttachments(
			Invoice invoice) {

		List<InvoiceAttachment> attachments =
				new ArrayList<>();

		if (
				invoice.getEntries() == null
						|| invoice.getEntries().isEmpty()
		) {

			return attachments;
		}

		for (InvoiceEntry entry : invoice.getEntries()) {

			String dutyLabel =
					entry.getDutyId() == null
							|| entry.getDutyId().isBlank()
							? "Duty"
							: "Duty " + entry.getDutyId();

			String dutySlipUri =
					resolveAttachmentUri(
							entry.getDutySlipImage()
					);

			if (dutySlipUri != null) {

				attachments.add(
						new InvoiceAttachment(
								"Duty Slip",
								dutyLabel,
								dutySlipUri
						)
				);
			}

			if (
					entry.getCharges() == null
							|| entry.getCharges().isEmpty()
			) {

				continue;
			}

			for (
					InvoiceExtraCharge charge
					: entry.getCharges()
			) {

				String chargeSlipUri =
						resolveAttachmentUri(
								charge.getImage()
						);

				if (chargeSlipUri == null) {
					continue;
				}

				String description =
						charge.getDescription() == null
								|| charge
								.getDescription()
								.isBlank()
								? "Extra Charge"
								: charge
								.getDescription()
								.trim();

				attachments.add(
						new InvoiceAttachment(
								"Extra Charge Slip",
								dutyLabel
										+ " | "
										+ description,
								chargeSlipUri
						)
				);
			}
		}

		return attachments;
	}

	private String resolveAttachmentUri(
			String filename) {

		if (
				filename == null
						|| filename.isBlank()
		) {

			return null;
		}

		return fileService.resolveFileUri(filename);
	}


	/*
	 * =========================================================
	 * UPI QR CODE
	 * =========================================================
	 */

	private String generateUpiQrCode(
			String upiId,
			Path targetDir) {

		if (
				upiId == null
						|| upiId.isBlank()
						|| targetDir == null
		) {
			return null;
		}

		try {

			String paymentUri =
					buildUpiPaymentUri(upiId);

			Map<EncodeHintType, Object> hints =
					new EnumMap<>(EncodeHintType.class);

			hints.put(
					EncodeHintType.CHARACTER_SET,
					StandardCharsets.UTF_8.name()
			);

			hints.put(
					EncodeHintType.ERROR_CORRECTION,
					ErrorCorrectionLevel.M
			);

			hints.put(
					EncodeHintType.MARGIN,
					2
			);

			int qrSize = 360;

			BitMatrix matrix =
					new QRCodeWriter().encode(
							paymentUri,
							BarcodeFormat.QR_CODE,
							qrSize,
							qrSize,
							hints
					);

			BufferedImage image =
					new BufferedImage(
							matrix.getWidth(),
							matrix.getHeight(),
							BufferedImage.TYPE_INT_RGB
					);

			for (
					int y = 0;
					y < matrix.getHeight();
					y++
			) {

				for (
						int x = 0;
						x < matrix.getWidth();
						x++
				) {

					image.setRGB(
							x,
							y,
							matrix.get(x, y)
									? 0xFF000000
									: 0xFFFFFFFF
					);
				}
			}

			Path qrFile =
					targetDir.resolve(
							"upi-qr.png"
					);

			boolean written =
					ImageIO.write(
							image,
							"PNG",
							qrFile.toFile()
					);

			if (!written) {
				throw new IllegalStateException(
						"PNG writer is not available."
				);
			}

			/*
			 * Return only the filename. withHtmlContent(...) uses
			 * tempAssetDir as its base URI, so OpenHTMLToPDF
			 * resolves this relative path from that directory.
			 */
			return qrFile
					.getFileName()
					.toString();

		} catch (Exception ex) {

			/*
			 * QR is supplementary. Do not fail invoice generation
			 * just because QR generation failed. Do not log the
			 * actual UPI ID.
			 */
			System.err.println(
					"UPI QR generation failed: "
							+ ex.getClass().getSimpleName()
			);

			return null;
		}
	}

	private String buildUpiPaymentUri(
			String upiId) {

		String encodedUpiId =
				URLEncoder.encode(
						upiId.trim(),
						StandardCharsets.UTF_8
				);

		return "upi://pay?pa="
				+ encodedUpiId;
	}

	private void deleteDirectoryQuietly(
			Path directory) {

		if (directory == null) {
			return;
		}

		try (
				var paths = Files.walk(directory)
		) {

			paths
					.sorted(Comparator.reverseOrder())
					.forEach(path -> {

						try {

							Files.deleteIfExists(path);

						} catch (Exception ignored) {

							/*
							 * Best-effort cleanup. Do not fail an
							 * otherwise valid invoice response.
							 */
						}
					});

		} catch (Exception ignored) {

			/*
			 * Best-effort cleanup.
			 */
		}
	}

	/*
	 * =========================================================
	 * ASSET HANDLING
	 * =========================================================
	 */

	private void copyClasspathAsset(
			String filename,
			Path targetDir) throws Exception {

		ClassPathResource resource =
				new ClassPathResource(
						"templates/invoice/assets/"
								+ filename
				);

		if (!resource.exists()) {

			System.err.println(
					"Missing invoice asset: "
							+ filename
			);
		}

		Path targetFile =
				targetDir.resolve(filename);

		try (
				InputStream inputStream =
						resource.getInputStream()
		) {

			Files.copy(
					inputStream,
					targetFile
			);
		}
	}

	/*
	 * =========================================================
	 * VALUE HELPERS
	 * =========================================================
	 */

	private String formatDisplayAddress(
			DisplayAddress address) {

		if (address == null) {
			return null;
		}

		return joinNonBlank(
				", ",
				address.getFormattedAddress(),
				address.getCity(),
				address.getState(),
				address.getPincode(),
				address.getCountryCode()
		);
	}

	private String joinNonBlank(
			String delimiter,
			String... values) {

		if (values == null || values.length == 0) {
			return null;
		}

		List<String> parts =
				new ArrayList<>();

		for (String value : values) {

			if (
					value == null
							|| value.isBlank()
			) {
				continue;
			}

			parts.add(value.trim());
		}

		if (parts.isEmpty()) {
			return null;
		}

		return String.join(
				delimiter,
				parts
		);
	}

	private BigDecimal moneyAmount(
			Money money) {

		if (
				money == null
						|| money.getAmount() == null
		) {

			return BigDecimal.ZERO;
		}

		return money.getAmount();
	}

	private BigDecimal zeroIfNull(
			BigDecimal value) {

		return value == null
				? BigDecimal.ZERO
				: value;
	}

	private String formatPercentage(
			BigDecimal rate) {

		if (rate == null) {
			return "0";
		}

		return rate
				.setScale(
						4,
						RoundingMode.HALF_UP
				)
				.stripTrailingZeros()
				.toPlainString();
	}

	private String safeFileName(
			String value) {

		if (
				value == null
						|| value.isBlank()
		) {

			return "invoice";
		}

		return value
				.trim()
				.replaceAll(
						"[^a-zA-Z0-9._-]",
						"-"
				);
	}

	/*
	 * =========================================================
	 * INTERNAL PDF DATA
	 * =========================================================
	 */

	private record GstDisplay(
			boolean hasIgst,
			boolean hasCgst,
			boolean hasSgst,
			BigDecimal igstAmount,
			BigDecimal cgstAmount,
			BigDecimal sgstAmount,
			String igstRate,
			String cgstRate,
			String sgstRate
	) {

		private static GstDisplay empty() {

			return new GstDisplay(
					false,
					false,
					false,
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					"0",
					"0",
					"0"
			);
		}
	}

	public static final class InvoicePaymentLine {

		private final String title;
		private final String dateLine;
		private final BigDecimal amount;

		public InvoicePaymentLine(
				String title,
				String dateLine,
				BigDecimal amount) {

			this.title = title;
			this.dateLine = dateLine;
			this.amount = amount;
		}

		public String getTitle() {
			return title;
		}

		public String getDateLine() {
			return dateLine;
		}

		public BigDecimal getAmount() {
			return amount;
		}
	}

	public record InvoiceAttachment(
			String title,
			String subtitle,
			String imageUri
	) {
	}

	/*
	 * =========================================================
	 * AUTO DELETE GENERATED PDF
	 * =========================================================
	 */

	private static class AutoDeleteFileInputStream
			extends FileInputStream {

		private final File file;

		public AutoDeleteFileInputStream(
				File file) throws Exception {

			super(file);
			this.file = file;
		}

		@Override
		public void close()
				throws java.io.IOException {

			try {

				super.close();

			} finally {

				if (
						file.exists()
								&& !file.delete()
				) {

					file.deleteOnExit();
				}
			}
		}
	}
}