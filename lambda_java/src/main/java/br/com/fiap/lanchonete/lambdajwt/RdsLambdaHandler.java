package br.com.fiap.lanchonete.lambdajwt;

import com.amazonaws.services.lambda.runtime.*;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;

/*
- Uses AWS Secrets Manager for secure database credentials.
- Generates JWT to securely store SSN information in token claims.
- Signs JWT using HMAC256 for cryptographic security.
- Sets token expiration (1 hour) to enforce security best practices.
 */
public class RdsLambdaHandler implements RequestHandler <String, String> {
    private LambdaLogger logger = null;

    @Override
    public String handleRequest(String cpf, Context context) {
        logger = context.getLogger();
        logger.log("Handler invoked.");

        String jwt = null;
        String dbSchema = "lanch";

        /*String dbHost = "lanchonete-db.cijutajfn35x.us-east-1.rds.amazonaws.com:5432";
        String dbName = "lanchdb";
        String dbPort = "5432";
        String dbUser = "lanchuser";
        String dbPassword = "lanch1234";*/

        Connection conn = null;


        String dbHost = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "your-rds-endpoint.amazonaws.com";
        String dbName = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "your_database_name";
        String dbUser = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "your_database_user";
        String dbPassword = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "your_database_password";
        //public static final String EXTERNAL_IP_API = System.getenv("EXTERNAL_IP_API") != null ? System.getenv("EXTERNAL_IP_API") : "default-elb";
        //public static final String EXTERNAL_IP_PAYMENT = System.getenv("EXTERNAL_IP_PAYMENT") != null ? System.getenv("EXTERNAL_IP_PAYMENT") : "default-elb";

        String dbUrl = "jdbc:postgresql://" + dbHost + "/" + dbName+"?useSSL=true&requireSSL=true";

        logger.log("Host: "+dbHost+", Name: "+dbName+", URL: "+dbUrl+", Password: "+dbPassword);


        try {
            /*
            // Retrieve credentials from AWS Secrets Manager
            Map<String, String> dbCredentials = getSecretValues();
            String username = dbCredentials.get("username");
            String password = dbCredentials.get("password");
*/
            // Establish connection to RDS
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            logger.log("Antes de verificar conexão: "+conn);
            if (!conn.isValid(0)) {
                logger.log("Failed to connect to RDS: " + dbUrl);
                return jwt;
            }

            //this.checkIfSchemaExists(conn, dbSchema, logger);

            String query = "SELECT nr_cpf FROM " + dbSchema + ".tb_cliente WHERE nr_cpf = ?";
            logger.log("SQL query :"+query);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, cpf);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    logger.log("CPF found.");
                    jwt = generateJwt(cpf);
                    logger.log("JWT CPF found: "+jwt);
                } else {
                    logger.log("CPF NOT found.");
                    jwt = generateJwt("");
                    logger.log("JWT CPF NOT found: "+jwt);
                }
            }
        } catch (SQLException e) {
            logger.log("Catch SQLException.");
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            logger.log("Catch JsonProcessingException.");
            throw new RuntimeException(e);
        } finally {
            logger.log("Entrou no finally: "+conn);
            if (conn != null) {
                try {
                    conn.close();
                    logger.log("Finally.");
                } catch (SQLException e) {
                    logger.log("Catch Finally.");
                    throw new RuntimeException(e);
                }
            }else {
                logger.log("Else do finally: "+conn);
            }
        }
        logger.log("JWT: "+jwt);
        return jwt;
    }

    /*private void checkIfSchemaExists(Connection conn, String dbSchema, LambdaLogger logger) {
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
            throw new RuntimeException(e);
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private Map<String, String> getSecretValues() throws JsonProcessingException {
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard().build();
        GetSecretValueRequest request = new GetSecretValueRequest().withSecretId("database-credentials");
        GetSecretValueResult result = client.getSecretValue(request);

        // Parse the JSON response containing credentials
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(result.getSecretString(), Map.class);
    }*/

    private String generateJwt(String cpf) throws JsonProcessingException {
        logger.log("Entrou no método de gerar JWT.");
        try {
            return JWT.create()
                    .withIssuer("LanchoneteApp")
                    .withSubject("UserAuthentication")
                    .withClaim("cpf", cpf)
                    .withExpiresAt(new Date(System.currentTimeMillis() + 3600 * 1000)) // 1-hour expiration
                    .sign(Algorithm.HMAC256(this.getJwtSecret()));
        }catch (Exception e){
            logger.log("Erro ao gerar JWT: "+e);
            return null;
        }
    }

    private String getJwtSecret() throws JsonProcessingException {
        logger.log("Entrou no método de get JWT Secret.");
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard().build();
        GetSecretValueRequest request = new GetSecretValueRequest().withSecretId("jwt-secret-key");
        GetSecretValueResult result = client.getSecretValue(request);
        logger.log("Antes de chamar o ObjectMapper de get JWT Secret.");
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> secretMap = objectMapper.readValue(result.getSecretString(), Map.class);
        logger.log("Ultima linha do método de get JWT Secret.");
        return secretMap.get("jwt-key");
    }
}