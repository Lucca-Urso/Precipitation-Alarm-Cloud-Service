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
import java.util.stream.IntStream;

import lombok.extern.slf4j.Slf4j;
import repository.PrecipitationRecordsRepository;
import service.SmsNotificationService;
import service.SsmService;

import org.json.JSONObject;

@Slf4j
public class PrecipitationAnalyzerLambda implements RequestHandler<Map<String, Object>, String> {
    private SsmService ssmService = new SsmService();

    private final String latitude = ssmService.getSsmParameter("/windy/latitude");
    private final String longitude = ssmService.getSsmParameter("/windy/longitude");

    private final long threeHoursInMili = 10800000;
    private final long currentDate = Instant.now().toEpochMilli();

    private final String apiUrl = "https://api.windy.com/api/point-forecast/v2";
    private final HttpClient client = HttpClient.newHttpClient();

    private PrecipitationRecordsRepository precipitationRecords = new PrecipitationRecordsRepository(latitude, longitude);
    private SmsNotificationService notificationService = new SmsNotificationService();

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            HttpResponse<String> response = createHttpRequest(apiUrl);
            Map<String, Long> currentResponse = parseResponseBodyByCurrentDate(response, currentDate);
            Map<String, Long> previousResponse = parseResponseBodyByCurrentDate(response, currentDate - threeHoursInMili);

            log.info("Windy API request returned status {} with body: \n{}", 
                response.statusCode(), currentResponse);

            if (currentResponse.get("ptype-surface") != previousResponse.get("ptype-surface")) {
                log.info("Precipitation type changed from {} to {}. Writing response in PrecipitationRecords",
                    previousResponse.get("ptype-surface"), currentResponse.get("ptype-surface"));

                precipitationRecords.writePrecipitationResponseInDynamo(currentResponse);

                if (previousResponse.get("ptype-surface") == 0 && currentResponse.get("ptype-surface") != 0) {
                    log.info("Precipitation has been detected in coordinates ({}, {}). Sending SMS alert to {}", 
                        latitude, longitude, System.getenv("PHONE_NUMBER"));
                    notificationService.sendNotification(
                        "WEATHER ALERT: Precipitation detected in your area, expected within the next 3 hours." + 
                        "Avoid open areas, trees, and flood-prone zones. Remain in safe, sheltered locations and follow official guidance."
                    );
                }
            }
            else {
                log.info("Precipitation type has not changed.");
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
            "{\"lat\": " + latitude + ", " +
            "\"lon\": " + longitude + ", " +
            "\"model\": \"gfs\", " +
            "\"parameters\": [\"ptype\", \"rh\"], " +
            "\"levels\": [\"surface\"], " +
            "\"key\": \"" + ssmService.getSsmParameter("/windy/api-key") + "\"}"
        );
        
        return json.toString();
    }

    private Map<String, Long> parseResponseBodyByCurrentDate(HttpResponse<String> response, long currentDate) throws IOException {
        final JsonNode root = new ObjectMapper().readTree(response.body());
        final JsonNode timestamps = root.get("ts");
        
        int index = IntStream.range(0, timestamps.size())
            .filter(i -> timestamps.get(i).asLong() >= currentDate)
            .findFirst()
            .orElse(0);
        
        return Map.of(
            "timestamp", timestamps.get(index).asLong(),
            "ptype-surface", root.get("ptype-surface").get(index).asLong(),
            "rh-surface", root.get("rh-surface").get(index).asLong()
        );
    }
}
