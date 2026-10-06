package com.hrportal.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** The record of one stored file. The bytes are in document storage under {@link #documentKey}. */
@Entity
@Table(name = "employee_documents")
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 40)
    private DocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "file_name", nullable = false, length = 150)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JsonIgnore
    @Column(name = "document_key", nullable = false, length = 300)
    private String documentKey;

    @Column(name = "uploaded_by", nullable = false, length = 120)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    public EmployeeDocument() {
    }

    public EmployeeDocument(Employee employee, DocumentType type, DocumentStatus status, String fileName,
                            String contentType, long sizeBytes, String documentKey, String uploadedBy,
                            LocalDateTime uploadedAt) {
        this.employee = employee;
        this.type = type;
        this.status = status;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.documentKey = documentKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public void review(DocumentStatus decision, String by, LocalDateTime at, String note) {
        this.status = decision;
        this.reviewedBy = by;
        this.reviewedAt = at;
        this.reviewNote = note;
    }

    public Long getEmployeeId() {
        return employee.getId();
    }

    public String getEmployeeName() {
        return employee.getFullName();
    }

    /** "240 KB", "1.3 MB". */
    public String getSizeLabel() {
        return sizeBytes < 1024 * 1024
                ? Math.max(1, Math.round(sizeBytes / 1024.0)) + " KB"
                : String.format(java.util.Locale.ENGLISH, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public DocumentType getType() {
        return type;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getDocumentKey() {
        return documentKey;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }
}
