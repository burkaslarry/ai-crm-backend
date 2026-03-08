/**
 * Scheduled automations: reminder before service (2 days, 24h) and feedback questionnaire (1 day after).
 * Jobs are stored in scheduled_jobs; run processDueScheduledJobs() (e.g. from cron or timer).
 */

import { db } from '../db/schema.js';

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
    .replace(/x/g, () => Math.floor(Math.random() * 16).toString(16))
    .replace(/y/g, () => ['8', '9', 'a', 'b'][Math.floor(Math.random() * 4)]);
}

const JOB_TYPES = {
  reminder_2d: { title: 'Send reminder: appointment in 2 days', taskType: 'reminder_2d' },
  reminder_24h: { title: 'Send reminder: appointment in 24 hours', taskType: 'reminder_24h' },
  feedback_1d: { title: 'Send feedback questionnaire (1 day after service)', taskType: 'feedback_1d' },
};

/**
 * Schedule reminder and feedback jobs for a lead when service_date is set.
 * Call when PATCH lead sets service_date, or when stage becomes Booked with service_date.
 */
export function scheduleJobsForLead(leadId, serviceDateISO) {
  const d = new Date(serviceDateISO);
  if (Number.isNaN(d.getTime())) return [];
  // Replace any existing pending jobs for this lead
  db.prepare("DELETE FROM scheduled_jobs WHERE lead_id = ? AND status = 'pending'").run(leadId);
  const runAt = (daysOffset) => {
    const t = new Date(d);
    t.setDate(t.getDate() + daysOffset);
    return t.toISOString().slice(0, 19).replace('T', ' ');
  };
  const jobs = [
    { job_type: 'reminder_2d', run_at: runAt(-2) },
    { job_type: 'reminder_24h', run_at: runAt(-1) },
    { job_type: 'feedback_1d', run_at: runAt(1) },
  ];
  const insert = db.prepare(
    'INSERT INTO scheduled_jobs (id, lead_id, job_type, run_at, status) VALUES (?, ?, ?, ?, ?)'
  );
  for (const j of jobs) {
    insert.run(uuid(), leadId, j.job_type, j.run_at, 'pending');
  }
  return jobs;
}

/**
 * Process all due scheduled jobs (run_at <= now, status = pending).
 * Creates a task + timeline event per job, then marks job done.
 */
export function processDueScheduledJobs() {
  const now = new Date().toISOString().slice(0, 19).replace('T', ' ');
  const due = db.prepare(
    "SELECT * FROM scheduled_jobs WHERE status = 'pending' AND run_at <= ? ORDER BY run_at"
  ).all(now);
  const processed = [];
  const insertTask = db.prepare(
    'INSERT INTO tasks (id, lead_id, type, title, due_at) VALUES (?, ?, ?, ?, ?)'
  );
  const updateJob = db.prepare("UPDATE scheduled_jobs SET status = 'done' WHERE id = ?");

  for (const job of due) {
    const meta = JOB_TYPES[job.job_type];
    const title = meta ? meta.title : `Scheduled: ${job.job_type}`;
    const taskId = uuid();
    insertTask.run(taskId, job.lead_id, meta?.taskType || job.job_type, title, job.run_at);
    db.prepare(
      'INSERT INTO timeline (id, lead_id, event_type, payload) VALUES (?, ?, ?, ?)'
    ).run(uuid(), job.lead_id, 'scheduled_job_processed', JSON.stringify({ job_type: job.job_type, run_at: job.run_at }));
    updateJob.run(job.id);
    processed.push({ job_id: job.id, lead_id: job.lead_id, job_type: job.job_type });
  }
  return processed;
}

export { JOB_TYPES };
