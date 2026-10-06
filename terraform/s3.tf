# Private bucket for stored files: payslip PDFs (payslips/...) and employee documents
# (documents/...). Only the app's IRSA role can read and write it (iam-irsa.tf); nothing in it
# is ever public. Browsers download through 5-minute presigned links the app signs with that role.
#
#   pod (service account hr-portal) ─IRSA─► role hr-portal-app ─► s3://hr-portal-documents-<account>/payslips/...
#                                                                                                  /documents/...

resource "aws_s3_bucket" "documents" {
  # Bucket names are global across all AWS accounts, so the account ID makes it unique.
  # The Jenkinsfile builds the same name from the account ID.
  bucket = "${local.name}-documents-${local.account_id}"

  # Learning environment: terraform destroy also deletes the payslips
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ACLs off: access is decided only by IAM and the bucket policy
resource "aws_s3_bucket_ownership_controls" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Overwritten or deleted payslips can be recovered for 90 days
resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "documents" {
  bucket     = aws_s3_bucket.documents.id
  depends_on = [aws_s3_bucket_versioning.documents]

  rule {
    id     = "expire-old-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 90
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  # Employee documents are kept for the whole employment but rarely opened after onboarding:
  # Standard-IA costs ~45% less to store and still opens instantly (Glacier would need a
  # restore first, which breaks the download links).
  rule {
    id     = "documents-to-infrequent-access"
    status = "Enabled"
    filter {
      prefix = "documents/"
    }
    transition {
      days          = 90
      storage_class = "STANDARD_IA"
    }
  }
}

# Refuse any request that isn't HTTPS
resource "aws_s3_bucket_policy" "documents" {
  bucket     = aws_s3_bucket.documents.id
  depends_on = [aws_s3_bucket_public_access_block.documents]
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.documents.arn, "${aws_s3_bucket.documents.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}
