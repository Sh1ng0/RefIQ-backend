
FROM eclipse-temurin:21-jdk-alpine

# Creamos un volumen temporal (Spring Boot lo usa para Tomcat)
VOLUME /tmp


ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar


EXPOSE 8080

# Variables de entorno por defecto
ENV JAVA_OPTS=""


ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]