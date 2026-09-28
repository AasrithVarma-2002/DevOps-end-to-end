output "state_bucket" {
  description = "S3 bucket that stores the main Terraform state"
  value       = aws_s3_bucket.tfstate.bucket
}

output "next_step" {
  value = "cd .. && terraform init -backend-config=backend.hcl"
}
