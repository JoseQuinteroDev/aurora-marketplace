CREATE TABLE batch_job_audit (
    id UUID PRIMARY KEY,
    job_name VARCHAR(120) NOT NULL,
    execution_id BIGINT,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    message VARCHAR(1000)
);

CREATE INDEX idx_batch_job_audit_job_name ON batch_job_audit (job_name);
CREATE INDEX idx_batch_job_audit_execution_id ON batch_job_audit (execution_id);
CREATE INDEX idx_batch_job_audit_started_at ON batch_job_audit (started_at);
CREATE INDEX idx_batch_job_audit_status ON batch_job_audit (status);
