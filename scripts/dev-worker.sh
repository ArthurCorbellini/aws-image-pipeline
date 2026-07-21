#!/bin/bash
set -e

cd "$(dirname "$0")/../worker"

./mvnw spring-boot:run
