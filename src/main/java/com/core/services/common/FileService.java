package com.core.services.common;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;

@Service
public class FileService {

	private static final long MAX_IMAGE_SIZE_BYTES =
			10L * 1024L * 1024L;

	private static final long MAX_IMAGE_PIXELS =
			25_000_000L;

	/*
	 * P1.6 -- applies only to saveDisplayImage below (ordinary avatar/catalog
	 * photos: Driver/Client/Employee.pic, MasterVehicle.pic). 1024px is
	 * generous for any current UI use of these (avatars/catalog cards render
	 * far smaller) while staying well clear of "aggressive". Never applied to
	 * saveFile, which every KYC/inspection/odometer/incident/receipt upload
	 * still goes through unchanged, at full original resolution and bytes.
	 */
	private static final int MAX_DISPLAY_DIMENSION_PX = 1024;
	private static final float DISPLAY_JPEG_QUALITY = 0.85f;

	@Value("${filepath}")
	private String path;

	/**
	 * Saves uploaded images after validating their actual binary format.
	 *
	 * Supported formats:
	 * - JPEG
	 * - PNG
	 *
	 * The saved extension is derived from the detected image format rather
	 * than trusting the client-provided filename or content type.
	 */
	public String saveFile(MultipartFile file) throws IOException {

		validateBasicFileRequirements(file);

		String detectedFormat =
				detectAndValidateImageFormat(file);

		String extension = resolveExtension(detectedFormat);

		Path storageDirectory = getStorageDirectory();

		Files.createDirectories(storageDirectory);

		String filename =
				UUID.randomUUID() + extension;

		Path destination = storageDirectory
				.resolve(filename)
				.normalize();

		if (!destination.startsWith(storageDirectory)) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Invalid file storage path."
			);
		}

		try (InputStream inputStream = file.getInputStream()) {
			Files.copy(
					inputStream,
					destination,
					StandardCopyOption.REPLACE_EXISTING
			);
		}

		return filename;
	}

	/**
	 * Saves an ordinary display/avatar image -- driver, client, and employee
	 * profile photos, and vehicle catalog photos -- NOT evidence. Unlike
	 * {@link #saveFile}, which stores every upload byte-for-byte at whatever
	 * resolution the client sent (required for KYC documents and inspection/
	 * odometer/incident photos, where original fidelity may matter later),
	 * this downscales to at most {@link #MAX_DISPLAY_DIMENSION_PX} on the
	 * longer side and always re-encodes as JPEG at
	 * {@link #DISPLAY_JPEG_QUALITY}: these are small, repeatedly-viewed UI
	 * images with no evidentiary requirement to preserve original bytes.
	 *
	 * Runs the exact same format/dimension/corruption validation as
	 * {@link #saveFile} first, so callers get identical rejection behavior
	 * for oversized/invalid/non-JPEG-PNG uploads.
	 */
	public String saveDisplayImage(MultipartFile file) throws IOException {

		validateBasicFileRequirements(file);
		detectAndValidateImageFormat(file);

		BufferedImage source;
		try (InputStream inputStream = file.getInputStream()) {
			source = ImageIO.read(inputStream);
		}

		if (source == null) {
			throw invalidImageException();
		}

		BufferedImage resized = resizeForDisplay(source);

		Path storageDirectory = getStorageDirectory();
		Files.createDirectories(storageDirectory);

		String filename = UUID.randomUUID() + ".jpg";

		Path destination = storageDirectory
				.resolve(filename)
				.normalize();

		if (!destination.startsWith(storageDirectory)) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Invalid file storage path."
			);
		}

		Path tempDestination = storageDirectory
				.resolve(filename + ".tmp")
				.normalize();

		try {
			writeJpeg(resized, tempDestination);
			Files.move(
					tempDestination,
					destination,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
			);
		} finally {
			Files.deleteIfExists(tempDestination);
		}

		return filename;
	}

	/**
	 * Scales {@code source} down to fit within {@link #MAX_DISPLAY_DIMENSION_PX}
	 * on its longer side (never up -- an already-small image is left at its
	 * own size) and flattens it onto an opaque white RGB canvas, since the
	 * JPEG output format has no alpha channel.
	 */
	private BufferedImage resizeForDisplay(BufferedImage source) {
		int width = source.getWidth();
		int height = source.getHeight();

		double scale = Math.min(1.0, (double) MAX_DISPLAY_DIMENSION_PX / Math.max(width, height));
		int targetWidth = Math.max(1, (int) Math.round(width * scale));
		int targetHeight = Math.max(1, (int) Math.round(height * scale));

		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

		Graphics2D g = target.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, targetWidth, targetHeight);
			g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
		} finally {
			g.dispose();
		}

		return target;
	}

	private void writeJpeg(BufferedImage image, Path destination) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

		if (!writers.hasNext()) {
			throw new IllegalStateException("No JPEG writer available.");
		}

		ImageWriter writer = writers.next();

		try (ImageOutputStream ios = ImageIO.createImageOutputStream(destination.toFile())) {
			writer.setOutput(ios);

			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(DISPLAY_JPEG_QUALITY);

			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
		}
	}

	private void validateBasicFileRequirements(
			MultipartFile file
	) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Image file is required."
			);
		}

		if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Image size cannot exceed 10 MB."
			);
		}
	}

	private String detectAndValidateImageFormat(
			MultipartFile file
	) {
		try (
				InputStream inputStream =
						file.getInputStream();

				ImageInputStream imageInputStream =
						ImageIO.createImageInputStream(
								inputStream
						)
		) {
			if (imageInputStream == null) {
				throw invalidImageException();
			}

			Iterator<ImageReader> readers =
					ImageIO.getImageReaders(
							imageInputStream
					);

			if (!readers.hasNext()) {
				throw new BusinessException(
						ErrorCode.BAD_REQUEST,
						"Only JPEG and PNG images are supported."
				);
			}

			ImageReader reader = readers.next();

			try {
				reader.setInput(
						imageInputStream,
						true,
						true
				);

				String format = reader
						.getFormatName()
						.toLowerCase(Locale.ROOT);

				if (!isSupportedFormat(format)) {
					throw new BusinessException(
							ErrorCode.BAD_REQUEST,
							"Only JPEG and PNG images are supported."
					);
				}

				int width = reader.getWidth(0);
				int height = reader.getHeight(0);

				validateImageDimensions(
						width,
						height
				);

				BufferedImage decodedImage =
						reader.read(0);

				if (decodedImage == null) {
					throw invalidImageException();
				}

				return format;

			} finally {
				reader.dispose();
			}

		} catch (BusinessException ex) {
			throw ex;

		} catch (IOException | RuntimeException ex) {
			throw invalidImageException();
		}
	}

	private boolean isSupportedFormat(String format) {
		return "jpeg".equals(format)
				|| "jpg".equals(format)
				|| "png".equals(format);
	}

	private void validateImageDimensions(
			int width,
			int height
	) {
		if (width <= 0 || height <= 0) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Invalid image dimensions."
			);
		}

		long totalPixels = (long) width * height;

		if (totalPixels > MAX_IMAGE_PIXELS) {
			throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Image dimensions are too large."
			);
		}
	}

	private String resolveExtension(String format) {
		return switch (format) {
			case "jpeg", "jpg" -> ".jpg";
			case "png" -> ".png";
			default -> throw new BusinessException(
					ErrorCode.BAD_REQUEST,
					"Only JPEG and PNG images are supported."
			);
		};
	}

	private Path getStorageDirectory() {
		if (path == null || path.isBlank()) {
			throw new IllegalStateException(
					"File storage path is not configured."
			);
		}

		return Path.of(path.trim())
				.toAbsolutePath()
				.normalize();
	}

	private BusinessException invalidImageException() {
		return new BusinessException(
				ErrorCode.BAD_REQUEST,
				"Invalid or corrupted image file."
		);
	}

	/**
	 * Resolves and opens a stored file for {@link com.core.controllers.FileController}.
	 *
	 * Normalizes the requested path against the configured storage directory
	 * and rejects anything that would resolve outside of it (path
	 * traversal, e.g. a filename containing "../"), the same protection
	 * {@link #saveFile} and {@link #resolveFileUri} already apply. A missing
	 * file falls back to the configured default placeholder image rather
	 * than failing -- unchanged pre-existing behaviour. Throws rather than
	 * swallowing the failure so a genuinely broken storage path surfaces as
	 * an error instead of a silent null causing an NPE downstream.
	 */
	public InputStream getFile(String fileName)
			throws FileNotFoundException {

		if (fileName == null || fileName.isBlank()) {
			throw new FileNotFoundException("File not found");
		}

		Path storageDirectory = getStorageDirectory();

		Path requested = storageDirectory
				.resolve(fileName)
				.normalize();

		if (!requested.startsWith(storageDirectory)) {
			throw new FileNotFoundException("File not found");
		}

		if (!Files.isRegularFile(requested)) {
			requested = storageDirectory
					.resolve("default.png")
					.normalize();
		}

		return new FileInputStream(requested.toFile());
	}

	/**
	 * Content type for a stored file, derived from its own extension rather
	 * than assumed -- {@link com.core.controllers.FileController} previously
	 * always set image/jpeg regardless of whether the stored file was
	 * actually a PNG. Only jpg/jpeg/png are ever written by {@link #saveFile},
	 * so those are the only extensions recognized here.
	 */
	public String resolveContentType(String fileName) {
		if (fileName == null) {
			return "image/jpeg";
		}

		String lower = fileName.toLowerCase(Locale.ROOT);

		if (lower.endsWith(".png")) {
			return "image/png";
		}

		return "image/jpeg";
	}

	public boolean deleteFile(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return false;
		}

		String filepath =
				path.trim()
						+ File.separator
						+ fileName;

		File file = new File(filepath);

		if (file.exists()) {
			return file.delete();
		}

		return false;
	}

	public String resolveFileUri(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}

		try {
			File baseDir =
					new File(path.trim())
							.getAbsoluteFile();

			File file =
					new File(
							baseDir,
							fileName.trim()
					).getAbsoluteFile();

			if (!file.getPath().startsWith(
					baseDir.getPath()
							+ File.separator
			)) {
				return null;
			}

			if (!file.exists() || !file.isFile()) {
				return null;
			}

			return file.toURI().toString();

		} catch (Exception ex) {
			return null;
		}
	}
}