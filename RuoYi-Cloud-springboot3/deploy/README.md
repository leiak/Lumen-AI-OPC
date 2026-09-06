# OPC 部署文档（Deploy README）

## 一键启动（本地开发）

```bash
cd RuoYi-Cloud-springboot3/deploy
docker-compose up -d

# 初始化数据库（首次）
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' < ../sql/ry_20260417.sql
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' < ../sql/ry_config_20260818.sql
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' < ../sql/quartz.sql
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' < ../sql/opc_20260903.sql

# 启动后端（在仓库根目录）
mvn clean install -DskipTests
mvn spring-boot:run -pl ruoyi-gateway
mvn spring-boot:run -pl ruoyi-auth
mvn spring-boot:run -pl ruoyi-modules/ruoyi-system
mvn spring-boot:run -pl ruoyi-modules/opc-ai-core
mvn spring-boot:run -pl ruoyi-modules/opc-user-center
mvn spring-boot:run -pl ruoyi-modules/opc-agent-hub
mvn spring-boot:run -pl ruoyi-modules/opc-billing
mvn spring-boot:run -pl ruoyi-modules/opc-finance

# 启动前端
cd ../RuoYi-Cloud-Vue3-typescript
npm install
npm run dev
```

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Nacos | 8848 | 注册/配置 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| RabbitMQ | 5672 / 15672 | 队列（管理后台） |
| Qdrant | 6333 / 6334 | 向量库 |
| MinIO | 9000 / 9001 | 对象存储 |
| ES | 9200 | 日志检索 |
| Prometheus | 9090 | 监控 |
| Grafana | 3000 | 监控大盘 |
| SkyWalking | 8081 | 链路追踪 |
| Gateway | 8080 | 网关 |
| ruoyi-system | 9201 | 系统服务 |
| ruoyi-job | 9203 | 定时任务 |
| ruoyi-file | 9200 | 文件服务 |
| ruoyi-monitor | 9100 | 监控服务 |
| opc-ai-core | 9301 | AI 中台 |
| opc-user-center | 9302 | 用户中心 |
| opc-agent-hub | 9303 | Agent Hub |
| opc-billing | 9304 | 计费 |
| opc-finance | 9305 | 财务 Agent |

## 公测部署（K8s）

```bash
# 1. 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 创建 Secrets（API Key 等敏感信息）
kubectl create secret generic opc-ai-secrets \
  --from-literal=openai-api-key=$OPENAI_API_KEY \
  --from-literal=zhipuai-api-key=$ZHIPUAI_API_KEY \
  -n opc

kubectl create secret generic opc-jasypt-secrets \
  --from-literal=password=OpcEncrypt!2026 \
  -n opc

# 3. 部署 OPC 服务
kubectl apply -f k8s/ -n opc

# 4. 查看部署状态
kubectl get pods -n opc
kubectl get svc -n opc

# 5. 配置 Ingress（域名 + HTTPS）
kubectl apply -f k8s/ingress.yaml
```

## 灰度发布

```bash
# 1. 给新版本打 tag
docker build -t registry.cn-hangzhou.aliyuncs.com/opc/opc-ai-core:1.0.1 .
docker push registry.cn-hangzhou.aliyuncs.com/opc/opc-ai-core:1.0.1

# 2. 创建灰度 Deployment
kubectl apply -f k8s/opc-ai-core-canary.yaml

# 3. 用 Istio/Envoy 切 5% 流量
kubectl apply -f k8s/virtualservice-canary-5pct.yaml

# 4. 监控关键指标，无异常则全量
# 5. 删除旧版本
kubectl delete deployment opc-ai-core-old
```

## 回滚

```bash
./runbooks/rollback.sh opc-ai-core 1.0.0
```

详见：[runbooks/incident-response.md](runbooks/incident-response.md)
