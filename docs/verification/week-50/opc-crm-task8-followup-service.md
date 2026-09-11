# Task 8: opc-crm FollowUpService

Date: 2026-09-11

## Files Created (3)

- service/FollowUpService.java
- service/impl/FollowUpServiceImpl.java
- test/.../FollowUpServiceTest.java

## Tests (6/6 PASS)

- should_create_follow_up
- should_reject_blank_content
- should_list_by_customer
- should_list_by_owner
- should_list_upcoming
- should_mark_follow_up_completed

## Verification

- FollowUpServiceTest: 6/6 PASS
- Full opc-crm suite: 21/21 PASS (1 mapper XML parse + 6 Contact + 8 Customer + 6 FollowUp)
- Compile: pass
- Commit: 531f2c4 pushed to origin/main

## Notes / Deviations from Plan

The plan's Task 8 instructions referenced mapper methods and entity fields that did
not actually exist in the codebase from Task 5. To honor the "Don't modify entities or
mappers" constraint, the tests were adapted to the actual API surface:

- Plan referenced `selectByContactId` → mapper has only `selectByOwnerId` / `selectByCustomerId`.
  Adapted `should_list_by_owner` to use `selectByOwnerId`.
- Plan referenced `selectByScheduledBetween(from, to, ownerId)` → mapper has
  `selectUpcoming(ownerId, now)`. Adapted `should_list_upcoming` to use that signature.
- Plan referenced entity fields `result` and `completedAt` → entity has neither.
  The CrmFollowUp entity exposes `nextAt` for the next reminder; "mark completed" is
  implemented as clearing `nextAt` (no further reminder needed). Updated
  `should_mark_follow_up_completed` accordingly.

FollowUpResult enum was deliberately NOT created since the entity has no `result`
field — the enum would be dead code.
