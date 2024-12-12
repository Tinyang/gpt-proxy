# 使用官方的Java开发镜像作为基础镜像
FROM maven:3.8.5-openjdk-17-slim AS build

# 设置工作目录
WORKDIR /usr/src/app

# 将pom.xml和src复制到docker容器中
COPY pom.xml .
COPY src ./src

# 使用maven打包
RUN mvn -f pom.xml clean compile assembly:single

# 使用Java运行时镜像作为基础镜像
FROM openjdk:17-jdk-slim

# 将工作目录设置为/app
WORKDIR /app

# 从build镜像中复制文件到当前镜像
COPY --from=build /usr/src/app/target/*.jar /app

# 设置容器启动后执行的命令
ENTRYPOINT ["java","-jar","/app/gpt-proxy-1.0-jar-with-dependencies.jar"]
