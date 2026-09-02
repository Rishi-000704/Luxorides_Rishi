package com.core.controllers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.core.exception.BusinessException;
import com.core.models.enums.FileAccessCategory;
import com.core.services.common.FileAccessTokenService;
import com.core.services.common.FileService;

/*
 * Covers Phase P0.2 end-to-end wiring at the controller layer -- proves the
 * token check actually gates file serving (not just that the two services
 * work correctly in isolation, which FileAccessTokenServiceTest/FileServiceTest
 * already cover separately).
 */
class FileControllerTest {

	private FileService fileService;
	private FileAccessTokenService fileAccessTokenService;
	private FileController controller;

	@BeforeEach
	void setUp() {
		fileService = mock(FileService.class);
		fileAccessTokenService = mock(FileAccessTokenService.class);
		controller = new FileController(fileService, fileAccessTokenService);
	}

	@Test
	void getFile_servesFile_whenTokenValid() throws Exception {
		when(fileAccessTokenService.validate("abc.jpg", "good-token")).thenReturn(FileAccessCategory.PRIVATE);
		when(fileService.resolveContentType("abc.jpg")).thenReturn("image/jpeg");
		when(fileService.getFile("abc.jpg")).thenReturn(new ByteArrayInputStream("bytes".getBytes()));

		MockHttpServletResponse response = new MockHttpServletResponse();
		controller.getFile("abc.jpg", "good-token", response);

		assertEqualsBytes("bytes", response.getContentAsByteArray());
	}

	@Test
	void getFile_rejectsRequest_whenTokenMissing() throws Exception {
		when(fileAccessTokenService.validate("abc.jpg", null))
				.thenThrow(new BusinessException(com.core.exception.ErrorCode.ACCESS_DENIED, "File not found"));

		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThrows(BusinessException.class, () -> controller.getFile("abc.jpg", null, response));
		verify(fileService, never()).getFile(any());
	}

	@Test
	void getFile_rejectsRequest_whenTokenInvalid() throws Exception {
		when(fileAccessTokenService.validate(eq("abc.jpg"), eq("bad-token")))
				.thenThrow(new BusinessException(com.core.exception.ErrorCode.ACCESS_DENIED, "File not found"));

		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThrows(BusinessException.class, () -> controller.getFile("abc.jpg", "bad-token", response));
		verify(fileService, never()).getFile(any());
	}

	@Test
	void getFile_setsNoStoreCacheControl_forPrivateCategory() throws Exception {
		when(fileAccessTokenService.validate("abc.jpg", "t")).thenReturn(FileAccessCategory.PRIVATE);
		when(fileService.resolveContentType("abc.jpg")).thenReturn("image/jpeg");
		when(fileService.getFile("abc.jpg")).thenReturn(new ByteArrayInputStream("bytes".getBytes()));

		MockHttpServletResponse response = new MockHttpServletResponse();
		controller.getFile("abc.jpg", "t", response);

		org.junit.jupiter.api.Assertions.assertEquals("private, no-store", response.getHeader("Cache-Control"));
	}

	@Test
	void getFile_setsPublicCacheControl_forPublicCategory() throws Exception {
		when(fileAccessTokenService.validate("abc.jpg", "t")).thenReturn(FileAccessCategory.PUBLIC);
		when(fileService.resolveContentType("abc.jpg")).thenReturn("image/jpeg");
		when(fileService.getFile("abc.jpg")).thenReturn(new ByteArrayInputStream("bytes".getBytes()));

		MockHttpServletResponse response = new MockHttpServletResponse();
		controller.getFile("abc.jpg", "t", response);

		org.junit.jupiter.api.Assertions.assertEquals("public, max-age=3600", response.getHeader("Cache-Control"));
	}

	@Test
	void getFile_validatesBeforeTouchingFileService() throws Exception {
		// Order matters: a request that fails validation must never reach
		// FileService.getFile at all (no filesystem I/O for a rejected request).
		when(fileAccessTokenService.validate("abc.jpg", "bad"))
				.thenThrow(new BusinessException(com.core.exception.ErrorCode.ACCESS_DENIED, "File not found"));

		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThrows(BusinessException.class, () -> controller.getFile("abc.jpg", "bad", response));
		verify(fileService, never()).resolveContentType(any());
		verify(fileService, never()).getFile(any());
	}

	private void assertEqualsBytes(String expected, byte[] actual) {
		org.junit.jupiter.api.Assertions.assertEquals(expected, new String(actual));
	}
}
