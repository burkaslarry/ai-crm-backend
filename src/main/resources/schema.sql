-- H2 schema for CRM (compatible with original SQLite design)
CREATE TABLE IF NOT EXISTS leads (
    id VARCHAR(36) PRIMARY KEY,
    channel VARCHAR(20) NOT NULL DEFAULT 'web',
    raw_message CLOB,
    name VARCHAR(500),
    contact VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stage VARCHAR(50) NOT NULL DEFAULT 'New',
    owner_id VARCHAR(255),
    vertical VARCHAR(50),
    source VARCHAR(255),
    service_date VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS ai_triage (
    lead_id VARCHAR(36) PRIMARY KEY REFERENCES leads(id),
    vertical VARCHAR(50),
    category VARCHAR(50),
    subcategory VARCHAR(255),
    intent VARCHAR(50),
    urgency_score INT,
    extracted_fields CLOB,
    missing_fields CLOB,
    summary CLOB,
    recommended_actions CLOB,
    safety_escalate INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(36) PRIMARY KEY,
    lead_id VARCHAR(36) NOT NULL REFERENCES leads(id),
    type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    due_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS timeline (
    id VARCHAR(36) PRIMARY KEY,
    lead_id VARCHAR(36) NOT NULL REFERENCES leads(id),
    event_type VARCHAR(100) NOT NULL,
    payload CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS automation_rules (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    trigger_condition CLOB NOT NULL,
    actions CLOB NOT NULL,
    enabled INT DEFAULT 1,
    sort_order INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS slot_suggestions (
    id VARCHAR(36) PRIMARY KEY,
    lead_id VARCHAR(36) NOT NULL REFERENCES leads(id),
    slots CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS scheduled_jobs (
    id VARCHAR(36) PRIMARY KEY,
    lead_id VARCHAR(36) NOT NULL REFERENCES leads(id),
    job_type VARCHAR(50) NOT NULL,
    run_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_leads_stage ON leads(stage);
CREATE INDEX IF NOT EXISTS idx_leads_channel ON leads(channel);
CREATE INDEX IF NOT EXISTS idx_leads_created ON leads(created_at);
CREATE INDEX IF NOT EXISTS idx_leads_service_date ON leads(service_date);
CREATE INDEX IF NOT EXISTS idx_tasks_lead ON tasks(lead_id);
CREATE INDEX IF NOT EXISTS idx_timeline_lead ON timeline(lead_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_jobs_run_at ON scheduled_jobs(run_at);
CREATE INDEX IF NOT EXISTS idx_scheduled_jobs_status ON scheduled_jobs(status);
