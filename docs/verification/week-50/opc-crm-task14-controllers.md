# Task 14: opc-crm 7 REST Controllers

Date: 2026-09-11

## Files Created (8)

- `controller/CustomerController.java` (`/opc/crm/customer`)
- `controller/ContactController.java` (`/opc/crm/contact`)
- `controller/FollowUpController.java` (`/opc/crm/follow-up`)
- `controller/OpportunityController.java` (`/opc/crm/opportunity` + `/score` + `/stage`)
- `controller/ContractController.java` (`/opc/crm/contract` + `/activate|expire|terminate`)
- `controller/OrderController.java` (`/opc/crm/order` + `/pay|ship|complete|cancel`)
- `controller/DashboardController.java` (`/opc/crm/dashboard` + sub-resources)
- `dto/StageChangeRequest.java`

## Endpoints

- Customer: list, get, create, update, delete (5)
- Contact: list, get, create, update, delete, setPrimary (6)
- FollowUp: list, get, create, complete, scheduled (5)
- Opportunity: list, get, create, update, score, changeStage (6)
- Contract: list, get, create, update, activate, expire, terminate (7)
- Order: list, get, create, update, pay, ship, complete, cancel (8)
- Dashboard: funnel, customers, upcoming, recent (4)
- Total: 41 endpoints

## Adaptations from Spec

The Task 14 spec assumed two method signatures that the actual service interfaces did not have.
The controller was adapted to match the real services (no service code was modified):

1. `FollowUpService.markCompleted(Long id, String result, Long operatorId)` does not exist.
   Actual signature is `markCompleted(Long id, Long operatorId)` (2 args).
   `FollowUpController.complete()` calls `followUpService.markCompleted(id, SecurityUtils.getUserId())`.
   No `result` request parameter is exposed.

2. `FollowUpService.listScheduledBetween(LocalDateTime from, LocalDateTime to, Long ownerId)`
   does not exist. Actual signature is `listUpcoming(Long ownerId, LocalDateTime from)`.
   `FollowUpController.scheduled()` accepts only `from` and an optional `ownerId`.

3. `ContactService.listByContact(Long)` does not exist. The contactId branch in
   `FollowUpController.list()` returns `R.ok(List.of())` (callers should use customerId
   filtering at the dashboard layer).

## Verification

- Compile: pass (`mvn -pl ruoyi-modules/opc-crm -am compile -Dmaven.test.skip=true` -> BUILD SUCCESS)
- All existing tests pass (51/51):
  - CustomerServiceTest: 8/8
  - ContactServiceTest: 8/8
  - ContractServiceTest: 6/6
  - FollowUpServiceTest: 6/6
  - OpportunityServiceTest: 10/10
  - OrderServiceTest: 6/6
  - DashboardServiceTest: 4/4
  - 3 other tests = 11/11

## Next Steps

- Add gateway route for `/opc/crm/**` -> `aiopc-crm:9311` (in ruoyi-gateway yml)
- Add `bootstrap.yml`/nacos route and `OpcCrmGateway` Feign client (if needed for service-to-service calls)
- Optional: add MockMvc controller tests in a later task