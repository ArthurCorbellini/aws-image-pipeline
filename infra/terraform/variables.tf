variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "s3_bucket_name" {
  description = "Name of the S3 bucket used by the image pipeline app"
  type        = string
  default     = "image-pipeline-s3-5dafe7b0"
}

variable "sqs_queue_name" {
  description = "Name of the SQS queue used by the image pipeline app"
  type        = string
  default     = "image-pipeline-sqs"
}
