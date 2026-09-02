package com.core.controllers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.core.models.enums.FileAccessCategory;
import com.core.services.common.FileAccessTokenService;
import com.core.services.common.FileService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/file")
public class FileController {
	private final FileService fileService;
	private final FileAccessTokenService fileAccessTokenService;

	public FileController(FileService fileService, FileAccessTokenService fileAccessTokenService) {
		this.fileService = fileService;
		this.fileAccessTokenService = fileAccessTokenService;
	}

//	@PostMapping
//	public String saveFile(@RequestPart MultipartFile file) throws IOException {
//		return this.fileService.saveFile(file);
//	}
//
//	@DeleteMapping("/{filename}")
//	public boolean deleteFile(@PathVariable String filename) {
//		return this.fileService.deleteFile(filename);
//	}

	/*
	 * /file/** is globally permitAll in SecurityConfiguration (existing
	 * frontends load images via plain <img src>, no Authorization header --
	 * see the Phase P0.2 report). The `token` query param is what actually
	 * authorizes this specific request for this specific filename -- every
	 * DTO/assembler that exposes a filename now runs it through
	 * FileAccessTokenService.toAccessUrl first, so a caller can only ever
	 * obtain a valid token for a file they were already authorized to see.
	 */
	@GetMapping("/{filename}")
	public void getFile(
			@PathVariable String filename,
			@RequestParam(required = false) String token,
			HttpServletResponse response
	) throws FileNotFoundException, IOException {

		FileAccessCategory category = fileAccessTokenService.validate(filename, token);

		response.setHeader("Cache-Control", category == FileAccessCategory.PUBLIC
				? "public, max-age=3600"
				: "private, no-store");
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setContentType(fileService.resolveContentType(filename));

		InputStream is = this.fileService.getFile(filename);
		StreamUtils.copy(is, response.getOutputStream());
		is.close();
		response.getOutputStream().flush();
	}
}
