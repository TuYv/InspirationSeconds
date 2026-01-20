FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY settings.xml /usr/share/maven/ref/
COPY pom.xml .
RUN mvn -s /usr/share/maven/ref/settings.xml dependency:go-offline
COPY src ./src
RUN mvn -s /usr/share/maven/ref/settings.xml -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

# 安装中文字体 (使用清华源加速，如果需要)
# eclipse-temurin:17-jre 基于 Ubuntu/Debian
RUN apt-get update && \
    apt-get install -y fonts-noto-cjk fontconfig && \
    fc-cache -fv && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV SPRING_PROFILES_ACTIVE=prod
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
