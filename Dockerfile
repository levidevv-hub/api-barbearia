FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon


FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/build/libs/barbearia-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]