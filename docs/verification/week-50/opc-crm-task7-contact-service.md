# Task 7: opc-crm ContactService

Date: 2026-09-11

## Files Created (3)

- `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/ContactService.java`
- `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/service/impl/ContactServiceImpl.java`
- `springboot3/ruoyi-modules/opc-crm/src/test/java/com/ruoyi/opc/crm/service/ContactServiceTest.java`

## Tests (6 @Test)

- `should_create_contact_for_customer` — create sets createBy/updateBy/deleted/isPrimary defaults
- `should_reject_blank_contact_name` — blank name throws IllegalArgumentException with "name"
- `should_list_contacts_by_customer` — listByCustomer returns mapper result
- `setPrimary_should_unset_others` — 2-step: `unsetPrimaryForCustomer(customerId)` then `setPrimary(contactId)`
- `should_delete_contact_by_id` — soft delete via setDeleted(1) + updateById
- `should_throw_when_delete_nonexistent` — delete on missing id throws IllegalArgumentException with "not found"

## Service API

- `create(CrmContact, Long)` — @Transactional, sets audit fields + defaults
- `update(Long, CrmContact, Long)` — @Transactional, partial field merge
- `delete(Long, Long)` — @Transactional, soft delete (setDeleted=1)
- `getById(Long)` — throws if null
- `listByCustomer(Long)` — pass-through to mapper
- `setPrimary(Long customerId, Long contactId)` — @Transactional, 2-step atomic swap

## Verification

- ContactServiceTest: 6/6 PASS
- CustomerServiceTest (regression): 8/8 PASS
- Combined: 14/14 PASS
- Compile: pass (23 source files)

## Mapper Coverage

All mapper methods used by service already existed (from Task 5):
`insert`, `updateById`, `selectById`, `selectByCustomerId`, `unsetPrimaryForCustomer`, `setPrimary`

## Commit

See git log.
