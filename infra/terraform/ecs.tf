resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/image-pipeline-api"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/ecs/image-pipeline-worker"
  retention_in_days = 7
}

resource "aws_ecs_cluster" "main" {
  name = "image-pipeline-cluster"
}

resource "aws_ecs_task_definition" "api" {
  family                   = "image-pipeline-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution_role.arn
  task_role_arn            = aws_iam_role.task_role.arn

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = "${aws_ecr_repository.api.repository_url}:latest"
      essential = true
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "APP_S3_BUCKET_NAME", value = aws_s3_bucket.image_pipeline.id },
        { name = "APP_SQS_QUEUE_URL", value = aws_sqs_queue.image_pipeline.url }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.api.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "api"
        }
      }
    }
  ])
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "image-pipeline-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution_role.arn
  task_role_arn            = aws_iam_role.task_role.arn

  container_definitions = jsonencode([
    {
      name      = "worker"
      image     = "${aws_ecr_repository.worker.repository_url}:latest"
      essential = true
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "APP_S3_BUCKET_NAME", value = aws_s3_bucket.image_pipeline.id },
        { name = "APP_SQS_QUEUE_URL", value = aws_sqs_queue.image_pipeline.url }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.worker.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "worker"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "api" {
  name            = "image-pipeline-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.api.id]
    assign_public_ip = true
  }

  # desired_count is toggled manually (`aws ecs update-service --desired-count`)
  # to scale down to 0 between test sessions and avoid paying for idle Fargate tasks.
  # Ignore drift here instead of letting `apply` silently scale it back up.
  lifecycle {
    ignore_changes = [desired_count]
  }
}

resource "aws_ecs_service" "worker" {
  name            = "image-pipeline-worker"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.worker.id]
    assign_public_ip = true
  }

  lifecycle {
    ignore_changes = [desired_count]
  }
}
