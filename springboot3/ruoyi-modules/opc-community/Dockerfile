# ============================================================
# OPC Community thin-jar Dockerfile (W52 MVP)
# 与 opc-crm 同样 pattern
# Build context: springboot3/ (docker-compose.yml 中 `..`)
# ============================================================
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="aiopc"

# 注意:不能用 VOLUME /home/aiopc
RUN mkdir -p /home/aiopc/lib
WORKDIR /home/aiopc
COPY ./ruoyi-modules/opc-community/target/opc-community.jar /home/aiopc/opc-community.jar
COPY ./ruoyi-modules/opc-community/target/dependency/ /home/aiopc/lib/
EXPOSE 9316
ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-cp", "opc-community.jar:lib/*", \
    "com.ruoyi.opc.community.OpcCommunityApplication"]
