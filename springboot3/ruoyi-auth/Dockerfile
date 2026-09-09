# 基础镜像
FROM eclipse-temurin:17-jre-jammy
# author
LABEL maintainer="aiopc"

# 挂载目录
VOLUME /home/aiopc
# 创建目录
RUN mkdir -p /home/aiopc
# 指定路径
WORKDIR /home/aiopc
# 复制 jar 文件到路径（build context 为仓库根 springboot3/）
COPY ./ruoyi-auth/target/ruoyi-auth.jar /home/aiopc/ruoyi-auth.jar
# 暴露端口
EXPOSE 9200
# 启动服务
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "ruoyi-auth.jar"]
