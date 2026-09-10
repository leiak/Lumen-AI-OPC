# 基础镜像
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="aiopc"

# 注意:不能用 VOLUME /home/aiopc — 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar/yml 全部 shadow 掉,容器跑的是上一次构建残留的旧文件。
RUN mkdir -p /home/aiopc/lib
WORKDIR /home/aiopc
COPY ./ruoyi-modules/opc-finance/target/opc-finance.jar /home/aiopc/opc-finance.jar
COPY ./ruoyi-modules/opc-finance/target/dependency/ /home/aiopc/lib/
# opc-finance 通过 opc-ai-core 的 TokenMeter 调用 LLM,TokenMeter 依赖 opc-agent-hub 提供的
# TokenUsageRecorderImpl(@Component 在 com.ruoyi.opc.agent.gateway 包),所以 finance 启动
# 时 opc-agent-hub.jar 也必须在 classpath。
COPY ./ruoyi-modules/opc-agent-hub/target/opc-agent-hub.jar /home/aiopc/lib/opc-agent-hub.jar
EXPOSE 9305
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-cp", "opc-finance.jar:lib/*", \
    "com.ruoyi.opc.finance.OpcFinanceApplication"]
