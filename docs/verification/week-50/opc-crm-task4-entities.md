# Task 4: opc-crm domain entities

Date: 2026-09-11

## Files Created (6)

- CrmCustomer.java
- CrmContact.java
- CrmFollowUp.java
- CrmOpportunity.java
- CrmContract.java
- CrmOrder.java

## Pattern

- @Data (Lombok)
- implements Serializable
- All LocalDateTime fields: @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="GMT+8")
- All LocalDate fields: @JsonFormat(pattern="yyyy-MM-dd", timezone="GMT+8")
- BigDecimal for monetary amounts (Customer/Customer amount, Opportunity amount, Contract amount, Order total)
- Audit cols: createBy, createTime, updateBy, updateTime, deleted

## Verification

- Compile: pass
- Pattern matches opc-notification NotificationInbox

## Commit

See git log.