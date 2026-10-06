# IAM roles for Kubernetes service accounts (IRSA).
# A pod running as a specific service account gets temporary AWS credentials for exactly one role.
# No access keys are stored anywhere. Two roles are for the add-ons installed in step 3; the third
# is for the HR Portal pods themselves (payslip PDFs in S3, stage 3).

locals {
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider     = module.eks.oidc_provider # issuer URL without https://
}

# Trust policy: "only the service account <namespace>/<name> in this cluster may assume the role"
data "aws_iam_policy_document" "irsa_trust" {
  for_each = {
    lb_controller    = "kube-system:aws-load-balancer-controller"
    external_secrets = "external-secrets:external-secrets"
    app              = "${local.app_namespace}:hr-portal" # the chart's service account
  }

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider}:sub"
      values   = ["system:serviceaccount:${each.value}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# ---------------------------------------------------------------- AWS Load Balancer Controller
# Turns a Kubernetes Ingress into an Application Load Balancer.

resource "aws_iam_role" "lb_controller" {
  name               = "${local.name}-aws-load-balancer-controller"
  assume_role_policy = data.aws_iam_policy_document.irsa_trust["lb_controller"].json
}

resource "aws_iam_policy" "lb_controller" {
  name        = "${local.name}-aws-load-balancer-controller"
  description = "Official AWS Load Balancer Controller policy (see policies/aws-load-balancer-controller.VERSION)"
  policy      = file("${path.module}/policies/aws-load-balancer-controller.json")
}

resource "aws_iam_role_policy_attachment" "lb_controller" {
  role       = aws_iam_role.lb_controller.name
  policy_arn = aws_iam_policy.lb_controller.arn
}

# ---------------------------------------------------------------- External Secrets Operator
# Reads the HR Portal secrets from Secrets Manager and creates Kubernetes Secrets. Nothing else.

resource "aws_iam_role" "external_secrets" {
  name               = "${local.name}-external-secrets"
  assume_role_policy = data.aws_iam_policy_document.irsa_trust["external_secrets"].json
}

resource "aws_iam_role_policy" "external_secrets" {
  name = "read-hr-portal-secrets"
  role = aws_iam_role.external_secrets.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "ReadHrPortalSecretsOnly"
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
      Resource = [aws_secretsmanager_secret.db.arn, aws_secretsmanager_secret.app_admin.arn]
    }]
  })
}

# ---------------------------------------------------------------- HR Portal app
# The app's own pods: read and write documents in the documents bucket. Nothing else.
# The role name is fixed so the Jenkinsfile can build its ARN from the account ID.

resource "aws_iam_role" "app" {
  name               = "${local.name}-app"
  assume_role_policy = data.aws_iam_policy_document.irsa_trust["app"].json
}

resource "aws_iam_role_policy" "app_documents" {
  name = "documents-bucket"
  role = aws_iam_role.app.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "ReadWriteDocuments"
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:GetObject"]
      Resource = "${aws_s3_bucket.documents.arn}/*"
    }]
  })
}
