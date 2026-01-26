package repository;

import model.PrecipitationRecordsModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import service.SsmService;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PrecipitationRecordsRepositoryTest {
    
    @Mock
    private DynamoDbTable<PrecipitationRecordsModel> table;
    @Mock
    private SsmService ssmService;
    
    private PrecipitationRecordsRepository repository;
    
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(ssmService.getSsmParameter("/windy/latitude")).thenReturn(anyString());
        when(ssmService.getSsmParameter("/windy/longitude")).thenReturn(anyString());
        
        repository = new PrecipitationRecordsRepository(
            ssmService.getSsmParameter("/windy/latitude"),
            ssmService.getSsmParameter("/windy/longitude")
        );
    }
    
    @Test
    public void shouldWriteRainingRecordWithLastAlertAt() {
        Map<String, Long> response = Map.of(
            "timestamp", 1700010800000L,
            "ptype-surface", 1L,
            "rh-surface", 75L
        );
        
        ArgumentCaptor<PrecipitationRecordsModel> captor = ArgumentCaptor.forClass(PrecipitationRecordsModel.class);
        
        repository.writePrecipitationResponseInDynamo(response);
        
        verify(table).putItem(captor.capture());
        PrecipitationRecordsModel record = captor.getValue();
        
        assertTrue(record.getIsRaining());
        assertEquals(1, record.getPType());
        assertEquals(75L, record.getHumidity());
        assertNotNull(record.getLastAlertAt());
        assertNotNull(record.getLocation());
    }
    
    @Test
    public void shouldWriteNonRainingRecordWithoutLastAlertAt() {
        Map<String, Long> response = Map.of(
            "timestamp", 1700010800000L,
            "ptype-surface", 0L,
            "rh-surface", 60L
        );
        
        ArgumentCaptor<PrecipitationRecordsModel> captor = ArgumentCaptor.forClass(PrecipitationRecordsModel.class);
        
        repository.writePrecipitationResponseInDynamo(response);
        
        verify(table).putItem(captor.capture());
        PrecipitationRecordsModel record = captor.getValue();
        
        assertFalse(record.getIsRaining());
        assertEquals(0, record.getPType());
        assertEquals(60L, record.getHumidity());
        assertNull(record.getLastAlertAt());
    }
}
