-- Employee documents: proofs the employee uploads for HR to verify, and letters HR issues to
-- the employee. The file itself is in S3 (local folder outside AWS); this row is its record.
-- Written to run unchanged on MySQL 8.4 (RDS) and H2 in MySQL mode (tests).

CREATE TABLE employee_documents (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id   BIGINT       NOT NULL,
    doc_type      VARCHAR(40)  NOT NULL,          -- DocumentType, e.g. PAN_CARD, OFFER_LETTER
    status        VARCHAR(20)  NOT NULL,          -- PENDING, VERIFIED, REJECTED, ISSUED
    file_name     VARCHAR(150) NOT NULL,          -- the uploaded name, cleaned, for display only
    content_type  VARCHAR(60)  NOT NULL,          -- detected from the file's bytes, not the browser
    size_bytes    BIGINT       NOT NULL,
    document_key  VARCHAR(300) NOT NULL,          -- documents/{employeeCode}/{random}.pdf
    uploaded_by   VARCHAR(120) NOT NULL,
    uploaded_at   DATETIME(6)  NOT NULL,
    reviewed_by   VARCHAR(120),
    reviewed_at   DATETIME(6),
    review_note   VARCHAR(500),
    CONSTRAINT uk_employee_documents_key UNIQUE (document_key),
    CONSTRAINT fk_employee_documents_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_employee_documents_employee ON employee_documents (employee_id);
CREATE INDEX idx_employee_documents_status ON employee_documents (status);
