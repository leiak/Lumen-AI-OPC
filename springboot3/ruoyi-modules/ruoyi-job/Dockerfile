# 基础镜像
FROM eclipse-temurin:17-jre-jammy
# author
LABEL maintainer="aiopc"

# 不能用 VOLUME /home/aiopc —— 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar shadow 掉, 容器跑的是上一次构建残留的旧文件。
# 创建目录
RUN mkdir -p /home/aiopc
# 指定路径
WORKDIR /home/aiopc
# 复制 jar 文件到路径（build context 为仓库根 springboot3/）
#
# ruoyi-job 的 pom 用的是 **fat jar**（spring-boot-maven-plugin 的 repackage 目标
# 正常运行，没有像 OPC 各模块那样用 <phase>none</phase> 挡掉），
# 所以启动方式是 `java -jar`，没有 lib/ 目录。
COPY ./ruoyi-modules/ruoyi-job/target/ruoyi-modules-job.jar /home/aiopc/ruoyi-job.jar
# 暴露端口
EXPOSE 9203
# 启动服务
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "ruoyi-job.jar"]
