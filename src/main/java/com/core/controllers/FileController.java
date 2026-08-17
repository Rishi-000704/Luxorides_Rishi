package com.core.controllers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.core.services.common.FileService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/file")
public class FileController {
	private FileService fileService;

	public FileController(FileService fileService) {
		super();
		this.fileService = fileService;
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

	@GetMapping("/{filename}")
	public void getFile(@PathVariable String filename, HttpServletResponse response)
			throws FileNotFoundException, IOException {
		response.setContentType(MediaType.IMAGE_JPEG_VALUE);
		InputStream is = this.fileService.getFile(filename);
		StreamUtils.copy(is, response.getOutputStream());
		is.close();
		response.getOutputStream().flush();
	}
}
