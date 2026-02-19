# Dev-only image
FROM eclipse-temurin:25-jdk

WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/

EXPOSE 8084 5005

CMD ["./mvnw", "spring-boot:run", \
     "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=true -Dspring.devtools.livereload.enabled=true -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", \
     "-Dspring-boot.run.arguments=--server.port=8084"]