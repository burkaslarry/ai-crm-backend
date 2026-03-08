/**
 * WhatsApp simulated inbox: POST pasted message → create lead, run triage, apply automations.
 * Input validated via lib/validation.js.
 */

import { Router } from 'express';
import { db } from '../db/schema.js';
import { runTriage } from '../services/triage.js';
import { applyAutomations } from '../services/automationEngine.js';
import { validateCreateInquiry } from '../lib/validation.js';

const router = Router();

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
    .replace(/x/g, () => Math.floor(Math.random() * 16).toString(16))
    .replace(/y/g, () => ['8', '9', 'a', 'b'][Math.floor(Math.random() * 4)]);
}

router.post('/', (req, res) => {
  let body;
  try {
    body = validateCreateInquiry(req.body);
  } catch (e) {
    return res.status(400).json({ error: e.message });
  }
  const id = uuid();
  db.prepare(
    'INSERT INTO leads (id, channel, raw_message, contact) VALUES (?, ?, ?, ?)'
  ).run(id, 'whatsapp', body.message, body.contact || null);
  db.prepare('INSERT INTO timeline (id, lead_id, event_type, payload) VALUES (?, ?, ?, ?)').run(
    uuid(), id, 'whatsapp_paste', JSON.stringify({ contact: body.contact || null })
  );
  const triage = runTriage(body.message, id);
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

export default router;
