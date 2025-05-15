package br.com.fiap.lanchonete.lambdajwt;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

// (A classe MockLambdaContext é a mesma do exemplo anterior e precisa estar presente)
public class MockLambdaContext implements Context {
    @Override
    public String getAwsRequestId() {
        return "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx";
    }

    @Override
    public String getLogGroupName() {
        return "/aws/lambda/YourFunctionName";
    }

    @Override
    public String getLogStreamName() {
        return "2025/05/15/[$LATEST]yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy";
    }

    @Override
    public String getFunctionName() {
        return "YourFunctionName";
    }

    @Override
    public String getFunctionVersion() {
        return "$LATEST";
    }

    @Override
    public String getInvokedFunctionArn() {
        return "arn:aws:lambda:sa-east-1:123456789012:function:YourFunctionName";
    }

    @Override
    public CognitoIdentity getIdentity() {
        return null;
    }

    @Override
    public ClientContext getClientContext() {
        return null;
    }

    @Override
    public int getRemainingTimeInMillis() {
        return 300000;
    }

    @Override
    public int getMemoryLimitInMB() {
        return 0;
    }

    @Override
    public LambdaLogger getLogger() {
        return new LambdaLogger() {
            @Override
            public void log(String message) {
                System.out.println("[LOG] " + message);
            }

            @Override
            public void log(byte[] message) {
                System.out.println("[LOG (bytes)] " + new String(message));
            }
        };
    }
}

