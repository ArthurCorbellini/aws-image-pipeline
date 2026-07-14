#!/bin/bash
set -e

cd "$(dirname "$0")/.."

./mvnw install -N
./mvnw install -pl common
./mvnw -pl api spring-boot:run
