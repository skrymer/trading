#!/usr/bin/env fish

set -l root (realpath (dirname (status filename)))
set -l build_gradle "$root/midgaard/build.gradle"

set -l current_version (grep "^version = " "$build_gradle" | string replace -r "version = '(.+)'" '$1')
if test -z "$current_version"
    echo "Could not read version from build.gradle"
    exit 1
end

set -l new_version
if test (count $argv) -ge 1
    set new_version $argv[1]
else
    # Auto-increment patch version (e.g., 1.0.4 -> 1.0.5)
    set -l parts (string split "." "$current_version")
    set -l patch (math $parts[3] + 1)
    set new_version "$parts[1].$parts[2].$patch"
end

sed -i "s/^version = '$current_version'/version = '$new_version'/" "$build_gradle"
echo "Version: $current_version -> $new_version"

# Clean old JARs first to prevent stale versions being picked up by Docker COPY wildcards
rm -f "$root/midgaard/build/libs/"*.jar

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

echo "Done (v$new_version). Run 'cd udgaard && docker compose up -d' to start."
