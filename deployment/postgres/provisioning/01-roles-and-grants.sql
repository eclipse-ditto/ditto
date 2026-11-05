-- Copyright (c) 2026 Contributors to the Eclipse Foundation
--
-- See the NOTICE file(s) distributed with this work for additional
-- information regarding copyright ownership.
--
-- This program and the accompanying materials are made available under the
-- terms of the Eclipse Public License 2.0 which is available at
-- http://www.eclipse.org/legal/epl-2.0
--
-- SPDX-License-Identifier: EPL-2.0
--
-- =============================================================================
-- Ditto PostgreSQL persistence backend — two-role provisioning
-- =============================================================================
--
-- Implements the two-role split from the Postgres persistence-backend spec
-- (§11 "Role privileges", resolves §6 important #4, finding [G5]).
--
--   schema  ditto      -- owned by ditto_ddl; holds *_journal, *_journal_seq,
--                         *_snaps (4 entity types + connectivity's remember-entities
--                         store) and schema_version.
--   ditto_ddl  -- DDL role: OWNS the schema and all tables; runs CREATE in the
--                 bootstrap DDL transaction (PostgresSchemaManager, [H2]).
--                 Used via the `ddl-credentials` block in the HOCON (§7)
--                 by the schema bootstrap, the runtime self-heal and the
--                 per-path search index builds (CREATE/DROP INDEX
--                 CONCURRENTLY).
--   ditto_app  -- runtime role: DML-only (SELECT/INSERT/UPDATE/DELETE).
--                 No CREATE, no ownership. This is the role behind the runtime
--                 `uri` (${POSTGRES_URI}) the actors use on the hot path.
--
-- PROVISIONING ORDER (mandatory — see deployment/postgres/README.md):
--   1. Run THIS script ONCE as a superuser / database owner. It establishes the
--      roles, schema ownership, and — critically — the ALTER DEFAULT PRIVILEGES
--      that make every FUTURE table ditto_ddl creates automatically grant DML to
--      ditto_app. The default privileges MUST be set BEFORE any bootstrap DDL
--      runs; PostgreSQL only applies default privileges to objects created AFTER
--      the ALTER DEFAULT PRIVILEGES statement — it does NOT retroactively grant
--      on tables that already exist.
--   2. THEN start the Ditto application. On first boot PostgresSchemaManager
--      connects as ditto_ddl and runs the bootstrap DDL (CREATE TABLE / CREATE
--      INDEX) inside a single pg_advisory_xact_lock'd transaction [H2]. Because
--      step 1 already set the default privileges, each newly created table is
--      born with the SELECT/INSERT/UPDATE/DELETE grant to ditto_app — no second
--      provisioning pass is required after the app boots.
--
-- Usage (psql variables let you keep passwords out of the file):
--   psql -v ddl_pw='...' -v app_pw='...' -f 01-roles-and-grants.sql
--
-- The schema name is `ditto` and the roles are ditto_ddl / ditto_app, matching
-- the spec; the role passwords are supplied as :ddl_pw / :app_pw psql variables.
-- =============================================================================

-- --- Roles ------------------------------------------------------------------
-- Both roles can LOGIN. Re-running on an existing cluster: drop the CREATE ROLE
-- lines (or wrap them) — roles are cluster-global and cannot use IF NOT EXISTS.
CREATE ROLE ditto_ddl LOGIN PASSWORD :'ddl_pw';
CREATE ROLE ditto_app LOGIN PASSWORD :'app_pw';

-- --- Schema ownership -------------------------------------------------------
-- The DDL role OWNS the schema, which implicitly grants it CREATE there.
-- This is intended to run exactly once on a fresh database; if you are
-- re-provisioning an existing schema, skip this statement (the schema and its
-- owner already exist) and run only the GRANT/REVOKE/ALTER block below.
CREATE SCHEMA ditto AUTHORIZATION ditto_ddl;  -- DDL role owns the schema => can CREATE

-- --- Schema-level privileges ------------------------------------------------
GRANT USAGE, CREATE ON SCHEMA ditto TO ditto_ddl;  -- explicit; redundant with ownership
GRANT USAGE             ON SCHEMA ditto TO ditto_app;  -- runtime may resolve names
REVOKE CREATE ON SCHEMA ditto FROM ditto_app;       -- no DDL on the runtime path
REVOKE CREATE ON SCHEMA ditto FROM PUBLIC;          -- lock down implicit PUBLIC CREATE

-- --- Default privileges for FUTURE tables -----------------------------------
-- MUST be set BEFORE the bootstrap DDL runs (see PROVISIONING ORDER above):
-- every table ditto_ddl creates AFTER this statement auto-grants DML to ditto_app.
ALTER DEFAULT PRIVILEGES FOR ROLE ditto_ddl IN SCHEMA ditto
      GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ditto_app;
-- Deliberately NO "GRANT ... ON SEQUENCES": every PK uses
-- GENERATED ALWAYS AS IDENTITY, whose INSERT needs NO USAGE ON SEQUENCE.
-- (Contrast with SERIAL / DEFAULT nextval(), which WOULD require a sequence
--  grant. The schema does not use SERIAL precisely to avoid this — do not add
--  SERIAL columns. Only if SERIAL is ever introduced add:
--    ALTER DEFAULT PRIVILEGES FOR ROLE ditto_ddl IN SCHEMA ditto
--          GRANT USAGE ON SEQUENCES TO ditto_app; )
-- Deliberately NO "GRANT TRUNCATE": cleanup performs physical row removal with
-- DELETE (range-DELETE per pid), never TRUNCATE — so no TRUNCATE privilege is
-- granted. A reviewer seeing the absence here should know it is intentional.

-- --- DML on any pre-existing tables (re-provisioning safety net) -------------
-- Default privileges only affect FUTURE objects; this covers tables that may
-- already exist when re-running provisioning against a populated schema.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ditto TO ditto_app;

-- --- search_path ------------------------------------------------------------
-- So unqualified table names resolve to the ditto schema for both roles.
ALTER ROLE ditto_ddl SET search_path = ditto;
ALTER ROLE ditto_app SET search_path = ditto;

-- --- statement_timeout ------------------------------------------------------
-- Server-side cap on every statement of the runtime role (SQLSTATE 57014 once
-- exceeded), same value as the ditto.postgresql.statement-timeout default.
-- Ditto also sends that setting as a connection startup parameter, but a
-- PgBouncer that lists statement_timeout in ignore_startup_parameters drops it
-- (in any pool mode), and the timeout is then silently not enforced.
-- A role default applies to every server session opened AS ditto_app, also
-- behind PgBouncer, as long as PgBouncer logs in to PostgreSQL as the client's
-- role (no forced user= in its [databases] entry). Where the startup parameter
-- reaches the server, it overrides this default; statement-timeout = 0 sends
-- none, so this default applies.
-- Deliberately NO default for ditto_ddl: the schema bootstrap and CREATE INDEX
-- CONCURRENTLY builds run as the DDL role and must not be cut off by a
-- role-level timeout. Without a separate DDL role (empty ddl-credentials),
-- Ditto runs its DDL as the runtime role and that role's default applies to it
-- too; see deployment/postgres/README.md.
ALTER ROLE ditto_app SET statement_timeout = '60s';
