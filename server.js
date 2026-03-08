#!/usr/bin/env node
// This backend is deprecated. Do not run it. Use the Kotlin backend instead.
console.error('DEPRECATED: Do not use the Node/SQLite backend.');
console.error('Run the Kotlin backend: from the project root run ./run-local.sh');
console.error('Or: cd ai-crm-backend-kotlin && export DATABASE_URL=postgresql://localhost:5432/aicrm && ./gradlew bootRun');
process.exit(1);
