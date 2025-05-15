data "aws_db_instance" "lanchonete_db" {
  db_instance_identifier = var.db_identifier
}

data "aws_eks_cluster" "lanchonete_cluster" {
  name = "lanchonete_cluster"
}

data "aws_eks_cluster_auth" "lanchonete_cluster_auth" {
  name = data.aws_eks_cluster.lanchonete_cluster.name
}

data "aws_iam_role" "labrole" {
  name = "LabRole"
}

data "kubernetes_service" "service-lanchonete-app" {
  metadata {
    name      = "service-lanchonete-app"
    namespace = "default"
  }
}

data "kubernetes_service" "service-pagamento-mock" {
  metadata {
    name      = "service-pagamento-mock"
    namespace = "default"
  }
}

data "aws_api_gateway_rest_api" "lanchonete_cluster_api_gw" {
  name = "EKS_API_Gateway"
}

data "kubernetes_secret" "secrets-lanchonete" {
 metadata {
   name = "secrets-lanchonete"
 }
}

data "aws_vpc" "lanchonete_vpc" {
  filter {
    name   = "cidr"
    values = ["10.0.0.0/16"]
  }

  filter {
    name   = "tag:Name"
    values = ["lanchonete_vpc"]
  }
}

data "aws_subnet" "lanchonete_private_subnet_1" {
  filter {
    name   = "tag:Name"
    values = ["lanchonete_private_subnet_1"]
  }

  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.lanchonete_vpc.id]
  }

  filter {
    name   = "cidrBlock"
    values = ["10.0.3.0/24"]
  }
}

data "aws_subnet" "lanchonete_private_subnet_2" {
  filter {
    name   = "tag:Name"
    values = ["lanchonete_private_subnet_2"]
  }

  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.lanchonete_vpc.id]
  }

  filter {
    name   = "cidrBlock"
    values = ["10.0.4.0/24"]
  }
}

