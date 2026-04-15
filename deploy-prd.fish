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

# --- Detect which projects have changes ---
function has_changes -a project_dir
    set -l changed_files (git diff --name-only HEAD -- "$project_dir" 2>/dev/null)
    set -l untracked_files (git ls-files --others --exclude-standard -- "$project_dir" 2>/dev/null)
    test (count $changed_files) -gt 0; or test (count $untracked_files) -gt 0
end

# --- Parse arguments ---
set -l midgaard_ver ""
set -l udgaard_ver ""
set -l force_all false

for i in (seq (count $argv))
    switch $argv[$i]
        case --midgaard
            set midgaard_ver $argv[(math $i + 1)]
        case --udgaard
            set udgaard_ver $argv[(math $i + 1)]
        case --all
            set force_all true
    end
end

# Detect changes
set -l deploy_midgaard false
set -l deploy_udgaard false
set -l deploy_asgaard false

if test "$force_all" = true
    set deploy_midgaard true
    set deploy_udgaard true
    set deploy_asgaard true
else
    # Explicit version args force a deploy for that service
    if test -n "$midgaard_ver"; or has_changes midgaard/
        set deploy_midgaard true
    end
    if test -n "$udgaard_ver"; or has_changes udgaard/
        set deploy_udgaard true
    end
    if has_changes asgaard/
        set deploy_asgaard true
    end
end

# If nothing changed, inform and exit
if test "$deploy_midgaard" = false -a "$deploy_udgaard" = false -a "$deploy_asgaard" = false
    echo "No changes detected in midgaard/, udgaard/, or asgaard/. Nothing to deploy."
    echo "Use --all to force a full deploy."
    exit 0
end

# --- Bump versions and build JARs for changed services ---
set -l services_to_build

if test "$deploy_midgaard" = true
    echo "=== Midgaard ==="
    set -l mg_result (bump_version "$root/midgaard/build.gradle" "$midgaard_ver")
    or exit 1
    echo "Version: $mg_result"

    # Clean old JARs to prevent stale versions being picked up by Docker COPY wildcards
    rm -f $root/midgaard/build/libs/*.jar 2>/dev/null; or true

    echo ""
    echo "Building Midgaard JAR..."
    cd "$root/midgaard"; and ./gradlew bootJar -x test -x generateJooq; or begin
        echo "Midgaard JAR build failed"
        exit 1
    end
    set -a services_to_build midgaard
else
    echo "=== Midgaard === SKIPPED (no changes)"
end

if test "$deploy_udgaard" = true
    echo ""
    echo "=== Udgaard ==="
    set -l ug_result (bump_version "$root/udgaard/build.gradle" "$udgaard_ver")
    or exit 1
    echo "Version: $ug_result"

    rm -f $root/udgaard/build/libs/*.jar 2>/dev/null; or true

    echo ""
    echo "Building Udgaard JAR..."
    cd "$root/udgaard"; and ./gradlew bootJar -x test -x generateJooq; or begin
        echo "Udgaard JAR build failed"
        exit 1
    end
    set -a services_to_build udgaard
else
    echo ""
    echo "=== Udgaard === SKIPPED (no changes)"
end

if test "$deploy_asgaard" = true
    set -a services_to_build asgaard
else
    echo ""
    echo "=== Asgaard === SKIPPED (no changes)"
end

# --- Build and deploy Docker containers ---
echo ""
echo "Building and deploying containers: $services_to_build..."
cd "$root"; and docker compose -f compose.prod.yaml build $services_to_build; or begin
    echo "Docker build failed"
    exit 1
end

docker compose -f compose.prod.yaml up -d $services_to_build; or begin
    echo "Docker deploy failed"
    exit 1
end

echo ""
echo "Deployed: $services_to_build"
echo "  Asgaard:  http://localhost:9000"
echo "  Udgaard:  http://localhost:9080"
echo "  Midgaard: http://localhost:9081"
echo "  Adminer:  http://localhost:9083"
echo "  Postgres: localhost:9432"
