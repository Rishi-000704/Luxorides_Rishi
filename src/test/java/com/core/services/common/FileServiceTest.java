package com.core.services.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/*
 * Covers Phase -1.1 Checkpoint B's confirmed, backend-only-fixable finding:
 * FileService.getFile previously had no path-normalization/base-directory
 * check at all (unlike saveFile and resolveFileUri, which both already had
 * one) -- a real path-traversal gap, now closed with the same pattern its
 * siblings already use. Also covers the hardcoded-content-type fix.
 *
 * Deliberately does NOT test authorization/ownership/cross-org scoping --
 * per the Checkpoint B investigation, /file/** is relied on by plain <img>
 * tags across every frontend with no Authorization header, so adding a
 * Bearer-auth requirement here would break existing image rendering
 * app-wide. That gap is reported, not silently left untested here.
 */
class FileServiceTest {

	private FileService fileService;

	@TempDir
	Path storageDir;

	@BeforeEach
	void setUp() throws Exception {
		fileService = new FileService();
		Field pathField = FileService.class.getDeclaredField("path");
		pathField.setAccessible(true);
		pathField.set(fileService, storageDir.toString());
	}

	@Test
	void getFile_servesExistingFile() throws Exception {
		Path real = storageDir.resolve("abc123.jpg");
		Files.writeString(real, "fake-jpeg-bytes");

		try (InputStream is = fileService.getFile("abc123.jpg")) {
			assertEquals("fake-jpeg-bytes", new String(is.readAllBytes()));
		}
	}

	@Test
	void getFile_fallsBackToDefault_whenFileMissing() throws Exception {
		Path defaultFile = storageDir.resolve("default.png");
		Files.writeString(defaultFile, "placeholder-bytes");

		try (InputStream is = fileService.getFile("does-not-exist.jpg")) {
			assertEquals("placeholder-bytes", new String(is.readAllBytes()));
		}
	}

	@Test
	void getFile_rejectsPathTraversal_dotDotSegments() {
		assertThrows(FileNotFoundException.class,
				() -> fileService.getFile("../../../../etc/passwd"));
	}

	@Test
	void getFile_rejectsPathTraversal_absolutePathEscape() {
		assertThrows(FileNotFoundException.class,
				() -> fileService.getFile("../outside-storage-dir/secret.txt"));
	}

	@Test
	void getFile_rejectsBlankFilename() {
		assertThrows(FileNotFoundException.class, () -> fileService.getFile(""));
		assertThrows(FileNotFoundException.class, () -> fileService.getFile(null));
	}

	/*
	 * P1.6 -- saveDisplayImage covers driver/client/employee profile photos
	 * and vehicle catalog photos only (see FileService, DriverService,
	 * ClientService, MasterVehicleService, AuthenticationService.updatePic).
	 * KYC/inspection/odometer/incident uploads all still go through saveFile
	 * above, completely unchanged -- not covered by any test here.
	 */
	private byte[] pngBytes(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private int[] readDimensions(Path file) throws Exception {
		BufferedImage image = ImageIO.read(file.toFile());
		return new int[] { image.getWidth(), image.getHeight() };
	}

	@Test
	void saveDisplayImage_downscalesOversizedImage_toMaxDisplayDimension() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", pngBytes(2000, 1000));

		String savedName = fileService.saveDisplayImage(file);

		assertTrue(savedName.endsWith(".jpg"), "display images are always saved as .jpg");
		int[] dims = readDimensions(storageDir.resolve(savedName));
		assertEquals(1024, dims[0], "longer side capped at 1024");
		assertEquals(512, dims[1], "aspect ratio preserved (2000x1000 -> 1024x512)");
	}

	@Test
	void saveDisplayImage_leavesAlreadySmallImage_atItsOwnDimensions() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "small.png", "image/png", pngBytes(200, 100));

		String savedName = fileService.saveDisplayImage(file);

		int[] dims = readDimensions(storageDir.resolve(savedName));
		assertEquals(200, dims[0], "small image is never upscaled");
		assertEquals(100, dims[1]);
	}

	@Test
	void saveDisplayImage_alwaysSavesAsJpeg_evenForPngInput() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes(300, 300));

		String savedName = fileService.saveDisplayImage(file);

		assertTrue(savedName.endsWith(".jpg"));

		try (javax.imageio.stream.ImageInputStream iis =
				ImageIO.createImageInputStream(storageDir.resolve(savedName).toFile())) {
			var reader = ImageIO.getImageReaders(iis).next();
			try {
				reader.setInput(iis);
				assertEquals("jpeg", reader.getFormatName().toLowerCase(java.util.Locale.ROOT));
			} finally {
				reader.dispose();
			}
		}
	}

	@Test
	void saveDisplayImage_leavesNoTempFileBehind_afterASuccessfulSave() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes(300, 300));

		String savedName = fileService.saveDisplayImage(file);

		try (Stream<Path> entries = Files.list(storageDir)) {
			assertTrue(entries.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
					"no .tmp file should remain after a successful saveDisplayImage");
		}
		assertTrue(Files.exists(storageDir.resolve(savedName)));
	}

	@Test
	void saveDisplayImage_rejectsOversizedFile_sameAsSaveFile() {
		MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", new byte[11 * 1024 * 1024]);

		assertThrows(Exception.class, () -> fileService.saveDisplayImage(file));
	}

	@Test
	void saveDisplayImage_rejectsNonImageContent_sameAsSaveFile() {
		MockMultipartFile file = new MockMultipartFile("file", "not-an-image.txt", "text/plain", "hello world".getBytes());

		assertThrows(Exception.class, () -> fileService.saveDisplayImage(file));
	}

	@Test
	void resolveContentType_pngExtension_returnsImagePng() {
		assertEquals("image/png", fileService.resolveContentType("some-file.png"));
		assertEquals("image/png", fileService.resolveContentType("SOME-FILE.PNG"));
	}

	@Test
	void resolveContentType_jpgExtension_returnsImageJpeg() {
		assertEquals("image/jpeg", fileService.resolveContentType("some-file.jpg"));
		assertEquals("image/jpeg", fileService.resolveContentType("some-file.jpeg"));
	}

	@Test
	void resolveContentType_nullFilename_defaultsToJpeg() {
		assertEquals("image/jpeg", fileService.resolveContentType(null));
	}

	@Test
	void getFile_traversalAttemptNeverEscapesConfiguredDirectory() throws Exception {
		// Sibling directory to the configured storage dir, containing a file
		// that must never be reachable through /file/{filename}.
		Path sibling = storageDir.resolveSibling(storageDir.getFileName() + "-sibling");
		Files.createDirectories(sibling);
		Files.writeString(sibling.resolve("secret.txt"), "top-secret");
		Path defaultFile = storageDir.resolve("default.png");
		Files.writeString(defaultFile, "placeholder-bytes");

		try {
			// Even if this resolves (falls back to default.png) rather than
			// throwing, it must never return the sibling directory's content.
			try (InputStream is = fileService.getFile("../" + sibling.getFileName() + "/secret.txt")) {
				String content = new String(is.readAllBytes());
				assertTrue(content.equals("placeholder-bytes"),
						"Path traversal must not leak content outside the storage directory");
			}
		} catch (FileNotFoundException expected) {
			// Also an acceptable, safe outcome.
		}
	}
}
