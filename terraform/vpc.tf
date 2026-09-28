# VPC across 2 AZs with three subnet tiers:
#   public    10.0.0.0/24,  10.0.1.0/24   ALB, NAT Gateway, Jenkins        (route to Internet Gateway)
#   private   10.0.10.0/24, 10.0.11.0/24  EKS worker nodes/pods, bastion   (route to NAT Gateway)
#   database  10.0.20.0/24, 10.0.21.0/24  RDS                              (no route to the internet)
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.7"

  name = "${local.name}-vpc"
  cidr = var.vpc_cidr
  azs  = local.azs

  public_subnets   = [for i in range(2) : cidrsubnet(var.vpc_cidr, 8, i)]      # 10.0.0.0/24, 10.0.1.0/24
  private_subnets  = [for i in range(2) : cidrsubnet(var.vpc_cidr, 8, i + 10)] # 10.0.10.0/24, 10.0.11.0/24
  database_subnets = [for i in range(2) : cidrsubnet(var.vpc_cidr, 8, i + 20)] # 10.0.20.0/24, 10.0.21.0/24

  # One NAT Gateway saves ~$35/month; production would use one per AZ (single_nat_gateway = false)
  enable_nat_gateway = true
  single_nat_gateway = true

  # RDS needs a subnet group. The database subnets get their OWN route table with only the
  # local VPC route (no NAT, no Internet Gateway). Without create_database_subnet_route_table
  # the module would attach them to the private route table, which routes 0.0.0.0/0 to the NAT.
  create_database_subnet_group           = true
  create_database_subnet_route_table     = true
  create_database_nat_gateway_route      = false
  create_database_internet_gateway_route = false

  # Needed for EKS private endpoint DNS and RDS endpoint names
  enable_dns_hostnames = true
  enable_dns_support   = true

  # Tell the AWS Load Balancer Controller where to put load balancers
  public_subnet_tags = {
    "kubernetes.io/role/elb" = 1 # internet-facing ALBs go here
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = 1 # internal load balancers go here
  }
}
