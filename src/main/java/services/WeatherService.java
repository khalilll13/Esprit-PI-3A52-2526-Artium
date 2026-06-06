package services;

import utils.CoordinateUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherService {
    
    public record WeatherData(double temperature, double windSpeed, double rain) {}
    
    public Optional<WeatherData> getForecastForEvent(CoordinateUtils.Coordinates coords, LocalDateTime dateDebut) {
        if (coords == null || dateDebut == null) {
            return Optional.empty();
        }
        
        try {
            // Open-Meteo allows forecast up to 16 days.
            String dateStr = dateDebut.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String url = String.format("https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&hourly=temperature_2m,wind_speed_10m,rain&start_date=%s&end_date=%s", 
                                       coords.latitude(), coords.longitude(), dateStr, dateStr);
                                       
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                    
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            
            String body = response.body();
            
            // Extract the arrays using regex since we don't have a JSON library
            String times = extractJsonArray(body, "\"time\"");
            String temps = extractJsonArray(body, "\"temperature_2m\"");
            String winds = extractJsonArray(body, "\"wind_speed_10m\"");
            String rains = extractJsonArray(body, "\"rain\"");
            
            if (times == null || temps == null || winds == null || rains == null) {
                return Optional.empty();
            }
            
            String[] timeArray = times.split(",");
            String[] tempArray = temps.split(",");
            String[] windArray = winds.split(",");
            String[] rainArray = rains.split(",");
            
            // Find the hour index matching the event's start time
            String targetHour = dateDebut.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"));
            int targetIndex = 0; // Default to midnight if exact hour not found
            for (int i = 0; i < timeArray.length; i++) {
                if (timeArray[i].replace("\"", "").trim().equals(targetHour)) {
                    targetIndex = i;
                    break;
                }
            }
            
            if (targetIndex >= tempArray.length || targetIndex >= windArray.length || targetIndex >= rainArray.length) {
                return Optional.empty();
            }
            
            // Parse values (accounting for potential nulls in weather data if too far in future)
            String tempStr = tempArray[targetIndex].trim();
            String windStr = windArray[targetIndex].trim();
            String rainStr = rainArray[targetIndex].trim();
            
            if (tempStr.equals("null") || windStr.equals("null") || rainStr.equals("null")) {
                return Optional.empty();
            }
            
            double t = Double.parseDouble(tempStr);
            double w = Double.parseDouble(windStr);
            double r = Double.parseDouble(rainStr);
            
            return Optional.of(new WeatherData(t, w, r));
            
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
    
    private String extractJsonArray(String json, String key) {
        Pattern pattern = Pattern.compile(key + "\\s*:\\s*\\[(.*?)\\]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
