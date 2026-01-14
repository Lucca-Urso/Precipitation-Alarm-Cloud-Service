package main.java.example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

@Slf4j
public class PrecipitationAnalyzerLambda implements RequestHandler<Map<String, Object>, String> {
    private final String apiUrl = "https://api.windy.com/api/point-forecast/v2";
    private final HttpClient client = HttpClient.newHttpClient();
    private final SsmClient ssmClient = SsmClient.create();

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            HttpResponse<String> response = createHttpRequest(apiUrl);
            log.info("Windy API request returned status {} with body: \n{}", 
                response.statusCode(), parseResponseBody(response));

            Map<String, Long> responseBody = parseResponseBody(response);

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
            "{\"lat\": CORDLAT, " +
            "\"lon\": CORDLON, " +
            "\"model\": \"gfs\", " +
            "\"parameters\": [\"ptype\", \"rh\"], " +
            "\"levels\": [\"surface\"], " +
            "\"key\": "+ getApiKei() + "}"
        );
        
        return json.toString();
    }

    private String getApiKei() {
        return ssmClient.getParameter(GetParameterRequest.builder()
                        .name("/windy/api-key")
                        .withDecryption(true)
                        .build())
                        .parameter()
                        .value();
    }

    private Map<String, Long> parseResponseBody(HttpResponse<String> response) throws IOException {
        JsonNode root = new ObjectMapper().readTree(response.body());
        
        return Map.of(
            "timestamp", root.at("/ts/0").asLong(),
            "ptype-surface", root.at("/ptype-surface/0").asLong(),
            "rh-surface", root.at("/rh-surface/0").asLong()
        );
    }
}
