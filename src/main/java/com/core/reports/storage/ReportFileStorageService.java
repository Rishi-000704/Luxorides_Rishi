package com.core.reports.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

import com.core.config.ReportGenerationProperties;
import com.core.exception.BusinessException;
import com.core.exception.ErrorCode;
import com.core.reports.generation.GeneratedReportFile;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportFileStorageService {

    private final ReportGenerationProperties properties;

    @Value("${filepath}")
    private String basePath;

    public StoredReportFile save(String orgId, GeneratedReportFile generatedFile) {
        if (orgId == null || orgId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Org id is required to save report file");
        }

        Path temporaryFile = null;

        try {
            Path root = rootPath();
            LocalDate today = LocalDate.now(properties.reportZone());
            String extension = extensionOf(generatedFile.originalFileName());

            Path relativeDirectory = Path.of(
                    "reports",
                    sanitizePathPart(orgId),
                    String.valueOf(today.getYear()),
                    String.format("%02d", today.getMonthValue())
            );

            Path absoluteDirectory = root.resolve(relativeDirectory).normalize();
            ensureInsideRoot(root, absoluteDirectory);
            Files.createDirectories(absoluteDirectory);

            String storedFileName = UUID.randomUUID() + extension;
            Path absoluteFile = absoluteDirectory.resolve(storedFileName).normalize();
            ensureInsideRoot(root, absoluteFile);

            temporaryFile = Files.createTempFile(absoluteDirectory, "report-", ".tmp");
            Files.write(temporaryFile, generatedFile.content());
            moveIntoPlace(temporaryFile, absoluteFile);
            temporaryFile = null;

            String relativeFileName = relativeDirectory.resolve(storedFileName)
                    .toString()
                    .replace("\\", "/");

            return new StoredReportFile(
                    relativeFileName,
                    generatedFile.originalFileName(),
                    generatedFile.contentType(),
                    generatedFile.fileSize()
            );
        } catch (IOException ex) {
            throw new BusinessException(
                    ErrorCode.REPORT_FILE_STORAGE_FAILED,
                    "Failed to save generated report file"
            );
        } finally {
            deleteQuietly(temporaryFile);
        }
    }

    public StoredReportResource load(String relativeFileName) {
        if (relativeFileName == null || relativeFileName.isBlank()) {
            throw new BusinessException(ErrorCode.REPORT_FILE_NOT_FOUND, "Report file name is required");
        }

        try {
            Path root = rootPath();
            Path file = root.resolve(relativeFileName.trim()).normalize();
            ensureInsideRoot(root, file);

            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                throw new BusinessException(ErrorCode.REPORT_FILE_NOT_FOUND, "Report file not found");
            }

            return new StoredReportResource(
                    new FileSystemResource(file),
                    Files.size(file)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.REPORT_FILE_NOT_FOUND, "Failed to read report file");
        }
    }

    public void delete(String relativeFileName) {
        if (relativeFileName == null || relativeFileName.isBlank()) {
            return;
        }

        try {
            Path root = rootPath();
            Path file = root.resolve(relativeFileName.trim()).normalize();
            ensureInsideRoot(root, file);
            Files.deleteIfExists(file);
        } catch (BusinessException | IOException ignored) {
            // Best-effort cleanup for files created by an obsolete generation attempt.
        }
    }

    public boolean exists(String relativeFileName) {
        if (relativeFileName == null || relativeFileName.isBlank()) {
            return false;
        }

        try {
            Path root = rootPath();
            Path file = root.resolve(relativeFileName.trim()).normalize();
            ensureInsideRoot(root, file);
            return Files.exists(file) && Files.isRegularFile(file);
        } catch (BusinessException | IOException ex) {
            return false;
        }
    }

    private void moveIntoPlace(Path temporaryFile, Path absoluteFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    absoluteFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path rootPath() throws IOException {
        if (basePath == null || basePath.isBlank()) {
            throw new BusinessException(ErrorCode.REPORT_FILE_STORAGE_FAILED, "File storage path is not configured");
        }

        Path root = Path.of(basePath.trim()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private void ensureInsideRoot(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Invalid report file path");
        }
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index).toLowerCase();
    }

    private String sanitizePathPart(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }

        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Best-effort cleanup of incomplete temporary files.
        }
    }

    public record StoredReportFile(
            String fileName,
            String originalFileName,
            String contentType,
            Long fileSize
    ) {
    }

    public record StoredReportResource(
            Resource resource,
            Long contentLength
    ) {
    }
}
