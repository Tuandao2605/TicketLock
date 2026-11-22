FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY src ./src
RUN ./gradlew --no-daemon bootJar && cp build/libs/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system ticketlock && useradd --system --gid ticketlock --no-create-home ticketlock
COPY --from=build --chown=ticketlock:ticketlock /workspace/app.jar /app/app.jar
USER ticketlock
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
