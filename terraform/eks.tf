# EKS cluster with a managed node group in the private subnets.
#
# Who can use kubectl (EKS access entries):
#   you (the IAM identity running Terraform)  cluster admin
#   Jenkins role                              admin in the "hr-portal" namespace only (deploys the app)
#   bastion role                              read-only view of the cluster (troubleshooting)
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 21.26"

  name               = "${local.name}-eks"
  kubernetes_version = var.kubernetes_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # API endpoint: private for Jenkins/bastion inside the VPC, public only for your IP (kubectl from your laptop)
  endpoint_private_access      = true
  endpoint_public_access       = true
  endpoint_public_access_cidrs = ["${var.my_ip}/32"]

  # IAM OIDC provider, needed for IRSA (IAM roles for Kubernetes service accounts)
  enable_irsa = true

  # Makes the identity running Terraform a cluster admin
  enable_cluster_creator_admin_permissions = true

  addons = {
    vpc-cni = {
      before_compute = true # networking must exist before nodes join
    }
    kube-proxy = {}
    coredns    = {}
  }

  eks_managed_node_groups = {
    default = {
      ami_type       = "AL2023_x86_64_STANDARD"
      instance_types = var.node_instance_types

      min_size     = 2
      max_size     = 3
      desired_size = var.node_desired_size

      # Lets you open a shell on a node with SSM if you ever need to debug it
      iam_role_additional_policies = {
        ssm = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
      }
    }
  }

  access_entries = {
    jenkins = {
      principal_arn = aws_iam_role.jenkins.arn
      policy_associations = {
        deploy = {
          policy_arn = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSAdminPolicy"
          access_scope = {
            type       = "namespace"
            namespaces = [local.app_namespace]
          }
        }
      }
    }

    bastion = {
      principal_arn = aws_iam_role.bastion.arn
      policy_associations = {
        view = {
          policy_arn   = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSViewPolicy"
          access_scope = { type = "cluster" }
        }
      }
    }
  }
}
