package handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import model.PrecipitationRecords;

import org.json.JSONObject;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

@Slf4j
public class PrecipitationAnalyzerLambda implements RequestHandler<Map<String, Object>, String> {
    private final String latitute = ;
    private final String longitute = ;
    private final int currentResponseBodyIndex = 1;
    private final String apiUrl = "https://api.windy.com/api/point-forecast/v2";
    private final HttpClient client = HttpClient.newHttpClient();
    private final SsmClient ssmClient = SsmClient.create();

    private final DynamoDbTable<PrecipitationRecords> table = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(DynamoDbClient.create())
            .build()
            .table("PrecipitationRecords", TableSchema.fromBean(PrecipitationRecords.class));

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            HttpResponse<String> response = createHttpRequest(apiUrl);
            log.info("Windy API request returned status {} with body: \n{}", 
                response.statusCode(), parseResponseBodyByIndex(response, currentResponseBodyIndex));

            log.info("Full Response Body: {}", response.body());

            Map<String, Long> currentResponse = parseResponseBodyByIndex(response, currentResponseBodyIndex);
            Map<String, Long> previousResponse = parseResponseBodyByIndex(response, 0);

            if (currentResponse.get("ptype-surface") != previousResponse.get("ptype-surface")) {
                log.info("Precipitation type changed from {} to {}. Writing response in PrecipitationRecords",
                    previousResponse.get("ptype-surface"), currentResponse.get("ptype-surface"));

                writePrecipitationResponseInDynamo(currentResponse);
            }
            

        } catch (IOException | InterruptedException exception) {
            log.error("HTTP Request failed with exception: {}", exception);
        }

        return "";
    }

    private HttpResponse<String> createHttpRequest(String apiUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(createRequestBody()))
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String createRequestBody() {
        JSONObject json = new JSONObject(
            "{\"lat\": " + latitute + ", " +
            "\"lon\": " + longitute + ", " +
            "\"model\": \"gfs\", " +
            "\"parameters\": [\"ptype\", \"rh\"], " +
            "\"levels\": [\"surface\"], " +
            "\"key\": \"" + getApiKey() + "\"}"
        );
        
        return json.toString();
    }

    private String getApiKey() {
        return ssmClient.getParameter(GetParameterRequest.builder()
                        .name("/windy/api-key")
                        .withDecryption(true)
                        .build())
                        .parameter()
                        .value();
    }

    private Map<String, Long> parseResponseBodyByIndex(HttpResponse<String> response, int index) throws IOException {
        final JsonNode root = new ObjectMapper().readTree(response.body());
        
        return Map.of(
            "timestamp", root.get("ts").get(index).asLong(),
            "ptype-surface", root.get("ptype-surface").get(index).asLong(),
            "rh-surface", root.get("rh-surface").get(index).asLong()
        );
    }

    private void writePrecipitationResponseInDynamo(Map<String, Long> responseBody) {
        PrecipitationRecords precipitationRecords = new PrecipitationRecords();

        precipitationRecords.setLocation(latitute + "," + longitute);
        precipitationRecords.setIsRaining(responseBody.get("ptype-surface") != 0);
        precipitationRecords.setLastAlertAt(Instant.ofEpochMilli(responseBody.get("timestamp")).toString());
        precipitationRecords.setPType(responseBody.get("ptype-surface").intValue());
        precipitationRecords.setHumidity(responseBody.get("rh-surface"));

        table.putItem(precipitationRecords);
    }
}
