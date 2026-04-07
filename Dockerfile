# Imagem da API Spring Boot (bootstrap) — build multi-stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY domain ./domain
COPY application ./application
COPY infrastructure ./infrastructure
COPY bootstrap ./bootstrap

# -am compila dependências; se a imagem ficar sem spring-ai-google-genai após mudar deps de IA, faça build --no-cache.
RUN mvn -pl bootstrap -am package -DskipTests -B \
    && cp /app/bootstrap/target/bootstrap-*.jar /app/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/app.jar app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
