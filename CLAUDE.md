# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An asynchronous image processing pipeline (learning/portfolio project): client uploads an image → API stores it in S3 and publishes a message to SQS → Worker consumes the message, resizes the image with Thumbnailator, stores the result in S3 → client polls a status endpoint and gets a presigned URL to the result.

## Commands

`api/` and `worker/` are **independent Maven projects** (own `pom.xml`, own Maven Wrapper) — not a multi-module build. Run all Maven commands from inside the relevant project directory.

```bash
# Build (skip tests, same as what the Dockerfiles do)
./mvnw package -DskipTests

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ApiApplicationTests

# Run the app locally against LocalStack (default Spring profile)
../scripts/dev-api.sh      # or dev-worker.sh — thin wrappers around `./mvnw spring-boot:run`
```

Terraform (`infra/terraform/`):
```bash
cd infra/terraform
terraform init
terraform plan
terraform apply
```
**First-time apply against empty infrastructure**: don't run a plain `terraform apply`. The ECS services need images already in ECR to start, and ECR needs to exist before an image can be pushed. Apply just the ECR repos first (`terraform apply -target=aws_ecr_repository.api -target=aws_ecr_repository.worker`), build/push both images, then apply the rest.

## Architecture

### Two independent apps, no shared module

`api` and `worker` don't share a module or a deploy — they only agree implicitly on the shape of the SQS message and the S3 key layout (`originals/<id>.<ext>`, `processed/<id>.<ext>`, `metadata/<id>.json`). `ImageMessage`/`ImageStatus` are duplicated (same package, separate files) in both projects rather than extracted into a shared library — a deliberate choice to avoid coupling their deploys together. If you change the message/status shape, change it in both places.

### Environment switching: LocalStack vs real AWS via Spring profile

Both apps read AWS config (`AwsProperties`, bound from the `aws.*` keys) through `AwsClientConfig`, which conditionally builds the S3/SQS clients:
- If `aws.endpoint-url` has text → `.endpointOverride(...)` is set (LocalStack). If blank → omitted, letting the SDK resolve the real AWS endpoint.
- If `aws.access-key-id` has text → `StaticCredentialsProvider` (LocalStack's dummy `test`/`test`). If blank → `DefaultCredentialsProvider`, which picks up the ECS task role when deployed.

`application.yml` (default profile) has the LocalStack values; `application-aws.yml` (activated via `SPRING_PROFILES_ACTIVE=aws`) blanks them out. This means the same code path serves both environments — don't add `if (profile == ...)` branching in Java for this, extend the properties/YAML instead.

`app.s3.bucket-name`/`app.sqs.queue-url` are deliberately **not** set in `application-aws.yml` — the queue URL embeds the AWS account ID, so those are only ever passed as env vars (`APP_S3_BUCKET_NAME`, `APP_SQS_QUEUE_URL`) at runtime, wired automatically by Terraform when deployed to ECS (`ecs.tf`).

### S3 path-style addressing

`S3Client` and `S3Presigner` both force path-style addressing (`.forcePathStyle(true)` / `S3Configuration.pathStyleAccessEnabled(true)`). Required because LocalStack/container endpoints are hostnames, not IPs, and the AWS SDK only falls back to path-style automatically for literal IP endpoints — otherwise it tries virtual-hosted-style (`<bucket>.<host>`), which doesn't resolve.

### Two separate endpoint properties for S3

`aws.endpoint-url` (used by `S3Client`/`SqsClient`) and `aws.presigned-endpoint-url` (used only by `S3Presigner`) are intentionally different properties. When running in a container, internal calls need the container-reachable endpoint, but a presigned URL handed to an external client must point at an endpoint *that client* can resolve — those are not the same address in a containerized LocalStack setup.

### Terraform file layout

Files are split by AWS service (`s3.tf`, `sqs.tf`, `iam.tf`, `network.tf`, `ecr.tf`, `ecs.tf`), not by any Terraform requirement — Terraform concatenates every `.tf` file in the directory into one configuration regardless of name, and resource creation order follows the dependency graph (attribute references), not file or line order. `network.tf` reads the account's **default VPC** via `data` sources rather than creating one. The Terraform state backend bucket (see `backend "s3" { ... }` in `providers.tf`) is intentionally *not* managed by this project's own Terraform config (bootstrapping paradox — Terraform can't create the bucket it needs before it can initialize).

### Known upstream provider quirk

`aws_sqs_queue.max_message_size` has `lifecycle { ignore_changes = [max_message_size] }` in `sqs.tf` — the AWS provider's client-side validation still caps at the old 262144-byte limit even though AWS's real API allows up to 1048576. Don't try to set this attribute explicitly; it'll be rejected by the provider before it ever reaches AWS ([hashicorp/terraform-provider-aws#43692](https://github.com/hashicorp/terraform-provider-aws/issues/43692)).

### desired_count is deliberately unmanaged

Both `aws_ecs_service` resources in `ecs.tf` also have `lifecycle { ignore_changes = [desired_count] }`. Fargate bills per running task with no free tier, so `desired_count` is toggled directly via `aws ecs update-service --desired-count 0|1` to stop/start billing between sessions — not through Terraform. Don't remove this `ignore_changes` or "fix" the count back to a fixed value; that would make routine cost-saving scale-downs get silently reverted on the next `apply`.
