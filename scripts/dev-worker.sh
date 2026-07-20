#!/bin/bash
set -e

cd "$(dirname "$0")/../modules"

./mvnw install -N
./mvnw install -pl common
./mvnw -pl worker spring-boot:run
