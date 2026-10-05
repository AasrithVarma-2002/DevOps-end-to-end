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

  # my_ip as a CIDR: a plain IP becomes x.x.x.x/32, a CIDR (e.g. 0.0.0.0/0) is used as it is
  admin_cidr = strcontains(var.my_ip, "/") ? var.my_ip : "${var.my_ip}/32"
}

# Running with root access keys is discouraged: the root user can do anything and can't assume
# IAM roles. Stop early unless you've explicitly opted in with allow_root_credentials = true.
resource "terraform_data" "require_iam_identity" {
  lifecycle {
    precondition {
      condition     = var.allow_root_credentials || !endswith(data.aws_caller_identity.current.arn, ":root")
      error_message = "Terraform is running with the AWS root user. Either use an IAM user (see terraform/README.md, step 2.0) or set allow_root_credentials = true in terraform.tfvars."
    }
  }
}
