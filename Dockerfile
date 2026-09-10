FROM eclipse-temurin:21-jdk-alpine

# Creates a temporary volume (used by Spring Boot's embedded Tomcat)
VOLUME /tmp

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

# Default environment variables
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]