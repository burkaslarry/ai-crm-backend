/**
 * CRM API entry. Express + JSON body; CORS enabled for frontend.
 * All routes use parameterized DB queries and validated input (see lib/validation.js).
 */

import express from 'express';
import cors from 'cors';
import { initSchema } from './db/schema.js';
import leadsRouter from './routes/leads.js';
import inquiriesRouter from './routes/inquiries.js';
import aiRouter from './routes/ai.js';
import automationsRouter from './routes/automations.js';

initSchema();

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
