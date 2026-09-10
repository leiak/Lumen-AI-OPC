# 基础镜像
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="aiopc"

# 注意:不能用 VOLUME /home/aiopc — 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar/yml 全部 shadow 掉,容器跑的是上一次构建残留的旧文件。
RUN mkdir -p /home/aiopc/lib
WORKDIR /home/aiopc
# 拷贝 thin jar + 全部 maven 依赖,通过 classpath 启动
# (这些模块的 pom 用 <phase>none</phase> 阻止 spring-boot:repackage,
#  因为 opc-ai-core 是 SPI 共享库,repackage 会把 classes 挪到 BOOT-INF 下,
#  下游模块找不到。所以 thin jar + 显式 classpath 是设计意图。)
COPY ./ruoyi-modules/opc-agent-hub/target/opc-agent-hub.jar /home/aiopc/opc-agent-hub.jar
COPY ./ruoyi-modules/opc-agent-hub/target/dependency/ /home/aiopc/lib/
EXPOSE 9303
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-cp", "opc-agent-hub.jar:lib/*", \
    "com.ruoyi.opc.agent.OpcAgentHubApplication"]
