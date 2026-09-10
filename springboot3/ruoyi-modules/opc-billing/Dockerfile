# 基础镜像
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="aiopc"

# 注意:不能用 VOLUME /home/aiopc — 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar/yml 全部 shadow 掉,容器跑的是上一次构建残留的旧文件。
# 改为普通 RUN mkdir,保留 image 内容。
RUN mkdir -p /home/aiopc/lib
WORKDIR /home/aiopc
COPY ./ruoyi-modules/opc-billing/target/opc-billing.jar /home/aiopc/opc-billing.jar
COPY ./ruoyi-modules/opc-billing/target/dependency/ /home/aiopc/lib/
# opc-billing 通过 opc-ai-core 的 TokenMeter 调用 LLM,billing 的 pom 没显式依赖 opc-ai-core,
# 但运行时需要 TokenMeter 接口和 agent-hub 提供的 TokenUsageRecorderImpl SPI。
COPY ./ruoyi-modules/opc-ai-core/target/opc-ai-core.jar /home/aiopc/lib/opc-ai-core.jar
COPY ./ruoyi-modules/opc-agent-hub/target/opc-agent-hub.jar /home/aiopc/lib/opc-agent-hub.jar
EXPOSE 9304
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-cp", "opc-billing.jar:lib/*", \
    "com.ruoyi.opc.billing.OpcBillingApplication"]
