# Bootstrapping This Project From Scratch

This is the runbook for going from **nothing** — no local tooling, no AWS resources, no Terraform state — to a fully working deployment, starting from an empty AWS account.

The one hard requirement: **the code must be pushed to GitHub** (or wherever it was cloned from). Everything else — local tooling, AWS resources, Terraform state — is reproducible from the code plus AWS account access.

## Prerequisites

- An AWS account, and an IAM user/role in it with enough permissions to create S3 buckets, SQS queues, IAM roles/policies, VPC security groups, ECR repositories, and ECS clusters/services (`AdministratorAccess` is the simplest choice for bootstrapping; this project doesn't ship a policy scoped for the deploying identity itself, only for the app's own runtime role — see `infra/terraform/iam.tf`). Access keys can always be (re)generated from the AWS Console/IAM, whether the user is brand new or pre-existing.
- A default VPC in the target region. Most AWS accounts have one automatically; if yours doesn't (e.g. it was deleted), create one with `aws ec2 create-default-vpc` or point `infra/terraform/network.tf`'s `data "aws_vpc" "default"` at a different VPC.
- Git.
- Java 21 (JDK). Maven itself is not a separate install — both `api/` and `worker/` ship their own Maven Wrapper (`./mvnw`).
- A container engine (Docker or Podman) reachable from wherever you run the build/push commands.
- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html).
- [Terraform](https://developer.hashicorp.com/terraform/install) >= 1.9 (needed for native S3 state locking — check the official install docs instead of copy-pasting install commands from memory, package repos change).

## 0. Get the code

```bash
git clone git@github.com:ArthurCorbellini/aws-image-pipeline.git
cd aws-image-pipeline
```

## 1. Pick your own resource names

S3 bucket names are unique **globally** — across every AWS account in existence, not just your own. The names baked into this repo (`image-pipeline-s3-*` in [`infra/terraform/variables.tf`](../infra/terraform/variables.tf), `image-pipeline-tfstate-*` in the `backend "s3" { ... }` block of [`infra/terraform/providers.tf`](../infra/terraform/providers.tf)) may already be taken. Edit both files with your own unique names before doing anything else — an S3 bucket name only becomes available again once whoever held it deletes it for good.

Deploying to a region other than `us-east-1` needs two separate edits, not one: `var.aws_region` in `variables.tf` (drives the `provider "aws"` block), **and** the literal `region = "us-east-1"` inside `providers.tf`'s `backend "s3" { ... }` block. Backend blocks can't reference variables, so that one has to be hardcoded independently — easy to change one and forget the other.

## 2. Configure AWS credentials

```bash
aws configure
```

## 3. Create the Terraform state backend bucket

The bucket that holds `terraform.tfstate` is **intentionally not managed by this project's own Terraform config** — Terraform can't create the very bucket it needs before it can initialize, so this one is bootstrapped by hand, once, before anything else.

```bash
aws s3api create-bucket --bucket <tfstate-bucket-name> --region us-east-1
aws s3api put-bucket-versioning --bucket <tfstate-bucket-name> --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket <tfstate-bucket-name> --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
aws s3api put-public-access-block --bucket <tfstate-bucket-name> --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

(Outside `us-east-1`, `create-bucket` additionally needs `--create-bucket-configuration LocationConstraint=<region>`.)

If this bucket already exists with data in it, skip this step — the rest of this guide then proceeds against a non-empty state instead of an empty one.

## 4. Initialize Terraform

```bash
cd infra/terraform
terraform init
```

## 5. Apply the ECR repositories first

The ECS services need images to already be in ECR before they can start; ECR needs to exist before an image can be pushed to it. Break the cycle by applying just the two repositories first:

```bash
terraform apply -target=aws_ecr_repository.api -target=aws_ecr_repository.worker
```

## 6. Build and push the Docker images

From the repo root:

```bash
podman build -t <account-id>.dkr.ecr.<region>.amazonaws.com/image-pipeline-api:latest ./api
podman build -t <account-id>.dkr.ecr.<region>.amazonaws.com/image-pipeline-worker:latest ./worker

aws ecr get-login-password --region <region> | podman login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

podman push <account-id>.dkr.ecr.<region>.amazonaws.com/image-pipeline-api:latest
podman push <account-id>.dkr.ecr.<region>.amazonaws.com/image-pipeline-worker:latest
```

Get `<account-id>` from `aws sts get-caller-identity`.

## 7. Apply the rest of the infrastructure

```bash
terraform apply
```

This creates the S3 bucket, SQS queue, IAM roles/policies, networking, ECS cluster, task definitions, and services — with `APP_S3_BUCKET_NAME`/`APP_SQS_QUEUE_URL` wired automatically via Terraform resource references (`ecs.tf`), nothing to configure by hand.

## 8. Validate end-to-end

```bash
# get the API task's public IP
TASK_ARN=$(aws ecs list-tasks --cluster image-pipeline-cluster --service-name image-pipeline-api --query 'taskArns[0]' --output text)
ENI=$(aws ecs describe-tasks --cluster image-pipeline-cluster --tasks "$TASK_ARN" --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value|[0]' --output text)
IP=$(aws ec2 describe-network-interfaces --network-interface-ids "$ENI" --query 'NetworkInterfaces[0].Association.PublicIp' --output text)

# upload and poll status
curl -F "file=@some-image.jpg" http://$IP:8080/images
curl http://$IP:8080/images/<imageId>
```

Expect `{"status":"READY","url":"..."}` once the Worker has processed the image, and a working download from that presigned URL.

## Gotchas

- **Bucket names are global, not per-account** — see step 1.
- **New public IPs**: each ECS task launch (including from a forced redeployment) gets a fresh public IP — always re-fetch it as shown in step 8, don't reuse an old one.
- **Local dev tooling**: this project was originally built inside a distrobox with Podman running on the host, controlled via `podman-remote`. That's an implementation detail, not a requirement — a regular local Docker/Podman install works the same way, and the commands above don't assume distrobox.
