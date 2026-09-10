# 基础镜像
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="aiopc"

# 注意:不能用 VOLUME /home/aiopc — 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar/yml 全部 shadow 掉,容器跑的是上一次构建残留的旧文件。
RUN mkdir -p /home/aiopc/lib
WORKDIR /home/aiopc
COPY ./ruoyi-modules/opc-insight/target/opc-insight.jar /home/aiopc/opc-insight.jar
COPY ./ruoyi-modules/opc-insight/target/dependency/ /home/aiopc/lib/
EXPOSE 9306
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-cp", "opc-insight.jar:lib/*", \
    "com.ruoyi.opc.insight.OpcInsightApplication"]