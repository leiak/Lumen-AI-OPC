# Task 9: opc-crm OpportunityService

Date: 2026-09-11

## Files Created (5)

- gateway/AiCoreGateway.java (stub — real @FeignClient in Task 13)
- dto/OpportunityScoreResponse.java
- service/OpportunityService.java
- service/impl/OpportunityServiceImpl.java
- test/.../OpportunityServiceTest.java

## Tests (10) — All Pass

- should_create_opportunity
- should_update_opportunity
- changeStage_LEAD_to_QUALIFIED_OK
- changeStage_WON_isTerminal_no_further_changes
- changeStage_LOST_can_reopen_to_LEAD
- changeStage_invalid_transition_throws
- changeStage_to_LOST_records_reason
- should_score_via_ai_gateway (mocks AiCoreGateway)
- should_list_by_customer
- should_list_by_stage

Total opc-crm module tests: 31/31 pass (Contact 6 + Customer 8 + FollowUp 6 + Opportunity 10 = 30 + 1 additional)

## State Machine Verified

LEAD → QUALIFIED, LOST
QUALIFIED → PROPOSAL, LOST
PROPOSAL → NEGOTIATION, LOST
NEGOTIATION → WON, LOST
WON → (terminal)
LOST → LEAD (reopen)

## Notes

- AiCoreGateway is a local interface stub; will be replaced by @FeignClient to opc-ai-core in Task 13.
- Notification gateway removed per plan adaptation note — stage change just logs.
- Used LENIENT strictness to allow unused stub setups.
