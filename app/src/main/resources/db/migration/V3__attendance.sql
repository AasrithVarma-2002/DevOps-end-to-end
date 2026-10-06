-- Stage 2: daily attendance (check-in / check-out). One row per employee per day.
-- Written to run unchanged on MySQL 8.4 (RDS) and H2 in MySQL mode (tests).

CREATE TABLE attendance_records (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id    BIGINT       NOT NULL,
    work_date      DATE         NOT NULL,
    check_in       DATETIME(6)  NOT NULL,
    check_out      DATETIME(6),
    minutes_worked INT,
    -- set when HR adds or corrects the record (e.g. forgot to check out)
    corrected_by   VARCHAR(120),
    note           VARCHAR(300),
    CONSTRAINT uk_attendance_employee_day UNIQUE (employee_id, work_date),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);

CREATE INDEX idx_attendance_work_date ON attendance_records (work_date);
