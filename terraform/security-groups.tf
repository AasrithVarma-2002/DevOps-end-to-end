# Who may talk to whom. Outbound traffic is open; inbound is only what's listed here.
#
#   internet ─80─► ALB (security group created by the AWS Load Balancer Controller in step 3)
#   ALB ─8080─► pods on the EKS nodes (rule added by the controller)
#   EKS nodes ─3306─► RDS
#   bastion   ─3306─► RDS
#   bastion, Jenkins ─443─► EKS API (private endpoint)
#   your IP ─8080, 22─► Jenkins

# ---------------------------------------------------------------- Jenkins

resource "aws_security_group" "jenkins" {
  name        = "${local.name}-jenkins"
  description = "Jenkins: UI and SSH from the admin IP only"
  vpc_id      = module.vpc.vpc_id
  tags        = { Name = "${local.name}-jenkins" }
}

resource "aws_vpc_security_group_ingress_rule" "jenkins_ui" {
  security_group_id = aws_security_group.jenkins.id
  description       = "Jenkins UI from my IP"
  cidr_ipv4         = local.admin_cidr
  ip_protocol       = "tcp"
  from_port         = 8080
  to_port           = 8080
}

resource "aws_vpc_security_group_ingress_rule" "jenkins_ssh" {
  security_group_id = aws_security_group.jenkins.id
  description       = "SSH from my IP"
  cidr_ipv4         = local.admin_cidr
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}

resource "aws_vpc_security_group_egress_rule" "jenkins_all" {
  security_group_id = aws_security_group.jenkins.id
  description       = "GitHub, ECR, EKS, package repositories"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ---------------------------------------------------------------- bastion

resource "aws_security_group" "bastion" {
  name        = "${local.name}-bastion"
  description = "Bastion: no inbound rules, reached through SSM Session Manager"
  vpc_id      = module.vpc.vpc_id
  tags        = { Name = "${local.name}-bastion" }
}

resource "aws_vpc_security_group_egress_rule" "bastion_all" {
  security_group_id = aws_security_group.bastion.id
  description       = "SSM (via NAT), RDS, EKS API"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ---------------------------------------------------------------- RDS

resource "aws_security_group" "rds" {
  name        = "${local.name}-rds"
  description = "RDS MySQL: 3306 from EKS nodes and the bastion only"
  vpc_id      = module.vpc.vpc_id
  tags        = { Name = "${local.name}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_eks_nodes" {
  security_group_id            = aws_security_group.rds.id
  description                  = "MySQL from EKS nodes (app pods)"
  referenced_security_group_id = module.eks.node_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_bastion" {
  security_group_id            = aws_security_group.rds.id
  description                  = "MySQL from the bastion (troubleshooting)"
  referenced_security_group_id = aws_security_group.bastion.id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}

# ---------------------------------------------------------------- EKS API (private endpoint)
# Jenkins and the bastion are inside the VPC, so they reach the Kubernetes API through its
# private endpoint. Your laptop uses the public endpoint (my_ip, or eks_public_access_cidrs, in eks.tf).

resource "aws_vpc_security_group_ingress_rule" "eks_api_from_jenkins" {
  security_group_id            = module.eks.cluster_security_group_id
  description                  = "Kubernetes API from Jenkins"
  referenced_security_group_id = aws_security_group.jenkins.id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}

resource "aws_vpc_security_group_ingress_rule" "eks_api_from_bastion" {
  security_group_id            = module.eks.cluster_security_group_id
  description                  = "Kubernetes API from the bastion"
  referenced_security_group_id = aws_security_group.bastion.id
  ip_protocol                  = "tcp"
  from_port                    = 443
  to_port                      = 443
}
