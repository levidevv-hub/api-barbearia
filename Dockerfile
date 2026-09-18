FROM eclipse-temurin:17-jre

WORKDIR /app

COPY build/libs/barbearia-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]