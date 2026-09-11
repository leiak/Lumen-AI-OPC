# Task 12: opc-crm DashboardService

Date: 2026-09-11

## Files Created (4)

- dto/DashboardFunnelResponse.java
- service/DashboardService.java
- service/impl/DashboardServiceImpl.java
- test/.../DashboardServiceTest.java

## Tests (4)

- should_compute_funnel
- should_aggregate_customers_by_level
- should_get_upcoming_follow_ups
- should_get_recent_follow_ups_limited

## Coverage

- Funnel: count by stage, top 10, active amount (LEAD/QUALIFIED/PROPOSAL/NEGOTIATION), won amount (WON)
- Customer summary: total + by level (A/B/C/D)
- Follow-ups: upcoming (via selectUpcoming), recent (sorted by createTime desc, limited)
