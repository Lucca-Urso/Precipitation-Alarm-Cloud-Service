package service;

import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.SsmClient;

public class SsmService {
    private final SsmClient ssmClient = SsmClient.create();

    public String getSsmParameter(String parameterName) {
        return ssmClient.getParameter(GetParameterRequest.builder()
                        .name(parameterName)
                        .withDecryption(true)
                        .build())
                        .parameter()
                        .value();
    }
}
