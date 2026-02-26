FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/midgaard-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Xmx4096m", "-Xms2048m", "-jar", "app.jar"]
