@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\work-ai\0401-lumen-opc\springboot3

echo === Cleaning opc-ai-core fat JAR ===
del /q ruoyi-modules\opc-ai-core\target\opc-ai-core.jar 2>nul

echo === Installing all OPC modules ===
call mvn -pl opc-common,ruoyi-modules/opc-ai-core -am install -Dmaven.test.skip=true -DskipTests -q %*