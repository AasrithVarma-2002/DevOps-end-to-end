-- Stage 1: core HR, users and leave management.
-- Written to run unchanged on MySQL 8.4 (RDS) and H2 in MySQL mode (tests).

CREATE TABLE departments (
    id       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    location VARCHAR(100),
    CONSTRAINT uk_departments_name UNIQUE (name)
);

CREATE TABLE employees (
    id                      BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_code           VARCHAR(20)    NOT NULL,
    first_name              VARCHAR(50)    NOT NULL,
    last_name               VARCHAR(50)    NOT NULL,
    email                   VARCHAR(120)   NOT NULL,
    phone                   VARCHAR(20),
    address                 VARCHAR(300),
    emergency_contact_name  VARCHAR(100),
    emergency_contact_phone VARCHAR(20),
    job_title               VARCHAR(100)   NOT NULL,
    department_id           BIGINT         NOT NULL,
    manager_id              BIGINT,
    salary                  DECIMAL(12, 2) NOT NULL,
    joining_date            DATE           NOT NULL,
    status                  VARCHAR(20)    NOT NULL,
    profile_completed       BOOLEAN        NOT NULL DEFAULT FALSE,
    last_working_day        DATE,
    exit_reason             VARCHAR(300),
    CONSTRAINT uk_employees_code UNIQUE (employee_code),
    CONSTRAINT uk_employees_email UNIQUE (email),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_employees_manager FOREIGN KEY (manager_id) REFERENCES employees (id)
);

CREATE TABLE user_accounts (
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username              VARCHAR(120) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    employee_id           BIGINT,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    locked                BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts       INT          NOT NULL DEFAULT 0,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at         DATETIME(6),
    CONSTRAINT uk_user_accounts_username UNIQUE (username),
    CONSTRAINT uk_user_accounts_employee UNIQUE (employee_id),
    CONSTRAINT fk_user_accounts_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE TABLE holidays (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    CONSTRAINT uk_holidays_date UNIQUE (holiday_date)
);

CREATE TABLE leave_balances (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT       NOT NULL,
    leave_year  INT          NOT NULL,
    leave_type  VARCHAR(20)  NOT NULL,
    allocated   DECIMAL(5, 1) NOT NULL,
    used        DECIMAL(5, 1) NOT NULL DEFAULT 0,
    CONSTRAINT uk_leave_balances UNIQUE (employee_id, leave_year, leave_type),
    CONSTRAINT fk_leave_balances_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE TABLE leave_requests (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id         BIGINT        NOT NULL,
    leave_type          VARCHAR(20)   NOT NULL,
    start_date          DATE          NOT NULL,
    end_date            DATE          NOT NULL,
    half_day            BOOLEAN       NOT NULL DEFAULT FALSE,
    days                DECIMAL(5, 1) NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(20)   NOT NULL,
    manager_id          BIGINT,
    manager_comment     VARCHAR(500),
    manager_decided_at  DATETIME(6),
    hr_decided_by       VARCHAR(120),
    hr_comment          VARCHAR(500),
    hr_decided_at       DATETIME(6),
    created_at          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_leave_requests_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_leave_requests_manager FOREIGN KEY (manager_id) REFERENCES employees (id)
);

CREATE INDEX idx_leave_requests_status ON leave_requests (status);
CREATE INDEX idx_leave_requests_employee_dates ON leave_requests (employee_id, start_date, end_date);
CREATE INDEX idx_leave_requests_manager_status ON leave_requests (manager_id, status);

CREATE TABLE notifications (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT       NOT NULL,
    message      VARCHAR(500) NOT NULL,
    link         VARCHAR(200),
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, is_read);

-- Append-only: the application never updates or deletes rows here.
CREATE TABLE audit_log (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    occurred_at DATETIME(6)   NOT NULL,
    actor       VARCHAR(120)  NOT NULL,
    action      VARCHAR(50)   NOT NULL,
    entity_type VARCHAR(50)   NOT NULL,
    entity_id   BIGINT,
    details     VARCHAR(2000)
);

CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
