#!/usr/bin/env fish

set -l root (realpath (dirname (status filename)))

# --- Ensure postgres containers are running ---
echo "=== Postgres ==="
cd "$root/udgaard"; and docker compose up -d postgres; or begin
    echo "Failed to start udgaard postgres"
    exit 1
end
cd "$root/midgaard"; and docker compose up -d postgres; or begin
    echo "Failed to start midgaard postgres"
    exit 1
end
echo "Waiting for postgres..."
docker exec midgaard-postgres pg_isready -U trading -d datastore -q; or sleep 3

# --- Rebuild and restart Midgaard container ---
echo ""
echo "=== Midgaard ==="
rm -f "$root/midgaard/build/libs/"*.jar
echo "Building JAR..."
cd "$root/midgaard"; and ./gradlew bootJar -x test; or begin
    echo "Midgaard JAR build failed"
    exit 1
end

echo ""
echo "Rebuilding container..."
cd "$root/udgaard"; and docker compose up -d --build midgaard; or begin
    echo "Midgaard container rebuild failed"
    exit 1
end

# --- Restart Udgaard (bootRun) ---
echo ""
echo "=== Udgaard ==="

# Stop existing bootRun if running
set -l existing_pid (lsof -ti:8080 2>/dev/null)
if test -n "$existing_pid"
    echo "Stopping existing Udgaard (PID $existing_pid)..."
    kill $existing_pid 2>/dev/null
    sleep 2
end

echo "Starting Udgaard..."
cd "$root/udgaard"; and nohup ./gradlew bootRun > /tmp/udgaard-dev.log 2>&1 &
echo "Udgaard starting in background (log: /tmp/udgaard-dev.log)"

echo ""
echo "Dev deployed!"
echo "  Udgaard:  http://localhost:8080 (starting...)"
echo "  Midgaard: http://localhost:8081"
echo "  Adminer:  http://localhost:8083"
echo "  Postgres: localhost:5432"
