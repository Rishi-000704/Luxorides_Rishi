package com.core.dtos.common;

import org.springframework.core.io.InputStreamResource;

public record PdfStream(InputStreamResource resource, long contentLength, String fileName) {
}
