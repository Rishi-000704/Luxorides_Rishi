package com.core.services.common;

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

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

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

	public InputStream getFile(String fileName)
			throws FileNotFoundException {

		try {
			String filepath =
					path.trim()
							+ File.separator
							+ fileName;

			File file = new File(filepath);

			if (!file.exists()) {
				String defaultFilePath =
						path.trim()
								+ File.separator
								+ "default.png";

				file = new File(defaultFilePath);
			}

			return new FileInputStream(file);

		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
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