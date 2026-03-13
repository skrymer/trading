FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
RUN mkdir -p /app/logs && chown app:app /app/logs
COPY --chown=app:app build/libs/udgaard-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx12288m", "-Xms4096m", "-jar", "app.jar"]
