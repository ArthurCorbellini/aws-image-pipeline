FROM docker.io/library/eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn/ .mvn/
COPY src/ src/

RUN ./mvnw package -DskipTests

FROM docker.io/library/eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/target/api-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
