# AI CRM API — Kotlin Spring Boot

Booking-centric CRM API (醫美 Aesthetic / Med Spa, Training). Same API contract as the Node.js backend; runs on H2 (file-based) and is deployable as a Docker image.

## Build and run locally

```bash
./gradlew bootRun
```

API: http://localhost:3001. Health: http://localhost:3001/api/health.

## Build JAR

```bash
./gradlew bootJar
# JAR: build/libs/ai-crm-api-1.0.0.jar
java -jar build/libs/ai-crm-api-1.0.0.jar
```

## Docker

```bash
docker build -t ai-crm-api .
docker run -p 3001:3001 -e PORT=3001 ai-crm-api
```

Data is stored in `./data/crm` inside the container (ephemeral unless you mount a volume).

## Deploy on Render

The app uses the `render` Spring profile and expects **DATABASE_URL** (Postgres `postgres://...` URL).

- **If you use the root repo Blueprint** (`render.yaml`): the web service is linked to database `ai-crm-db` and Render sets DATABASE_URL automatically.
- **If you use your own database:** set **DATABASE_URL** in the web service environment: Render Dashboard → **Web Services** → **ai-crm-backend** → **Environment** → add **DATABASE_URL** with the connection string from your database’s **Connect** tab (Internal or External URL).

If DATABASE_URL is missing, the app fails at startup with a message pointing to this setup.

## API

- `GET /api/health` — health check
- `GET /api/leads` — list leads (optional `?channel=web|whatsapp`, `?stage=...`)
- `GET /api/leads/:id` — lead detail with triage, tasks, timeline, slot_suggestions
- `POST /api/leads` — create lead (web form); body: `raw_message`, `channel`, `name`, `contact`, etc.
- `PATCH /api/leads/:id` — update `stage`, `owner_id`, `service_date`
- `POST /api/leads/:id/slots` — save slot suggestions; body: `{ "slots": ["...", ...] }`
- `POST /api/leads/:id/tasks/:taskId/complete` — mark task complete
- `POST /api/inquiries` — WhatsApp paste; body: `message`, `contact`
- `POST /api/ai/triage` — run triage; body: `rawMessage` or `leadId`
- `POST /api/ai/draft` — get WhatsApp draft; body: `vertical`, `intent`, `name`, `service`, `slots`, etc.
- `GET /api/automations/rules` — list automation rules
- `POST /api/automations/rules/seed` — seed default rules
- `POST /api/automations/apply/:leadId` — apply automations for a lead
- `POST /api/automations/process-scheduled` — process due scheduled jobs
- `GET /api/automations/scheduled-jobs` — list scheduled jobs (`?status=pending` optional)

## Seed

On first start, if there are no automation rules, the app seeds default rules and 5 sample leads. No separate seed command needed.

## Tech

- Kotlin 1.9, Spring Boot 3.2, Spring JDBC, H2 (file), Jackson (snake_case JSON).
