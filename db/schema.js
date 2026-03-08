/**
 * SQLite schema for CRM.
 * Safety: All queries elsewhere use parameterized statements (?) only; no user input in raw SQL.
 */

import Database from 'better-sqlite3';
import { fileURLToPath } from 'url';
import path from 'path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dbPath = path.join(__dirname, '..', 'crm.sqlite');

export const db = new Database(dbPath);

export function initSchema() {
  db.exec(`
    -- Leads (web form or WhatsApp paste). service_date = booked service/appointment date (ISO YYYY-MM-DD).
    CREATE TABLE IF NOT EXISTS leads (
      id TEXT PRIMARY KEY,
      channel TEXT NOT NULL DEFAULT 'web',
      raw_message TEXT,
      name TEXT,
      contact TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now')),
      updated_at TEXT NOT NULL DEFAULT (datetime('now')),
      stage TEXT NOT NULL DEFAULT 'New',
      owner_id TEXT,
      vertical TEXT,
      source TEXT,
      service_date TEXT
    );

    -- AI triage result (JSON blobs; parsed in app code only).
    CREATE TABLE IF NOT EXISTS ai_triage (
      lead_id TEXT PRIMARY KEY REFERENCES leads(id),
      vertical TEXT,
      category TEXT,
      subcategory TEXT,
      intent TEXT,
      urgency_score INTEGER,
      extracted_fields TEXT,
      missing_fields TEXT,
      summary TEXT,
      recommended_actions TEXT,
      safety_escalate INTEGER DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    -- Tasks (from automation rules and scheduled jobs).
    CREATE TABLE IF NOT EXISTS tasks (
      id TEXT PRIMARY KEY,
      lead_id TEXT NOT NULL REFERENCES leads(id),
      type TEXT NOT NULL,
      title TEXT NOT NULL,
      due_at TEXT,
      completed_at TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS timeline (
      id TEXT PRIMARY KEY,
      lead_id TEXT NOT NULL REFERENCES leads(id),
      event_type TEXT NOT NULL,
      payload TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS automation_rules (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      trigger_condition TEXT NOT NULL,
      actions TEXT NOT NULL,
      enabled INTEGER DEFAULT 1,
      sort_order INTEGER DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS slot_suggestions (
      id TEXT PRIMARY KEY,
      lead_id TEXT NOT NULL REFERENCES leads(id),
      slots TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );

    -- Scheduled jobs: reminder 2d/24h before service, feedback 1d after. Processed by processDueScheduledJobs().
    CREATE TABLE IF NOT EXISTS scheduled_jobs (
      id TEXT PRIMARY KEY,
      lead_id TEXT NOT NULL REFERENCES leads(id),
      job_type TEXT NOT NULL,
      run_at TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'pending',
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  // Add service_date if missing (migration for existing DBs).
  try {
    const cols = db.prepare('PRAGMA table_info(leads)').all();
    if (!cols.some((c) => c.name === 'service_date')) {
      db.exec(`ALTER TABLE leads ADD COLUMN service_date TEXT`);
    }
  } catch (_) {}

  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_leads_stage ON leads(stage);
    CREATE INDEX IF NOT EXISTS idx_leads_channel ON leads(channel);
    CREATE INDEX IF NOT EXISTS idx_leads_created ON leads(created_at);
    CREATE INDEX IF NOT EXISTS idx_leads_service_date ON leads(service_date);
    CREATE INDEX IF NOT EXISTS idx_tasks_lead ON tasks(lead_id);
    CREATE INDEX IF NOT EXISTS idx_timeline_lead ON timeline(lead_id);
    CREATE INDEX IF NOT EXISTS idx_scheduled_jobs_run_at ON scheduled_jobs(run_at);
    CREATE INDEX IF NOT EXISTS idx_scheduled_jobs_status ON scheduled_jobs(status);
  `);
}
