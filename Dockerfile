FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml ./pom.xml
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build --chown=app:app /app/target/Pet_Sitter-0.0.1-SNAPSHOT.jar /app/app.jar
USER app

EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
