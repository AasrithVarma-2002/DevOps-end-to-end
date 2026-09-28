# Passwords live in AWS Secrets Manager, never in Git or in the container image.
# In step 3, External Secrets Operator copies them into a Kubernetes Secret for the pods.

resource "random_password" "app_admin" {
  length      = 20
  special     = false
  min_upper   = 2
  min_lower   = 2
  min_numeric = 2
}

resource "aws_secretsmanager_secret" "db" {
  name        = "${local.name}/db"
  description = "RDS MySQL connection details for the HR Portal"
  # 0 = delete immediately on destroy, so the same name can be reused when you re-create
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    host     = aws_db_instance.main.address
    port     = tostring(aws_db_instance.main.port)
    dbname   = aws_db_instance.main.db_name
    username = aws_db_instance.main.username
    password = random_password.db.result
  })
}

resource "aws_secretsmanager_secret" "app_admin" {
  name                    = "${local.name}/app-admin"
  description             = "First Super Admin login for the HR Portal"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app_admin" {
  secret_id = aws_secretsmanager_secret.app_admin.id
  secret_string = jsonencode({
    email    = var.app_admin_email
    password = random_password.app_admin.result
  })
}
