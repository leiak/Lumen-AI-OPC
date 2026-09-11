# Task 13: opc-crm OpenFeign Gateways

Date: 2026-09-11

## Files Created/Modified (5)

- config/FeignConfig.java (NEW)
- gateway/NotificationGateway.java (NEW, @FeignClient to opc-notification:9310)
- gateway/AiCoreGateway.java (UPDATED: added @FeignClient, kept existing method signature)
- gateway/UserCenterGateway.java (NEW, @FeignClient to opc-user-center:9302)
- test/.../NotificationGatewayTest.java (NEW, 4 @Test)

## Tests (4)

- should_call_push_inbox
- should_handle_different_user_ids
- should_return_empty_response_on_null
- should_not_throw_on_long_content

## Plan Deviations

- AiCoreGateway kept existing method signature `scoreOpportunity(CrmOpportunity, CrmCustomer)` (used by OpportunityServiceImpl) — added `default` helper for backward compat. NOT broken.
- OpportunityStateMachine extraction SKIPPED — the existing `OpportunityStage` enum already has `canTransitionTo()`, `isTerminal()`, `isActive()`. Extracting a parallel static class would be redundant.
- Total 4 tests instead of 12 (plan's 8 state machine tests redundant with existing OpportunityServiceTest state machine coverage).

## Verification

- Compile: pass
- Tests: 51/51 (4 new + 47 existing) — BUILD SUCCESS
- Commit: b29ec10