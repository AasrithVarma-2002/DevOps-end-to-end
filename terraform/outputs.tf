output "region" {
  value = var.region
}

output "vpc_id" {
  value = module.vpc.vpc_id
}

# ---------------------------------------------------------------- EKS

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "kubeconfig_command" {
  description = "Run on your laptop to use kubectl against the cluster"
  value       = "aws eks update-kubeconfig --region ${var.region} --name ${module.eks.cluster_name}"
}

output "admin_cidr" {
  description = "Who can reach Jenkins and the EKS API from the internet (local.admin_cidr in main.tf)"
  value       = local.admin_cidr
}

output "app_namespace" {
  value = local.app_namespace
}

output "lb_controller_role_arn" {
  description = "IRSA role for the AWS Load Balancer Controller (step 3)"
  value       = aws_iam_role.lb_controller.arn
}

output "external_secrets_role_arn" {
  description = "IRSA role for External Secrets Operator (step 3)"
  value       = aws_iam_role.external_secrets.arn
}

output "app_role_arn" {
  description = "IRSA role for the HR Portal pods (S3 documents). The Jenkinsfile sets it on the service account."
  value       = aws_iam_role.app.arn
}

output "app_config_secret_name" {
  value = aws_secretsmanager_secret.app_config.name
}

output "notification_email" {
  description = "SES sender. Check it is verified: aws sesv2 get-email-identity --email-identity <it>"
  value       = var.notification_email
}

output "documents_bucket" {
  description = "Private S3 bucket for payslip PDFs"
  value       = aws_s3_bucket.documents.bucket
}

# ---------------------------------------------------------------- ECR

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

# ---------------------------------------------------------------- RDS

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "db_secret_name" {
  value = aws_secretsmanager_secret.db.name
}

output "app_admin_secret_name" {
  value = aws_secretsmanager_secret.app_admin.name
}

# ---------------------------------------------------------------- Jenkins

output "jenkins_url" {
  value = "http://${aws_eip.jenkins.public_ip}:8080"
}

output "jenkins_ssh_command" {
  value = "ssh -i ${trimsuffix(var.jenkins_ssh_public_key_path, ".pub")} ec2-user@${aws_eip.jenkins.public_ip}"
}

output "jenkins_instance_id" {
  value = aws_instance.jenkins.id
}

# ---------------------------------------------------------------- bastion

output "bastion_instance_id" {
  value = aws_instance.bastion.id
}

output "bastion_ssm_command" {
  value = "aws ssm start-session --region ${var.region} --target ${aws_instance.bastion.id}"
}

output "rds_port_forward_command" {
  description = "Tunnel RDS to localhost:3306 on your laptop through the bastion"
  value       = "aws ssm start-session --region ${var.region} --target ${aws_instance.bastion.id} --document-name AWS-StartPortForwardingSessionToRemoteHost --parameters host=${aws_db_instance.main.address},portNumber=3306,localPortNumber=3306"
}
