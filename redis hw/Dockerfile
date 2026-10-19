FROM eclipse-temurin:25

WORKDIR /app
COPY target/GameHub_Redis-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
