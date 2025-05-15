# AWS provider configuration
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

# DB IDENTIFIER
variable "db_identifier" {
  description = "The identifier for the RDS instance"
  type        = string
  default     = "lanchonete-db"
}