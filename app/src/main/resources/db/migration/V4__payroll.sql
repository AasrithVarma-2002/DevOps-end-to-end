-- Stage 3: monthly payroll. A run is one month for the whole company; each payslip keeps
-- the figures as they were calculated, so later salary changes don't rewrite history.
-- Written to run unchanged on MySQL 8.4 (RDS) and H2 in MySQL mode (tests).

CREATE TABLE payroll_runs (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pay_month    VARCHAR(7)   NOT NULL,          -- "2026-03"
    status       VARCHAR(20)  NOT NULL,          -- DRAFT, FINALIZED
    created_by   VARCHAR(120) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    finalized_by VARCHAR(120),
    finalized_at DATETIME(6),
    CONSTRAINT uk_payroll_runs_month UNIQUE (pay_month)
);

CREATE TABLE payslips (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id            BIGINT         NOT NULL,
    employee_id       BIGINT         NOT NULL,
    monthly_salary    DECIMAL(12, 2) NOT NULL,   -- the employee's salary when calculated
    days_in_month     INT            NOT NULL,
    paid_days         DECIMAL(5, 1)  NOT NULL,
    lop_days          DECIMAL(5, 1)  NOT NULL,   -- loss of pay: unpaid leave + days not employed
    basic             DECIMAL(12, 2) NOT NULL,
    hra               DECIMAL(12, 2) NOT NULL,
    special_allowance DECIMAL(12, 2) NOT NULL,
    gross_earned      DECIMAL(12, 2) NOT NULL,
    provident_fund    DECIMAL(12, 2) NOT NULL,
    professional_tax  DECIMAL(12, 2) NOT NULL,
    total_deductions  DECIMAL(12, 2) NOT NULL,
    net_pay           DECIMAL(12, 2) NOT NULL,
    -- where the PDF is stored (S3 key in AWS), set when the run is finalized
    document_key      VARCHAR(300),
    CONSTRAINT uk_payslips_run_employee UNIQUE (run_id, employee_id),
    CONSTRAINT fk_payslips_run FOREIGN KEY (run_id) REFERENCES payroll_runs (id),
    CONSTRAINT fk_payslips_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_payslips_employee ON payslips (employee_id);
