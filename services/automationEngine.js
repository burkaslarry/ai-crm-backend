/**
 * Automation rules engine: triggered on triage completion.
 * Rules: booking intent → offer slots + task; missing fields → ask top 2; high urgency → call task; complaint → escalate.
 * All DB access uses parameterized queries; trigger/actions are our own JSON, not user input.
 */

import { db } from '../db/schema.js';

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
    .replace(/x/g, () => Math.floor(Math.random() * 16).toString(16))
    .replace(/y/g, () => ['8', '9', 'a', 'b'][Math.floor(Math.random() * 4)]);
}

export function getDefaultRules() {
  return [
    {
      id: 'rule-booking',
      name: 'Booking intent',
      trigger_condition: JSON.stringify({ intent: 'book', hasTriage: true }),
      actions: JSON.stringify([
        { type: 'set_stage', value: 'Offered Slots' },
        { type: 'assign_owner', valueByVertical: { med_spa: 'receptionist-a', training: 'enrollment-staff' } },
        { type: 'create_task', taskType: 'send_slots', title: 'Send 3 available slots within 15 minutes', dueMinutes: 15 },
      ]),
      enabled: 1,
      sort_order: 10,
    },
    {
      id: 'rule-missing-info',
      name: 'Missing key info',
      trigger_condition: JSON.stringify({ missingFieldsNotEmpty: true }),
      actions: JSON.stringify([
        { type: 'set_stage', value: 'Needs Info' },
        { type: 'draft_message', askTopMissing: 2 },
      ]),
      enabled: 1,
      sort_order: 20,
    },
    {
      id: 'rule-high-urgency',
      name: 'High urgency (醫美 / aesthetic)',
      trigger_condition: JSON.stringify({ urgencyMin: 80 }),
      actions: JSON.stringify([
        { type: 'create_task', taskType: 'call', title: 'Call within 10 minutes', dueMinutes: 10 },
        { type: 'add_banner', value: 'High urgency lead' },
      ]),
      enabled: 1,
      sort_order: 15,
    },
    {
      id: 'rule-complaint',
      name: 'Complaint / medical concern',
      trigger_condition: JSON.stringify({ intent: 'complaint', safetyEscalate: true }),
      actions: JSON.stringify([
        { type: 'set_stage', value: 'Needs Info' },
        { type: 'assign_owner', value: 'manager' },
        { type: 'add_banner', value: 'Escalate' },
      ]),
      enabled: 1,
      sort_order: 5,
    },
  ];
}

export function applyAutomations(leadId) {
  const lead = db.prepare('SELECT * FROM leads WHERE id = ?').get(leadId);
  if (!lead) return { applied: [] };
  const triage = db.prepare('SELECT * FROM ai_triage WHERE lead_id = ?').get(leadId);
  if (!triage) return { applied: [] };

  const extracted = JSON.parse(triage.extracted_fields || '{}');
  const missing = JSON.parse(triage.missing_fields || '[]');
  const vertical = triage.vertical || lead.vertical;
  const intent = triage.intent;
  const urgency = triage.urgency_score || 0;
  const safetyEscalate = triage.safety_escalate === 1;

  const rules = db.prepare('SELECT * FROM automation_rules WHERE enabled = 1 ORDER BY sort_order ASC').all();
  const applied = [];
  let newStage = lead.stage;
  let newOwner = lead.owner_id;

  for (const rule of rules) {
    const cond = JSON.parse(rule.trigger_condition || '{}');
    let match = false;

    if (cond.intent && cond.intent === intent && cond.hasTriage) match = true;
    if (cond.missingFieldsNotEmpty && missing.length > 0) match = true;
    if (cond.urgencyMin != null && urgency >= cond.urgencyMin) match = true;
    if (cond.intent === 'complaint' && cond.safetyEscalate && intent === 'complaint' && safetyEscalate) match = true;

    if (!match) continue;

    const actions = JSON.parse(rule.actions || '[]');
    for (const action of actions) {
      if (action.type === 'set_stage') {
        newStage = action.value;
        db.prepare('UPDATE leads SET stage = ?, updated_at = datetime(\'now\') WHERE id = ?').run(newStage, leadId);
        applied.push({ rule: rule.name, action: 'set_stage', value: newStage });
      }
      if (action.type === 'assign_owner') {
        const owner = action.valueByVertical?.[vertical] || action.value;
        if (owner) {
          newOwner = owner;
          db.prepare('UPDATE leads SET owner_id = ?, updated_at = datetime(\'now\') WHERE id = ?').run(owner, leadId);
          applied.push({ rule: rule.name, action: 'assign_owner', value: owner });
        }
      }
      if (action.type === 'create_task') {
        const dueAt = new Date(Date.now() + (action.dueMinutes || 15) * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');
        db.prepare('INSERT INTO tasks (id, lead_id, type, title, due_at) VALUES (?, ?, ?, ?, ?)').run(
          uuid(), leadId, action.taskType || 'general', action.title, dueAt
        );
        applied.push({ rule: rule.name, action: 'create_task', title: action.title });
      }
    }
  }

  db.prepare('INSERT INTO timeline (id, lead_id, event_type, payload) VALUES (?, ?, ?, ?)').run(
    uuid(), leadId, 'automations_applied', JSON.stringify({ applied })
  );

  return { applied, newStage, newOwner };
}
