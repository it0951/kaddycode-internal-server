package com.kaddycode.internal.repository;

import com.kaddycode.internal.domain.entity.QdrantPayloadBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QdrantPayloadBackupRepository extends JpaRepository<QdrantPayloadBackup, Long> {

    List<QdrantPayloadBackup> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<QdrantPayloadBackup> findByCollection(String collection);
    void deleteByTenantIdAndCollection(Long tenantId, String collection);

    @Query(
            value = "SELECT COUNT(*) FROM qdrant_payload_backup WHERE payload::text LIKE CONCAT('%qa://', CAST(:tenantId AS text), '/%') AND payload::text LIKE '%language%qa%'",
            nativeQuery = true
    )
    long countQaVectorsByTenantId(@Param("collection") String collection,
                                  @Param("tenantId") Long tenantId);
}