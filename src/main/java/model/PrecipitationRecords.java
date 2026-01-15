package model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import lombok.Data;

@Data
@DynamoDbBean
public class PrecipitationRecords {
    private String location;
    private Boolean isRaining;
    private String lastAlertAt;
    private Integer pType;
    private Long humidity;

    @DynamoDbPartitionKey
    public String getLocation() {
        return location;
    }
}
