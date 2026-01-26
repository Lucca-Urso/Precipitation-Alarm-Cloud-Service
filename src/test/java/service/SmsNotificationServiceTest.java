package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SmsNotificationServiceTest {
    
    @Mock
    private SnsClient snsClient;
    
    private SmsNotificationService service;
    
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        service = new SmsNotificationService();
    }
    
    @Test
    public void shouldSendNotification() {
        String message = "Test alert message";
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().build());
        
        service.sendNotification(message);
        
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        
        PublishRequest request = captor.getValue();
        assertEquals(message, request.message());
        assertNotNull(request.topicArn());
    }
}
