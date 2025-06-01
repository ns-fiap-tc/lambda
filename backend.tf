terraform {
  backend "s3" {
    bucket         = "lanchonete-tfstate-bucket"
    key            = "infra-lambda/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "terraform-lock"
    encrypt        = true
  }
}
