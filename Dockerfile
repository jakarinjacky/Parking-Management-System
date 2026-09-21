FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY src ./src
RUN mkdir bin && find src -name '*.java' -print > sources.txt \
    && javac -encoding UTF-8 -d bin @sources.txt

FROM eclipse-temurin:17-jre-jammy
ARG POSTGRES_JDBC_VERSION=42.7.7
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/lib \
    && curl -fsSLo /app/lib/postgresql.jar \
       "https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_JDBC_VERSION}/postgresql-${POSTGRES_JDBC_VERSION}.jar"
WORKDIR /app
COPY --from=build /build/bin ./bin
COPY web ./web
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/api/platform/health || exit 1
CMD ["java", "-cp", "bin:lib/*", "server.ParkingServer"]
