# Private registry for the app image. Jenkins pushes hr-portal:<git-sha>; EKS nodes pull it.

resource "aws_ecr_repository" "app" {
  name = local.name

  # A tag always means the same image: hr-portal:<git-sha> can never be overwritten
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  # Learning environment: let terraform destroy remove the repo even if it has images
  force_delete = true
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only the 20 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}
