# Task 2: opc-crm MySQL schema + seed

Date: 2026-09-11

## Files Created

- springboot3/sql/migrations/V20260918__crm_schema.sql (6 tables)
- springboot3/sql/seed/crm_seed.sql (10 customers/30 contacts/50 follow-ups/20 opps/8 contracts/12 orders)
- springboot3/deploy/mysql-initdb.d/09-opc-crm-schema.sql
- springboot3/deploy/mysql-initdb.d/95-opc-crm-seed.sql

## Tables (6)

- opc_crm_customer
- opc_crm_contact
- opc_crm_follow_up
- opc_crm_opportunity
- opc_crm_contract
- opc_crm_order

## Verification

- Schema applied to live MySQL: yes
- Seed data inserted: yes
- Row counts: customers=10, contacts=30, follow-ups=50, opps=20, contracts=8, orders=12

## Commit

See git log.
