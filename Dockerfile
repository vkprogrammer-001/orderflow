# syntax=docker/dockerfile:1.7
# One Dockerfile for every Java service:  docker build --build-arg SERVICE=order-service -t orderflow/order-service .

FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${SERVICE} -am package -DskipTests \
 && cp ${SERVICE}/target/app.jar /app.jar

# Split the fat jar into layers so a code change only rebuilds the small "application" layer.
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /layers
COPY --from=build /app.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=layers /layers/dependencies/ ./
COPY --from=layers /layers/spring-boot-loader/ ./
COPY --from=layers /layers/snapshot-dependencies/ ./
COPY --from=layers /layers/application/ ./
USER app
# Container-aware heap sizing; die fast on OOM so Kubernetes restarts a clean JVM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
