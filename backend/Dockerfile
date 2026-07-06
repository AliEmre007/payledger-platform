# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system payledger \
    && useradd --system --gid payledger --home-dir /app payledger

COPY --from=build /workspace/target/payledger-api-0.0.1-SNAPSHOT.jar /app/payledger-api.jar

USER payledger
EXPOSE 18080

ENV SERVER_PORT=18080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/payledger-api.jar"]
