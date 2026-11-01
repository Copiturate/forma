FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B verify

FROM eclipse-temurin:21-jre
RUN groupadd --system forma && useradd --system --gid forma forma \
    && mkdir /app && chown forma:forma /app
WORKDIR /app
COPY --from=build --chown=forma:forma /build/target/forma-1.0.0.jar app.jar
RUN mkdir data && chown forma:forma data
USER forma
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
