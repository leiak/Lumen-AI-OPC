# E2E 报告 (2026-09-10)

**Total**: 50 | **Pass**: 49 | **Fail**: 1

| Group | Method | Path | HTTP | R-code | Msg | Note |
|---|---|---|---|---|---|---|
| user | GET | `/opc/user/profile` | 200 | 200 | 操作成功 |  ✅ |
| user | POST | `/opc/user/profile` | 200 | 200 | 操作成功 |  ✅ |
| user | GET | `/opc/user/companies` | 200 | 200 | 操作成功 |  ✅ |
| user | GET | `/opc/user/company/1` | 200 | 200 | 操作成功 |  ✅ |
| user | PUT | `/opc/user/company` | 200 | 200 | 操作成功 |  ✅ |
| user | GET | `/opc/user/home` | 200 | 200 | 操作成功 |  ✅ |
| user | POST | `/opc/user/company (create)` | 200 | 200 | 操作成功 |  ✅ |
| user | GET | `/opc/user/invitations` | 200 | 200 | 操作成功 |  ✅ |
| user | POST | `/opc/user/invitations/generate` | 200 | 200 | 操作成功 | creates invite ✅ |
| agent | GET | `/opc/agent/market` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/market?category=FINANCE` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/market?category=ERP` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/market?category=CRM` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/market?category=HR` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/market?category=ECOM` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/market?category=CONTENT` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/market?category=INSIGHT` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/detail/1` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/detail/4` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/instances` | 200 | 200 | 操作成功 |  ✅ |
| agent | POST | `/opc/agent/hire` | 200 | 200 | 操作成功 | creates instance ✅ |
| agent | GET | `/opc/agent/instance/7` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/instance/7/tasks` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/instance/7/usage` | 200 | 200 | 操作成功 |  ✅ |
| agent | POST | `/opc/agent/instance/7/action?action=PAUSE` | 200 | 200 | 操作成功 |  ✅ |
| agent | POST | `/opc/agent/instance/7/action?action=RESUME` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/usage/summary` | 200 | 200 | 操作成功 |  ✅ |
| agent | GET | `/opc/agent/usage/daily` | 200 | 200 | 操作成功 |  ✅ |
| agent | POST | `/opc/agent/task/run` | 200 | 200 | 操作成功 |  ✅ |
| billing | GET | `/opc/billing/wallet` | 200 | 200 | 操作成功 |  ✅ |
| billing | GET | `/opc/billing/orders` | 200 | 200 | 操作成功 |  ✅ |
| billing | POST | `/opc/billing/wallet/recharge` | 200 | 200 | 操作成功 | creates order ✅ |
| billing | GET | `/opc/billing/order/O20260910314632` | 200 | 200 | 操作成功 |  ✅ |
| finance | GET | `/opc/finance/vouchers` | 200 | 200 | 操作成功 |  ✅ |
| finance | POST | `/opc/finance/voucher (create)` | 200 | 200 | 操作成功 |  ✅ |
| finance | GET | `/opc/finance/flows/pending` | 200 | 200 | 操作成功 |  ✅ |
| finance | POST | `/opc/finance/flows/extract` | 200 | 200 | 操作成功 |  ✅ |
| finance | POST | `/opc/finance/flows/upload` | 200 | 200 | 操作成功 | batch upload 2 flows ✅ |
| finance | GET | `/opc/finance/tax-reports` | 200 | 200 | 操作成功 |  ✅ |
| finance | POST | `/opc/finance/tax-reports/generate` | 200 | 200 | 操作成功 | creates tax report ✅ |
| finance | POST | `/opc/finance/daily-report` | 200 | 200 | 操作成功 |  ✅ |
| insight | GET | `/opc/insight/dashboard` | 200 | 200 |  |  ✅ |
| insight | GET | `/opc/insight/alerts` | 200 | 200 |  |  ✅ |
| insight | GET | `/opc/insight/advice` | 200 | 200 |  |  ✅ |
| insight | GET | `/opc/insight/daily` | 200 | 200 |  |  ✅ |
| insight | POST | `/opc/insight/daily/generate` | 200 | 200 |  | creates daily report ✅ |
| insight | GET | `/opc/insight/advice (for id)` | 200 | 200 |  |  ✅ |
| insight | GET | `/opc/insight/alerts (for id)` | 200 | 200 |  |  ✅ |
| llm | GET | `/opc/llm/models` | 200 | 200 | OK |  ✅ |
| llm | POST | `/opc/llm/chat` | 200 | 500 | 所有 LLM provider 失败，最后错误：unknown | EXPECTED FAIL: no real OpenAI key, LLM provider fallback fails ❌ |
