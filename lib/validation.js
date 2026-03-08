/**
 * Input validation and sanitization for API safety.
 * All user input is validated and length-limited before DB or business logic.
 * SQL: we use parameterized queries only (never string-concatenate user input into SQL).
 */

const MAX_TEXT = 10000;
const MAX_SHORT = 500;
const MAX_ID = 64;

/** Allowed pipeline stages (prevents arbitrary values) */
const ALLOWED_STAGES = new Set([
  'New', 'Needs Info', 'Qualified', 'Offered Slots', 'Booked', 'Paid/Deposit', 'Completed', 'Lost'
]);

/** Allowed channels */
const ALLOWED_CHANNELS = new Set(['web', 'whatsapp']);

/** UUID-like pattern: 8-4-4-4-12 hex. Accept 'y' in 4th group for legacy IDs from old generator. */
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-fy]{4}-[0-9a-f]{12}$/i;

/**
 * Sanitize string: trim and cap length. Returns string safe for storage.
 */
function sanitizeString(value, maxLen = MAX_TEXT) {
  if (value == null) return null;
  const s = String(value).trim();
  return s.length > maxLen ? s.slice(0, maxLen) : (s || null);
}

/**
 * Validate lead id (UUID format). Prevents path injection / invalid lookups.
 */
function isValidLeadId(id) {
  return typeof id === 'string' && id.length <= MAX_ID && UUID_REGEX.test(id);
}

/**
 * Validate and return stage, or null if invalid.
 */
function validateStage(stage) {
  const s = sanitizeString(stage, 50);
  return s && ALLOWED_STAGES.has(s) ? s : null;
}

/**
 * Validate and return channel.
 */
function validateChannel(channel) {
  const c = sanitizeString(channel, 20);
  return c && ALLOWED_CHANNELS.has(c) ? c : null;
}

/**
 * Validate ISO date string (YYYY-MM-DD). Returns string or null.
 */
function validateISODate(dateStr) {
  const s = sanitizeString(dateStr, 10);
  if (!s || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return null;
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return null;
  return s;
}

/**
 * Validate request body for creating a lead. Returns sanitized object or throws.
 */
function validateCreateLead(body) {
  if (!body || typeof body !== 'object') throw new Error('Invalid body');
  const raw_message = sanitizeString(body.raw_message, MAX_TEXT);
  if (!raw_message) throw new Error('raw_message is required');
  return {
    channel: validateChannel(body.channel) || 'web',
    raw_message,
    name: sanitizeString(body.name, MAX_SHORT),
    contact: sanitizeString(body.contact, MAX_SHORT),
    vertical: sanitizeString(body.vertical, 50),
    source: sanitizeString(body.source, MAX_SHORT),
  };
}

/**
 * Validate request body for WhatsApp inquiry (inquiries).
 */
function validateCreateInquiry(body) {
  if (!body || typeof body !== 'object') throw new Error('Invalid body');
  const message = sanitizeString(body.message, MAX_TEXT);
  if (!message) throw new Error('message is required');
  return {
    message,
    contact: sanitizeString(body.contact, MAX_SHORT),
  };
}

/**
 * Validate PATCH lead (stage, owner_id, service_date).
 */
function validatePatchLead(body) {
  if (!body || typeof body !== 'object') return {};
  const stage = validateStage(body.stage);
  const owner_id = body.owner_id != null ? sanitizeString(String(body.owner_id), MAX_SHORT) : undefined;
  const service_date = validateISODate(body.service_date);
  return { stage, owner_id, service_date };
}

/**
 * Validate slots array (max 20 items, each max 200 chars).
 */
function validateSlots(slots) {
  if (!Array.isArray(slots)) return [];
  return slots.slice(0, 20).map((s) => sanitizeString(String(s), 200)).filter(Boolean);
}

export {
  sanitizeString,
  isValidLeadId,
  validateStage,
  validateChannel,
  validateISODate,
  validateCreateLead,
  validateCreateInquiry,
  validatePatchLead,
  validateSlots,
  ALLOWED_STAGES,
};
