FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system filebridge && \
    useradd --system --gid filebridge --home-dir /app --shell /usr/sbin/nologin filebridge && \
    mkdir -p /data/local && \
    chown -R filebridge:filebridge /app /data/local

COPY --from=build /workspace/target/secure-filebridge-*.jar /app/app.jar

USER filebridge:filebridge
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD java -version >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
