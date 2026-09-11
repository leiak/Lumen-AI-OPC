# 基础镜像
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="aiopc"

# 注意:不能用 VOLUME /home/aiopc — 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar/yml 全部 shadow 掉,容器跑的是上一次构建残留的旧文件。
RUN mkdir -p /home/aiopc/lib
WORKDIR /home/aiopc
# opc-hr 的 pom 用 <phase>none</phase> 阻止 spring-boot:repackage,
# thin jar 需要显式 classpath 才能启动。
COPY ./ruoyi-modules/opc-hr/target/opc-hr.jar /home/aiopc/opc-hr.jar
COPY ./ruoyi-modules/opc-hr/target/dependency/ /home/aiopc/lib/
EXPOSE 9322
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-cp", "opc-hr.jar:lib/*", \
    "com.ruoyi.opc.hr.OpcHrApplication"]