#!/bin/bash
# =============================================================================
# Create additional databases required by microservices.
# This script runs as part of the postgres entrypoint (docker-entrypoint-initdb.d).
# The default database (spendsmart_expense) is created by POSTGRES_DB env var.
# =============================================================================

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Create extra databases for services that use their own DB
    SELECT 'CREATE DATABASE spendsmart_ocr'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'spendsmart_ocr')\gexec

    SELECT 'CREATE DATABASE spendsmart_gst'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'spendsmart_gst')\gexec

    SELECT 'CREATE DATABASE spendsmart_workflow'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'spendsmart_workflow')\gexec
EOSQL

echo "Additional databases created successfully."
