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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@Component
@EnableScheduling
public class LiveData {

    @Autowired
    private RabbitTemplate rabbitTemplate;

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
        RestTemplate restTemplate = new RestTemplate();
        for (int i = 0; i < FLIGHT_STATUS.size(); i++) {
            String url = API_URL + apiKey + "&dep_icao=" + DEP_ICAO + "&flight_status=" + FLIGHT_STATUS.get(i);
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                String jsonData = response.getBody();
                rabbitTemplate.convertAndSend(QUEUE_NAME, jsonData);
                System.out.println("Data sent to queue");
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                System.err.println("Error accessing API: " + e.getRawStatusCode() + " - " + e.getStatusText());
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
            }
        }
    }
}