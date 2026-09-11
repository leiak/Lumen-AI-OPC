Task 13 verification (opc-notification integration):
================================================
- Nacos push (opc-notification-dev.yml): PASS
  - Used Python urllib POST to /nacos/v1/cs/configs (not in import-dev.sh NAMES list)
  - Verified via GET returning full YAML content
- Thin jar build: PASS (with -Dmaven.test.skip=true workaround for opc-common test compile error)
  - opc-notification.jar built (target/opc-notification.jar)
  - 233 dependency jars copied to target/dependency/
- Docker image build: PASS (aiopc-notification:latest)
- Container start: PASS
  - "Started OpcNotificationApplication in 6.673 seconds"
  - "Tomcat started on port 9310"
  - Registered with Nacos: opc-notification 172.x.x.x:9310
  - "OPC 通知中心启动成功" log marker
  - SMS provider correctly no-op (no Aliyun credentials in dev)
- Gateway route added: /opc/notification/** -> aiopc-notification:9310
  - Gateway rebuilt + restarted (jar repackaged correctly without skip flag)
- Inbox list endpoint: PASS (HTTP 200, {"code":200,"data":{"items":[],"unreadCount":0,"page":1,"pageSize":5}})
- Unread count endpoint: PASS (HTTP 200, {"code":200,"data":0})

Additional notes:
- import-dev.sh script only pushes application-dev.yml; per-service opc-*-dev.yml files
  in nacos/ folder need to be pushed individually via Python urllib POST
- ruoyi-gateway must NOT use -Dspring-boot.repackage.skip=true (it's a fat jar with -jar entrypoint)
- opc-common test compile error (OpcNacosStartupCheckerTest) requires -Dmaven.test.skip=true
  for any -am reactor build that pulls in opc-common
- Gateway application.yml is the actual route source (Nacos config for gateway doesn't override routes)
