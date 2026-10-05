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

  # Who may reach Jenkins (8080, 22) and the EKS API from the internet. Open to anywhere because
  # the admin's home IP keeps changing; Jenkins login, the SSH key and IAM still protect them.
  # To lock down again, put your IP here, e.g. "49.37.10.20/32".
  admin_cidr = "0.0.0.0/0"
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
