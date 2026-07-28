output "s3_bucket_name" {
  value = aws_s3_bucket.image_pipeline.id
}

output "sqs_queue_url" {
  value = aws_sqs_queue.image_pipeline.url
}

output "task_role_arn" {
  value = aws_iam_role.task_role.arn
}

output "ecr_api_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "ecr_worker_repository_url" {
  value = aws_ecr_repository.worker.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}
