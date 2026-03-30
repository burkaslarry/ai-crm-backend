#!/usr/bin/env bash
# Insert Zomate follow-up cases into production Postgres (requires DATABASE_URL).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
URL="${DATABASE_URL:?Set DATABASE_URL to your production Postgres connection string}"
if ! command -v psql >/dev/null 2>&1; then
  echo "psql not found. Install PostgreSQL client (e.g. brew install libpq && brew link --force libpq)."
  exit 1
fi
psql "$URL" -v ON_ERROR_STOP=1 -f "$ROOT/scripts/insert-zomate-follow-up-cases.sql"
echo "Inserted Zomate follow-up cases."
