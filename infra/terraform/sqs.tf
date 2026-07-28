resource "aws_sqs_queue" "image_pipeline" {
  name = var.sqs_queue_name

  # max_message_size can't be set to AWS's real current default (1048576) here —
  # the AWS provider still validates against the old 262144 ceiling (hashicorp/terraform-provider-aws#43692).
  # Ignore drift on this attribute instead of downgrading the real queue to match the stale client-side limit.
  lifecycle {
    ignore_changes = [max_message_size]
  }
}
