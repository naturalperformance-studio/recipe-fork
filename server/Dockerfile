# Runs the distribution produced by `./gradlew :server:installDist`.
FROM eclipse-temurin:21-jre
WORKDIR /opt/recipe-fork
COPY build/install/server/ ./
EXPOSE 8080
ENTRYPOINT ["./bin/server"]
