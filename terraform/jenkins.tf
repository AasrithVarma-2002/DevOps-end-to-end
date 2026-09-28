# Jenkins CI/CD server: public subnet, UI and SSH from your IP only,
# AWS access through an IAM role (no access keys on the server).

# ---------------------------------------------------------------- IAM role

# Trust policy: only EC2 may assume this role (and only the instance it's attached to gets it)
resource "aws_iam_role" "jenkins" {
  name = "${local.name}-jenkins"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Permissions: push to this ECR repo, connect to this EKS cluster. Nothing else.
resource "aws_iam_role_policy" "jenkins_ecr_eks" {
  name = "ecr-push-and-eks-connect"
  role = aws_iam_role.jenkins.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*" # this action doesn't support resource-level permissions
      },
      {
        Sid    = "EcrPushPullHrPortalOnly"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
          "ecr:DescribeImages",
          "ecr:DescribeImageScanFindings"
        ]
        Resource = aws_ecr_repository.app.arn
      },
      {
        Sid      = "ConnectToThisClusterOnly"
        Effect   = "Allow"
        Action   = "eks:DescribeCluster"
        Resource = module.eks.cluster_arn
      }
    ]
  })
}

# Backup way in (SSM Session Manager) if SSH isn't possible, e.g. your IP changed
resource "aws_iam_role_policy_attachment" "jenkins_ssm" {
  role       = aws_iam_role.jenkins.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# The "wrapper" that lets an EC2 instance carry the role
resource "aws_iam_instance_profile" "jenkins" {
  name = "${local.name}-jenkins"
  role = aws_iam_role.jenkins.name
}

# ---------------------------------------------------------------- SSH key

resource "aws_key_pair" "jenkins" {
  key_name   = "${local.name}-jenkins"
  public_key = file(pathexpand(var.jenkins_ssh_public_key_path))
}

# ---------------------------------------------------------------- EC2

resource "aws_instance" "jenkins" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = var.jenkins_instance_type
  subnet_id              = module.vpc.public_subnets[0]
  vpc_security_group_ids = [aws_security_group.jenkins.id]
  iam_instance_profile   = aws_iam_instance_profile.jenkins.name # ← attaches the role to THIS instance
  key_name               = aws_key_pair.jenkins.key_name

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted   = true
  }

  # IMDSv2 only (protects the role credentials from SSRF-style attacks)
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  # Installs Jenkins, Docker, kubectl, Helm, Trivy on first boot (log: /var/log/hr-portal-setup.log)
  user_data = templatefile("${path.module}/scripts/jenkins-user-data.sh.tftpl", {
    region             = var.region
    cluster_name       = module.eks.cluster_name
    ecr_registry       = split("/", aws_ecr_repository.app.repository_url)[0]
    ecr_repository     = aws_ecr_repository.app.name
    kubernetes_version = var.kubernetes_version
  })
  # Editing the script later must not wipe the Jenkins server
  user_data_replace_on_change = false

  tags = { Name = "${local.name}-jenkins" }

  # Boot only after routes, NAT and Internet Gateway exist, so the install script has internet
  depends_on = [module.vpc]

  lifecycle {
    ignore_changes = [ami, user_data] # a newer AMI shouldn't recreate Jenkins
  }
}

# Fixed public IP, so the Jenkins URL doesn't change if the instance is stopped and started
resource "aws_eip" "jenkins" {
  domain   = "vpc"
  instance = aws_instance.jenkins.id
  tags     = { Name = "${local.name}-jenkins" }
}
