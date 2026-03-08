/**
 * Lead CRUD and actions. All DB access uses parameterized queries.
 * Input is validated via lib/validation.js before use.
 */

import { Router } from 'express';
import { db } from '../db/schema.js';
import { runTriage } from '../services/triage.js';
import { applyAutomations } from '../services/automationEngine.js';
import { scheduleJobsForLead } from '../services/scheduledJobs.js';
import {
  isValidLeadId,
  validateCreateLead,
  validatePatchLead,
  validateSlots,
} from '../lib/validation.js';

const router = Router();

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
    .replace(/x/g, () => Math.floor(Math.random() * 16).toString(16))
    .replace(/y/g, () => ['8', '9', 'a', 'b'][Math.floor(Math.random() * 4)]);
}

// GET /api/leads — list; optional filter by channel, stage (validated enums)
router.get('/', (req, res) => {
  const channel = req.query.channel;
  const stage = req.query.stage;
  if (channel && stage) {
    const leads = db.prepare('SELECT * FROM leads WHERE channel = ? AND stage = ? ORDER BY created_at DESC').all(channel, stage);
    return res.json(leads);
  }
  if (channel) {
    const leads = db.prepare('SELECT * FROM leads WHERE channel = ? ORDER BY created_at DESC').all(channel);
    return res.json(leads);
  }
  if (stage) {
    const leads = db.prepare('SELECT * FROM leads WHERE stage = ? ORDER BY created_at DESC').all(stage);
    return res.json(leads);
  }
  const leads = db.prepare('SELECT * FROM leads ORDER BY created_at DESC').all();
  res.json(leads);
});

// GET /api/leads/:id — single lead with triage, tasks, timeline
router.get('/:id', (req, res) => {
  if (!isValidLeadId(req.params.id)) return res.status(400).json({ error: 'Invalid lead id' });
  const lead = db.prepare('SELECT * FROM leads WHERE id = ?').get(req.params.id);
  if (!lead) return res.status(404).json({ error: 'Lead not found' });
  const triage = db.prepare('SELECT * FROM ai_triage WHERE lead_id = ?').get(req.params.id);
  const tasks = db.prepare('SELECT * FROM tasks WHERE lead_id = ? ORDER BY due_at').all(req.params.id);
  const timeline = db.prepare('SELECT * FROM timeline WHERE lead_id = ? ORDER BY created_at DESC').all(req.params.id);
  const slots = db.prepare('SELECT * FROM slot_suggestions WHERE lead_id = ? ORDER BY created_at DESC LIMIT 1').get(req.params.id);
  res.json({ ...lead, ai_triage: triage || null, tasks, timeline, slot_suggestions: slots });
});

// POST /api/leads — create from web form; triage + automations run
router.post('/', (req, res) => {
  let body;
  try {
    body = validateCreateLead(req.body);
  } catch (e) {
    return res.status(400).json({ error: e.message });
  }
  const id = uuid();
  db.prepare(
    'INSERT INTO leads (id, channel, raw_message, name, contact, vertical, source) VALUES (?, ?, ?, ?, ?, ?, ?)'
  ).run(id, body.channel, body.raw_message, body.name, body.contact, body.vertical, body.source);
  db.prepare('INSERT INTO timeline (id, lead_id, event_type, payload) VALUES (?, ?, ?, ?)').run(
    uuid(), id, 'created', JSON.stringify({ channel: body.channel, source: body.source || null })
  );
  const triage = runTriage(body.raw_message, id);
  db.prepare(
    `INSERT OR REPLACE INTO ai_triage (lead_id, vertical, category, subcategory, intent, urgency_score, extracted_fields, missing_fields, summary, recommended_actions, safety_escalate)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(
    id, triage.vertical, triage.category, triage.subcategory, triage.intent, triage.urgencyScore,
    JSON.stringify(triage.extractedFields || {}), JSON.stringify(triage.missingFields || []),
    triage.summary, JSON.stringify(triage.recommendedActions || []), triage.safetyEscalate ? 1 : 0
  );
  db.prepare('UPDATE leads SET vertical = ?, updated_at = datetime(\'now\') WHERE id = ?').run(triage.vertical, id);
  applyAutomations(id);
  const lead = db.prepare('SELECT * FROM leads WHERE id = ?').get(id);
  const ai_triage = db.prepare('SELECT * FROM ai_triage WHERE lead_id = ?').get(id);
  const tasks = db.prepare('SELECT * FROM tasks WHERE lead_id = ?').all(id);
  res.status(201).json({ lead, ai_triage, tasks });
});

// PATCH /api/leads/:id — update stage, owner_id, service_date (validated)
router.patch('/:id', (req, res) => {
  if (!isValidLeadId(req.params.id)) return res.status(400).json({ error: 'Invalid lead id' });
  const lead = db.prepare('SELECT * FROM leads WHERE id = ?').get(req.params.id);
  if (!lead) return res.status(404).json({ error: 'Lead not found' });
  const { stage, owner_id, service_date } = validatePatchLead(req.body);
  if (stage) db.prepare('UPDATE leads SET stage = ?, updated_at = datetime(\'now\') WHERE id = ?').run(stage, req.params.id);
  if (owner_id !== undefined) db.prepare('UPDATE leads SET owner_id = ?, updated_at = datetime(\'now\') WHERE id = ?').run(owner_id, req.params.id);
  if (service_date !== undefined) {
    db.prepare('UPDATE leads SET service_date = ?, updated_at = datetime(\'now\') WHERE id = ?').run(service_date, req.params.id);
    // Schedule reminder (2d, 24h) and feedback (1d after) jobs
    if (service_date) scheduleJobsForLead(req.params.id, service_date);
  }
  const updated = db.prepare('SELECT * FROM leads WHERE id = ?').get(req.params.id);
  res.json(updated);
});

// POST /api/leads/:id/slots — save manual slot suggestions
router.post('/:id/slots', (req, res) => {
  if (!isValidLeadId(req.params.id)) return res.status(400).json({ error: 'Invalid lead id' });
  const lead = db.prepare('SELECT * FROM leads WHERE id = ?').get(req.params.id);
  if (!lead) return res.status(404).json({ error: 'Lead not found' });
  const slots = validateSlots(req.body?.slots);
  const slotId = uuid();
  db.prepare('INSERT INTO slot_suggestions (id, lead_id, slots) VALUES (?, ?, ?)').run(slotId, req.params.id, JSON.stringify(slots));
  db.prepare('UPDATE leads SET stage = ?, updated_at = datetime(\'now\') WHERE id = ?').run('Offered Slots', req.params.id);
  db.prepare('INSERT INTO timeline (id, lead_id, event_type, payload) VALUES (?, ?, ?, ?)').run(uuid(), req.params.id, 'slots_offered', JSON.stringify({ slots }));
  res.status(201).json(db.prepare('SELECT * FROM slot_suggestions WHERE id = ?').get(slotId));
});

// POST /api/leads/:id/tasks/:taskId/complete
router.post('/:id/tasks/:taskId/complete', (req, res) => {
  if (!isValidLeadId(req.params.id)) return res.status(400).json({ error: 'Invalid lead id' });
  db.prepare('UPDATE tasks SET completed_at = datetime(\'now\') WHERE id = ? AND lead_id = ?').run(req.params.taskId, req.params.id);
  const task = db.prepare('SELECT * FROM tasks WHERE id = ?').get(req.params.taskId);
  res.json(task);
});

export default router;
