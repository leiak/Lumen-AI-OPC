# Task 11: opc-crm OrderService

Date: 2026-09-11

## Files Created (3)

- service/OrderService.java
- service/impl/OrderServiceImpl.java
- test/.../OrderServiceTest.java

## Tests (6)

- should_create_order (default PENDING)
- should_reject_blank_items_json
- should_pay_pending_order (PENDING→PAID, paidAt=now)
- should_ship_paid_order (PAID→SHIPPED, shippedAt=now)
- should_complete_shipped_order (SHIPPED→COMPLETED)
- should_cancel_pending_order (→CANCELLED with reason)

## State Machine

PENDING → PAID → SHIPPED → COMPLETED
PENDING/PAID → CANCELLED
