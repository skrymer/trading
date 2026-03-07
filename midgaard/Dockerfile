FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --chown=app:app build/libs/midgaard-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-Xmx4096m", "-Xms2048m", "-jar", "app.jar"]
