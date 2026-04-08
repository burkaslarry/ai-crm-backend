#!/usr/bin/env bash
# Insert Zomate demo data into production Postgres (requires DATABASE_URL).
# Usage:
#   ./scripts/run-insert-zomate-cases.sh              # follow_up_cases only
#   ./scripts/run-insert-zomate-cases.sh --leads      # leads + ai_triage + timeline
#   ./scripts/run-insert-zomate-cases.sh --all        # both
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
URL="${DATABASE_URL:?Set DATABASE_URL to your production Postgres connection string}"
if ! command -v psql >/dev/null 2>&1; then
  echo "psql not found. Install PostgreSQL client (e.g. brew install libpq && brew link --force libpq)."
  exit 1
fi
MODE="${1:-}"
case "$MODE" in
  --leads)
    psql "$URL" -v ON_ERROR_STOP=1 -f "$ROOT/scripts/insert-zomate-cases-as-leads.sql"
    echo "Inserted Zomate leads (+ triage + timeline)."
    ;;
  --all)
    psql "$URL" -v ON_ERROR_STOP=1 -f "$ROOT/scripts/insert-zomate-follow-up-cases.sql"
    psql "$URL" -v ON_ERROR_STOP=1 -f "$ROOT/scripts/insert-zomate-cases-as-leads.sql"
    echo "Inserted follow_up_cases and leads."
    ;;
  *)
    psql "$URL" -v ON_ERROR_STOP=1 -f "$ROOT/scripts/insert-zomate-follow-up-cases.sql"
    echo "Inserted Zomate follow-up cases."
    ;;
esac
