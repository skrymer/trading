#!/usr/bin/env fish

set -l root (realpath (dirname (status filename)))

# --- Version bumping helper ---
function bump_version -a build_gradle explicit_version
    set -l current (grep "^version = " "$build_gradle" | string replace -r "version = '(.+)'" '$1')
    if test -z "$current"
        echo "Could not read version from $build_gradle"
        return 1
    end

    set -l new_version
    if test -n "$explicit_version"
        set new_version $explicit_version
    else
        set -l parts (string split "." "$current")
        set -l patch (math $parts[3] + 1)
        set new_version "$parts[1].$parts[2].$patch"
    end

    sed -i "s/^version = '$current'/version = '$new_version'/" "$build_gradle"
    echo "$current -> $new_version"
end

# --- Parse arguments ---
set -l midgaard_ver ""
set -l udgaard_ver ""

for i in (seq (count $argv))
    switch $argv[$i]
        case --midgaard
            set midgaard_ver $argv[(math $i + 1)]
        case --udgaard
            set udgaard_ver $argv[(math $i + 1)]
    end
end

# --- Bump versions ---
echo "=== Midgaard ==="
set -l mg_result (bump_version "$root/midgaard/build.gradle" "$midgaard_ver")
or exit 1
echo "Version: $mg_result"

echo "=== Udgaard ==="
set -l ug_result (bump_version "$root/udgaard/build.gradle" "$udgaard_ver")
or exit 1
echo "Version: $ug_result"

# --- Build JARs ---
# Clean old JARs first to prevent stale versions being picked up by Docker COPY wildcards
rm -f "$root/midgaard/build/libs/"*.jar "$root/udgaard/build/libs/"*.jar

echo ""
echo "Building Midgaard JAR..."
cd "$root/midgaard"; and ./gradlew bootJar -x test -x generateJooq; or begin
    echo "Midgaard JAR build failed"
    exit 1
end

echo ""
echo "Building Udgaard JAR..."
cd "$root/udgaard"; and ./gradlew bootJar -x test -x generateJooq; or begin
    echo "Udgaard JAR build failed"
    exit 1
end

# --- Build and deploy Docker containers ---
echo ""
echo "Building and deploying prod containers..."
cd "$root"; and docker compose -f compose.prod.yaml build; or begin
    echo "Docker build failed"
    exit 1
end

docker compose -f compose.prod.yaml up -d; or begin
    echo "Docker deploy failed"
    exit 1
end

echo ""
echo "Prod deployed successfully!"
echo "  Asgaard:  http://localhost:9000"
echo "  Udgaard:  http://localhost:9080"
echo "  Midgaard: http://localhost:9081"
echo "  Adminer:  http://localhost:9083"
echo "  Postgres: localhost:9432"
