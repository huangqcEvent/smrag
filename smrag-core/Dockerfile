FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
COPY smrag-core/pom.xml smrag-core/pom.xml
RUN mvn -B -pl smrag-core -am dependency:go-offline

COPY smrag-core/src smrag-core/src
RUN mvn -B -pl smrag-core -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && groupadd --system fundrag \
    && useradd --system --gid fundrag --home-dir /app --shell /usr/sbin/nologin fundrag \
    && mkdir -p /app \
    && chown fundrag:fundrag /app \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build --chown=fundrag:fundrag /workspace/smrag-core/target/smrag-core-*.jar /app/app.jar

USER fundrag
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
