FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:26-jre
WORKDIR /app
RUN groupadd -g 10001 appgroup && \
    useradd -u 10001 -g appgroup -d /app -s /usr/sbin/nologin appuser && \
    chown -R appuser:appgroup /app
COPY --from=build --chown=appuser:appgroup /workspace/target/metin-market-api-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
