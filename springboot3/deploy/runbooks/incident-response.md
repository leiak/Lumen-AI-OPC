# OPC 故障处理手册（Runbook）

> 适用场景：公测环境 7×24 监控值守
> 维护：DevOps 团队

---

## 1. 故障分级

| 等级 | 描述 | 响应时间 | 通知方式 |
|------|------|---------|---------|
| P0   | 核心功能完全不可用 | 5 分钟 | 电话+短信+钉钉@所有人 |
| P1   | 核心功能部分不可用 | 15 分钟 | 钉钉+短信 |
| P2   | 非核心功能异常 | 30 分钟 | 钉钉群 |

---

## 2. 常见故障与处理

### F-01: Nacos 不可用

**症状**：所有微服务注册失败

**处理**：
1. `kubectl get pods -n opc -l app=nacos`
2. 重启异常节点：`kubectl delete pod <pod-name> -n opc`
3. 检查 Nacos 数据源：MySQL
4. 备用：切换到 Nacos 集群其他节点

### F-02: LLM API 超时

**症状**：Agent 任务长时间 RUNNING，LLM 调用报错

**处理**：
1. 检查 DeepSeek 控制台：https://platform.deepseek.com/status
2. 等待自动 fallback（30 秒内）
3. 必要时手动切到 gpt-4o-mini：
   ```bash
   curl -X PUT "http://nacos:8848/nacos/v1/cs/configs?dataId=opc-ai-core-prod.yml&group=DEFAULT_GROUP" \
     -d "content=opc.llm.primary=gpt-4o-mini"
   ```
4. 记录时间，发布客户公告

### F-03: MySQL 主从延迟

**症状**：读到的数据是旧的

**处理**：
1. `SHOW SLAVE STATUS\G` 看 Seconds_Behind_Master
2. 如果 > 60s：检查是否有大事务
3. 紧急：手动切换主从：`MHA manager` 或 `Orchestrator`

### F-04: Redis 故障

**症状**：登录态丢失、限流失效

**处理**：
1. 检查 Redis 集群：`redis-cli -c cluster nodes`
2. 重启故障节点：`systemctl restart redis`
3. 应用层会自动重连

### F-05: Qdrant 不可用

**症状**：长期记忆查询失败

**处理**：
1. 检查 Qdrant 状态：访问 6333 端口
2. 不影响核心功能，Agent 仅短期记忆可用
3. 恢复后数据会自动重建（重新写入）

### F-06: 财务 Agent 算错账

**症状**：用户反馈凭证金额/科目错误

**处理**：
1. 立即下架问题 Prompt 版本（`opc_prompt_template.enabled=0`）
2. 紧急回滚到上一版本
3. 复盘：调用 `opc_agent_eval_result` 看哪个用例失败
4. 修复后重新跑评测，达到 85% 通过率才能重新上架

### F-07: 钱包扣费异常

**症状**：用户余额对不上

**处理**：
1. 查 `opc_transaction` 看每笔流水
2. 对比 `opc_billing_order` 看订单
3. 如果发现重复扣费：手动补回 + 公告 + 补偿代金券

---

## 3. 应急联系

- DevOps Lead：138-XXXX-XXXX
- BE-A（AI 中台）：139-XXXX-XXXX
- 产品负责人：136-XXXX-XXXX

---

## 4. 关键脚本

### 一键回滚（K8s）
```bash
#!/bin/bash
SERVICE=$1
PREV_TAG=$2
kubectl set image deployment/$SERVICE $SERVICE=registry.cn-hangzhou.aliyuncs.com/opc/$SERVICE:$PREV_TAG -n opc
kubectl rollout status deployment/$SERVICE -n opc
```

### 数据库备份
```bash
#!/bin/bash
mysqldump -h mysql-host -u root -p'Opc@2026!' \
  --single-transaction --routines --triggers \
  ry-vue-opc | gzip > /backup/opc_$(date +%Y%m%d_%H%M%S).sql.gz
```

### 清空 LLM 缓存
```bash
redis-cli -h redis-host -a 'Opc@2026!' --scan --pattern 'opc:llm:cache:*' | xargs -L 100 redis-cli -h redis-host -a 'Opc@2026!' DEL
```
