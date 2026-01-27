package service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SmsNotificationServiceTest {
    
    @Mock
    private SnsClient snsClient;
    
    private SmsNotificationService service;
    private MockedStatic<SnsClient> snsClientMock;
    
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        snsClientMock = mockStatic(SnsClient.class);
        snsClientMock.when(SnsClient::create).thenReturn(snsClient);
    }
    
    @AfterEach
    public void cleanup() {
        if (snsClientMock != null) {
            snsClientMock.close();
        }
    }
    
    @Test
    public void shouldSendNotification() throws Exception {
        String message = "Test alert message";
        String testTopicArn = "arn:aws:sns:us-east-1:123456789012:test-topic";
        
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().build());
        
        service = new SmsNotificationService();
        
        Field topicArnField = SmsNotificationService.class.getDeclaredField("topicArn");
        topicArnField.setAccessible(true);
        topicArnField.set(service, testTopicArn);
        
        service.sendNotification(message);
        
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        
        PublishRequest request = captor.getValue();
        assertEquals(message, request.message());
        assertEquals(testTopicArn, request.topicArn());
    }
    
    @Test
    public void shouldNotSendNotificationWhenTopicArnIsNull() throws Exception {
        String message = "Test alert message";
        
        service = new SmsNotificationService();
        
        Field topicArnField = SmsNotificationService.class.getDeclaredField("topicArn");
        topicArnField.setAccessible(true);
        topicArnField.set(service, null);
        
        service.sendNotification(message);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        
        PublishRequest request = captor.getValue();
        assertEquals(message, request.message());
        assertNull(request.topicArn());
    }
}
