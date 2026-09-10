-- =============================================================================
-- V20260910__opc_company_demo_seed.sql
-- M1+M2 Demo seed: 给 sys_user.id=1 (admin) 创建示例一人公司,让
-- /opc/agent/detail/{id} 的"公司"下拉、/opc/billing/wallet、/opc/finance/flows
-- 的公司选择器立刻有数据可用,避免 UI 卡在空状态。
--
-- 触发场景:
--   - 全新部署 docker stack 后,opc_company_profile 是空表
--   - 用户(默认 admin)从未在 /opc/user/profile 的"+ 创建公司"对话框提交过数据
--   - 任何需要 companyId 的 endpoint 都会下拉为空
--
-- 设计:
--   * idempotent: 先 SELECT 再 INSERT
--   * opc_company_profile.owner_user_id=1 是 RuoYi 默认 admin user_id
--   * opc_company_member 也同步插一行 (role=OWNER, status=ACTIVE),
--     以便后续 invitation accept、wallet 查询等依赖 company_member 的逻辑
--     出现"无成员记录"错误时能自愈
--   * 配套的 sys_user 默认 charset 可能是 utf8mb3, 这里 COMPANY 名称含中文,
--     显式 SET NAMES utf8mb4 兜底
--
-- 应用方式 (W48.3 教训: 必须显式执行,不要等 Flyway 自动):
--   docker exec -i aiopc-mysql mysql -uroot -p'Opc@2026!' ry-vue-opc \
--     < springboot3/sql/seed/opc_company_demo_seed.sql
-- =============================================================================

SET NAMES utf8mb4;

-- 1) opc_company_profile: 一行示例公司
INSERT INTO opc_company_profile
  (company_code, owner_user_id, company_name, company_type,
   industry_code, industry_name, registered_capital,
   province, city, address, legal_person, phone, email,
   introduction, verified, status,
   create_by, create_time, update_by, update_time)
SELECT
  'C1000000001', 1, '示例一人公司', 'INDIVIDUAL',
  'ECOM', '电子商务', 100000.00,
  '浙江省', '杭州市', '西湖区文三路 100 号', 'admin', '138001000000',
  'admin@example.com',
  'OPC 示例公司,可在此雇佣 Agent。',
  1, 'NORMAL',
  'admin', NOW(), 'admin', NOW()
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM opc_company_profile WHERE owner_user_id = 1
);

-- 2) opc_company_member: 同步插一行 (owner 角色),供 invitation/wallet 关联查询
INSERT INTO opc_company_member
  (company_id, user_id, role, department, position, joined_at, status,
   create_by, create_time, update_by, update_time)
SELECT
  c.id, 1, 'OWNER', '管理层', '创始人', NOW(), 'ACTIVE',
  'admin', NOW(), 'admin', NOW()
FROM opc_company_profile c
WHERE c.owner_user_id = 1
  AND NOT EXISTS (
    SELECT 1 FROM opc_company_member
    WHERE user_id = 1 AND company_id = c.id
  );

-- 3) 校验
SELECT
  (SELECT COUNT(*) FROM opc_company_profile WHERE owner_user_id=1) AS my_companies,
  (SELECT COUNT(*) FROM opc_company_member WHERE user_id=1)          AS my_memberships;