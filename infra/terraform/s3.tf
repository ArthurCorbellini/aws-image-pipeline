resource "aws_s3_bucket" "image_pipeline" {
  bucket = var.s3_bucket_name
}
