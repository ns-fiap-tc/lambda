locals {
  db_user     = data.kubernetes_secret.secrets-lanchonete.data["DB_USER"]
  db_password = data.kubernetes_secret.secrets-lanchonete.data["DB_PASSWORD"]
  db_name     = data.kubernetes_secret.secrets-lanchonete.data["DB_NAME"]
}

resource "aws_db_subnet_group" "lanchonete_lambda_subnet_group" {
  name       = "lanchonete_lambda_subnet_group"
  subnet_ids = [
    data.aws_subnet.lanchonete_private_subnet_1.id,
    data.aws_subnet.lanchonete_private_subnet_2.id
  ]

  tags = {
    Name = "Database Subnet Group"
  }
}

resource "aws_security_group" "lanchonete_lambda_sg" {
  name        = "lanchonete_lambda_sg"
  description = "Security group para o lambda"
  vpc_id      = data.aws_vpc.lanchonete_vpc.id

  # Regras de segurança (exemplo para permitir tráfego de dentro da VPC)
  egress {
    cidr_blocks = ["0.0.0.0/0"]
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
  }

  ingress {
    cidr_blocks = ["0.0.0.0/0"] 
    from_port   = 0  
    to_port     = 0
    protocol    = "-1"
  }

   tags = {
     Name = "lanchonete_lambda_sg"
   }
}

# Cria recurso lambda
resource "aws_lambda_function" "lanchonete_lambda" {
  function_name = "lanchonete_lambda"
  runtime       = "java17" 
  handler       = "br.com.fiap.lanchonete.lambdajwt.RdsLambdaHandler::handleRequest"
  role          = data.aws_iam_role.labrole.arn
  filename      = "./lambda_java/target/lambda-1.0-SNAPSHOT-jar-with-dependencies.jar"
  timeout	= 120

  depends_on = [
    aws_security_group.lanchonete_lambda_sg
  ]

  vpc_config {
    subnet_ids         = [data.aws_subnet.lanchonete_private_subnet_1.id,
                          data.aws_subnet.lanchonete_private_subnet_2.id]
    security_group_ids = [aws_security_group.lanchonete_lambda_sg.id/* ??? eks_security_group ???*/]
  }

  environment {
    variables = {
      IP_EXTERNO_LANCHONETE_APP       = "http://${data.kubernetes_service.service-lanchonete-app.status[0].load_balancer[0].ingress[0].hostname}:80"
      IP_EXTERNO_PAGAMENTO_MOCK_HOST  = "http://${data.kubernetes_service.service-pagamento-mock.status[0].load_balancer[0].ingress[0].hostname}:8081"
      DB_NAME                         = local.db_name
      DB_USER                         = local.db_user
      DB_PASSWORD                     = local.db_password
      DB_HOST                         = data.aws_db_instance.lanchonete_db.endpoint
      JWT_SECRET_NAME                 = data.aws_secretsmanager_secret.jwt-secret-key.name 
    }
  }
}

# Recurso dinâmico base "/{proxy+}"
resource "aws_api_gateway_resource" "proxy" {
  rest_api_id = data.aws_api_gateway_rest_api.lanchonete_cluster_api_gw.id
  parent_id   = data.aws_api_gateway_rest_api.lanchonete_cluster_api_gw.root_resource_id
  path_part   = "{proxy+}"
}

# Método para todas as requisições
resource "aws_api_gateway_method" "proxy_method" {
  rest_api_id   = data.aws_api_gateway_rest_api.lanchonete_cluster_api_gw.id
  resource_id   = aws_api_gateway_resource.proxy.id
  http_method   = "ANY"
  authorization = "NONE"
}

# Integração com o Lambda
resource "aws_api_gateway_integration" "lambda_integration" {
  rest_api_id = data.aws_api_gateway_rest_api.lanchonete_cluster_api_gw.id
  resource_id = aws_api_gateway_resource.proxy.id
  http_method = aws_api_gateway_method.proxy_method.http_method

  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.lanchonete_lambda.invoke_arn
}

# Permissão para o Lambda ser invocado pelo API Gateway
resource "aws_lambda_permission" "api_gateway_permission" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.lanchonete_lambda.arn
  principal     = "apigateway.amazonaws.com"
}

resource "aws_api_gateway_deployment" "lanchonete_api_deployment" {
  rest_api_id = data.aws_api_gateway_rest_api.lanchonete_cluster_api_gw.id

  depends_on = [
    aws_api_gateway_method.proxy_method, aws_api_gateway_integration.lambda_integration
  ]
}

resource "aws_api_gateway_stage" "prod_stage" {
  deployment_id = aws_api_gateway_deployment.lanchonete_api_deployment.id
  rest_api_id   = data.aws_api_gateway_rest_api.lanchonete_cluster_api_gw.id
  stage_name    = "prod"
}
