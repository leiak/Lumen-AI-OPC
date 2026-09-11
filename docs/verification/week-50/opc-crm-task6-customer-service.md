# Task 6: opc-crm CustomerService

Date: 2026-09-11

## Files Created (3)

- service/CustomerService.java
- service/impl/CustomerServiceImpl.java
- test/.../CustomerServiceTest.java

## Tests (8 @Test)

1. should_create_customer_with_ownerId_from_security
2. should_reject_blank_name
3. should_update_existing_customer
4. should_throw_when_update_nonexistent
5. should_soft_delete_by_id
6. should_list_with_filters
7. should_throw_when_get_nonexistent
8. should_count_by_owner

## Verification

- All 8 tests pass
- Compile: pass
- Pattern: Mockito + Lenient strictness + @InjectMocks

## Commit

See git log.
