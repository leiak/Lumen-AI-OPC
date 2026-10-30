-- ============================================================
-- V20261033__opc_scheduler_truth.sql
--
-- 这一版把「定时任务」这件事从「声明」变成「事实」,并且把三处
-- 借着这个功能名堂而皇之写着假话的地方一并收回来。
--
-- 背景:ruoyi-job(Quartz 调度器)的代码一直在仓库里,但
--   - 没有 application.yml → 没有 DataSource → SysJobServiceImpl.init()
--     这个 @PostConstruct 启动就失败
--   - 没有 Dockerfile、没有 compose 容器
--   - 网关没有 /schedule 路由 → 界面「定时任务」菜单点进去全是 404
-- 所以 sys_job 里那条「财务日报」(cron 0 0 9 * * ?)从来没被触发过,
-- sys_job_log 表 0 行 —— 一次都没跑过。
--
-- 这一版做完之后:
--   1. ruoyi-job 真的跑起来了(见 ruoyi-job/pom.xml、Dockerfile、application.yml、
--      deploy/docker-compose.yml、ruoyi-gateway/application.yml)
--   2. 菜单里的「定时任务」点进去能用了
--   3. 运行记录里的 trigger_type 记的是**这次真的怎么触发的**
--   4. 四个指向 localhost 的死外链菜单不再摆在界面上
--
-- 幂等:每条语句都可以重复执行。
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 四个指向 localhost 的外链菜单 —— 演示环境从浏览器打开它们只会得到
--    "无法访问此网站",因为它们指向的是**访客自己电脑**的 localhost。
--
--    111 Sentinel控制台 → http://localhost:8718   (访客本机没有 Sentinel)
--    112 Nacos控制台   → http://localhost:8848/nacos
--    113 Admin控制台   → http://localhost:9100/login (9100 上根本没有服务)
--    116 系统接口      → http://localhost:8080/swagger-ui/index.html
--                      (swagger-ui 在 ruoyi-service 里,8080 是网关且没这个路由)
--
--    处理:111/112/116 改成 visible='1'(RuoYi 约定:'0'=显示,'1'=隐藏),
--    113 直接删掉 —— 9100 端口上**什么都没有**,留着连"藏起来"都不该。
--    README 里改成写 SSH 端口转发的命令,需要的人自己开隧道看。
-- ----------------------------------------------------------------------------

UPDATE sys_menu
   SET visible = '1',
       remark  = '演示环境隐藏:指向访客本机 localhost,点开必然是"无法访问此网站"。请用 README 里的 ssh -L 端口转发打开'
 WHERE menu_id = 111
   AND visible = '0';

UPDATE sys_menu
   SET visible = '1',
       remark  = '演示环境隐藏:指向访客本机 localhost,点开必然是"无法访问此网站"。请用 README 里的 ssh -L 端口转发打开'
 WHERE menu_id = 112
   AND visible = '0';

UPDATE sys_menu
   SET visible = '1',
       remark  = '演示环境隐藏:指向访客本机 localhost,点开必然是"无法访问此网站"。请用 README 里的 ssh -L 端口转发打开'
 WHERE menu_id = 116
   AND visible = '0';

-- 113:9100 端口上什么都没有(既不是网关也不是任何一个业务服务),
-- 是一条**指不到任何东西**的链接 —— 删掉,连角色授权一起清干净。
DELETE FROM sys_role_menu WHERE menu_id = 113;
DELETE FROM sys_menu      WHERE menu_id = 113;

-- ----------------------------------------------------------------------------
-- 2. 运行记录里的 trigger_type —— 它记的是**这次到底怎么触发的**,
--    不是工作流配置里声明的类型。
--
--    以前 WorkflowTriggerServiceImpl 直接 run.setTriggerType(workflow.getTriggerType()),
--    而工作流行上的 trigger_type 是 CRON(它"打算"被定时器触发)。
--    结果是 32 条运行记录里:
--      - 2 条 trigger_source IS NULL、trigger_type='CRON'  ← 硬插进去的假记录
--      - 30 条 trigger_source='healthcheck'、trigger_type='CRON' ← 健康检查触发的,
--        硬说自己是定时器
--    而那时调度器**一次都没跑过** —— 类型字段在冒充事实。
--
--    现在改成按实际来源判定(见 WorkflowTriggerServiceImpl.resolveTriggerType):
--      trigger_source LIKE 'quartz:%' → CRON
--      其他                            → MANUAL
--
--    已有的 30 条 healthcheck 记录改成 MANUAL;
--    那 2 条 trigger_source IS NULL 的假记录直接删 —— 它们连"怎么触发的"
--    都说不出来,output_result('财务日报已生成')和 error_message('LLM 超时')
--    都是编的,留着就是让演示环境继续说假话。
-- ----------------------------------------------------------------------------

UPDATE opc_agent_workflow_run
   SET trigger_type = 'MANUAL'
 WHERE trigger_source IS NOT NULL
   AND trigger_source NOT LIKE 'quartz:%'
   AND trigger_type <> 'MANUAL';

DELETE FROM opc_agent_workflow_run
 WHERE trigger_source IS NULL
   AND run_code IN ('RUN20261016001', 'RUN20261017002');

-- ----------------------------------------------------------------------------
-- 3. 定时任务本身:保持启用。
--
--    status='0' = 正常(会跑),'1' = 暂停。
--    「财务日报」是演示环境里唯一一个**真的会自己跑**的定时任务,
--    09:00 每天触发一次 opc-agent-hub 的 finance_daily_report_v1。
--
--    1/2/3 三条 RuoYi 自带的示例任务(ryTask.*)保持暂停 —— 它们调的是
--    demo 用的 ryTask bean,跑起来只会往控制台刷无意义的日志。
--
--    改成「暂停」是**让界面不再说假话**的做法;改成「启用」是**让它成为真的**。
--    现在选后者 —— 调度器已经真的跑起来了,那这条任务就该真的每天跑。
-- ----------------------------------------------------------------------------

UPDATE sys_job
   SET status = '0',
       remark = '每天 09:00 触发 opc-agent-hub 的 finance_daily_report_v1 工作流(调度器 ruoyi-job 已部署,真的会自己跑)'
 WHERE job_id = 100;

UPDATE sys_job
   SET status = '1',
       remark = 'RuoYi 自带示例任务,演示环境保持暂停(调的是 ryTask demo bean,没有业务含义)'
 WHERE job_id IN (1, 2, 3);
