package repository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import model.PrecipitationRecordsModel;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class PrecipitationRecordsRepository {
    private String latitude;
    private String longitude;
    private final long threeHoursInMili = 10800000;

    private final DynamoDbTable<PrecipitationRecordsModel> table = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(DynamoDbClient.create())
        .build()
        .table(System.getenv("DYNAMODB_TABLE_NAME"), TableSchema.fromBean(PrecipitationRecordsModel.class));

    public PrecipitationRecordsRepository(String latitute, String longitute) {
        this.latitude = latitute;
        this.longitude = longitute;
    }

    public void writePrecipitationResponseInDynamo(Map<String, Long> responseBody) {
        PrecipitationRecordsModel precipitationRecords = new PrecipitationRecordsModel();
        final Boolean isRaining = responseBody.get("ptype-surface") != 0;
        final String locationId = UUID.randomUUID().toString();

        precipitationRecords.setLocation(String.format("Weather Alert %s: lat:%s, long:%s", locationId, latitude, longitude));
        precipitationRecords.setIsRaining(isRaining);
        precipitationRecords.setPType(responseBody.get("ptype-surface").intValue());
        precipitationRecords.setHumidity(responseBody.get("rh-surface"));

        if (isRaining) {
            precipitationRecords.setLastAlertAt(Instant.ofEpochMilli(responseBody.get("timestamp") - threeHoursInMili).toString());
        }

        table.putItem(precipitationRecords);
    }
}
