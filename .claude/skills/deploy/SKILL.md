---
name: deploy
description: Deploy the trading platform to production. Builds JARs, Docker images, starts containers, and verifies health.
disable-model-invocation: true
argument-hint: "[--midgaard version] [--udgaard version]"
---

# Deploy to Production

Deploy the trading platform by building backend JARs, Docker images, and starting all production containers. Verify health after deployment.

## Instructions

### 1. Pre-Flight Check

Verify Docker is running:

```bash
docker info > /dev/null 2>&1
```

If Docker is not running, tell the user and stop.

### 2. Run the Deploy Script

Execute the deploy script. Pass through any arguments the user provided (e.g., `/deploy --midgaard 1.0.5 --udgaard 1.0.1`).

```bash
cd /home/skrymer/Development/git/trading && ./deploy-prd.fish $ARGUMENTS
```

If no arguments are provided, the script auto-bumps patch versions.

### 3. Verify Health

After the script completes successfully, wait 15 seconds for Spring Boot startup, then verify all services are healthy. Run all health checks **in parallel**:

```bash
curl -sf http://localhost:9081/actuator/health  # Midgaard
curl -sf http://localhost:9080/udgaard/actuator/health  # Udgaard
curl -sf http://localhost:9000  # Asgaard
```

Also check container status:

```bash
docker compose -f compose.prod.yaml ps
```

### 4. Report Results

Present a deployment summary:

| Service | URL | Status |
|---------|-----|--------|
| Asgaard | http://localhost:9000 | UP / DOWN |
| Udgaard | http://localhost:9080 | UP / DOWN |
| Midgaard | http://localhost:9081 | UP / DOWN |
| PostgreSQL | localhost:9432 | UP / DOWN |
| Adminer | http://localhost:9083 | UP / DOWN |

Include the version numbers from the deploy script output.

If any service is DOWN, check its logs:

```bash
docker compose -f compose.prod.yaml logs --tail=50 <service-name>
```

Report the relevant error and suggest a fix.

## Important

- The deploy script builds JARs with `-x test -x generateJooq` — run `/pre-commit` before deploying if you want test verification
- Health endpoints may take 30-60 seconds after container start (Spring Boot startup)
- If health checks fail immediately, retry once after 15 seconds before reporting failure
- Services start in dependency order: postgres -> midgaard -> udgaard -> asgaard
