-- Resignations: the employee resigns, HR accepts (setting the last working day) or declines,
-- and a nightly job completes the exit once the last working day has passed.
-- Written to run unchanged on MySQL 8.4 (RDS) and H2 in MySQL mode (tests).

CREATE TABLE resignations (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id        BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL,      -- SUBMITTED, ACCEPTED, DECLINED, WITHDRAWN, COMPLETED
    reason             VARCHAR(1000) NOT NULL,
    requested_last_day DATE         NOT NULL,      -- what the employee asked for
    last_working_day   DATE,                       -- what HR agreed, set on acceptance
    submitted_at       DATETIME(6)  NOT NULL,
    decided_by         VARCHAR(120),
    decided_at         DATETIME(6),
    hr_note            VARCHAR(500),
    completed_at       DATETIME(6),
    CONSTRAINT fk_resignations_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_resignations_employee ON resignations (employee_id);
CREATE INDEX idx_resignations_status ON resignations (status);
