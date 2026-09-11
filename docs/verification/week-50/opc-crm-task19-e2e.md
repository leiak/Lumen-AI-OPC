# Task 19: opc-crm E2E Test

Date: 2026-09-11

## File Created

- tmp_e2e/crm_e2e.py (20 scenarios)

## Coverage

- Customer: list, get, create, validation
- Contact: list, create
- FollowUp: create, complete
- Opportunity: create, change stage, score (LLM)
- Contract: create, activate
- Order: create, pay, ship
- Dashboard: funnel, customer summary
- Validation: blank name, invalid stage transition

## Results

Ran 19/20 (3 opportunity-dependent tests skipped because create failed).
Pass: 18/19. Fail: 1.

```
Total: 19  Pass: 18  Fail: 1
FAILURES:
  - create opportunity 200: HTTP=200 R=500 Column 'score' cannot be null
```

## Concern: Backend Bug — Opportunity Create Fails

`POST /opc/crm/opportunity` returns `code=500` with:

```
java.sql.SQLIntegrityConstraintViolationException: Column 'score' cannot be null
```

### Root cause

- `mysql-initdb.d/09-opc-crm-schema.sql:68` defines `score INT NOT NULL DEFAULT 0`
- `CrmOpportunityMapper.xml` INSERT explicitly includes the `score` column:
  ```sql
  INSERT INTO opc_crm_opportunity (
      customer_id, name, amount, stage, score, score_reason, expected_close,
      owner_id, lost_reason, create_by, create_time, update_by, update_time, deleted
  ) VALUES (
      #{customerId}, #{name}, #{amount}, #{stage}, #{score}, #{scoreReason}, #{expectedClose},
      #{ownerId}, #{lostReason}, #{createBy}, NOW(), #{updateBy}, NOW(), #{deleted}
  )
  ```
- `OpportunityServiceImpl.create()` does NOT set `score` (it's null from JSON body)
- When `score` column is explicitly listed in INSERT, MySQL applies the supplied
  value (NULL) instead of the column DEFAULT — so NOT NULL constraint fires.

### Fix (recommended, NOT applied in this task per scope)

Either:
1. `OpportunityServiceImpl.create()` add `if (opportunity.getScore() == null) opportunity.setScore(0);` before insert (matches existing pattern used for `stage` and `deleted`)
2. Change mapper XML to `IFNULL(#{score}, 0)` in VALUES
3. Drop NOT NULL on `score` (least preferred — loses data integrity)

Option 1 is consistent with the existing service-layer default-fixup pattern.

### Impact

- Frontend cannot create any opportunity → blocks Customer 360 "create opportunity" flow
- Dashboard funnel uses seed data only — would not reflect newly created opportunities
- Same bug probably affects future `update()` flow when `score` is unset on the row

### Suggested follow-up

Create a small fix task (Task 20 or hotfix) to add the score=0 default to the service
layer. Re-run `tmp_e2e/crm_e2e.py` and confirm opportunity create returns 200.