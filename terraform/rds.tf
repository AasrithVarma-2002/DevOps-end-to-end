# MySQL 8.4 on RDS, in the private database subnets, reachable only from EKS nodes and the bastion.

resource "random_password" "db" {
  length           = 24
  special          = true
  override_special = "!#%*-_=+" # RDS rejects / @ " and spaces
}

resource "aws_db_parameter_group" "mysql" {
  name   = "${local.name}-mysql84"
  family = "mysql8.4"

  # Refuse unencrypted connections (the app connects with sslMode=REQUIRED)
  parameter {
    name  = "require_secure_transport"
    value = "1"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name}-db"
  engine         = "mysql"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  db_name  = "hrdb"
  username = "hradmin"
  password = random_password.db.result
  port     = 3306

  allocated_storage     = 20
  max_allocated_storage = 50 # storage autoscaling
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = var.db_multi_az
  parameter_group_name   = aws_db_parameter_group.mysql.name

  backup_retention_period    = var.db_backup_retention_days
  backup_window              = "20:00-21:00" # 01:30-02:30 IST
  maintenance_window         = "sun:21:30-sun:22:30"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  enabled_cloudwatch_logs_exports = ["error", "slowquery"]

  # Learning environment: allow terraform destroy without a final snapshot.
  # Production: deletion_protection = true, skip_final_snapshot = false.
  deletion_protection = false
  skip_final_snapshot = true
  apply_immediately   = true

  tags = { Name = "${local.name}-db" }
}
