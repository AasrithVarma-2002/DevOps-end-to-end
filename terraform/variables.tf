# ---------------------------------------------------------------- general

variable "region" {
  description = "AWS region for all resources"
  type        = string
  default     = "ap-south-1"
}

variable "project" {
  description = "Name prefix for resources"
  type        = string
  default     = "hr-portal"
}

variable "environment" {
  description = "Environment name, used in tags"
  type        = string
  default     = "dev"
}

variable "my_ip" {
  description = "Your public IP (curl -s https://checkip.amazonaws.com), or a CIDR. Only this can reach Jenkins (8080, 22) and the EKS API. \"0.0.0.0/0\" = anywhere (Jenkins login and SSH key still required)."
  type        = string

  validation {
    condition     = can(regex("^\\d{1,3}(\\.\\d{1,3}){3}$", var.my_ip)) || can(cidrhost(var.my_ip, 0))
    error_message = "my_ip must be an IPv4 address like 49.37.10.20, or a CIDR like 49.37.10.0/24 or 0.0.0.0/0."
  }
}

variable "eks_public_access_cidrs" {
  description = "Who can reach the EKS API from the internet. Empty = only my_ip. [\"0.0.0.0/0\"] = anywhere (still needs IAM + an access entry); handy while your home IP keeps changing."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for c in var.eks_public_access_cidrs : can(cidrhost(c, 0))])
    error_message = "eks_public_access_cidrs must be CIDRs like 49.37.10.0/24 or 0.0.0.0/0."
  }
}

variable "allow_root_credentials" {
  description = "Allow running Terraform with the AWS root user's access keys (not recommended; an IAM user is safer)"
  type        = bool
  default     = false
}

# ---------------------------------------------------------------- network

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

# ---------------------------------------------------------------- EKS

variable "kubernetes_version" {
  description = "EKS Kubernetes version. Check what's available: aws eks describe-cluster-versions --region ap-south-1 --query 'clusterVersions[].clusterVersion'"
  type        = string
  default     = "1.35"
}

variable "node_instance_types" {
  description = "EC2 instance types for the EKS worker nodes"
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_desired_size" {
  description = "Number of worker nodes"
  type        = number
  default     = 2
}

# ---------------------------------------------------------------- RDS

variable "db_instance_class" {
  description = "RDS instance size"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_engine_version" {
  description = "MySQL major version for RDS"
  type        = string
  default     = "8.4"
}

variable "db_backup_retention_days" {
  description = "Days of automated backups (AWS free-plan accounts may only allow 1)"
  type        = number
  default     = 7
}

variable "db_multi_az" {
  description = "Standby replica in a second AZ (doubles the RDS cost; use true in production)"
  type        = bool
  default     = false
}

# ---------------------------------------------------------------- app

variable "app_admin_email" {
  description = "Login for the first Super Admin, created when the app starts against an empty database"
  type        = string
  default     = "admin@hrportal.local"
}

# ---------------------------------------------------------------- Jenkins

variable "jenkins_instance_type" {
  description = "EC2 instance type for Jenkins (it builds Java and Docker images)"
  type        = string
  default     = "t3.medium"
}

variable "jenkins_ssh_public_key_path" {
  description = "Public key for SSH to Jenkins (create with: ssh-keygen -t ed25519 -f ~/.ssh/hr-portal-jenkins)"
  type        = string
  default     = "~/.ssh/hr-portal-jenkins.pub"
}
