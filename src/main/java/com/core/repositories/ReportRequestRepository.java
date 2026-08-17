package com.core.repositories;

import java.util.Optional;

import com.core.models.ReportRequest;
import com.core.models.enums.ReportCategory;
import com.core.models.enums.ReportFormat;
import com.core.models.enums.ReportStatus;
import com.core.models.enums.ReportType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRequestRepository extends JpaRepository<ReportRequest, String> {

    Optional<ReportRequest> findByIdAndOrgId(String id, String orgId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM ReportRequest r
            WHERE r.id = :id
              AND r.orgId = :orgId
            """)
    Optional<ReportRequest> findByIdAndOrgIdForUpdate(
            @Param("id") String id,
            @Param("orgId") String orgId
    );

    @Query("""
            SELECT r
            FROM ReportRequest r
            WHERE r.orgId = :orgId
              AND (:status IS NULL OR r.status = :status)
              AND (:category IS NULL OR r.category = :category)
              AND (:reportType IS NULL OR r.reportType = :reportType)
              AND (:format IS NULL OR r.format = :format)
              AND (:searchStr IS NULL OR :searchStr = ''
                    OR LOWER(r.displayName) LIKE LOWER(CONCAT('%', :searchStr, '%'))
                    OR LOWER(r.fileName) LIKE LOWER(CONCAT('%', :searchStr, '%'))
                    OR LOWER(r.originalFileName) LIKE LOWER(CONCAT('%', :searchStr, '%')))
            """)
    Page<ReportRequest> findPage(
            @Param("orgId") String orgId,
            @Param("status") ReportStatus status,
            @Param("category") ReportCategory category,
            @Param("reportType") ReportType reportType,
            @Param("format") ReportFormat format,
            @Param("searchStr") String searchStr,
            Pageable pageable
    );
}
