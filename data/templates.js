/**
 * WhatsApp draft templates per vertical and intent.
 * Variables: name, service, location, slots, booking link. Safety disclaimer for med spa complaint/symptoms.
 */

const SAFETY_DISCLAIMER = '⚠️ This is not medical advice. If you have severe redness, swelling, breathing difficulty or chest pain, please seek immediate medical attention.';

export const templates = {
  med_spa: {
    book: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! Thanks for your interest in ${vars.service || 'our service'}. Here are 3 suggested slots:\n\n${vars.slots || 'Slot 1, Slot 2, Slot 3'}\n\nPlease reply with your preferred time, or book here: ${vars.bookingLink || '[Booking link]'}`,
    price: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! Here’s our price list for ${vars.service || 'treatments'}. We’ll send the PDF shortly. Any questions, just reply.`,
    info: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! Thanks for reaching out. For ${vars.service || 'our services'} at ${vars.location || 'our clinic'}, we’d be happy to share details. Reply with your preferred date/time and we’ll send availability.`,
    complaint: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}, we’re sorry to hear you’re experiencing this.\n\n${SAFETY_DISCLAIMER}\n\nWe recommend you contact our clinic for a follow-up. Please call us or reply here and we’ll arrange for someone to assist you.`,
  },
  training: {
    book: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! Thanks for your interest in ${vars.service || 'our course'}. Next intake slots:\n\n${vars.slots || 'Slot 1, Slot 2, Slot 3'}\n\nReply with your preference or book here: ${vars.bookingLink || '[Booking link]'}`,
    price: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! Course fee for ${vars.service || 'this course'} depends on level and format. We’ll send the fee schedule shortly.`,
    info: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! For ${vars.service || 'our courses'} we have weekday/weekend options. Tell us your preferred schedule and we’ll send the next intake dates.`,
    complaint: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}, we’re sorry for any inconvenience. We’ll have our team follow up with you shortly.`,
  },
  scheduled: {
    reminder_2d: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! Reminder: your appointment for ${vars.service || 'our service'} is in 2 days (${vars.service_date || 'scheduled date'}). See you soon!`,
    reminder_24h: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! Reminder: your appointment is tomorrow. We look forward to seeing you.`,
    feedback_1d: (vars) => `Hi${vars.name ? ` ${vars.name}` : ''}! Thank you for visiting us. We'd love your feedback — please take 1 min: [Feedback link]. Thank you!`,
  },
};

export function getDraft(vertical, intent, vars = {}) {
  const v = templates[vertical] || templates.med_spa;
  const fn = v[intent] || v.info;
  return fn(vars);
}

export function getSafetyDisclaimer() {
  return SAFETY_DISCLAIMER;
}
