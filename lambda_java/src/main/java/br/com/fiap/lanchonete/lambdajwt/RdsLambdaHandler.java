package br.com.fiap.lanchonete.lambdajwt;

import com.amazonaws.services.lambda.runtime.*;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class RdsLambdaHandler implements RequestHandler <APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private LambdaLogger logger;
    private Connection conn = null;
    private String dbSchema = "lanch";
    private String jwt = null;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        logger = context.getLogger();
        logger.log("Lambda invocada.");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        String cpf = obterCpfRequisicao(requestEvent);
        boolean ehRequestPedido = requestEvent.getPath().startsWith("/pedido-service");
        if(ehRequestPedido){
            if (cpf.isEmpty()){
                return response.withStatusCode(400).withBody("CPF deve ser informado");
            }

            verificarCpfNaBaseDeDados(response,cpf);
            if (Objects.nonNull(response.getStatusCode()) && response.getStatusCode() >= 400){
                return response;
            }
        }
        return fazerRequisicaoApi(obterUrlApi(requestEvent),requestEvent,ehRequestPedido);
    }

    private void verificarCpfNaBaseDeDados(APIGatewayProxyResponseEvent response, String cpf){
        conectarAoBancoDeDados(response);
        validarCpfEGerarJwt(cpf,response);
    }

    private String obterUrlApi(APIGatewayProxyRequestEvent request){
        String urlApp = System.getenv("IP_EXTERNO_LANCHONETE_APP") != null ? System.getenv("IP_EXTERNO_LANCHONETE_APP") : "default-elb";
        String urlMockPagamento = System.getenv("IP_EXTERNO_PAGAMENTO_MOCK_HOST") != null ? System.getenv("IP_EXTERNO_PAGAMENTO_MOCK_HOST") : "default-elb";
        return request.getPath().startsWith("/pagamento-mock-service")?urlMockPagamento:urlApp;
    }

    private void conectarAoBancoDeDados(APIGatewayProxyResponseEvent response){
        String dbHost = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "your-rds-endpoint.amazonaws.com";
        String dbName = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "your_database_name";
        String dbUser = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "your_database_user";
        String dbPassword = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "your_database_password";
        String dbUrl = "jdbc:postgresql://" + dbHost + "/" + dbName+"?useSSL=true&requireSSL=true";
        try {
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            logger.log("Antes de verificar conexão: "+conn);
            if (!conn.isValid(0)) {
                logger.log("Failed to connect to RDS: " + dbUrl);
                response.withStatusCode(500).withBody("Erro ao conectar ao banco de dados");
            }
        } catch (SQLException e) {
            logger.log("Failed to connect to RDS: " + dbUrl);
            response.withStatusCode(500).withBody("Erro ao conectar ao banco de dados");
        }

        //this.checkIfSchemaExists(response);
    }

    private String obterCpfRequisicao(APIGatewayProxyRequestEvent requestEvent) {
        String cpf = "";
        Map<String, String> queryStringParameters = requestEvent.getQueryStringParameters();
        if (queryStringParameters != null && queryStringParameters.containsKey("cpf")) {
            cpf = queryStringParameters.get("cpf");
            logger.log("CPF obtido do query string da requisição: " + cpf);
        }
        return cpf;
    }

    private void checkIfSchemaExists(APIGatewayProxyResponseEvent response) {
        logger.log("Checking if schema exists...");
        String createSchemaStmt = "CREATE SCHEMA IF NOT EXISTS " + dbSchema + " AUTHORIZATION lanchuser";
        Statement statement = null;
        try {
            statement = conn.createStatement();
            statement.execute(createSchemaStmt);
            String createTableStmt = "CREATE TABLE IF NOT EXISTS " + dbSchema + ".tb_cliente (nr_cpf VARCHAR(11) PRIMARY KEY)";
            statement.execute(createTableStmt);
            String insertStmt = "INSERT INTO " + dbSchema + ".tb_cliente (nr_cpf) VALUES ('12345678901') ON CONFLICT DO NOTHING";
            statement.execute(insertStmt);
        } catch (SQLException e) {
            response.withStatusCode(500);
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    response.withStatusCode(500);
                }
            }
        }
    }

    private void validarCpfEGerarJwt(String cpf,APIGatewayProxyResponseEvent response){
        String query = "SELECT nr_cpf FROM " + dbSchema + ".tb_cliente WHERE nr_cpf = ?";
        logger.log("SQL query :"+query);
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, cpf);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                this.jwt = generateJwt(cpf);
            } else {
                response.withStatusCode(400).withBody("CPF não encontrado");
            }
        } catch (SQLException | JsonProcessingException e) {
            response.withStatusCode(500).withBody("Erro ao buscar CPF no banco");
        }
    }

    private String generateJwt(String cpf) throws JsonProcessingException {
        logger.log("Entrou no método de gerar JWT.");
        try {
            String jwt2 = JWT.create()
                    .withIssuer("LanchoneteApp")
                    .withSubject("UserAuthentication")
                    .withClaim("cpf", cpf)
                    .withExpiresAt(new Date(System.currentTimeMillis() + 3600 * 1000)) // 1-hour expiration
                    .sign(Algorithm.HMAC256(this.getJwtSecret()));
            logger.log("JWT gerado: "+jwt2);
            return jwt2;
        }catch (Exception e){
            logger.log("Erro ao gerar JWT: "+e);
            return null;
        }
    }

    private String getJwtSecret() throws JsonProcessingException {
        String jwtSecretName = System.getenv("JWT_SECRET_NAME") != null ? System.getenv("JWT_SECRET_NAME") : "name-jwt-secret";
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard().build();
        GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(jwtSecretName);
        GetSecretValueResult result = client.getSecretValue(request);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> secretMap = objectMapper.readValue(result.getSecretString(), Map.class);
        return secretMap.get("jwt-key");
    }

    private APIGatewayProxyResponseEvent fazerRequisicaoApi(String urlString, APIGatewayProxyRequestEvent requestEvent, boolean ehRequestPedido){
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Content-Type", "application/json"); // Define um Content-Type padrão para a resposta

        if (urlString == null || urlString.isEmpty()) {
            logger.log("Variável de ambiente com a URL da aplicação não configurada.");
            return response
                    .withStatusCode(500)
                    .withHeaders(responseHeaders)
                    .withBody("Erro de configuração: URL do Eks não definida.");
        }

        try {
            // Construir a URL do endpoint no EKS preservando query parameters da requisição original
            StringBuilder eksUrlBuilder = new StringBuilder(urlString);
            eksUrlBuilder.append(requestEvent.getPath());

            // Adiciona query parameters da requisição original
            if (requestEvent.getQueryStringParameters() != null && !requestEvent.getQueryStringParameters().isEmpty()) {
                eksUrlBuilder.append("?");
                eksUrlBuilder.append(requestEvent.getQueryStringParameters().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&")));
            }

            URL url = new URL(eksUrlBuilder.toString());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Configurar o método HTTP da requisição original
            connection.setRequestMethod(requestEvent.getHttpMethod());
            connection.setDoOutput(true); // Permitir envio de corpo para métodos como POST, PUT
            connection.setDoInput(true);  // Permitir leitura da resposta

            //Adicionar o token aos cabeçalhos de requisições de /pedido-service
            if(ehRequestPedido){
                logger.log("recebendo JWT para setar no header: "+jwt);
                connection.setRequestProperty("Authorization", "Bearer " + this.jwt);
            }

            //Copiar outros cabeçalhos relevantes da requisição original para a nova requisição
            // Evitando copiar cabeçalhos que são específicos do API Gateway ou internos da AWS
            if (requestEvent.getHeaders() != null) {
                for (Map.Entry<String, String> entry : requestEvent.getHeaders().entrySet()) {
                    String headerName = entry.getKey();
                    String headerValue = entry.getValue();

                    // Lista de cabeçalhos a serem ignorados (comuns do API Gateway/Lambda)
                    if (!headerName.equalsIgnoreCase("X-Amz-Security-Token") &&
                            !headerName.equalsIgnoreCase("X-Amzn-Trace-Id") &&
                            !headerName.equalsIgnoreCase("Host") &&
                            !headerName.equalsIgnoreCase("User-Agent") &&
                            !headerName.equalsIgnoreCase("X-Forwarded-For") &&
                            !headerName.equalsIgnoreCase("X-Forwarded-Proto") &&
                            !headerName.equalsIgnoreCase("X-Forwarded-Port")) {
                        connection.setRequestProperty(headerName, headerValue);
                    }
                }
            }

            // Copiar o corpo da requisição original, se houver
            String requestBody = requestEvent.getBody();
            if (requestBody != null && !requestBody.isEmpty() && connection.getDoOutput()) {
                // Definir Content-Type para o corpo da requisição enviada ao EKS
                String originalContentType = requestEvent.getHeaders().getOrDefault("Content-Type", "application/json");
                connection.setRequestProperty("Content-Type", originalContentType);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
            }

            //Fazer a chamada ao endpoint do EKS e ler a resposta
            int statusCode = connection.getResponseCode();
            logger.log("Status code da resposta do EKS: " + statusCode);

            BufferedReader in;
            if (statusCode >= 200 && statusCode < 300) {
                in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            } else {
                in = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"));
            }

            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            connection.disconnect();

            //Replicar os cabeçalhos da resposta do EKS para a resposta do API Gateway
            Map<String, String> eksResponseHeaders = new HashMap<>();
            connection.getHeaderFields().forEach((key, values) -> {
                if (key != null && values != null && !values.isEmpty()) {
                    eksResponseHeaders.put(key, values.get(0)); // Pega apenas o primeiro valor se houver múltiplos
                }
            });
            response.setHeaders(eksResponseHeaders); // Define os cabeçalhos da resposta do EKS

            //Construir a resposta para o API Gateway
            return response
                    .withStatusCode(statusCode)
                    .withBody(content.toString());
        } catch (Exception e) {
            return response
                    .withHeaders(responseHeaders)
                    .withStatusCode(500)
                    .withBody("Erro interno ao processar requisição para EKS.");
        }
    }
}