/**
 * Deterministic Mock AI triage: vertical, intent, category, urgency, extracted fields, missing fields, summary, recommended actions.
 * English + Chinese (Cantonese/Mandarin) keyword rules.
 */

import { normalizeFields, getMissingFields } from './normalization.js';

const VERTICAL_KEYWORDS = {
  med_spa: [
    'laser', 'facial', 'hair removal', 'consultation', 'skin', 'aesthetic', 'clinic', 'med spa', 'treatment',
    '脫毛', '面部', '激光', '美容', '療程', '皮膚', '諮詢', '醫美', 'facial', 'pigment', 'acne', 'slimming',
    '醫美', '雷射', '微針', '填充', '肉毒', '玻尿酸', '皮膚科', '美容醫學',
  ],
  training: [
    'course', 'training', 'class', 'certification', 'learn', 'intake', 'nail course', 'beauty course',
    '課程', '培訓', '學', '證書', '入讀', '美甲', '美容課程',
  ],
};

const INTENT_KEYWORDS = {
  book: ['book', 'booking', '預約', '想約', '想請', 'join', '報名', '想上', 'schedule', 'appointment'],
  price: ['price', 'cost', 'fee', '價錢', '幾錢', '費用', 'how much', 'budget', '預算'],
  info: ['info', 'information', '了解', '想知', '介紹', 'what do you', 'inquiry', '查詢'],
  complaint: ['complaint', 'problem', 'issue', 'redness', 'swelling', 'pain', '不適', '紅腫', '痛', '有問題', 'what should i do'],
  reschedule: ['reschedule', 'change', '改期', '取消', 'cancel', 'postpone'],
};

const SAFETY_RED_FLAGS = [
  'severe allergic', 'anaphylaxis', 'fainting', 'faint', 'breathing', 'chest pain', 'severe infection',
  '紅腫', '腫脹', '過敏', '呼吸', '胸痛', '頭暈', '暈倒', '發燒', '感染', '發炎', 'redness and swelling',
  'what should i do', 'emergency', 'urgent',
];

function detectVertical(text) {
  const lower = text.toLowerCase();
  const t = text;
  let best = { vertical: 'unknown', score: 0 };
  for (const [v, keywords] of Object.entries(VERTICAL_KEYWORDS)) {
    let score = 0;
    for (const k of keywords) {
      if (lower.includes(k.toLowerCase()) || t.includes(k)) score++;
    }
    if (score > best.score) best = { vertical: v, score };
  }
  return best.vertical;
}

function detectIntent(text) {
  const lower = text.toLowerCase();
  const intents = [];
  for (const [intent, keywords] of Object.entries(INTENT_KEYWORDS)) {
    for (const k of keywords) {
      if (lower.includes(k) || text.includes(k)) {
        intents.push(intent);
        break;
      }
    }
  }
  if (intents.includes('complaint')) return 'complaint';
  if (intents.includes('book')) return 'book';
  if (intents.includes('reschedule')) return 'reschedule';
  if (intents.includes('price')) return 'price';
  if (intents.includes('info')) return 'info';
  return intents[0] || 'info';
}

function urgencyScore(text, vertical, intent, extracted) {
  let score = 50;
  if (intent === 'complaint') score = 90;
  if (intent === 'book') score += 15;
  if (/urgent|急|盡快|asap|within\s*\d+\s*(day|week)/i.test(text)) score += 20;
  if (vertical === 'med_spa' && /急|urgent|盡快|asap/i.test(text)) score = Math.max(score, 85);
  return Math.min(100, Math.max(0, score));
}

function safetyEscalate(text) {
  const lower = text.toLowerCase();
  for (const flag of SAFETY_RED_FLAGS) {
    if (lower.includes(flag.toLowerCase()) || text.includes(flag)) return 1;
  }
  return 0;
}

function buildSummary(vertical, intent, extracted, missing) {
  const parts = [];
  if (extracted.serviceName) parts.push(`Service: ${extracted.serviceName}`);
  if (extracted.location) parts.push(`Location: ${extracted.location}`);
  if (extracted.preferredTime || extracted.preferredDates?.length) parts.push(`Preference: ${[extracted.preferredTime, ...(extracted.preferredDates || [])].filter(Boolean).join(', ')}`);
  if (intent === 'complaint') parts.push('Medical/safety concern – consider escalation.');
  if (missing.length) parts.push(`Missing: ${missing.join(', ')}`);
  return parts.length ? parts.join('. ') : 'Inquiry received; triage completed.';
}

function recommendedActions(intent, missing, safetyEscalate, vertical) {
  const actions = [];
  if (safetyEscalate) actions.push('Escalate to manager / medical team');
  if (intent === 'book') {
    actions.push('Offer 3 time slots');
    if (vertical === 'med_spa') actions.push('Assign to receptionist');
    if (vertical === 'training') actions.push('Assign to enrollment staff');
  }
  if (intent === 'price') actions.push('Send price list / quote');
  if (missing.length) actions.push(`Ask for top missing fields: ${missing.slice(0, 2).join(', ')}`);
  if (intent === 'complaint' && !safetyEscalate) actions.push('Reply with care instructions + suggest clinic visit');
  return actions;
}

export function runTriage(rawMessage, leadId) {
  const vertical = detectVertical(rawMessage);
  const intent = detectIntent(rawMessage);
  const extracted = normalizeFields(rawMessage, vertical);
  const missing = getMissingFields(extracted, vertical);
  const urgency = urgencyScore(rawMessage, vertical, intent, extracted);
  const escalate = safetyEscalate(rawMessage);
  const summary = buildSummary(vertical, intent, extracted, missing);
  const actions = recommendedActions(intent, missing, escalate, vertical);

  let category = vertical;
  let subcategory = extracted.serviceName || null;

  const ai_triage = {
    vertical,
    category,
    subcategory,
    intent,
    urgencyScore: urgency,
    extractedFields: extracted,
    missingFields: missing,
    summary,
    recommendedActions: actions,
    safetyEscalate: escalate,
  };
  return ai_triage;
}
