# Imagem da API Spring Boot (bootstrap) — build multi-stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY domain ./domain
COPY application ./application
COPY infrastructure ./infrastructure
COPY bootstrap ./bootstrap

# clean evita ficheiros órfãos em target/ (ex.: migração Flyway renomeada) que duplicariam versões no JAR.
# -am compila dependências; se a imagem ficar sem spring-ai-google-genai após mudar deps de IA, faça build --no-cache.
RUN mvn -pl bootstrap -am clean package -DskipTests -B \
    && cp /app/bootstrap/target/bootstrap-*.jar /app/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# DataDog APM: agent na raiz da imagem; versão/ambiente vêm de env no compose (DD_*)
RUN curl -fsSL -o /dd-java-agent.jar "https://dtdg.co/latest-java-tracer"

ENV JAVA_TOOL_OPTIONS="-javaagent:/dd-java-agent.jar"

RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

COPY --from=build /app/app.jar /app/app.jar

RUN chown spring:spring /app/app.jar /dd-java-agent.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java -jar /app/app.jar"]
