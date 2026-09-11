# Task 18: opc-crm Frontend

Date: 2026-09-11

## Files Created/Modified (6)

- `vue3-typescript/src/api/opc/crm.ts` (NEW, ~290 lines, 41 API functions)
- `vue3-typescript/src/views/opc/crm/CustomerList.vue` (NEW, search form + table + new/edit dialog + pager)
- `vue3-typescript/src/views/opc/crm/CustomerDetail.vue` (NEW, 6 tabs: basic/contact/follow/opportunity/contract/order)
- `vue3-typescript/src/views/opc/crm/OpportunityKanban.vue` (NEW, 6-column kanban + stage state machine)
- `vue3-typescript/src/views/opc/crm/FollowUpTimeline.vue` (NEW, el-timeline + create + complete)
- `vue3-typescript/src/router/index.ts` (MODIFIED: 4 new routes inside `/opc` Layout)

## API coverage (41 endpoints)

- Customer: list / getById / create / update / delete (5)
- Contact: list / create / update / delete / setPrimary (5)
- FollowUp: list / create / complete (3)
- Opportunity: list / getById / create / update / changeStage / score (6)
- Contract: list / create / activate / expire / terminate (5)
- Order: list / create / pay / ship / complete / cancel (6)
- Dashboard: funnel / customers-by-level / upcoming-followups (3)

## Build

- `vue-tsc --noEmit`: errors only in pre-existing files (insight/, system/, llm/, invite.vue, etc.) — same pattern across entire codebase, not introduced by these changes
- `npm run build:prod`: PASS — all 4 views compiled successfully
  - CustomerList.js: 7.73 kB
  - CustomerDetail.js: 10.56 kB
  - OpportunityKanban.js: 7.80 kB
  - FollowUpTimeline.js: 6.44 kB

## Notes

- Sidebar menu auto-renders from `meta.icon` in router; no layout/index.vue change required
- All views use `<script setup lang="ts" name="...">` + auto-imported Element Plus components
- Stages state machine matches backend: LEAD→QUALIFIED→PROPOSAL→NEGOTIATION→WON/LOST; LOST→LEAD reopens
