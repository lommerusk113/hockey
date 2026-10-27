FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon -q shadowJar

FROM eclipse-temurin:25-jre
COPY --from=build /src/build/libs/*-all.jar /app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app.jar"]
