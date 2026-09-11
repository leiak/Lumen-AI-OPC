# Task 10: opc-crm ContractService

Date: 2026-09-11

## Files Created (3)

- springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/ContractService.java
- springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/ContractServiceImpl.java
- springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/ContractServiceTest.java

## Tests (6)

- should_create_contract (default DRAFT)
- should_reject_duplicate_contract_no
- should_activate_draft_contract (DRAFT->ACTIVE, signedAt=now)
- should_expire_active_contract (ACTIVE->EXPIRED)
- should_terminate_active_contract (->TERMINATED with reason)
- should_list_by_customer

## Verification

- Compile: PASS
- ContractServiceTest: 6/6 PASS
- Full opc-crm suite: 37/37 PASS (no regressions)

## Commit

- 5f47062 feat(crm): Task 10 — ContractService + 6 @Test (create/activate/expire/terminate)
- Pushed to origin/main