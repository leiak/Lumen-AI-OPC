# 基础镜像
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="aiopc"

# 注意:不能用 VOLUME /home/aiopc — 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar/yml 全部 shadow 掉,容器跑的是上一次构建残留的旧文件。
RUN mkdir -p /home/aiopc/lib
WORKDIR /home/aiopc
# opc-erp 的 pom 用 <phase>none</phase> 阻止 spring-boot:repackage,
# thin jar 需要显式 classpath 才能启动。
COPY ./ruoyi-modules/opc-erp/target/opc-erp.jar /home/aiopc/opc-erp.jar
COPY ./ruoyi-modules/opc-erp/target/dependency/ /home/aiopc/lib/
EXPOSE 9311
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-cp", "opc-erp.jar:lib/*", \
    "com.ruoyi.opc.erp.OpcErpApplication"]
