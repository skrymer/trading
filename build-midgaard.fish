#!/usr/bin/env fish

set -l root (realpath (dirname (status filename)))

echo "Building Midgaard JAR..."
cd "$root/midgaard"; and ./gradlew bootJar -x test -x generateJooq; or begin
    echo "JAR build failed"
    exit 1
end

echo "Building Midgaard Docker image..."
cd "$root/udgaard"; and docker compose build midgaard; or begin
    echo "Docker build failed"
    exit 1
end

echo "Done. Run 'cd udgaard && docker compose up -d' to start."
