/**
 * CRM API entry. Express + JSON body; CORS enabled for frontend.
 * All routes use parameterized DB queries and validated input (see lib/validation.js).
 */

import express from 'express';
import cors from 'cors';
import { db, initSchema } from './db/schema.js';
import { runSeed } from './db/seed.js';
import leadsRouter from './routes/leads.js';
import inquiriesRouter from './routes/inquiries.js';
import aiRouter from './routes/ai.js';
import automationsRouter from './routes/automations.js';

initSchema();

// Seed on first start when DB is empty (e.g. Render ephemeral disk or new deploy).
const ruleCount = db.prepare('SELECT COUNT(*) as n FROM automation_rules').get().n;
if (ruleCount === 0) {
  runSeed();
  console.log('Seeded default rules and sample leads.');
}

const app = express();
app.use(cors());
app.use(express.json({ limit: '100kb' })); // cap body size

app.use('/api/leads', leadsRouter);
app.use('/api/inquiries', inquiriesRouter);
app.use('/api/ai', aiRouter);
app.use('/api/automations', automationsRouter);

app.get('/api/health', (req, res) => res.json({ ok: true }));

const PORT = process.env.PORT || 3001;
app.listen(PORT, () => console.log(`CRM API running at http://localhost:${PORT}`));
