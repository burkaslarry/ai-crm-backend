import { fileURLToPath } from 'url';
import path from 'path';
import { db, initSchema } from './schema.js';
import { getDefaultRules } from '../services/automationEngine.js';
import { runTriage } from '../services/triage.js';
import { applyAutomations } from '../services/automationEngine.js';

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
    .replace(/x/g, () => Math.floor(Math.random() * 16).toString(16))
    .replace(/y/g, () => ['8', '9', 'a', 'b'][Math.floor(Math.random() * 4)]);
}

/** Run seed (rules + sample leads). Caller must have called initSchema() first. */
export function runSeed() {
  const rules = getDefaultRules();
  const ruleInsert = db.prepare(
    'INSERT OR REPLACE INTO automation_rules (id, name, trigger_condition, actions, enabled, sort_order) VALUES (?, ?, ?, ?, ?, ?)'
  );
  for (const r of rules) ruleInsert.run(r.id, r.name, r.trigger_condition, r.actions, r.enabled, r.sort_order);

  const sampleInquiries = [
    { channel: 'whatsapp', raw: 'Hi can I book laser hair removal this week? Prefer Thu after 6pm. Price?', contact: '+852 9123 4567' },
    { channel: 'whatsapp', raw: 'I did facial yesterday, today redness and swelling. What should I do?', contact: '+852 9876 5432' },
    { channel: 'web', raw: '想預約醫美諮詢，激光脫毛同面部療程，請問價錢同可約時間？地點銅鑼灣。', name: '陳小姐', contact: 'chan@email.com' },
    { channel: 'web', raw: '請問雷射淡斑療程幾錢？想約下星期，中環或尖沙咀分店都可以。', name: 'Wong', contact: '+852 6111 2222' },
    { channel: 'whatsapp', raw: 'I want to join beginner nail course. Weekend only. When\'s the next intake? Cost?', contact: 'nails@mail.com' },
  ];

  for (const s of sampleInquiries) {
    const id = uuid();
    db.prepare(
      'INSERT INTO leads (id, channel, raw_message, name, contact) VALUES (?, ?, ?, ?, ?)'
    ).run(id, s.channel, s.raw, s.name || null, s.contact || null);
    db.prepare('INSERT INTO timeline (id, lead_id, event_type, payload) VALUES (?, ?, ?, ?)').run(
      uuid(), id, s.channel === 'web' ? 'created' : 'whatsapp_paste', JSON.stringify({})
    );
    const triage = runTriage(s.raw, id);
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
  }

  return sampleInquiries.length;
}

const isMain = process.argv[1] && path.resolve(fileURLToPath(import.meta.url)) === path.resolve(process.argv[1]);
if (isMain) {
  initSchema();
  const n = runSeed();
  console.log('Seed done: rules +', n, 'sample leads.');
}
