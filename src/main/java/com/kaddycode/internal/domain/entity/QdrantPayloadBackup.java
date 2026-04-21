package com.kaddycode.internal.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "qdrant_payload_backup", uniqueConstraints = {
        @UniqueConstraint(name = "uq_qdrant_payload_vector_id", columnNames = {"vector_id"})
})
@Getter @Setter
@NoArgsConstructor
public class QdrantPayloadBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vector_id", nullable = false, length = 100)
    private String vectorId;

    @Column(name = "collection", nullable = false, length = 100)
    private String collection;

    @Column(name = "tenant_id")
    private Long tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}