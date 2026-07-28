output "s3_bucket_name" {
  value = aws_s3_bucket.image_pipeline.id
}

output "sqs_queue_url" {
  value = aws_sqs_queue.image_pipeline.url
}

output "task_role_arn" {
  value = aws_iam_role.task_role.arn
}
