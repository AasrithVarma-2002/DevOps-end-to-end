# Offline test: plans and applies against a MOCKED AWS (nothing is created). Run: terraform init -backend=false && terraform test
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012", arn = "arn:aws:iam::123456789012:user/devops-admin" }
  }
  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-south-1a", "ap-south-1b", "ap-south-1c"] }
  }
  mock_data "aws_ssm_parameter" {
    defaults = { value = "ami-0123456789abcdef0" }
  }
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
  mock_data "aws_partition" {
    defaults = { partition = "aws", dns_suffix = "amazonaws.com" }
  }
  mock_data "aws_iam_session_context" {
    defaults = { issuer_arn = "arn:aws:iam::123456789012:user/devops-admin" }
  }
  mock_resource "aws_ecr_repository" {
    defaults = { repository_url = "123456789012.dkr.ecr.ap-south-1.amazonaws.com/hr-portal", arn = "arn:aws:ecr:ap-south-1:123456789012:repository/hr-portal" }
  }
  mock_resource "aws_eks_cluster" {
    defaults = {
      arn                   = "arn:aws:eks:ap-south-1:123456789012:cluster/hr-portal-eks"
      endpoint              = "https://ABC.gr7.ap-south-1.eks.amazonaws.com"
      certificate_authority = [{ data = "Y2VydA==" }]
      identity              = [{ oidc = [{ issuer = "https://oidc.eks.ap-south-1.amazonaws.com/id/ABC" }] }]
    }
  }
  mock_resource "aws_launch_template" {
    defaults = { id = "lt-0123456789abcdef0", latest_version = 1 }
  }
  mock_resource "aws_security_group" {
    defaults = { id = "sg-0123456789abcdef0" }
  }
  mock_data "aws_eks_addon_version" {
    defaults = { version = "v1.20.0-eksbuild.1" }
  }
  mock_data "tls_certificate" {
    defaults = { certificates = [{ sha1_fingerprint = "9e99a48a9960b14926bb7f3b02e22da2b0ab7280" }] }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/mock" }
  }
  mock_resource "aws_iam_policy" {
    defaults = { arn = "arn:aws:iam::123456789012:policy/mock" }
  }
  mock_resource "aws_iam_openid_connect_provider" {
    defaults = { arn = "arn:aws:iam::123456789012:oidc-provider/oidc.eks.ap-south-1.amazonaws.com/id/ABC" }
  }
  mock_resource "aws_kms_key" {
    defaults = { arn = "arn:aws:kms:ap-south-1:123456789012:key/mock" }
  }
  mock_resource "aws_secretsmanager_secret" {
    defaults = { arn = "arn:aws:secretsmanager:ap-south-1:123456789012:secret:mock" }
  }
}
mock_provider "tls" {
  mock_data "tls_certificate" {
    defaults = { certificates = [{ sha1_fingerprint = "9e99a48a9960b14926bb7f3b02e22da2b0ab7280" }] }
  }
}
mock_provider "time" {}
mock_provider "random" {}
mock_provider "null" {}
mock_provider "cloudinit" {}

variables {
  my_ip                       = "49.37.10.20"
  jenkins_ssh_public_key_path = "tests/fixtures/test_key.pub"
}

run "apply_as_iam_user" {
  command = apply

  assert {
    condition     = tolist(module.vpc.public_subnets_cidr_blocks) == tolist(["10.0.0.0/24", "10.0.1.0/24"]) && tolist(module.vpc.database_subnets_cidr_blocks) == tolist(["10.0.20.0/24", "10.0.21.0/24"])
    error_message = "unexpected public subnets"
  }
  assert {
    condition     = length(module.vpc.database_route_table_ids) == 1 && length(module.vpc.database_nat_gateway_route_ids) == 0 && module.vpc.database_internet_gateway_route_id == null
    error_message = "database subnets must have their own route table with no NAT or internet route"
  }
  assert {
    condition     = aws_vpc_security_group_ingress_rule.jenkins_ui.cidr_ipv4 == "49.37.10.20/32"
    error_message = "Jenkins UI must only be open to my_ip"
  }
  assert {
    condition     = strcontains(aws_instance.jenkins.user_data, "EKS_CLUSTER_NAME=hr-portal-eks")
    error_message = "user data not rendered"
  }
  assert {
    condition     = strcontains(aws_instance.jenkins.user_data, "ECR_REGISTRY=123456789012.dkr.ecr.ap-south-1.amazonaws.com") && strcontains(aws_instance.bastion.user_data, "stable-1.35.txt")
    error_message = "ECR registry / kubectl version not rendered"
  }
  assert {
    condition     = jsondecode(aws_iam_role_policy.jenkins_ecr_eks.policy).Statement[1].Resource == "arn:aws:ecr:ap-south-1:123456789012:repository/hr-portal"
    error_message = "Jenkins ECR permissions must be scoped to the hr-portal repo"
  }
  assert {
    condition     = length(aws_security_group.bastion.ingress) == 0
    error_message = "bastion must have no inbound rules"
  }
  assert {
    condition     = aws_db_instance.main.publicly_accessible == false && aws_db_instance.main.storage_encrypted
    error_message = "RDS must be private and encrypted"
  }
}

run "root_user_is_rejected" {
  command = plan
  override_data {
    target = data.aws_caller_identity.current
    values = { account_id = "123456789012", arn = "arn:aws:iam::123456789012:root" }
  }
  expect_failures = [terraform_data.require_iam_identity]
}

run "root_user_allowed_when_opted_in" {
  command = plan
  variables { allow_root_credentials = true }
  override_data {
    target = data.aws_caller_identity.current
    values = { account_id = "123456789012", arn = "arn:aws:iam::123456789012:root" }
  }
}

run "bad_ip_is_rejected" {
  command = plan
  variables { my_ip = "49.37.10.20/32" }
  expect_failures = [var.my_ip]
}

run "eks_api_defaults_to_my_ip" {
  command = plan
  assert {
    condition     = tolist(output.eks_public_access_cidrs) == tolist(["49.37.10.20/32"])
    error_message = "EKS public endpoint must default to my_ip only"
  }
}

run "eks_api_can_be_opened" {
  command = plan
  variables { eks_public_access_cidrs = ["0.0.0.0/0"] }
  assert {
    condition     = tolist(output.eks_public_access_cidrs) == tolist(["0.0.0.0/0"]) && aws_vpc_security_group_ingress_rule.jenkins_ssh.cidr_ipv4 == "49.37.10.20/32"
    error_message = "eks_public_access_cidrs must open only the EKS API, not Jenkins"
  }
}
