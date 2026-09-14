# opc-content Dockerfile (W74 · AI 内容创作中心)
# 基于 Eclipse Temurin JRE 17 (Spring Boot 3 要求 JDK 17)
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="opc-team"
LABEL service="opc-content"

# 注意:不能用 VOLUME /home/aiopc — 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar 全部 shadow 掉,容器跑的是上一次构建残留的旧文件。
RUN mkdir -p /home/aiopc/lib
WORKDIR /home/aiopc

# build context 是 springboot3/,所以用 ./ruoyi-modules/opc-content/...
COPY ./ruoyi-modules/opc-content/target/opc-content.jar /home/aiopc/opc-content.jar
COPY ./ruoyi-modules/opc-content/target/dependency/ /home/aiopc/lib/

EXPOSE 9325

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:9325/actuator/health || exit 1

# thin jar 启动: java -cp "xxx.jar:lib/*" Main-Class
ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-Dfile.encoding=UTF-8", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-cp", "opc-content.jar:lib/*", \
  "com.ruoyi.opc.content.OpcContentApplication"]
