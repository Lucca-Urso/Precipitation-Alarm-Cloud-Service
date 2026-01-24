package service;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class SmsNotificationService {
    private final SnsClient snsClient;
    private final String topicArn;

    public SmsNotificationService() {
        this.snsClient = SnsClient.create();
        this.topicArn = System.getenv("SNS_TOPIC_ARN");
    }

    public void sendNotification(String message) {
        snsClient.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(message)
                .build());
    }
}
