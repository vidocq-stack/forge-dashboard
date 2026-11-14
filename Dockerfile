# syntax=docker/dockerfile:1.7
#
# forge-dashboard: the Vidocq distribution of forge-dashboard-app on a Java 25 JRE.
# Build it first, on the host or in CI:  ./mvnw -ntp -B -U package -DskipTests
# (it writes forge-dashboard-app/target/forge-dashboard-app-<version>/: bin/, lib/, app/).
#
FROM bellsoft/liberica-openjre-debian:25

LABEL org.opencontainers.image.source="https://codefloe.com/VidocqTools/forge-dashboard"
LABEL org.opencontainers.image.title="forge-dashboard"
LABEL org.opencontainers.image.licenses="Apache-2.0"

# wget for the healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin dashboard

COPY forge-dashboard-app/target/forge-dashboard-app-*/ /app/
# The launcher comes out of the build as rwxr--r--: make it runnable by the non-root user.
RUN chmod -R a+rX /app && chmod 755 /app/bin/forge-dashboard-app.sh
WORKDIR /app

ENV JAVA_OPTS="-Dvaadin.productionMode=true"
# WORKAROUND (see README, « Contournements »): the HTTP port is read from VIDOCQ_HTTP_PORT.
ENV VIDOCQ_HTTP_PORT=8080

USER dashboard
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/healthz || exit 1

ENTRYPOINT ["/app/bin/forge-dashboard-app.sh"]
