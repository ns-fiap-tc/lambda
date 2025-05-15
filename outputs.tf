# Output para o endpoint HTTPS do API Gateway
output "api_gateway_endpoint" {
  value = "https://${data.aws_api_gateway_rest_api.lanchonete_cluster_api_gw.id}.execute-api.us-east-1.amazonaws.com/${aws_api_gateway_stage.prod_stage.stage_name}"
  description = "Endpoint HTTPS global do API Gateway para acessar os recursos da API."
}

output "iam_role_arn" {
  description = "ARN do LabRole para gerenciar as permissões"
  value = data.aws_iam_role.labrole.arn
}

output "lanchonete_api_url" {
  value = data.kubernetes_service.service-lanchonete-app.status[0].load_balancer[0].ingress[0].hostname
  description = "Endpoint público do FastFood API."
}

output "payment_api_url" {
  value = data.kubernetes_service.service-pagamento-mock.status[0].load_balancer[0].ingress[0].hostname
  description = "Endpoint público do Payment API."
}