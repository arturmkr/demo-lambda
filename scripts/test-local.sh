#!/usr/bin/env bash
set -euo pipefail

docker compose -f docker-compose.test.yml up -d
trap 'docker compose -f docker-compose.test.yml down -v' EXIT

echo "Waiting for LocalStack..."
for _ in {1..30}; do
  if docker compose -f docker-compose.test.yml ps --format json | grep -q '"health":"healthy"'; then
    break
  fi
  sleep 2
done

AWS_REGION=us-east-1 ./gradlew clean test shadowJar
