# Run Backend API

This skill runs the Udgaard backend API server using Gradle bootRun.

## Instructions

When the user asks to run the backend API or start the server:

1. Navigate to the udgaard directory
2. Run the Spring Boot application using Gradle bootRun
3. The server will start on port 8080 by default
4. Monitor the output for any startup errors

## Command

```bash
cd udgaard && ./gradlew bootRun
```

## Expected Output

The server should start successfully and you should see:
- Spring Boot banner
- Application initialization logs
- "Started UdgaardApplication in X seconds"
- Server listening on port 8080

## Common Issues

- **Port already in use**: Kill any existing process on port 8080
- **Build failures**: Run `./gradlew clean build` first
- **Missing dependencies**: Ensure all required services (database, etc.) are running

## Endpoints

Once running, the API is available at:
- Base URL: `http://localhost:8080`
- API endpoints: `http://localhost:8080/api/*`
- CORS enabled for: `http://localhost:3000`, `http://localhost:8080`

## Stopping the Server

Use `Ctrl+C` to gracefully stop the server.
