/**
 * Automation rules and scheduled job processing. No user input in SQL.
 */

import { Router } from 'express';
import { db } from '../db/schema.js';
import { applyAutomations, getDefaultRules } from '../services/automationEngine.js';
import { processDueScheduledJobs } from '../services/scheduledJobs.js';
import { isValidLeadId } from '../lib/validation.js';

const router = Router();

router.get('/rules', (req, res) => {
  const rules = db.prepare('SELECT * FROM automation_rules ORDER BY sort_order').all();
  res.json(rules);
});

router.post('/rules/seed', (req, res) => {
  const defaults = getDefaultRules();
  const insert = db.prepare(
    'INSERT OR REPLACE INTO automation_rules (id, name, trigger_condition, actions, enabled, sort_order) VALUES (?, ?, ?, ?, ?, ?)'
  );
  for (const r of defaults) insert.run(r.id, r.name, r.trigger_condition, r.actions, r.enabled, r.sort_order);
  res.json(db.prepare('SELECT * FROM automation_rules ORDER BY sort_order').all());
});

router.post('/apply/:leadId', (req, res) => {
  if (!isValidLeadId(req.params.leadId)) return res.status(400).json({ error: 'Invalid lead id' });
  const result = applyAutomations(req.params.leadId);
  res.json(result);
});

/** Process due scheduled jobs (reminder 2d/24h, feedback 1d after). Call from cron or timer. */
router.post('/process-scheduled', (req, res) => {
  const processed = processDueScheduledJobs();
  res.json({ processed: processed.length, jobs: processed });
});

/** List scheduled jobs (optional ?status=pending). */
router.get('/scheduled-jobs', (req, res) => {
  const jobs = req.query.status === 'pending'
    ? db.prepare("SELECT * FROM scheduled_jobs WHERE status = 'pending' ORDER BY run_at").all()
    : db.prepare('SELECT * FROM scheduled_jobs ORDER BY run_at DESC LIMIT 200').all();
  res.json(jobs);
});

export default router;
