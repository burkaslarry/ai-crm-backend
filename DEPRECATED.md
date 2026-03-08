# Deprecated: Node/SQLite backend

**Do not use this backend for the app.** It used SQLite (`db/schema.js`) and can hit "attempt to write a readonly database" and other issues.

**Use the Kotlin backend instead:** from repo root run `./run-local.sh`, which starts `ai-crm-backend-kotlin` (Kotlin Spring Boot + H2 or PostgreSQL). The frontend proxies `/api` to port 3001 where the Kotlin backend runs.

This folder (`ai-crm-backend`) is kept only for reference. All features (leads, slots, automations, triage) are in `ai-crm-backend-kotlin`.
