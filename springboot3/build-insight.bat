@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.17.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\work-ai\0401-lumen-opc\springboot3
call mvn -pl ruoyi-modules/opc-insight -am package -Dmaven.test.skip=true %*