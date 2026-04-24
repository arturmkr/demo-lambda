#!/usr/bin/env bash
set -euo pipefail

docker compose -f docker-compose.test.yml up -d
trap 'docker compose -f docker-compose.test.yml logs --no-color || true; docker compose -f docker-compose.test.yml down -v' EXIT

echo "Waiting for LocalStack..."
for _ in {1..30}; do
  if curl -sf http://localhost:4566/_localstack/health >/dev/null; then
    break
  fi
  sleep 2
done

if ! curl -sf http://localhost:4566/_localstack/health >/dev/null; then
  echo "LocalStack did not become ready in time" >&2
  exit 1
fi

AWS_REGION=us-east-1 ./gradlew clean test shadowJar
