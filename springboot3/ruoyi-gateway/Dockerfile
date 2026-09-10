# 基础镜像
FROM eclipse-temurin:17-jre-jammy
# author
LABEL maintainer="aiopc"

# W48.5 修复:不能用 VOLUME /home/aiopc — 那会让 docker 在 container 启动时创建匿名 volume,
# 把 image COPY 进来的 jar shadow 掉,容器跑的是上一次构建残留的旧 jar (应用的是旧 yml)
RUN mkdir -p /home/aiopc
# 指定路径
WORKDIR /home/aiopc
# 复制 jar 文件到路径（build context 为仓库根 springboot3/）
COPY ./ruoyi-gateway/target/ruoyi-gateway.jar /home/aiopc/ruoyi-gateway.jar
# 暴露端口
EXPOSE 8080
# 启动服务
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "ruoyi-gateway.jar"]
