/**
 * AI endpoints: triage (mock) and draft generation. No user input in SQL; triage input length-limited in service.
 */

import { Router } from 'express';
import { db } from '../db/schema.js';
import { runTriage } from '../services/triage.js';
import { getDraft } from '../data/templates.js';
import { sanitizeString } from '../lib/validation.js';

const router = Router();

const MAX_MESSAGE_LEN = 10000;

router.post('/triage', (req, res) => {
  const rawMessage = req.body.rawMessage;
  const leadId = req.body.leadId;
  let message = typeof rawMessage === 'string' ? sanitizeString(rawMessage, MAX_MESSAGE_LEN) : null;
  if (!message && leadId) {
    const row = db.prepare('SELECT raw_message FROM leads WHERE id = ?').get(leadId);
    message = row ? sanitizeString(row.raw_message, MAX_MESSAGE_LEN) : null;
  }
  if (!message) return res.status(400).json({ error: 'rawMessage or leadId with existing message required' });
  const triage = runTriage(message, leadId || '');
  if (leadId) {
    db.prepare(
      `INSERT OR REPLACE INTO ai_triage (lead_id, vertical, category, subcategory, intent, urgency_score, extracted_fields, missing_fields, summary, recommended_actions, safety_escalate)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).run(
      leadId, triage.vertical, triage.category, triage.subcategory, triage.intent, triage.urgencyScore,
      JSON.stringify(triage.extractedFields || {}), JSON.stringify(triage.missingFields || []),
      triage.summary, JSON.stringify(triage.recommendedActions || []), triage.safetyEscalate ? 1 : 0
    );
  }
  res.json(triage);
});

router.post('/draft', (req, res) => {
  const body = req.body || {};
  const vertical = sanitizeString(body.vertical, 50) || 'med_spa';
  const intent = sanitizeString(body.intent, 50) || 'info';
  const slotsStr = Array.isArray(body.slots) ? body.slots.map((s) => sanitizeString(String(s), 200)).filter(Boolean).join('\n') : sanitizeString(body.slots, 500) || '';
  const draft = getDraft(vertical, intent, {
    name: sanitizeString(body.name, 200) || '',
    service: sanitizeString(body.service, 200) || '',
    location: sanitizeString(body.location, 200) || '',
    slots: slotsStr,
    bookingLink: sanitizeString(body.bookingLink, 500) || '[Booking link]',
    service_date: sanitizeString(body.service_date, 20) || '',
  });
  res.json({ draft });
});

export default router;
