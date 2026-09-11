# Task 1: opc-crm scaffold

Date: 2026-09-11

## Files Created

- springboot3/ruoyi-modules/opc-crm/pom.xml
- springboot3/ruoyi-modules/opc-crm/Dockerfile (thin-jar, port 9312)
- springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/OpcCrmApplication.java
- springboot3/ruoyi-modules/opc-crm/src/main/resources/bootstrap.yml
- springboot3/ruoyi-modules/opc-crm/src/main/resources/application.yml
- Modified: springboot3/ruoyi-modules/pom.xml (added opc-crm to modules)

## Verification

- Compile: pass (BUILD SUCCESS)
- Service name: opc-crm
- Port: 9312
- MyBatis: vanilla 3.5.19 (NOT mybatis-plus, mybatis-plus-boot-starter is provided scope only)
- Nacos namespace: opc-dev
- Annotations on OpcCrmApplication: @EnableCustomConfig, @EnableRyFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"}), @SpringBootApplication, @ComponentScan({"com.ruoyi.opc", "com.ruoyi.system"})
- Thin-jar pattern: spring-boot:repackage set to <phase>none</phase>
- Dockerfile EXPOSE 9312, ENTRYPOINT uses classpath jar:lib/* with com.ruoyi.opc.crm.OpcCrmApplication

## Commit

- af451af feat(crm): Task 1 — scaffold opc-crm module (pom + Dockerfile + app + yml)
- Pushed to origin/main