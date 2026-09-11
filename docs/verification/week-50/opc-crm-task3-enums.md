# Task 3: opc-crm domain enums

Date: 2026-09-11

## Files Created (5)

- `OpportunityStage.java` (with state machine: `canTransitionTo`, `isTerminal`, `isActive`)
- `CustomerLevel.java` (A, B, C, D)
- `CustomerSource.java` (REFERRAL, AD, WEBSITE, COLD_CALL, OTHER)
- `ContractStatus.java` (DRAFT, ACTIVE, EXPIRED, TERMINATED) + `isActive()`
- `OrderStatus.java` (PENDING, PAID, SHIPPED, COMPLETED, CANCELLED) + `isFinal()`

## Verification

- Compile: pass (`BUILD SUCCESS`, 5.244 s, 6 source files compiled)
- State machine: `LEAD → QUALIFIED / LOST`, `QUALIFIED → PROPOSAL / LOST`, `PROPOSAL → NEGOTIATION / LOST`, `NEGOTIATION → WON / LOST`, `WON` terminal, `LOST → LEAD` (reopen)

## Commit

- `04355ad` — feat(crm): Task 3 — domain enums (stage state machine + level/source/status)
- Pushed to origin/main.
