package com.core.reports.generation;

import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;

public record GeneratedReportFile(
        byte[] content,
        String originalFileName,
        String contentType
) {
    public GeneratedReportFile {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.REPORT_GENERATION_FAILED, "Generated report file is empty");
        }

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.REPORT_GENERATION_FAILED, "Generated report file name is required");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new BusinessException(ErrorCode.REPORT_GENERATION_FAILED, "Generated report content type is required");
        }
    }

    public long fileSize() {
        return content.length;
    }
}
