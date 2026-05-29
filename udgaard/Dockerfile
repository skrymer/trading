FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache unzip && addgroup -S app && adduser -S app -G app
WORKDIR /app
RUN mkdir -p /app/logs && chown app:app /app/logs

# Run the app EXPLODED, not via `java -jar`. Script-backed conditions compile user Kotlin
# through the Kotlin scripting host, which cannot resolve classes — including the Kotlin
# stdlib — from Spring Boot's nested-jar classloader, but works over a plain classpath of
# real jar files. So the boot jar is unpacked at image-build time and run with an explicit
# -cp. The glob excludes the `-plain` jar (matches a digit before `.jar`).
COPY build/libs/udgaard-*[0-9].jar /tmp/app.jar
RUN cd /app && unzip -q /tmp/app.jar && rm /tmp/app.jar \
    && chmod -R u+rX /app && chown -R app:app /app

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx20480m", "-Xms4096m", "-cp", "/app/BOOT-INF/classes:/app/BOOT-INF/lib/*", "com.skrymer.udgaard.UdgaardApplicationKt"]
