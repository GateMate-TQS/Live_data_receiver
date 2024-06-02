package gatemate;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Bean;

import java.util.List;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@Component
@EnableScheduling
public class LiveData {

    
    private RabbitTemplate rabbitTemplate;

    private RestTemplate restTemplate;

    @Autowired
    public LiveData(RabbitTemplate rabbitTemplate , RestTemplate restTemplate){
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = restTemplate;
    }

    @Value("${api.key}")
    private String apiKey;

    private static final String DEP_ICAO = "LPPT";
    private static final List<String> FLIGHT_STATUS = List.of("active", "scheduled");
    private static final String API_URL = "http://api.aviationstack.com/v1/flights?access_key=";

    private static final String QUEUE_NAME = "flight-data";

    @Bean
    public Queue queue() {
        return new Queue(QUEUE_NAME, false);
    }

    @Scheduled(fixedRate = 60000000) // every minute
    public void fetchDataAndSendToQueue() {
        System.out.println("Fetching data from API");
        for (String status : FLIGHT_STATUS) {
            String jsonData = fetchFlightData(restTemplate, status); // Call the extracted method
            if (jsonData != null) {
                rabbitTemplate.convertAndSend(QUEUE_NAME, jsonData);
                System.out.println("Data sent to queue");
            }
        }
    }

    // Extracted method for API call
    protected String fetchFlightData(RestTemplate restTemplate, String flightStatus) {
        String url = API_URL + apiKey + "&dep_icao=" + DEP_ICAO + "&flight_status=" + flightStatus;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("Error accessing API: " + e.getRawStatusCode() + " - " + e.getStatusText());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
        return null;
    }
}