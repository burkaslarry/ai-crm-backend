# Backend — JavaScript safety & alternatives

## Safety measures in this codebase

- **SQL**: All queries use **parameterized statements** (`?` placeholders). No string concatenation of user input into SQL. This prevents SQL injection.
- **Input validation**: Request body and query params are validated and length-limited in `lib/validation.js` (sanitizeString, validateStage, validateISODate, etc.) before use.
- **IDs**: Lead and task IDs are UUIDs we generate, or validated with `isValidLeadId()` before use in queries.
- **JSON**: We parse JSON from our own DB (triage blobs); request JSON is limited (express.json limit) and we don’t eval user input.

For a **demo or internal tool**, this is a reasonable level of safety. For **high-compliance or regulated production**, consider the following.

## When to consider Kotlin + Spring Boot

- Stricter typing and null-safety across the stack.
- Spring Security (auth, rate limiting, CSRF) and audit trails out of the box.
- JVM ecosystem and ops tooling (monitoring, compliance).
- Same API design (REST, same payloads) can be reimplemented in Spring Boot; this Node API can serve as the spec.

## Running scheduled jobs in production

- **Reminder (2 days before, 24 hours before)** and **Feedback (1 day after service)** are stored in `scheduled_jobs`. They are **processed** by `POST /api/automations/process-scheduled`.
- In production, call that endpoint on a schedule (e.g. cron every 15 minutes), or run a small worker that calls it in a loop. This backend does not run a built-in scheduler.
