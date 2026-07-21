#!/bin/bash
set -e

cd "$(dirname "$0")/../api"

./mvnw spring-boot:run
