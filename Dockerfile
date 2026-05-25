# syntax=docker/dockerfile:1.7
#
# Image runtime forge-dashboard.
# Build prealable requis cote host : `mvn -ntp -B install -DskipTests` (les SNAPSHOTs
# Vidocq ne sont qu'en M2 local pour l'instant). Le Dockerfile copie simplement le
# JAR + les deps dans l'image.
#
FROM bellsoft/liberica-openjre-debian:25 AS runtime

LABEL org.opencontainers.image.source="https://codeberg.org/VidocqTools/forge-dashboard"
LABEL org.opencontainers.image.title="forge-dashboard"
LABEL org.opencontainers.image.licenses="Apache-2.0"

# wget pour le healthcheck (l'image debian-slim a busybox-less, on installe la version pure).
RUN apt-get update && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Toutes les deps runtime, puis le jar applicatif.
COPY forge-dashboard-app/target/dependency/ /app/lib/
COPY forge-dashboard-app/target/forge-dashboard-app-*.jar /app/lib/

# Vidocq + Vauban + Cassini + Chappe sont des modules JPMS — on lance via module-path.
# Le main-class est resolu via App.java -> Vidocq.main(args).
ENTRYPOINT ["java", \
    "--module-path", "/app/lib", \
    "--add-modules", "ALL-MODULE-PATH", \
    "-m", "io.vidocq.tools.dashboard.app/io.vidocq.tools.dashboard.App"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/healthz || exit 1
