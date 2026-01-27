package handler;

import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import repository.PrecipitationRecordsRepository;
import service.SmsNotificationService;
import service.SsmService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PrecipitationAnalyzerLambdaTest {
    
    @Mock
    private HttpClient httpClient;
    @Mock
    private HttpResponse<String> httpResponse;
    @Mock
    private PrecipitationRecordsRepository repository;
    @Mock
    private SmsNotificationService notificationService;
    @Mock
    private SsmService ssmService;
    @Mock
    private Context context;
    
    private PrecipitationAnalyzerLambda lambda;
    
    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        lambda = new PrecipitationAnalyzerLambda();
        
        Field clientField = PrecipitationAnalyzerLambda.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(lambda, httpClient);
        
        Field repoField = PrecipitationAnalyzerLambda.class.getDeclaredField("precipitationRecords");
        repoField.setAccessible(true);
        repoField.set(lambda, repository);
        
        Field notificationField = PrecipitationAnalyzerLambda.class.getDeclaredField("notificationService");
        notificationField.setAccessible(true);
        notificationField.set(lambda, notificationService);
    }

    @Test
    public void shouldWritePrecipitationResponseAndSendAlert() throws IOException, InterruptedException {
        long currentTime = System.currentTimeMillis();
        long threeHoursAgo = currentTime - 10800000;
        
        String responseBody = String.format(
            "{\"ts\":[%d,%d],\"ptype-surface\":[0,1],\"rh-surface\":[60,70]}", threeHoursAgo, currentTime
        );
        
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        
        lambda.handleRequest(Map.of(), context);
        
        verify(repository).writePrecipitationResponseInDynamo(any());
        verify(notificationService).sendNotification(anyString());
    }

    @Test
    public void shouldOnlyWritePrecipitationResponse() throws IOException, InterruptedException {
        long currentTime = System.currentTimeMillis();
        long threeHoursAgo = currentTime - 10800000;
        
        String responseBody = String.format(
            "{\"ts\":[%d,%d],\"ptype-surface\":[1,2],\"rh-surface\":[60,70]}", threeHoursAgo, currentTime
        );
        
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        
        lambda.handleRequest(Map.of(), context);
        
        verify(repository).writePrecipitationResponseInDynamo(any()); 
        verify(notificationService, never()).sendNotification(anyString());
    }

    @Test
    public void shoudNotWritePrecipitationResponse() throws IOException, InterruptedException {
        long currentTime = System.currentTimeMillis();
        long threeHoursAgo = currentTime - 10800000;
        
        String responseBody = String.format(
            "{\"ts\":[%d,%d],\"ptype-surface\":[1,1],\"rh-surface\":[60,70]}", threeHoursAgo, currentTime
        );
        
        when(httpResponse.body()).thenReturn(responseBody);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        
        lambda.handleRequest(Map.of(), context);
        
        verify(repository, never()).writePrecipitationResponseInDynamo(any());
        verify(notificationService, never()).sendNotification(anyString());
    }

    @Test
    public void shouldThrowExceptionWhenParsingPrecipitationResponse() throws IOException, InterruptedException {
        when(httpResponse.body()).thenReturn("invalid json");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        
        lambda.handleRequest(Map.of(), context);
        
        verify(repository, never()).writePrecipitationResponseInDynamo(any());
        verify(notificationService, never()).sendNotification(anyString());
    }

    @Test
    public void shouldHandleHttpClientException() throws IOException, InterruptedException {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("Connection failed"));
        
        lambda.handleRequest(Map.of(), context);
        
        verify(repository, never()).writePrecipitationResponseInDynamo(any());
        verify(notificationService, never()).sendNotification(anyString());
    }
}
