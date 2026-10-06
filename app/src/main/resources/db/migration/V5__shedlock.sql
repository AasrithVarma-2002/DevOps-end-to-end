-- Stage 4: scheduled jobs. Every pod runs the same schedule; ShedLock lets only the pod that
-- inserts/updates the row for a job first run it. Layout from the ShedLock documentation.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
