#!/bin/bash
set -e

cd "$(dirname "$0")"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Using AWS account: $ACCOUNT_ID"

TMP_POLICY=$(mktemp)
sed "s/<ACCOUNT_ID>/$ACCOUNT_ID/" iam-policy.json > "$TMP_POLICY"

echo "Creating IAM role..."
aws iam create-role \
  --role-name image-pipeline-task-role \
  --assume-role-policy-document file://iam-trust-policy.json

echo "Creating IAM policy..."
aws iam create-policy \
  --policy-name image-pipeline-least-privilege \
  --policy-document file://"$TMP_POLICY"

echo "Attaching policy to role..."
aws iam attach-role-policy \
  --role-name image-pipeline-task-role \
  --policy-arn arn:aws:iam::$ACCOUNT_ID:policy/image-pipeline-least-privilege

rm "$TMP_POLICY"

echo "Done."
