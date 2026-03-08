/**
 * Deterministic field normalization for booking-centric CRM.
 * Extracts: date/time windows, location, service, due date, duration, language, budget.
 */

const LOCATION_KEYWORDS = {
  en: ['central', 'kowloon', 'mong kok', 'tst', 'tsim sha tsui', 'causeway bay', 'wan chai', 'shatin', 'sha tin', 'tuen mun', 'yuen long', 'tai po', 'fanling', 'kwai fong', 'tseung kwan o', 'sai kung'],
  zh: ['中環', '尖沙咀', '旺角', '銅鑼灣', '灣仔', '沙田', '屯門', '元朗', '大埔', '粉嶺', '葵芳', '將軍澳', '西貢', '九龍', '港島', '新界'],
};

const BUDGET_PATTERNS = [
  /\d{4,6}\s*(hkd|hk\$|元|蚊)/i,
  /budget\s*[:\s]*([^.\n]+)/i,
  /預算\s*[約大概]?\s*([^.\n]+)/i,
  /(under|below|within)\s*\$\d+/i,
];

const DURATION_DAYS = [14, 26, 42];

function extractPreferredDateTime(text) {
  const lower = text.toLowerCase();
  const result = { preferredDates: [], preferredTime: null };
  const dayNames = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];
  for (const day of dayNames) {
    if (lower.includes(day)) result.preferredDates.push(day.slice(0, 3));
  }
  if (/\b(weekend|sat|sun|saturday|sunday)\b/i.test(text)) {
    if (!result.preferredDates.includes('sat')) result.preferredDates.push('Sat');
    if (!result.preferredDates.includes('sun')) result.preferredDates.push('Sun');
  }
  if (/\b(weekday|weekdays|mon|tue|wed|thu|fri)\b/i.test(text)) result.preferredTime = result.preferredTime || 'weekdays';
  if (/\bafter\s*(\d{1,2})\s*(pm|am|o\'?clock)?/i.test(text)) {
    const m = text.match(/after\s*(\d{1,2})/i);
    if (m) result.preferredTime = `after ${m[1]}`;
  }
  if (/\b(\d{1,2})\s*[:\s]*(\d{2})?\s*(pm|am)/i.test(text)) {
    const m = text.match(/(\d{1,2})\s*[:\s]*(\d{2})?\s*(pm|am)/i);
    if (m) result.preferredTime = result.preferredTime || `${m[1]}${m[3] || ''}`;
  }
  if (/下午|晚上|朝早|早上|中午/i.test(text)) result.preferredTime = result.preferredTime || 'afternoon/evening';
  if (/今周|這周|this week|今個星期/i.test(text)) result.preferredDates.push('this_week');
  return result;
}

function extractLocation(text) {
  const lower = text.toLowerCase();
  const found = [];
  for (const loc of LOCATION_KEYWORDS.en) {
    if (lower.includes(loc)) found.push(loc);
  }
  for (const loc of LOCATION_KEYWORDS.zh) {
    if (text.includes(loc)) found.push(loc);
  }
  if (/district\s*[:\s]*(\w+)/i.test(text)) {
    const m = text.match(/district\s*[:\s]*(\w+)/i);
    if (m) found.push(m[1]);
  }
  if (/地點\s*[為是]?\s*([^.\n,，]+)/.test(text)) {
    const m = text.match(/地點\s*[為是]?\s*([^.\n,，]+)/);
    if (m) found.push(m[1].trim());
  }
  return found.length ? found[0] : null;
}

function extractDueDateOrStart(text) {
  const patterns = [
    /(?:due date|預產期|due)\s*[:\s]*(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{2,4})/i,
    /(\d{4})年\s*(\d{1,2})月\s*(\d{1,2})日?/,
    /(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{2,4})/,
    /(?:start|開始|上工)\s*(?:date)?\s*[:\s]*(\d{1,2})[\/\-](\d{1,2})/i,
  ];
  for (const re of patterns) {
    const m = text.match(re);
    if (m) {
      const parts = m.slice(1).map(Number);
      if (parts.length >= 3) return { year: parts[2] > 50 ? 1900 + parts[2] : 2000 + parts[2], month: parts[0] || parts[1], day: parts[1] || parts[2] };
      if (parts.length === 2) return { month: parts[0], day: parts[1] };
    }
  }
  return null;
}

function extractDurationDays(text) {
  const m = text.match(/(\d+)\s*(?:days?|日|天)/i) || text.match(/(\d+)\s*day/i);
  if (m) {
    const d = parseInt(m[1], 10);
    if (DURATION_DAYS.includes(d)) return d;
    return d;
  }
  if (/14|26|42/.test(text)) {
    const n = text.match(/14|26|42/);
    if (n) return parseInt(n[0], 10);
  }
  return null;
}

function extractLanguagePreference(text) {
  if (/廣東話|粵語|cantonese|廣東/i.test(text)) return 'cantonese';
  if (/普通話|國語|mandarin|簡體|简体/i.test(text)) return 'mandarin';
  if (/bilingual|雙語|中英/i.test(text)) return 'bilingual';
  return null;
}

function extractBudgetBand(text) {
  for (const re of BUDGET_PATTERNS) {
    const m = text.match(re);
    if (m) return m[0].slice(0, 80);
  }
  if (/預算\s*[約大概]?/.test(text) && !/預算\s*[約大概]?\s*\d+/.test(text)) return null;
  return null;
}

function extractServiceName(text, vertical) {
  const lower = text.toLowerCase();
  if (vertical === 'med_spa') {
    if (/laser\s*hair|脫毛|hair removal/i.test(text)) return 'Laser hair removal';
    if (/facial|facial|面部|facial/i.test(text)) return 'Facial';
    if (/consult|consultation|諮詢|諮詢/i.test(text)) return 'Consultation';
    if (/pigment|斑|美白/i.test(text)) return 'Pigmentation treatment';
    if (/acne|暗瘡|痘/i.test(text)) return 'Acne treatment';
    if (/slim|瘦身|body/i.test(text)) return 'Slimming';
  }
  if (vertical === 'training') {
    if (/nail|美甲|指甲/i.test(text)) return 'Nail course';
    if (/beauty|美容|化妝/i.test(text)) return 'Beauty course';
    if (/massage|按摩/i.test(text)) return 'Massage course';
  }
  return null;
}

export function normalizeFields(text, vertical) {
  const preferred = extractPreferredDateTime(text);
  const location = extractLocation(text);
  const dueOrStart = extractDueDateOrStart(text);
  const durationDays = extractDurationDays(text);
  const language = extractLanguagePreference(text);
  const budget = extractBudgetBand(text);
  const service = extractServiceName(text, vertical);

  return {
    preferredDates: preferred.preferredDates.length ? preferred.preferredDates : undefined,
    preferredTime: preferred.preferredTime || undefined,
    location: location || undefined,
    dueDate: dueOrStart || undefined,
    durationDays: durationDays ?? undefined,
    languagePreference: language || undefined,
    budgetBand: budget || undefined,
    serviceName: service || undefined,
  };
}

export function getMissingFields(extracted, vertical) {
  const missing = [];
  if (vertical === 'med_spa') {
    if (!extracted.serviceName) missing.push('service');
    if (!extracted.preferredDates?.length && !extracted.preferredTime) missing.push('preferred_date_time');
    if (!extracted.location) missing.push('location');
  }
  if (vertical === 'training') {
    if (!extracted.serviceName) missing.push('course_name');
    if (!extracted.preferredDates?.length && !extracted.preferredTime) missing.push('schedule_preference');
  }
  return missing;
}
