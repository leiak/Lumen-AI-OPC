# opc-content Dockerfile (W74 · AI 内容创作中心)
# 基于 Eclipse Temurin JRE 17 (Spring Boot 3 要求 JDK 17)
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="opc-team"
LABEL service="opc-content"

WORKDIR /app

# 拷贝 fat jar (注意: 默认 repackage 已 none-phase,需要 mvn package + dependency:copy-dependencies)
ARG JAR_FILE=target/opc-content.jar
ARG LIB_DIR=target/dependency
COPY ${JAR_FILE} app.jar
COPY ${LIB_DIR}/ lib/

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:9325/actuator/health || exit 1

EXPOSE 9325

# thin jar 启动: java -cp "app.jar:lib/*" Main-Class
ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", \
  "-Dfile.encoding=UTF-8", \
  "-cp", "app.jar:lib/*", \
  "com.ruoyi.opc.content.OpcContentApplication"]
