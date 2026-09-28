data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

# Latest Amazon Linux 2023 image (SSM Agent pre-installed), used for Jenkins and the bastion
data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

locals {
  name       = var.project
  account_id = data.aws_caller_identity.current.account_id
  azs        = slice(data.aws_availability_zones.available.names, 0, 2)

  # Kubernetes namespace the app is deployed into (Jenkins may only deploy here)
  app_namespace = "hr-portal"
}

# EKS access entries and STS role assumption don't work with root credentials,
# so stop early with a clear message instead of failing halfway through an apply.
resource "terraform_data" "require_iam_identity" {
  lifecycle {
    precondition {
      condition     = !endswith(data.aws_caller_identity.current.arn, ":root")
      error_message = "Terraform is running with the AWS root user. Create an IAM user with AdministratorAccess, run 'aws configure' with its keys, and try again (see terraform/README.md, step 2.0)."
    }
  }
}
