# Multi-stage build: Maven build -> JRE runtime
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /app/target/smart-parking-backend-1.0.0.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m" \
    SPRING_PROFILES_ACTIVE=mysql
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD java -cp app.jar org.springframework.boot.loader.launch.JarLauncher --help > /dev/null 2>&1 || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
