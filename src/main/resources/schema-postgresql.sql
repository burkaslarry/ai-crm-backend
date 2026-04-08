-- PostgreSQL schema for AI CRM only (TEXT instead of CLOB).
-- Safe on shared DBs (e.g. BNI Render "eventxp"): uses CREATE TABLE IF NOT EXISTS only — no DROP,
-- no changes to existing bni_anchor_* or other non-CRM tables.
CREATE TABLE IF NOT EXISTS leads (
    id VARCHAR(36) PRIMARY KEY,
    channel VARCHAR(20) NOT NULL DEFAULT 'web',
    raw_message TEXT,
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
    extracted_fields TEXT,
    missing_fields TEXT,
    summary TEXT,
    recommended_actions TEXT,
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
    payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS automation_rules (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    trigger_condition TEXT NOT NULL,
    actions TEXT NOT NULL,
    enabled INT DEFAULT 1,
    sort_order INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS slot_suggestions (
    id VARCHAR(36) PRIMARY KEY,
    lead_id VARCHAR(36) NOT NULL REFERENCES leads(id),
    slots TEXT NOT NULL,
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

-- RAG: services and products (CSV import, search by region HK/TW/CN)
CREATE TABLE IF NOT EXISTS rag_services (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    region VARCHAR(10) NOT NULL DEFAULT 'hk',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS rag_products (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    region VARCHAR(10) NOT NULL DEFAULT 'hk',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rag_services_region ON rag_services(region);
CREATE INDEX IF NOT EXISTS idx_rag_products_region ON rag_products(region);

CREATE TABLE IF NOT EXISTS follow_up_cases (
    id VARCHAR(36) PRIMARY KEY,
    case_name VARCHAR(500) NOT NULL,
    contact VARCHAR(500),
    status VARCHAR(50),
    notes TEXT,
    lead_ref VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_follow_up_cases_status ON follow_up_cases(status);
CREATE INDEX IF NOT EXISTS idx_follow_up_cases_created ON follow_up_cases(created_at);

CREATE TABLE IF NOT EXISTS rag_documents (
    id VARCHAR(36) PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    region VARCHAR(10) NOT NULL DEFAULT 'sg',
    extracted_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_document_links (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL REFERENCES rag_documents(id),
    item_type VARCHAR(20) NOT NULL,
    item_id VARCHAR(36) NOT NULL,
    item_name VARCHAR(500) NOT NULL,
    score DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rag_document_links_document ON rag_document_links(document_id);
CREATE INDEX IF NOT EXISTS idx_rag_document_links_item ON rag_document_links(item_type, item_id);

-- Users: superadmin and operators (for confirming bookings, handling inquiries)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'operator',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
