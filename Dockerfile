# syntax=docker/dockerfile:1.7
# ---- build stage ----
FROM bellsoft/liberica-openjdk-debian:25 AS build

ARG MAVEN_VERSION=4.0.0-rc-5
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL -o /tmp/maven.tgz "https://dlcdn.apache.org/maven/maven-4/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    && tar -xzf /tmp/maven.tgz -C /opt && rm /tmp/maven.tgz \
    && ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn

WORKDIR /src
COPY pom.xml ./
COPY forge-dashboard-app/pom.xml forge-dashboard-app/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -ntp -B dependency:go-offline -DskipTests || true

COPY forge-dashboard-app/src forge-dashboard-app/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -ntp -B -DskipTests package

# ---- runtime stage ----
FROM bellsoft/liberica-openjre-debian:25
LABEL org.opencontainers.image.source="https://forge.vidocq.dev/vidocq-tools/forge-dashboard"
LABEL org.opencontainers.image.title="forge-dashboard"
LABEL org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /app
COPY --from=build /src/forge-dashboard-app/target/forge-dashboard-app-*.jar app.jar
COPY --from=build /root/.m2/repository /m2/repository

ENV DASHBOARD_PORT=8080
EXPOSE 8080

# Le module-path complet est resolu via la conf Vidocq.
ENTRYPOINT ["java", "-jar", "app.jar"]
